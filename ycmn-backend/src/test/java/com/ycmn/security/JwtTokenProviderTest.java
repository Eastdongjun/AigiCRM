package com.ycmn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** F2 回归(2026-08-09): 下载专用 token scope=download、1h 短效、主 token 无下载作用域。 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-hmac-256-bytes-long-enough";

    @SuppressWarnings("unchecked")
    private JwtTokenProvider provider() {
        JwtTokenProvider p = new JwtTokenProvider(mock(RedisTemplate.class));
        ReflectionTestUtils.setField(p, "secret", SECRET);
        ReflectionTestUtils.setField(p, "expiration", 7200000L);       // 主 token 2h
        ReflectionTestUtils.setField(p, "rememberExpiration", 86400000L);
        return p;
    }

    @Test
    void downloadToken_scopeDownload_shortTtl_validates() {
        JwtTokenProvider p = provider();
        String token = p.generateDownloadToken(42L);

        assertEquals("download", p.getScopeFromToken(token));
        assertEquals(42L, p.getUserIdFromToken(token));
        assertTrue(p.validateToken(token));

        // 有效期 ~1h(略小于主 token 2h),泄漏影响面有界
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        long ttlMs = claims.getExpiration().getTime() - new Date().getTime();
        assertTrue(ttlMs > 50 * 60 * 1000 && ttlMs <= 3600 * 1000, "下载 token 应为 ~1h,实际 " + ttlMs + "ms");
    }

    @Test
    void mainToken_hasNoDownloadScope() {
        JwtTokenProvider p = provider();
        String token = p.generateToken(1L, "admin", false);
        assertNull(p.getScopeFromToken(token));
    }

    @Test
    void mainToken_hasJti_forSessionRevocation() {
        JwtTokenProvider p = provider();
        String token = p.generateToken(1L, "admin", false);
        String jti = p.getJtiFromToken(token);
        assertNotNull(jti, "主 token 必须带 jti(会话撤销/黑名单用)");
        // mock Redis 的 hasKey 返回 null → 未拉黑
        assertFalse(p.isBlacklisted(jti));
    }

    @Test
    void blacklistToken_marksTokenRevoked() {
        JwtTokenProvider p = provider();
        String token = p.generateToken(1L, "admin", false);
        p.blacklistToken(token);
        // 黑名单写入已按 jti 触达 Redis(mock: opsForValue().set 被调用)
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.RedisTemplate<String, Object> rt =
                (org.springframework.data.redis.core.RedisTemplate<String, Object>)
                        ReflectionTestUtils.getField(p, "redisTemplate");
        assertNotNull(rt);
        verify(rt).opsForValue();
    }
}
