package com.ycmn.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ycmn.security.SecurityUser;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

    // ---- 配置绑定（@Value ycmn.tenant.enable 真实验证，轻量上下文无数据源） ----

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(MybatisPlusConfig.class);

    @Test
    void configBindingOnRegistersTenantInterceptor() {
        contextRunner.withPropertyValues("ycmn.tenant.enable=true").run(ctx -> {
            MybatisPlusInterceptor interceptor = ctx.getBean(MybatisPlusInterceptor.class);
            assertTrue(interceptor.getInterceptors().stream()
                            .anyMatch(i -> i instanceof TenantLineInnerInterceptor),
                    "enable=true 应注册租户拦截器");
            assertTrue(interceptor.getInterceptors().stream()
                            .anyMatch(i -> i instanceof PaginationInnerInterceptor),
                    "分页拦截器应始终存在");
        });
    }

    @Test
    void configBindingOffRegistersOnlyPagination() {
        contextRunner.withPropertyValues("ycmn.tenant.enable=false").run(ctx -> {
            MybatisPlusInterceptor interceptor = ctx.getBean(MybatisPlusInterceptor.class);
            assertEquals(1, interceptor.getInterceptors().size(), "enable=false 只应注册分页");
            assertFalse(interceptor.getInterceptors().stream()
                    .anyMatch(i -> i instanceof TenantLineInnerInterceptor));
        });
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

    // ---- 写入路径（隔离写入关键路径） ----

    @Test
    void whitelistInsertGetsTenantColumnAndValue() {
        authAs(List.of(7L));
        String out = MybatisPlusConfig.buildTenantInterceptor().parserSingle(
                "INSERT INTO biz_customer (id, name) VALUES (1, 'x')", null);
        assertTrue(out.contains("tenant_id"), "INSERT 应自动填充 tenant_id 列，实际: " + out);
        assertTrue(out.contains("7"), "INSERT 应填充集团值 7，实际: " + out);
        assertTrue(out.startsWith("INSERT INTO biz_customer"), "表名不应被改写，实际: " + out);
    }

    @Test
    void nonWhitelistInsertIsIgnored() {
        authAs(List.of(7L));
        String out = MybatisPlusConfig.buildTenantInterceptor().parserSingle(
                "INSERT INTO sys_user (id, name) VALUES (1, 'x')", null);
        assertFalse(out.contains("tenant_id"), "非白名单表 INSERT 不应填充，实际: " + out);
    }

    @Test
    void whitelistUpdateGetsTenantCondition() {
        authAs(List.of(7L));
        String out = MybatisPlusConfig.buildTenantInterceptor().parserSingle(
                "UPDATE biz_customer SET name = 'y' WHERE id = 1", null);
        assertTrue(out.contains("tenant_id = 7"), "UPDATE 应附加 tenant_id = 7，实际: " + out);
        assertTrue(out.contains("id = 1"), "原有 WHERE 条件应保留，实际: " + out);
    }

    @Test
    void nonWhitelistUpdateIsIgnored() {
        authAs(List.of(7L));
        String out = MybatisPlusConfig.buildTenantInterceptor().parserSingle(
                "UPDATE sys_user SET name = 'y' WHERE id = 1", null);
        assertFalse(out.contains("tenant_id"), "非白名单表 UPDATE 不应附加条件，实际: " + out);
    }

    // ---- 批① 2026-08-10: admin 忽略租户 + 26 表白名单 ----

    @Test
    void admin_ignoresTenantCondition() {
        SecurityUser user = new SecurityUser();
        user.setRoleCodes(List.of("admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
        TenantLineInnerInterceptor it = MybatisPlusConfig.buildTenantInterceptor();
        String rewritten = it.parserSingle("SELECT * FROM biz_customer WHERE id = 1", "id");
        assertFalse(rewritten.toLowerCase().contains("tenant_id"), "平台管理员不应附加租户条件: " + rewritten);
    }

    @Test
    void nonAdmin_appliesTenantCondition() {
        authAs(List.of(1L));
        TenantLineInnerInterceptor it = MybatisPlusConfig.buildTenantInterceptor();
        String rewritten = it.parserSingle("SELECT * FROM biz_customer WHERE id = 1", "id");
        assertTrue(rewritten.toLowerCase().contains("tenant_id = 1"), "非管理员应附加当前集团条件: " + rewritten);
    }

    @Test
    void tenantTables_includeNewBusinessTables() {
        // 25 张业务表全部在白名单(8 张原有 + 18 张 V9 新增 - 1 张 V15 删除的 biz_file_permission);
        // 快照表与废弃表排除
        for (String t : List.of("biz_customer", "biz_sea_pool", "biz_contact", "biz_invoice",
                "biz_payment", "biz_score_entry", "biz_folder_acl", "biz_incentive_category",
                "biz_customer_transfer", "biz_approval_node")) {
            authAs(List.of(1L));
            TenantLineInnerInterceptor it = MybatisPlusConfig.buildTenantInterceptor();
            String rewritten = it.parserSingle("SELECT * FROM " + t + " WHERE id = 1", "id");
            assertTrue(rewritten.toLowerCase().contains("tenant_id"),
                    "业务表 " + t + " 应附加租户条件: " + rewritten);
        }
        // 快照表不加租户条件(无 tenant_id 列)
        authAs(List.of(1L));
        TenantLineInnerInterceptor it = MybatisPlusConfig.buildTenantInterceptor();
        String bak = it.parserSingle("SELECT * FROM biz_customer_bak_0624 WHERE id = 1", "id");
        assertFalse(bak.toLowerCase().contains("tenant_id"), "快照表不应附加租户条件: " + bak);
        // 废弃表不加租户条件(V15 已删表, 白名单同步移除)
        String dead = it.parserSingle("SELECT * FROM biz_file_permission WHERE id = 1", "id");
        assertFalse(dead.toLowerCase().contains("tenant_id"), "废弃表不应附加租户条件: " + dead);
    }
}
