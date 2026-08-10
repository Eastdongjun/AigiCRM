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
        // 仅允许 GET 请求（导出下载等场景）通过 query param 传 token
        // 注意：URL 参数 token 会被服务器日志记录，生产环境建议关闭或使用临时下载令牌
        if ("GET".equalsIgnoreCase(request.getMethod())) {
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
