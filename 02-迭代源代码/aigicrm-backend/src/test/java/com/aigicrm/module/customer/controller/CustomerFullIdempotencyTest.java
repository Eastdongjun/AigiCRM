package com.aigicrm.module.customer.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.common.idempotency.IdempotencyService;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【PR-05 残余 20260921】POST /customer/full 强幂等(控制器层):
 * 同键重试复用首次结果不再建客 / 执行失败释放键可重试 / 非法键自动忽略。
 */
class CustomerFullIdempotencyTest {

    private record Svc(CustomerController controller, CustomerService customerService, IdempotencyService idempotency) {}

    private static Svc build() {
        CustomerService customerService = mock(CustomerService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        CustomerController controller = new CustomerController(
                customerService, mock(SeaService.class), idempotency,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        return new Svc(controller, customerService, idempotency);
    }

    // 架构守卫: controller 包(含测试)不得依赖实体 —— 请求体构造收敛到 fixture 包
    private static CustomerController.CustomerWithIndicatorRequest body() {
        return com.aigicrm.module.customer.fixture.CustomerFullFixtures.request("幂等测试客户");
    }

    private static HttpServletResponse response() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        return response;
    }

    @Test
    void sameKeyRetry_reusesFirstResult_doesNotCreateTwice() {
        Svc s = build();
        when(s.idempotency().isValidKey("key-12345678")).thenReturn(true);
        when(s.idempotency().scope(eq("customer:full"), eq(9L), eq("key-12345678"))).thenReturn("scoped");
        // 首次: 无结果 → 抢占成功 → 落库 501 → finish
        when(s.idempotency().awaitResult("scoped", 0)).thenReturn(null);
        when(s.idempotency().tryBegin("scoped")).thenReturn(true);
        when(s.customerService().createWithIndicator(any(), any())).thenReturn(501L);
        // 重试: 已有结果 501
        when(s.idempotency().awaitResult("scoped", 0)).thenReturn(null, "501");

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(true);
            security.when(SecurityUtils::getCurrentUserId).thenReturn(9L);

            Result<Long> first = s.controller().createFull(body(), "key-12345678", response());
            assertEquals(501L, first.getData());
            HttpServletResponse replayResponse = response();
            Result<Long> second = s.controller().createFull(body(), "key-12345678", replayResponse);
            assertEquals(501L, second.getData(), "同键重试拿到同一客户 id");
        }
        verify(s.customerService(), times(1)).createWithIndicator(any(), any());
        verify(s.idempotency(), times(1)).finish("scoped", "501");
        verify(s.idempotency(), never()).abort(anyString());
    }

    @Test
    void concurrentSameKey_waitsForInProgressResult_insteadOfCreating() {
        Svc s = build();
        when(s.idempotency().isValidKey("key-87654321")).thenReturn(true);
        when(s.idempotency().scope(anyString(), anyLong(), anyString())).thenReturn("scoped2");
        when(s.idempotency().awaitResult("scoped2", 0)).thenReturn(null);
        when(s.idempotency().tryBegin("scoped2")).thenReturn(false);
        when(s.idempotency().awaitResult("scoped2", 5000)).thenReturn("602");
        when(s.customerService().createWithIndicator(any(), any())).thenReturn(999L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(true);
            security.when(SecurityUtils::getCurrentUserId).thenReturn(9L);
            HttpServletResponse replayResponse = response();
            Result<Long> r = s.controller().createFull(body(), "key-87654321", replayResponse);
            assertEquals(602L, r.getData(), "并发同键取在途执行者的结果");
        }
        verify(s.customerService(), never()).createWithIndicator(any(), any());
    }

    @Test
    void createFailure_releasesKey_andPropagates() {
        Svc s = build();
        when(s.idempotency().isValidKey("key-fail0001")).thenReturn(true);
        when(s.idempotency().scope(anyString(), anyLong(), anyString())).thenReturn("scoped3");
        when(s.idempotency().awaitResult("scoped3", 0)).thenReturn(null);
        when(s.idempotency().tryBegin("scoped3")).thenReturn(true);
        when(s.customerService().createWithIndicator(any(), any()))
                .thenThrow(new BusinessException(500, "指标校验失败"));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(true);
            security.when(SecurityUtils::getCurrentUserId).thenReturn(9L);
            assertThrows(BusinessException.class,
                    () -> s.controller().createFull(body(), "key-fail0001", response()));
        }
        verify(s.idempotency()).abort("scoped3");
        verify(s.idempotency(), never()).finish(anyString(), anyString());
    }

    @Test
    void invalidOrMissingKey_ignoresIdempotency_entirely() {
        Svc s = build();
        when(s.idempotency().isValidKey(anyString())).thenReturn(false);
        when(s.customerService().createWithIndicator(any(), any())).thenReturn(701L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(true);
            security.when(SecurityUtils::getCurrentUserId).thenReturn(9L);
            assertEquals(701L, s.controller().createFull(body(), "bad", response()).getData());
            assertEquals(701L, s.controller().createFull(body(), null, response()).getData());
        }
        verify(s.idempotency(), never()).tryBegin(anyString());
        verify(s.idempotency(), never()).finish(anyString(), anyString());
    }
}
