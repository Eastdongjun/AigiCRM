package com.aigicrm.common.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【PR-05 残余 20260921】提交幂等护栏单测:
 * 键合法性 / 两段式(tryBegin→finish) / abort 释放 / 并发等待 / Redis 全链路旁路降级不抛。
 */
class IdempotencyServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private IdempotencyService svc;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(redis.opsForValue()).thenReturn(ops);
        svc = new IdempotencyService(redis);
    }

    @Test
    void keyValidation_acceptsUuishapeAndRejectsBadInput() {
        assertTrue(svc.isValidKey("9f1c2ab4-7d3e-4f55-a6b7-8c9d0e1f2a3b"));
        assertTrue(svc.isValidKey("x".repeat(128)));
        assertFalse(svc.isValidKey(null), "空键忽略");
        assertFalse(svc.isValidKey("short"), "过短忽略(防误用固定短语)");
        assertFalse(svc.isValidKey("x".repeat(129)), "超长忽略");
        assertFalse(svc.isValidKey("bad key\n"), "含空白/控制字符忽略");
        assertFalse(svc.isValidKey("bad key"), "含空格忽略");
    }

    @Test
    void scope_bindsPrefixAndUser() {
        assertEquals("idem:customer:full:9:abc", svc.scope("customer:full", 9L, "abc"));
    }

    @Test
    void tryBegin_setIfAbsentWinsOrLoses() {
        when(ops.setIfAbsent(anyString(), eq(IdempotencyService.PENDING), eq(Duration.ofSeconds(30))))
                .thenReturn(true, false);
        assertTrue(svc.tryBegin("k1"));
        assertFalse(svc.tryBegin("k1"), "同键第二次抢占失败(并发执行者唯一)");
    }

    @Test
    void awaitResult_returnsOnlyRealValue_pendingOrMissingGivesNull() {
        when(ops.get("done")).thenReturn("501");
        when(ops.get("pending")).thenReturn(IdempotencyService.PENDING);
        when(ops.get("none")).thenReturn(null);
        assertEquals("501", svc.awaitResult("done", 0));
        assertNull(svc.awaitResult("pending", 0), "PENDING 不是结果");
        assertNull(svc.awaitResult("none", 0));
    }

    @Test
    void awaitResult_pollsUntilResultAppears() {
        // 第一次 PENDING,第二次出结果 —— 轮询路径生效(总耗时≥一个 80ms 睡眠)
        when(ops.get("slow")).thenReturn(IdempotencyService.PENDING, "777");
        long t0 = System.currentTimeMillis();
        assertEquals("777", svc.awaitResult("slow", 3000));
        assertTrue(System.currentTimeMillis() - t0 >= 70, "至少轮询了一轮");
    }

    @Test
    void awaitResult_timesOut() {
        when(ops.get("stuck")).thenReturn(IdempotencyService.PENDING);
        assertNull(svc.awaitResult("stuck", 180));
    }

    @Test
    void redisOutage_degradesFailOpen_neverThrows() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        IdempotencyService broken = new IdempotencyService(redis);
        assertTrue(broken.tryBegin("k"), "Redis 挂时 fail-open 放行执行");
        assertNull(broken.awaitResult("k", 0));
        assertDoesNotThrow(() -> broken.finish("k", "1"));
        assertDoesNotThrow(() -> broken.abort("k"));
        assertTrue(broken.isValidKey("abcdefgh"), "键校验不依赖 Redis");
    }
}
