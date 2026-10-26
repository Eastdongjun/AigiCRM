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

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                // F2: 下载专用 token(scope=download)仅允许用于下载/导出路径，防泄漏后被当作会话滥用
                if ("download".equals(jwtTokenProvider.getScopeFromToken(token)) && !isDownloadPath(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                // M2 2026-08-10: 已拉黑(注销/泄露后踢下线)的 token 立即失效, 不建立会话
                if (jwtTokenProvider.isBlacklisted(jwtTokenProvider.getJtiFromToken(token))) {
                    filterChain.doFilter(request, response);
                    return;
                }
                // 【2026-10-27 深度检查修复】下载 token 随主 token 注销联动撤销: dlToken 带签发时主
                // token 的 pjti——主 token 被拉黑(登出/踢下线)后其派生下载 token 一并失效
                if ("download".equals(jwtTokenProvider.getScopeFromToken(token))) {
                    String parentJti = jwtTokenProvider.getParentJtiFromToken(token);
                    if (parentJti != null && jwtTokenProvider.isBlacklisted(parentJti)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
                // 【修复 2026-08-10 P2-13】改密后早于改密时刻签发的旧 token 立即失效(含"记住我"7 天 token)
                if (jwtTokenProvider.isInvalidatedByPasswordChange(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(userId));

                // 【2026-08-14 安全 P0】首登强制改密：服务端强制(非仅前端提示)，pwd_reset_required=1 时
                // 仅放行改密/注销/用户信息端点，其余一律 403 提示改密
                if (userDetails instanceof com.aigicrm.security.SecurityUser su
                        && Integer.valueOf(1).equals(su.getPwdResetRequired())
                        && !isPasswordResetWhitelistPath(request)) {
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, "首次登录必须修改密码");
                    return;
                }

                // 【2026-08-14 安全 P0】改密撤销 DB 级校验(fail-closed, 不依赖 Redis):
                // 早于 pwd_changed_at 签发的 token 一律拒绝。秒级精度下取整比对, 防"同秒改密后新登录被误拒"
                // 【2026-08-15 安全审计】时区固定 Asia/Shanghai 与 MySQL DATETIME 落库口径一致,
                // 不再依赖容器 TZ 环境变量(容器 UTC 时曾把新 token 全部误判 401, 测试环境实测复现)
                if (userDetails instanceof com.aigicrm.security.SecurityUser su && su.getPwdChangedAt() != null) {
                    Date iat = jwtTokenProvider.getIssuedAtFromToken(token);
                    long changedSec = su.getPwdChangedAt()
                            .atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
                    // 【G3 2026-09-21 fail-closed】iat 缺失(无法证明晚于改密)同样拒绝, 堵住无 iat token 逃逸 DB 级撤销
                    if (iat == null || iat.getTime() < changedSec * 1000L) {
                        writeUnauthorized(response);
                        return;
                    }
                }

                // 【批④ 2026-08-10 多集团会话】会话集团注入: token claim 优先(每个 tab 独立),
                // 旧 token 无 claim 回退最小集团; PermissionContext 缓存含 groupIds(TTL 10min 兜底,
                // 业务接口变更时 evict+bump 即时失效)。
                // 【PR-22 活体验证 2026-09-19 归属撤销 fail-closed】claim 必须仍属于用户当前归属集合:
                // 集团归属被解除/调整后, 旧 token 内嵌 currentGroupId 不再放行(此前可存活至 token 过期)。
                // claim 失效但仍有其他集团 → 回退最小当前集团; 完全无归属 → null(fail-closed)。
                if (userDetails instanceof com.aigicrm.security.SecurityUser su) {
                    Long cg = jwtTokenProvider.getCurrentGroupIdFromToken(token);
                    if (cg != null && (su.getGroupIds() == null || !su.getGroupIds().contains(cg))) {
                        cg = null;
                    }
                    if (cg == null && su.getGroupIds() != null && !su.getGroupIds().isEmpty()) {
                        cg = su.getGroupIds().stream().min(Long::compareTo).orElse(null);
                    }
                    su.setCurrentGroupId(cg);
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 【2026-08-14】用户禁用/删除/缓存构建异常统一 401 JSON（此前异常穿透导致 500 或空 403）
                log.warn("token 鉴权失败: {}", e.getMessage());
                writeUnauthorized(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
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
                    && "download".equals(jwtTokenProvider.getScopeFromToken(queryToken))) {
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
