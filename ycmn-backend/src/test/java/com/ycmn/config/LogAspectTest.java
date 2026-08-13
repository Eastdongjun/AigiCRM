package com.ycmn.config;

import com.ycmn.module.system.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LogAspect 单元测试（纯 mock，无 Spring 上下文）：
 * - 失败操作(写方法抛异常)也必须写 sys_log——安全审计闭环；
 * - @YcmnLog 注解值覆盖 URL 推断的 module/action。
 */
class LogAspectTest {

    private SysLogService logService;
    private LogAspect aspect;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        logService = mock(SysLogService.class);
        aspect = new LogAspect(logService);
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/system/users/5/reset-password");
        when(request.getMethod()).thenReturn("POST");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private JoinPoint joinPoint(Object... args) {
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(args);
        return jp;
    }

    @Test
    void writeFailure_stillLogged() {
        JoinPoint jp = joinPoint();

        aspect.logWriteFailure(jp, new RuntimeException("审批已结束，无法操作"));

        ArgumentCaptor<String> module = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(logService).log(module.capture(), action.capture(), isNull(), isNull(),
                detail.capture(), any());
        assertEquals("人员", module.getValue());
        assertEquals("重置密码", action.getValue());
        assertTrue(detail.getValue().contains("审批已结束"),
                "失败日志应含异常消息: " + detail.getValue());
    }

    @Test
    void writeFailure_withoutRequest_noLogNoException() {
        RequestContextHolder.resetRequestAttributes();
        JoinPoint jp = joinPoint();

        aspect.logWriteFailure(jp, new RuntimeException("x"));

        verify(logService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ycmnLogAnnotation_overridesUrlInference() throws Exception {
        java.lang.reflect.Method m = com.ycmn.module.system.controller.SysUserController.class
                .getMethod("resetPassword", Long.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        JoinPoint jp = joinPoint();
        when(jp.getSignature()).thenReturn(sig);

        com.ycmn.common.annotation.YcmnLog ann = aspect.findYcmnLog(jp);
        assertNotNull(ann, "resetPassword 应带 @YcmnLog 注解");
        assertEquals("人员", ann.module());
        assertEquals("重置密码", ann.action());
    }
}
