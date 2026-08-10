package com.ycmn.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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
            // 【修复 2026-08-10 P2-13】改密后早于改密时刻签发的旧 token 立即失效(含"记住我"7 天 token)
            if (jwtTokenProvider.isInvalidatedByPasswordChange(token)) {
                filterChain.doFilter(request, response);
                return;
            }
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(userId));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 【修复 2026-08-10 P2-14】URL query token 仅限下载/导出路径(<img>/导出无法带 header)——
        // 主 JWT 即使出现在 URL 中也不会被本过滤器接受，防止泄漏进日志后被复用
        if ("GET".equalsIgnoreCase(request.getMethod()) && isDownloadPath(request)) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }
        return null;
    }

    /** 下载专用 token 允许访问的路径（文件下载/缩略图 + 导出） */
    private boolean isDownloadPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/file/download/") || uri.startsWith("/api/score/export/");
    }
}
