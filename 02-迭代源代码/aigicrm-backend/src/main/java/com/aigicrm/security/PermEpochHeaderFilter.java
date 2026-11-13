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
