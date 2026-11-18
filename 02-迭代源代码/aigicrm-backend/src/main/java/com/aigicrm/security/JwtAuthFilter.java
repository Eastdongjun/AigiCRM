package com.aigicrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_DOWNLOAD = "download";


    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                AuthOutcome outcome = authenticateToken(token, request, response);
                if (outcome == AuthOutcome.RESPONDED) return;
                if (outcome == AuthOutcome.SKIP) {
                    filterChain.doFilter(request, response);
                    return;
                }
            } catch (Exception e) {
                // 【2026-08-14】用户禁用/删除/缓存构建异常统一 401 JSON（此前异常穿透导致 500 或空 403）
                log.warn("token 鉴权失败: {}", e.getMessage());
                writeUnauthorized(response);
                return;
            }
        }

        renewIfNeeded(token, response);
        filterChain.doFilter(request, response);
    }

    /** 鉴权结果：成功建会话 / 维持匿名继续过滤链 / 已直接写响应（不再继续）。 */
    private enum AuthOutcome { AUTHENTICATED, SKIP, RESPONDED }

    private AuthOutcome authenticateToken(String token, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        // F2: 下载专用 token(scope=download)仅允许用于下载/导出路径，防泄漏后被当作会话滥用
        if (isDownloadTokenMisused(token, request)) return AuthOutcome.SKIP;
        // M2 2026-08-10: 已拉黑(注销/泄露后踢下线)的 token 立即失效, 不建立会话
        if (isBlacklistedToken(token)) return AuthOutcome.SKIP;
        // 【2026-10-27 深度检查修复】下载 token 随主 token 注销联动撤销
        if (isRevokedDownloadToken(token)) return AuthOutcome.SKIP;
        // 【修复 2026-08-10 P2-13】改密后早于改密时刻签发的旧 token 立即失效(含"记住我"7 天 token)
        if (jwtTokenProvider.isInvalidatedByPasswordChange(token)) return AuthOutcome.SKIP;

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(userId));

        // 【2026-08-14 安全 P0】首登强制改密：服务端强制(非仅前端提示)
        if (mustChangePassword(userDetails, request)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "首次登录必须修改密码");
            return AuthOutcome.RESPONDED;
        }
        // 【2026-08-14 安全 P0】改密撤销 DB 级校验(fail-closed, 不依赖 Redis)
        if (isTokenOlderThanPasswordChange(token, userDetails)) {
            writeUnauthorized(response);
            return AuthOutcome.RESPONDED;
        }
        // 【批④ 2026-08-10 多集团会话】会话集团注入: token claim 优先(每个 tab 独立)
        applySessionGroup(token, userDetails);
        setAuthentication(userDetails, request);
        return AuthOutcome.AUTHENTICATED;
    }

    private boolean isDownloadTokenMisused(String token, HttpServletRequest request) {
        return LIT_DOWNLOAD.equals(jwtTokenProvider.getScopeFromToken(token)) && !isDownloadPath(request);
    }

    private boolean isBlacklistedToken(String token) {
        return jwtTokenProvider.isBlacklisted(jwtTokenProvider.getJtiFromToken(token));
    }

    /** 【2026-10-27】dlToken 带签发时主 token 的 pjti——主 token 被拉黑后其派生下载 token 一并失效。 */
    private boolean isRevokedDownloadToken(String token) {
        if (!LIT_DOWNLOAD.equals(jwtTokenProvider.getScopeFromToken(token))) return false;
        String parentJti = jwtTokenProvider.getParentJtiFromToken(token);
        return parentJti != null && jwtTokenProvider.isBlacklisted(parentJti);
    }

    private boolean mustChangePassword(UserDetails userDetails, HttpServletRequest request) {
        // 【2026-08-14 安全 P0】pwd_reset_required=1 时仅放行改密/注销/用户信息端点, 其余 403 提示改密
        return userDetails instanceof com.aigicrm.security.SecurityUser su
                && Integer.valueOf(1).equals(su.getPwdResetRequired())
                && !isPasswordResetWhitelistPath(request);
    }

    /** 改密撤销 DB 级校验: 秒级精度取整比对, 时区固定 Asia/Shanghai 与 MySQL DATETIME 一致。 */
    private boolean isTokenOlderThanPasswordChange(String token, UserDetails userDetails) {
        if (!(userDetails instanceof com.aigicrm.security.SecurityUser su) || su.getPwdChangedAt() == null) {
            return false;
        }
        Date iat = jwtTokenProvider.getIssuedAtFromToken(token);
        long changedSec = su.getPwdChangedAt()
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
        // 【G3 2026-09-21 fail-closed】iat 缺失(无法证明晚于改密)同样拒绝, 堵住无 iat token 逃逸 DB 级撤销
        return iat == null || iat.getTime() < changedSec * 1000L;
    }

    /** 【PR-22 活体验证 2026-09-19 归属撤销 fail-closed】claim 失效但仍有其他集团→回退最小集团；无归属→null。 */
    private void applySessionGroup(String token, UserDetails userDetails) {
        if (!(userDetails instanceof com.aigicrm.security.SecurityUser su)) return;
        Long cg = jwtTokenProvider.getCurrentGroupIdFromToken(token);
        if (cg != null && (su.getGroupIds() == null || !su.getGroupIds().contains(cg))) {
            cg = null;
        }
        if (cg == null && su.getGroupIds() != null && !su.getGroupIds().isEmpty()) {
            cg = su.getGroupIds().stream().min(Long::compareTo).orElse(null);
        }
        su.setCurrentGroupId(cg);
    }

    private void setAuthentication(UserDetails userDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 【2026-11-05 用户选 C"保持登录"】滑动续期: 剩余寿命<半周期 → X-New-Token 换发(失败不阻断)。 */
    private void renewIfNeeded(String token, HttpServletResponse response) {
        if (SecurityContextHolder.getContext().getAuthentication() == null
                || LIT_DOWNLOAD.equals(jwtTokenProvider.getScopeFromToken(token))) return;
        try {
            if (jwtTokenProvider.shouldRenew(token)) {
                String renewed = jwtTokenProvider.renewToken(token);
                if (renewed != null && !renewed.isEmpty()) {
                    response.setHeader("X-New-Token", renewed);
                }
            }
        } catch (Exception e) {
            log.debug("token 滑动续期跳过: {}", e.getMessage());
        }
    }

    /** 401 JSON 响应（与 Result 结构一致的语义化未认证码） */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已失效，请重新登录");
    }

    /** 统一的 JSON 拒绝响应（401/403 共用） */
    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }

    /** 首登强制改密白名单：改密、注销、用户信息、重新登录（带临时 token 的新 tab 访问登录页不应 403） */
    private boolean isPasswordResetWhitelistPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/system/users/change-password")
                || uri.startsWith("/api/auth/logout")
                || uri.startsWith("/api/auth/info")
                || uri.startsWith("/api/auth/login");
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 【修复 2026-08-10 P2-14】URL query token 仅限下载/导出路径(<img>/导出无法带 header)。
        // 【2026-08-11 收紧】URL token 强制必须为 scope=download 的短时下载 token(getDownloadToken):
        // 主 JWT(无 scope)即使出现在下载/导出路径 URL 中也被拒绝, 防主 JWT 泄漏进日志后被复用。
        if ("GET".equalsIgnoreCase(request.getMethod()) && isDownloadPath(request)) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)
                    && LIT_DOWNLOAD.equals(jwtTokenProvider.getScopeFromToken(queryToken))) {
                return queryToken;
            }
            return null;
        }
        return null;
    }

    /** 下载专用 token 允许访问的路径（文件下载/缩略图 + 导出）。
     *  注意: 新增走 getDownloadToken() 进 URL 的下载/导出端点时, 必须在此同步登记前缀, 否则会被拒为 401。 */
    private boolean isDownloadPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/file/download/") || uri.startsWith("/api/score/export/");
    }
}
