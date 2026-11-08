package com.aigicrm.config;

import com.aigicrm.security.JwtAuthFilter;
import com.aigicrm.security.PermissionHintService;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityAuditService securityAudit;
    private final PermissionHintService permissionHintService;
    /** 【修复 2026-11-08】构造注入 ObjectMapper 在 SecurityConfig 早期初始化阶段无 bean 可用
     * (测试服实测 APPLICATION FAILED TO START)——改私有静态实例, Jackson 线程安全, 语义不变。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 【2026-11-08 权限报错中文化】显式构造器取代 @RequiredArgsConstructor：
     * PermissionHintService 依赖 RequestMappingHandlerMapping（MVC 基础设施），而 SecurityConfig
     * 产出 SecurityFilterChain 属早期初始化的配置类——直接注入会形成 Security→MVC→…→Security
     * 的启动顺序环，故 @Lazy 懒解析（首次 403 才取代理实例）。Lombok 不拷贝字段注解到构造参数，
     * 必须显式构造器才能把 @Lazy 落在参数上。
     */
    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          SecurityAuditService securityAudit,
                          @Lazy PermissionHintService permissionHintService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.securityAudit = securityAudit;
        this.permissionHintService = permissionHintService;
    }

    /** 【权限审计批次1 2026-09-21】Swagger 显式开关, 安全默认关: 不再依赖 "prod profile 是否激活" 推断
     *  (默认 profile 启动曾导致 Swagger 匿名全量暴露), 仅开发环境显式置 aigicrm.swagger.enabled=true */
    @org.springframework.beans.factory.annotation.Value("${aigicrm.swagger.enabled:false}")
    private boolean swaggerEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // 【体检 2026-10-12】移除 /api/auth/captcha 残留白名单: 验证码端点后端不存在、
                // 前端零调用(放行的是 404 路径, 无危害但暴露面文档与实际不符), security-auth.md 同步剔除
                auth.requestMatchers("/api/auth/login", "/api/auth/forgot-password/**",
                        "/api/system/configs/public", "/api/system/configs/logo",
                        "/api/file/download/public/**",
                        "/actuator/health").permitAll(); // 【可观测性】仅 health 匿名(无详情); metrics/prometheus 由 anyRequest authenticated 保护

                // Swagger 默认关闭, 显式 aigicrm.swagger.enabled=true 才开放(原 profile 推断已移除)
                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll(); // knife4j 已移除(无 /doc.html)
                }

                auth.anyRequest().authenticated();
            })
            // 【BS-A09 2026-09-19】越权拒绝(403)统一汇入安全审计:@PreAuthorize/anyRequest 拒绝
            // 都走此处,留 uri/用户/IP 结构化痕迹 + IP 阈值告警。响应保持与原默认一致的 403 语义
            // (补 JSON 体,与全站 Result 口径一致,前端拦截器按状态码处理不受影响)。
            // 【2026-10-09 工作流复核确认修复】未认证(无 token/过期 token)补 401 入口点:
            // 原默认 Http403ForbiddenEntryPoint 返回 403+空 body——MP 请求层只认 401 才清令牌
            // 踢回登录,过期用户被困业务页(所有请求 toast"请求失败";PC 靠本地解 JWT exp 兜底,
            // MP 无对应机制)。401=未认证/403=已认证无权,语义归位后两端零改动闭环。
            .exceptionHandling(ex -> ex
                    .accessDeniedHandler(this::auditAccessDenied)
                    .authenticationEntryPoint(this::unauthenticatedEntry))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 未认证/凭证过期统一入口: 401+JSON(与全站 Result 口径一致; 前端拦截器按 401 清令牌回登录) */
    private void unauthenticatedEntry(HttpServletRequest request, HttpServletResponse response,
                                      org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\"}");
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
        // 【2026-11-08 权限报错中文化】message 由 PermissionHintService 按 method+path 匹配
        // @PreAuthorize 端点生成“缺少「XX(权限码)」…去【系统设置 → 权限中心 → …】开启”自助指引；
        // 未命中端点退回 fallback 全量指引。JSON 体用 Jackson 序列化（中文/引号转义不走手工拼串）。
        String message = permissionHintService.hint(request, PermissionHintService.FALLBACK_HINT);
        try {
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(Map.of("code", 403, "message", message)));
        } catch (Exception jsonEx) {
            // 序列化失败不得改变 403 语义, 回退原固定文案
            response.getWriter().write("{\"code\":403,\"message\":\"没有访问权限\"}");
        }
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
