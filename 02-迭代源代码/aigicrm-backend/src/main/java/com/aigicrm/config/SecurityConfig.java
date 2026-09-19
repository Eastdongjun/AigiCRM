package com.aigicrm.config;

import com.aigicrm.security.JwtAuthFilter;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final Environment env;
    private final SecurityAuditService securityAudit;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Swagger 只在非生产环境开放
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/auth/login", "/api/auth/captcha", "/api/auth/forgot-password/**",
                        "/api/system/configs/public", "/api/system/configs/logo",
                        "/api/file/download/public/**").permitAll();

                // Swagger 仅非生产环境开放
                if (!isProd) {
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll(); // knife4j 已移除(无 /doc.html)
                }

                auth.anyRequest().authenticated();
            })
            // 【BS-A09 2026-09-19】越权拒绝(403)统一汇入安全审计:@PreAuthorize/anyRequest 拒绝
            // 都走此处,留 uri/用户/IP 结构化痕迹 + IP 阈值告警。响应保持与原默认一致的 403 语义
            // (补 JSON 体,与全站 Result 口径一致,前端拦截器按状态码处理不受影响)。
            .exceptionHandling(ex -> ex.accessDeniedHandler(this::auditAccessDenied))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void auditAccessDenied(HttpServletRequest request, HttpServletResponse response,
                                   org.springframework.security.access.AccessDeniedException ex)
            throws IOException {
        try {
            securityAudit.accessDenied(request.getRequestURI(),
                    SecurityUtils.getCurrentUserId(),
                    com.aigicrm.common.util.ClientIpUtils.getClientIp(request));
        } catch (Exception auditEx) {
            // 审计旁路失败不得改变拒绝语义
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":403,\"message\":\"没有访问权限\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
