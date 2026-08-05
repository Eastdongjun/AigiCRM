package com.ycmn.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ycmn.security.SecurityUser;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MybatisPlusConfig 租户插件配置测试。
 * 纯单元断言（无 Spring 上下文）：配置类拆出 buildInterceptor/buildTenantInterceptor/resolveTenantId
 * 三个静态方法直接测试；SQL 重写用 TenantLineInnerInterceptor.parserSingle 直调 + SecurityContextHolder
 * 模拟登录态（走真实 jsqlparser 重写，非 mock）。
 */
class MybatisPlusConfigTest {

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    /** 模拟登录用户：groupIds 由 UserDetailsServiceImpl 推导后写入 SecurityUser */
    private static void authAs(List<Long> groupIds) {
        SecurityUser user = new SecurityUser();
        user.setGroupIds(groupIds);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    // ---- 开关逻辑（默认关闭：未迁移 tenant_id 列前不得注册拦截器） ----

    @Test
    void switchOffRegistersOnlyPagination() {
        MybatisPlusInterceptor interceptor = MybatisPlusConfig.buildInterceptor(false);
        assertEquals(1, interceptor.getInterceptors().size());
        assertTrue(interceptor.getInterceptors().get(0) instanceof PaginationInnerInterceptor);
        assertFalse(interceptor.getInterceptors().stream()
                .anyMatch(i -> i instanceof TenantLineInnerInterceptor));
    }

    @Test
    void switchOnRegistersTenantBeforePagination() {
        MybatisPlusInterceptor interceptor = MybatisPlusConfig.buildInterceptor(true);
        assertEquals(2, interceptor.getInterceptors().size());
        assertTrue(interceptor.getInterceptors().get(0) instanceof TenantLineInnerInterceptor);
        assertTrue(interceptor.getInterceptors().get(1) instanceof PaginationInnerInterceptor);
    }

    // ---- 集团过滤表达式分支 ----

    @Test
    void emptyGroupIdsFallsBackToPrimaryGroup() {
        // 平台管理员/无集团归属：主集团默认值方案（见配置类注释：3.5.6 下 NullValue 非恒真）
        assertEquals(1L, ((LongValue) MybatisPlusConfig.resolveTenantId(null)).getValue());
        assertEquals(1L, ((LongValue) MybatisPlusConfig.resolveTenantId(List.of())).getValue());
    }

    @Test
    void singleGroupUsesItsId() {
        assertEquals(5L, ((LongValue) MybatisPlusConfig.resolveTenantId(List.of(5L))).getValue());
    }

    @Test
    void multiGroupFailsClosedToSmallestStableGroup() {
        // MP 3.5.6 无条件 EqualsTo 包装 getTenantId()，多集团 IN 无法表达（会生成非法 SQL）；
        // 取排序后最小集团 fail-closed，保证同一用户多次登录命中同一集团（HashSet 顺序不稳定）
        assertEquals(1L, ((LongValue) MybatisPlusConfig.resolveTenantId(List.of(3L, 1L, 2L))).getValue());
        assertEquals(1L, ((LongValue) MybatisPlusConfig.resolveTenantId(List.of(1L, 3L))).getValue());
    }

    // ---- 真实 SQL 重写（parserSingle 直调，走真实 jsqlparser 重写链） ----

    @Test
    void whitelistQueryGetsTenantCondition() {
        authAs(List.of(7L));
        String out = MybatisPlusConfig.buildTenantInterceptor().parserSingle(
                "SELECT * FROM biz_customer WHERE id = 1", null);
        assertTrue(out.contains("tenant_id = 7"), "单集团应附加 tenant_id = 7，实际: " + out);
    }

    @Test
    void nonWhitelistTableIsIgnored() {
        authAs(List.of(7L));
        String out = MybatisPlusConfig.buildTenantInterceptor().parserSingle(
                "SELECT * FROM sys_user WHERE id = 1", null);
        assertFalse(out.contains("tenant_id"), "非白名单表不应附加租户条件，实际: " + out);
    }

    @Test
    void emptyGroupIdsRendersPrimaryGroupFallback() {
        authAs(List.of());
        String out = MybatisPlusConfig.buildTenantInterceptor().parserSingle(
                "SELECT * FROM biz_customer WHERE id = 1", null);
        assertTrue(out.contains("tenant_id = 1"), "空集团应退回主集团默认值，实际: " + out);
    }
}
