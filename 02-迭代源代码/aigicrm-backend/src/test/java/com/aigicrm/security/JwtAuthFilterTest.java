package com.aigicrm.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 2026-08-11 注释审计发现: URL token 曾在下载/导出路径接受任意 token(含主 JWT)。
 *  修复后 URL token 必须为 scope=download 的短时下载 token——主 JWT 出现在下载路径 URL 中被拒绝。 */
class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-key-for-jwt-hmac-256-bytes-long-enough";

    private JwtAuthFilter filterWith(JwtTokenProvider p) {
        return new JwtAuthFilter(p, mock(UserDetailsService.class));
    }

    @SuppressWarnings("unchecked")
    private JwtTokenProvider provider() {
        // 【2026-08-14】构造器新增 DB 兜底 JdbcTemplate(黑名单撤销 fail-closed)
        JwtTokenProvider p = new JwtTokenProvider(mock(RedisTemplate.class), mock(JdbcTemplate.class));
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

    /** 【2026-08-14 安全 P0】改密晚于签发 → DB 级撤销：旧 token 401（不依赖 Redis） */
    @Test
    void passwordChangedAfterTokenIssued_returns401() throws Exception {
        JwtTokenProvider p = provider();
        UserDetailsService uds = mock(UserDetailsService.class);
        JwtAuthFilter f = new JwtAuthFilter(p, uds);

        String token = p.generateToken(1L, "admin", false);
        com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
        su.setUserId(1L);
        su.setUsername("admin");
        su.setPwdChangedAt(java.time.LocalDateTime.now().plusSeconds(30));
        when(uds.loadUserByUsername("1")).thenReturn(su);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/customer/list");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        f.doFilter(req, resp, new MockFilterChain());

        assertEquals(401, resp.getStatus(), "改密后早于改密时刻签发的 token 必须 401");
    }

    /** 【2026-08-14 安全 P0】改密早于签发（改密后新登录）→ 正常放行 */
    @Test
    void passwordChangedBeforeTokenIssued_passesThrough() throws Exception {
        JwtTokenProvider p = provider();
        UserDetailsService uds = mock(UserDetailsService.class);
        JwtAuthFilter f = new JwtAuthFilter(p, uds);

        String token = p.generateToken(1L, "admin", false);
        com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
        su.setUserId(1L);
        su.setUsername("admin");
        su.setPwdChangedAt(java.time.LocalDateTime.now().minusSeconds(60));
        when(uds.loadUserByUsername("1")).thenReturn(su);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/customer/list");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        f.doFilter(req, resp, new MockFilterChain());

        assertEquals(200, resp.getStatus(), "改密前签发的 token 不受影响");
    }

    /** 【2026-08-14 安全 P0】pwd_reset_required=1 服务端强制：普通路径 403，改密/注销/信息端点放行（非仅前端提示） */
    @Test
    void pwdResetRequired_enforcedServerSide() throws Exception {
        JwtTokenProvider p = provider();
        UserDetailsService uds = mock(UserDetailsService.class);
        JwtAuthFilter f = new JwtAuthFilter(p, uds);

        String token = p.generateToken(1L, "admin", false);
        com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
        su.setUserId(1L);
        su.setUsername("admin");
        su.setPwdResetRequired(1);
        when(uds.loadUserByUsername("1")).thenReturn(su);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/customer/list");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        f.doFilter(req, resp, new MockFilterChain());
        assertEquals(403, resp.getStatus(), "首登未改密时业务路径必须服务端 403");

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/system/users/change-password");
        req2.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        f.doFilter(req2, resp2, new MockFilterChain());
        assertEquals(200, resp2.getStatus(), "改密端点必须放行");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 【PR-22 活体验证 2026-09-19 归属撤销 fail-closed】集团声明不在当前归属集合 → 回退最小当前集团 */
    @Test
    void sessionGroupClaimRevoked_fallsBackToCurrentMembership() throws Exception {
        JwtTokenProvider p = provider();
        UserDetailsService uds = mock(UserDetailsService.class);
        JwtAuthFilter f = new JwtAuthFilter(p, uds);

        String token = p.generateToken(76L, "pr22a", false, 1L);
        com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
        su.setUserId(76L);
        su.setUsername("pr22a");
        su.setGroupIds(List.of(2L));
        when(uds.loadUserByUsername("76")).thenReturn(su);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/customer/list");
        req.addHeader("Authorization", "Bearer " + token);
        f.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(2L, ((com.aigicrm.security.SecurityUser) principal).getCurrentGroupId(),
                "失效声明(集团1)必须被丢弃,回退到当前归属的最小集团(2)");
    }

    /** 完全无集团归属 → 会话集团置 null(fail-closed, 不复活旧声明) */
    @Test
    void sessionGroupClaimRevoked_noMembership_null() throws Exception {
        JwtTokenProvider p = provider();
        UserDetailsService uds = mock(UserDetailsService.class);
        JwtAuthFilter f = new JwtAuthFilter(p, uds);

        String token = p.generateToken(76L, "pr22a", false, 1L);
        com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
        su.setUserId(76L);
        su.setUsername("pr22a");
        su.setGroupIds(List.of());
        when(uds.loadUserByUsername("76")).thenReturn(su);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/customer/list");
        req.addHeader("Authorization", "Bearer " + token);
        f.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertNull(((com.aigicrm.security.SecurityUser) principal).getCurrentGroupId(),
                "无任何集团归属时不得保留旧 token 声明");
    }

    /** 声明仍属于当前归属 → 原样保留(switch-group 多 tab 会话不受影响) */
    @Test
    void sessionGroupClaimValid_kept() throws Exception {
        JwtTokenProvider p = provider();
        UserDetailsService uds = mock(UserDetailsService.class);
        JwtAuthFilter f = new JwtAuthFilter(p, uds);

        String token = p.generateToken(76L, "pr22a", false, 2L);
        com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
        su.setUserId(76L);
        su.setUsername("pr22a");
        su.setGroupIds(List.of(1L, 2L));
        when(uds.loadUserByUsername("76")).thenReturn(su);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/customer/list");
        req.addHeader("Authorization", "Bearer " + token);
        f.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(2L, ((com.aigicrm.security.SecurityUser) principal).getCurrentGroupId(),
                "合法声明(∈当前归属)必须保留");
    }
}
