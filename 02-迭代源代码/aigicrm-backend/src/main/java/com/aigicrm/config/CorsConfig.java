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
     * 允许的跨域来源列表，逗号分隔
     * 生产环境必须设置为具体域名，如：https://aigicrm.example.com
     */
    @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
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
