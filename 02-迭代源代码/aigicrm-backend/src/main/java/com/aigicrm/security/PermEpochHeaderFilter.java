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

    private final PermEpochService permEpochService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 【三轮部署实测修复】头必须在 doFilter **之前**设定——大响应(auth/info JSON>8KB)
        // 在 body 写出时即提交 headers(chunked 首块), 外层 finally 里 setHeader 已 committed 无效
        // (实测 /login 定长小响应有头、/auth/info 大响应无头)。
        // 语义: 头=请求开始时的版本号; 若处理期间权限变更, 下一次请求带新值(30s 缓存内)。
        try {
            response.setHeader(HEADER, String.valueOf(permEpochService.current()));
        } catch (Exception ignore) {
            // epoch 不可用(表未就绪)不阻断响应
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 兜底: 若 chain 中 epoch 被推进(本请求内 bump), 用最新值覆盖
            try {
                response.setHeader(HEADER, String.valueOf(permEpochService.current()));
            } catch (Exception ignore) {
                // 已提交时无效, 由下一次请求携带
            }
        }
    }
}
