// 【2026-11-05 用户选 C"保持登录"】滑动续期单测: 半寿命判定/续签保真(rm/集团)/非续期窗口
package com.aigicrm.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderSlidingRenewalTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor(redisTemplate, jdbc) — 续期路径不触达二者, 传空实现/桩即可
        provider = new JwtTokenProvider(
                org.mockito.Mockito.mock(org.springframework.data.redis.core.RedisTemplate.class),
                new org.springframework.jdbc.core.JdbcTemplate());
        // hmacShaKeyFor 要求 >=32 字节
        ReflectionTestUtils.setField(provider, "secret", "unit-test-secret-0123456789abcdef-unit-test-secret-0123456789abcdef");
        ReflectionTestUtils.setField(provider, "expiration", 24L * 3600 * 1000);
        ReflectionTestUtils.setField(provider, "rememberExpiration", 7L * 24 * 3600 * 1000);
    }

    @Test
    void freshToken_shouldNotRenew_andRmClaimRoundtrip() {
        String t = provider.generateToken(7L, "TY200", true, 3L);
        assertTrue(provider.isRememberMeFromToken(t), "rm 声明应随 rememberMe 签发");
        assertFalse(provider.shouldRenew(t), "全新 token(全寿命)不应续签");
        String plain = provider.generateToken(7L, "TY200", false, null);
        assertFalse(provider.isRememberMeFromToken(plain), "非保持登录档不写 rm 声明");
    }

    @Test
    void pastHalfLife_shouldRenew_andPreserveRmAndGroup() {
        // 半寿命模拟: 临时把 expiration 压到 2s → 睡 1.2s(剩余 0.8s < 半周期 1s) → 应续签
        // TTL=6s: 睡 3.5s(剩余 2.5s < 半周期 3s)——边缘余量 ±500ms, 防 CI 抖动误判
        ReflectionTestUtils.setField(provider, "expiration", 6000L);
        String old = provider.generateToken(7L, "TY200", false, 3L);
        try { Thread.sleep(3500); } catch (InterruptedException ignored) { }
        assertTrue(provider.shouldRenew(old), "剩余<半周期应判续签");
        String renewed = provider.renewToken(old);
        assertNotEquals(old, renewed);
        assertEquals(7L, provider.getUserIdFromToken(renewed));
        assertEquals("TY200", provider.getUsernameFromToken(renewed));
        assertEquals(3L, provider.getCurrentGroupIdFromToken(renewed));
        assertFalse(provider.isRememberMeFromToken(renewed), "非保持登录档续签后仍是 24h 档");
    }

    @Test
    void rememberToken_renewStaysRememberTier() {
        ReflectionTestUtils.setField(provider, "rememberExpiration", 6000L);
        ReflectionTestUtils.setField(provider, "expiration", 6000L);
        String old = provider.generateToken(7L, "TY200", true, 3L);
        try { Thread.sleep(3500); } catch (InterruptedException ignored) { }
        assertTrue(provider.shouldRenew(old));
        String renewed = provider.renewToken(old);
        assertTrue(provider.isRememberMeFromToken(renewed), "保持登录档续签后仍是 7d 档");
        assertEquals(3L, provider.getCurrentGroupIdFromToken(renewed), "当前集团跨续签保留");
    }
}
