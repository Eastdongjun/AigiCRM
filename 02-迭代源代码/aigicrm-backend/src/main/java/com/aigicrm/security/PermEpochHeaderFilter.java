package com.aigicrm.security;

import com.aigicrm.common.auth.PermEpochService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 【WP5 双端生效 2026-11-13】X-Perm-Epoch 响应头过滤器(规格 §WP5)。
 * 每个响应携带当前权限版本号: PC/MP 请求层比对, 与本地记录不一致即静默拉新
 * /auth/info(菜单/按钮/数据范围即时生效, 用户无需刷新页面)。
 * epoch 读取走 30s 缓存(PermEpochService), 零额外 DB 压力; 认证放行的静态/匿名路径同样回传无妨。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class PermEpochHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Perm-Epoch";

    private final PermEpochService permEpochService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                response.setHeader(HEADER, String.valueOf(permEpochService.current()));
            } catch (Exception ignore) {
                // epoch 不可用(表未就绪)不阻断响应
            }
        }
    }
}
