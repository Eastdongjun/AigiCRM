package com.aigicrm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * 默认允许来源（本地开发用）。抽成常量而不是写在 {@code ${key:default}} 里：
     * 【S6857 2026-11-23】默认值本身含 "http://…:5173" 这种冒号，占位符的 ":" 分隔语义
     * 在这里是歧义的（Spring 取首个 ":" 之后全部内容，恰好能用但极易被误读/误改）。
     * 生产必须用 CORS_ALLOWED_ORIGINS 覆盖成具体域名。
     */
    private static final String DEFAULT_ALLOWED_ORIGINS =
            "http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000";

    /**
     * 允许的跨域来源列表，逗号分隔
     * 生产环境必须设置为具体域名，如：https://aigicrm.example.com
     */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        String raw = (allowedOrigins == null || allowedOrigins.isBlank())
                ? DEFAULT_ALLOWED_ORIGINS : allowedOrigins;
        List<String> origins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(origins);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * 【三轮修复 2026-11-13】X-Perm-Epoch 头显式注册(order=最高优先, chain 最外层):
     * 最外层 filter 的 finally 在 security+servlet 全部返回后、响应提交前执行——setHeader 可靠。
     * 原 @Component 自动注册 + LOWEST_PRECEDENCE(链最内层)实测响应头丢失(测试环境部署验证抓出)。
     * 仅 /api/* 生效(静态资源与 actuator 无需版本头)。
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.aigicrm.security.PermEpochHeaderFilter> permEpochHeaderFilter(
            com.aigicrm.common.auth.PermEpochService permEpochService) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<com.aigicrm.security.PermEpochHeaderFilter> reg =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
                        new com.aigicrm.security.PermEpochHeaderFilter(permEpochService));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("permEpochHeaderFilter");
        return reg;
    }
}
