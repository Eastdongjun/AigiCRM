package com.ycmn.config;

import com.ycmn.common.annotation.YcmnLog;
import com.ycmn.module.system.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 全局操作日志 AOP 切面
 * 自动拦截所有 Controller 的写操作(POST/PUT/DELETE)并记录到 sys_log：
 * - 成功走 {@link #logWriteOperation}（@AfterReturning）；
 * - 失败走 {@link #logWriteFailure}（@AfterThrowing，安全审计闭环，detail 含异常消息）；
 * - 方法带 @YcmnLog 注解时用注解值覆盖 URL 推断的 module/action。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final SysLogService logService;

    private static final String WRITE_POINTCUT =
            "execution(* com.ycmn.module..controller..*(..)) && (" +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping))";

    @AfterReturning(pointcut = WRITE_POINTCUT)
    public void logWriteOperation(JoinPoint jp) {
        try {
            HttpServletRequest request = getRequest();
            if (request == null) return;

            String uri = request.getRequestURI();
            String method = request.getMethod();

            // 跳过不需要记录的接口
            if (uri.contains("/auth/login") || uri.contains("/auth/logout")) return;

            YcmnLog ann = findYcmnLog(jp);
            String module = resolveModule(uri);
            String action = resolveAction(method, uri);
            if (ann != null) {
                if (!ann.module().isBlank()) module = ann.module();
                if (!ann.action().isBlank()) action = ann.action();
            }
            String detail = resolveDetail(jp, uri);

            logService.log(module, action, ann != null ? ann.targetType() : null, null, detail, request);
        } catch (Exception e) {
            log.debug("日志记录失败: {}", e.getMessage());
        }
    }

    /** 【2026-08-13 新增】写操作失败也必须入审计日志（原仅记成功，失败=审计盲区） */
    @AfterThrowing(pointcut = WRITE_POINTCUT, throwing = "t")
    public void logWriteFailure(JoinPoint jp, Throwable t) {
        try {
            HttpServletRequest request = getRequest();
            if (request == null) return;

            String uri = request.getRequestURI();
            String method = request.getMethod();
            if (uri.contains("/auth/login") || uri.contains("/auth/logout")) return;

            YcmnLog ann = findYcmnLog(jp);
            String module = resolveModule(uri);
            String action = resolveAction(method, uri);
            if (ann != null) {
                if (!ann.module().isBlank()) module = ann.module();
                if (!ann.action().isBlank()) action = ann.action();
            }

            // 失败详情: 异常类名 + 消息(截断 200 字符防超长)
            String msg = t.getMessage() == null ? t.getClass().getSimpleName()
                    : t.getClass().getSimpleName() + ": " + t.getMessage();
            String detail = "失败 " + (msg.length() > 200 ? msg.substring(0, 200) : msg);

            logService.log(module, action, ann != null ? ann.targetType() : null, null, detail, request);
        } catch (Exception e) {
            log.debug("日志记录失败: {}", e.getMessage());
        }
    }

    /** 提取方法上的 @YcmnLog 注解（无则 null）。包内可见便于单测。 */
    YcmnLog findYcmnLog(JoinPoint jp) {
        if (jp.getSignature() instanceof MethodSignature ms) {
            return ms.getMethod().getAnnotation(YcmnLog.class);
        }
        return null;
    }

    /** URL → 模块名 */
    private String resolveModule(String uri) {
        if (uri.contains("/customer")) return "客户";
        if (uri.contains("/file")) return "文件";
        if (uri.contains("/approval")) return "客户";
        if (uri.contains("/sea")) return "客户";
        if (uri.contains("/incentive")) return "文件";
        if (uri.contains("/score")) return "系统";
        if (uri.contains("/system/users") || uri.contains("/system/user")) return "人员";
        if (uri.contains("/system/role")) return "系统";
        if (uri.contains("/system/dept")) return "系统";
        if (uri.contains("/system/config")) return "系统";
        if (uri.contains("/system/dict")) return "系统";
        if (uri.contains("/reminder")) return "系统";
        return "系统";
    }

    /** HTTP method + URL → 动作 */
    private String resolveAction(String method, String uri) {
        if (uri.contains("/approve")) return "审批通过";
        if (uri.contains("/reject")) return "审批驳回";
        if (uri.contains("/release")) return "释放客户";
        if (uri.contains("/upload")) return "上传";
        if (uri.contains("/share")) return "分享";
        if (uri.contains("/reset-password")) return "重置密码";
        if (uri.contains("/change-password")) return "修改密码";
        if (uri.contains("/batch")) return "批量保存";

        return switch (method) {
            case "POST" -> "创建";
            case "PUT" -> "编辑";
            case "DELETE" -> "删除";
            default -> method;
        };
    }

    /** 从方法参数中提取可读的操作详情 */
    private String resolveDetail(JoinPoint jp, String uri) {
        Object[] args = jp.getArgs();
        for (Object arg : args) {
            if (arg == null) continue;
            // 跳过 request/response 对象
            if (arg instanceof HttpServletRequest || arg instanceof jakarta.servlet.http.HttpServletResponse) continue;
            if (arg instanceof org.springframework.web.multipart.MultipartFile mf) {
                return mf.getOriginalFilename();
            }
            // Map 类型：尝试提取常见字段
            if (arg instanceof Map<?, ?> map) {
                for (String key : new String[]{"customerName", "name", "realName", "roleName",
                        "deptName", "dictName", "reason", "type", "comment"}) {
                    Object v = map.get(key);
                    if (v != null && !v.toString().isEmpty()) return v.toString();
                }
            }
            // Entity 类型：反射获取 name 类字段
            try {
                for (String getter : new String[]{"getCustomerName", "getRealName", "getRoleName",
                        "getDeptName", "getFileName", "getDictName", "getCategoryName", "getProduct"}) {
                    var m = arg.getClass().getMethod(getter);
                    Object v = m.invoke(arg);
                    if (v != null && !v.toString().isEmpty()) return v.toString();
                }
            } catch (Exception ignored) {
            // 尽力而为:URL 参数提取失败不影响主日志流程(SonarLint S1166 注明吞因)
            }
        }
        // 从 URL 提取 path variable ID
        String[] parts = uri.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) return "ID:" + parts[i];
        }
        return "";
    }

    private HttpServletRequest getRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
