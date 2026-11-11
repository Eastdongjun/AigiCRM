package com.aigicrm.architecture;

import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.common.idempotency.IdempotencyService;
import com.aigicrm.module.customer.controller.CustomerController;
import com.aigicrm.module.customer.service.CustomerEventService;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.sea.service.SeaService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 【底座收敛 P2 第一步 2026-11-11】权限行为矩阵(@WebMvcTest 级, 评估报告"0 个 Spring 上下文
 * 测试"的首批): 用真实 Spring Security 上下文求值 @PreAuthorize —— 角色(权限码组合) × 端点 ×
 * 预期(401/403/200)。此前权限只有 Mockito 级单测与源码字符串契约, 注解表达式本身的求值行为
 * (or/and 组合、三通道)从未被 Spring 真实执行过。
 *
 * 范围界定: 本矩阵只测**注解层**(功能码门)——Service 层对象级/范围判定(三通道落库后的
 * assertCanManageFinance、T2 的 incentive:add 服务层门)各有单测覆盖; 全量 SecurityConfig
 * 链(401 入口点/审计/审计顺序)另有守卫契约。此处用最小测试安全配置(anyRequest authenticated
 * + @EnableMethodSecurity), 不引入 JwtAuthFilter/@Lazy 依赖环。
 */
@SpringBootTest(classes = PermissionBehaviorMatrixTest.Ctx.class)
@AutoConfigureMockMvc
class PermissionBehaviorMatrixTest {

    /**
     * 自控上下文(放弃 @WebMvcTest 切片: Boot 4 切片把 mapper/filter 全拖进装配, 逐个 mock
     * 打地鼠): 空壳 @SpringBootConfiguration 阻断对主应用的组件扫描, 只显式装配被测
     * controller + 最小安全配置 —— 行为矩阵的语义本就是"注解求值面", 装配面越小越准。
     */
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc // 默认 converters(Jackson 在 classpath)
    @Import({MinimalMethodSecurityConfig.class, CustomerController.class})
    static class Ctx {
    }

    /** 最小安全配置: 认证门 + 方法级注解求值(不装载生产 SecurityConfig 的 filter 链/审计面) */
    @EnableWebSecurity
    @EnableMethodSecurity
    static class MinimalMethodSecurityConfig {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.ignoringRequestMatchers("/**"))
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    // 401=未认证 / 403=已认证无权(对齐生产 SecurityConfig 的语义归位)
                    .exceptionHandling(e -> e.authenticationEntryPoint(
                            (req, res, ex) -> res.sendError(401)))
                    .build();
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean CustomerService customerService;
    @MockitoBean SeaService seaService;
    @MockitoBean IdempotencyService idempotencyService;
    @MockitoBean CustomerAccessService accessService;
    @MockitoBean CustomerEventService customerEventService;
    @MockitoBean com.aigicrm.security.PermissionHintService permissionHintService;

    private static final String STAGE_URL = "/api/customer/1/stage-amounts";
    private static final String STAGE_BODY = "{\"periodYear\":2026,\"periodType\":\"H1\",\"invoiceAmount\":10}";


    /** 请求级认证注入: user("名字").authorities(码...) —— 不依赖 WithMockUser 的监听桥
     *  (Boot 4.1 + 自控上下文场景实测监听桥不把 TestContext 传给 MockMvc, auth=null;
     *   请求级 post-processor 是 spring-security-test 最稳路径)。 */
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(String... authorities) {
        // Security 7 重载: authorities(GrantedAuthority...) —— 字符串码包 SimpleGrantedAuthority
        return user("matrix-user").authorities(java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }

    // ==================== 矩阵 A: 客户列表(单码门) ====================

    @Test
    void list_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/customer/list").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    @Test
    void list_withCode_200() throws Exception {
        mockMvc.perform(get("/api/customer/list").accept(MediaType.APPLICATION_JSON).with(as("customer:view"))).andExpect(status().isOk());
    }

    @Test
    void list_wrongCode_403() throws Exception { // 有别的码但无 view → 403(非 401)
        mockMvc.perform(get("/api/customer/list").accept(MediaType.APPLICATION_JSON).with(as("customer:edit"))).andExpect(status().isForbidden());
    }

    // ==================== 矩阵 B: 阶段总额写(三通道 or 组合, 最复杂的注解表达式) ====================

    @Test
    void stageAmounts_unauthenticated_401() throws Exception {
        mockMvc.perform(post(STAGE_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(STAGE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stageAmounts_noFinanceChannel_403() throws Exception { // 三通道一支不占 → 403, service 不被触达
        mockMvc.perform(post(STAGE_URL).with(csrf()).accept(MediaType.APPLICATION_JSON).with(as("customer:view"))
                        .contentType(MediaType.APPLICATION_JSON).content(STAGE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(customerService);
    }

    @Test
    void stageAmounts_channel1_financeManage_200() throws Exception {
        mockMvc.perform(post(STAGE_URL).with(csrf()).accept(MediaType.APPLICATION_JSON).with(as("finance:manage"))
                        .contentType(MediaType.APPLICATION_JSON).content(STAGE_BODY))
                .andExpect(status().isOk());
        verify(customerService).saveStageAmount(any(), any());
    }

    @Test
    void stageAmounts_channel2_financeEdit_200() throws Exception {
        // 2026-11-03 拆码的第三通道(行政生产配置)——T3 曾漏此通道致行政无按钮, 此处钉住注解侧
        mockMvc.perform(post(STAGE_URL).with(csrf()).accept(MediaType.APPLICATION_JSON).with(as("customer:finance:edit"))
                        .contentType(MediaType.APPLICATION_JSON).content(STAGE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void stageAmounts_channel3_editAndView_200() throws Exception {
        mockMvc.perform(post(STAGE_URL).with(csrf()).accept(MediaType.APPLICATION_JSON).with(as("customer:edit", "finance:view"))
                        .contentType(MediaType.APPLICATION_JSON).content(STAGE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void stageAmounts_halfOfChannel3_403() throws Exception { // 通道三只占一半 → 403(and 不得短路放行)
        mockMvc.perform(post(STAGE_URL).with(csrf()).accept(MediaType.APPLICATION_JSON).with(as("customer:edit"))
                        .contentType(MediaType.APPLICATION_JSON).content(STAGE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(customerService);
    }
}
