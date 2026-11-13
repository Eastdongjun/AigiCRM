package com.aigicrm.security;

import com.aigicrm.common.auth.PermEpochService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 【WP5 双端生效 2026-11-13】X-Perm-Epoch 响应头过滤器(规格 §WP5)。
 * 每个响应携带当前权限版本号: PC/MP 请求层比对, 与本地记录不一致即静默拉新
 * /auth/info(菜单/按钮/数据范围即时生效, 用户无需刷新页面)。
 * epoch 读取走 30s 缓存(PermEpochService), 零额外 DB 压力。
 *
 * 【三轮修复】注册方式改 FilterRegistrationBean 显式注册(见 CorsConfig), order=最高优先
 * ——最外层 filter 的 finally 在整个 chain(security+servlet)返回后、响应提交前执行,
 * setHeader 可靠; 原自动注册+LOWEST_PRECEDENCE 处于链最内层, 实测响应头丢失。
 */
@RequiredArgsConstructor
public class PermEpochHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Perm-Epoch";
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PermEpochHeaderFilter.class);

    private final PermEpochService permEpochService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 【三轮部署实测修复】头必须在 doFilter **之前**设定——大响应(auth/info JSON>8KB)
        // 在 body 写出时即提交 headers(chunked 首块), 外层 finally 里 setHeader 已 committed 无效。
        // 语义: 头=请求开始时的版本号; 若处理期间权限变更, 下一次请求带新值(30s 缓存内)。
        boolean committedBefore = response.isCommitted();
        try {
            String v = String.valueOf(permEpochService.current());
            response.setHeader(HEADER, v);
            log.debug("[PermEpochFilter] uri={} setHeader={} committedBefore={}", request.getRequestURI(), v, committedBefore);
        } catch (Exception e) {
            log.warn("[PermEpochFilter] uri={} current()失败: {}", request.getRequestURI(), e.getMessage());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                response.setHeader(HEADER, String.valueOf(permEpochService.current()));
                log.debug("[PermEpochFilter] uri={} finally header={} committed={}", request.getRequestURI(),
                        response.containsHeader(HEADER), response.isCommitted());
            } catch (Exception ignore) {
                // 已提交时无效, 由下一次请求携带
            }
        }
    }
}
