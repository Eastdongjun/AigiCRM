package com.ycmn.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/** 2026-08-11 注释审计发现: URL token 曾在下载/导出路径接受任意 token(含主 JWT)。
 *  修复后 URL token 必须为 scope=download 的短时下载 token——主 JWT 出现在下载路径 URL 中被拒绝。 */
class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-key-for-jwt-hmac-256-bytes-long-enough";

    private JwtAuthFilter filterWith(JwtTokenProvider p) {
        return new JwtAuthFilter(p, mock(UserDetailsService.class));
    }

    @SuppressWarnings("unchecked")
    private JwtTokenProvider provider() {
        JwtTokenProvider p = new JwtTokenProvider(mock(RedisTemplate.class));
        ReflectionTestUtils.setField(p, "secret", SECRET);
        ReflectionTestUtils.setField(p, "expiration", 7200000L);
        ReflectionTestUtils.setField(p, "rememberExpiration", 86400000L);
        return p;
    }

    private String tokenFrom(JwtAuthFilter f, MockHttpServletRequest req) {
        return ReflectionTestUtils.invokeMethod(f, "getTokenFromRequest", (Object) req);
    }

    @Test
    void downloadPath_downloadToken_accepted() {
        JwtTokenProvider p = provider();
        JwtAuthFilter f = filterWith(p);
        String dl = p.generateDownloadToken(42L);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/file/download/123");
        req.setParameter("token", dl);
        assertEquals(dl, tokenFrom(f, req), "scope=download 的短时下载 token 应被接受");

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/score/export/grid");
        req2.setParameter("token", dl);
        assertEquals(dl, tokenFrom(f, req2), "导出路径同样应接受下载 token");
    }

    @Test
    void downloadPath_mainJwt_rejected() {
        JwtTokenProvider p = provider();
        JwtAuthFilter f = filterWith(p);
        String main = p.generateToken(1L, "admin", false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/file/download/123");
        req.setParameter("token", main);
        assertNull(tokenFrom(f, req), "主 JWT(无 scope)出现在下载路径 URL 必须被拒绝, 防经 URL 复用");
    }

    @Test
    void nonDownloadPath_urlToken_notAccepted() {
        JwtTokenProvider p = provider();
        JwtAuthFilter f = filterWith(p);
        String main = p.generateToken(1L, "admin", false);
        String dl = p.generateDownloadToken(42L);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/customer/list");
        req.setParameter("token", main);
        assertNull(tokenFrom(f, req), "非下载路径不接受 URL token");

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/system/users");
        req2.setParameter("token", dl);
        assertNull(tokenFrom(f, req2), "非下载路径不接受下载 token");
    }

    @Test
    void downloadPath_mainJwtViaHeader_stillAccepted() {
        // 防回归: 收紧只针对 URL query token, Authorization header 携带主 JWT 的下载(fetch blob)必须不受影响
        JwtTokenProvider p = provider();
        JwtAuthFilter f = filterWith(p);
        String main = p.generateToken(1L, "admin", false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/file/download/123");
        req.addHeader("Authorization", "Bearer " + main);
        assertEquals(main, tokenFrom(f, req), "header 携带主 JWT 走下载路径仍应被接受(header 分支不受 URL 收紧影响)");
    }

    @Test
    void downloadPath_emptyOrGarbageToken_rejected() {
        JwtTokenProvider p = provider();
        JwtAuthFilter f = filterWith(p);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/file/download/123");
        req.setParameter("token", "");
        assertNull(tokenFrom(f, req), "空 token 应被拒绝");

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/file/download/123");
        req2.setParameter("token", "not-a-valid-jwt");
        assertNull(tokenFrom(f, req2), "非法 token 应被拒绝");
    }
}
