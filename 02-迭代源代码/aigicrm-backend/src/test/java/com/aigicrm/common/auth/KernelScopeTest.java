package com.aigicrm.common.auth;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 【WP3 鉴权内核钉子】ScopeSpec 并集 / ScopeSpecResolver 12 档 fail-closed /
 * AuthorizationService 读开关与编排 / ScopeQuery 注入语义(规格 §3.2/§3.3)。
 */
@ExtendWith(MockitoExtension.class)
class KernelScopeTest {

    @Mock JdbcTemplate jdbc;
    @Mock OrgScopeResolver legacyResolver;
    @Mock RolePermScopeService rolePermScopeService;
    @Mock SysConfigService configService;

    // ==================== ScopeSpec 并集(锁定决策#1) ====================

    @Test
    void union_mergesSets_andAllShortCircuits() {
        ScopeSpec a = ScopeSpec.ofOwners(List.of(1L, 2L));
        a.merge(ScopeSpec.ofDepts(List.of(10L)));
        ScopeSpec b = ScopeSpec.ofOwners(List.of(2L, 3L));
        ScopeSpec u = ScopeSpec.union(List.of(a, b));
        assertEquals(Set.of(1L, 2L, 3L), u.getOwnerIds());
        assertEquals(Set.of(10L), u.getDeptIds());

        // 任一 all → all 短路(锁定决策#6: ALL=本集团不过滤)
        ScopeSpec u2 = ScopeSpec.union(List.of(a, ScopeSpec.all()));
        assertTrue(u2.isAll());
    }

    @Test
    void spec_canOwner_selfAlwaysVisible() {
        ScopeSpec s = ScopeSpec.none();
        assertTrue(s.canOwner(7L, 7L), "本人恒可见(兼容现状)");
        assertFalse(s.canOwner(7L, 8L), "空集他人不可见(fail-closed)");
        assertTrue(ScopeSpec.all().canOwner(7L, 99L), "all 不过滤");
    }

    // ==================== ScopeSpecResolver 12 档 ====================

    private SecurityUser su(Long id, List<Long> empOrgs, Long primary) {
        SecurityUser u = new SecurityUser();
        u.setUserId(id);
        u.setEmploymentOrgIds(empOrgs);
        u.setPrimaryEmploymentOrgId(primary);
        return u;
    }

    private ScopeSpecResolver resolver() {
        return new ScopeSpecResolver(jdbc);
    }

    @Test
    void resolver_selfAndAll() {
        ScopeSpecResolver r = resolver();
        GrantReader.GrantRow self = new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales");
        assertEquals(Set.of(7L), r.resolve(su(7L, List.of(10L), 10L), self).getOwnerIds());
        GrantReader.GrantRow all = new GrantReader.GrantRow("ALL", "OWNER", "ROLE", "admin");
        assertTrue(r.resolve(su(7L, List.of(), null), all).isAll());
    }

    @Test
    void resolver_myDept_usesAllEmploymentOrgs() {
        // 锁定决策#2: 本部门 = 全部有效任职组织(主职+兼职)
        ScopeSpecResolver r = resolver();
        GrantReader.GrantRow row = new GrantReader.GrantRow("MY_DEPT", "OWNER", "ROLE", "sales");
        ScopeSpec s = r.resolve(su(7L, List.of(10L, 20L), 10L), row);
        assertEquals(Set.of(10L, 20L), s.getDeptIds());
    }

    @Test
    void resolver_myDeptSub_expandsClosure() {
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(10L))).thenReturn(List.of(11L, 12L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(20L))).thenReturn(List.of());
        ScopeSpecResolver r = resolver();
        GrantReader.GrantRow row = new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales");
        ScopeSpec s = r.resolve(su(7L, List.of(10L, 20L), 10L), row);
        assertEquals(Set.of(10L, 11L, 12L, 20L), s.getDeptIds());
    }

    @Test
    void resolver_regionWithoutAnchor_isEmptyFailClosed() {
        // 继承 WP1.3 fail-closed: 任职组织无大区祖先 → 空集(不放大)
        // ancestorsOf(10) → 闭包反向 [1](depth 升序); dept 1 类型=4(部门) → 无 region 锚
        when(jdbc.queryForList(contains("ancestor_id"), eq(Long.class), eq(10L))).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("dept_type"), eq(Integer.class), eq(1L))).thenReturn(List.of(4));
        ScopeSpecResolver r = resolver();
        GrantReader.GrantRow row = new GrantReader.GrantRow("MY_REGION", "OWNER", "ROLE", "sales");
        ScopeSpec s = r.resolve(su(7L, List.of(10L), 10L), row);
        assertTrue(s.isEmpty(), "无大区锚 → 空集");
        assertFalse(s.isAll());
    }

    @Test
    void resolver_regionAnchorsAtNearest() {
        // 任职组织 10 → 祖先链 [3(办事处), 2(大区), 1(总部)]; 最近大区=2
        when(jdbc.queryForList(contains("ancestor_id"), eq(Long.class), eq(10L))).thenReturn(List.of(3L, 2L, 1L));
        when(jdbc.queryForList(contains("dept_type"), eq(Integer.class), eq(3L))).thenReturn(List.of(3));
        when(jdbc.queryForList(contains("dept_type"), eq(Integer.class), eq(2L))).thenReturn(List.of(2));
        ScopeSpecResolver r = resolver();
        GrantReader.GrantRow row = new GrantReader.GrantRow("MY_REGION", "OWNER", "ROLE", "sales");
        ScopeSpec s = r.resolve(su(7L, List.of(10L), 10L), row);
        assertEquals(Set.of(2L), s.getDeptIds(), "每个任职组织只取最近一个大区锚");
    }

    @Test
    void resolver_teamChain_boundedDepth() {
        // manager 链 7→8→9; 第三层无
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(8L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(8L))).thenReturn(List.of(9L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(9L))).thenReturn(List.of());
        ScopeSpecResolver r = resolver();
        GrantReader.GrantRow row = new GrantReader.GrantRow("MY_TEAM", "OWNER", "ROLE", "sales");
        assertEquals(Set.of(7L, 8L, 9L), r.resolve(su(7L, List.of(), null), row).getOwnerIds());
    }

    @Test
    void resolver_unknownScopeType_failClosedEmpty() {
        ScopeSpecResolver r = resolver();
        GrantReader.GrantRow row = new GrantReader.GrantRow("HACKED", "OWNER", "ROLE", "sales");
        assertTrue(r.resolve(su(7L, List.of(10L), 10L), row).isEmpty(), "未知档 → 空集不放大");
    }

    // ==================== AuthorizationService 编排/读开关 ====================

    private AuthorizationService auth(GrantReader reader, PermActionRegistry registry) {
        return new AuthorizationService(registry, reader, resolver(), legacyResolver,
                rolePermScopeService, configService, jdbc);
    }

    private PermActionRegistry.ActionDef def(String code, String module, boolean scopeRequired, boolean viewLike) {
        return new PermActionRegistry.ActionDef(code, module, "r", "owner_id", "dept_id", scopeRequired, viewLike);
    }

    private SecurityUser suPerm(Long id, String... perms) {
        SecurityUser u = new SecurityUser();
        u.setUserId(id);
        u.setPermissions(new java.util.HashSet<>(List.of(perms)));
        return u;
    }

    @Test
    void can_permissionSemantics_andAdminBypass() {
        AuthorizationService svc = auth(new GrantReader(jdbc), mock(PermActionRegistry.class));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(suPerm(7L, "customer:view"));
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.can("customer:view"));
            assertFalse(svc.can("customer:delete"));
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            assertTrue(svc.can("customer:delete"), "admin 恒真");
        }
    }

    @Test
    void scope_defaultReadModeOld_delegatesLegacy() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        when(configService.getValue("perm.read.customer")).thenReturn(null); // 缺失 → old
        AuthorizationService svc = auth(new GrantReader(jdbc), registry);
        SecurityUser su = suPerm(7L, "customer:view");
        when(rolePermScopeService.anchorOf(any(), eq("customer"), eq("view")))
                .thenReturn(ScopeRequest.AnchorType.OWNER);
        ScopeDecision expected = ScopeDecision.all();
        when(legacyResolver.resolveScope(any())).thenReturn(expected);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            assertSame(expected, svc.scope("customer:view"), "默认 old: 委托旧链(零行为变化)");
        }
    }

    @Test
    void scope_newTrack_noGrant_none_andSwitchOnlyForViewLike() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        when(configService.getValue("perm.read.customer")).thenReturn("new");
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of());
        AuthorizationService svc = auth(reader, registry);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(suPerm(7L, "customer:view"));
            ScopeDecision d = svc.scope("customer:view");
            assertFalse(d.isAll());
            assertEquals(List.of(), d.getVisibleUserIds(), "无授权 → NONE(fail-closed)");
        }
    }

    @Test
    void scope_entryCode_noop_all() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("statistics:view")).thenReturn(def("statistics:view", "statistics", false, false));
        AuthorizationService svc = auth(new GrantReader(jdbc), registry);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(suPerm(7L, "statistics:view"));
            assertTrue(svc.scope("statistics:view").isAll(), "入口码 scope_required=0 → no-op(all)");
        }
    }

    // ==================== ScopeQuery 注入语义(规格 §3.3) ====================

    @Test
    void scopeQuery_unregisteredCode_appliesFalsePredicate() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("ghost:code")).thenReturn(null);
        ScopeQuery q = new ScopeQuery(mock(AuthorizationService.class), registry);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Object>();
        assertTrue(q.apply(wrapper, "ghost:code"));
        assertTrue(wrapper.getSqlSegment().contains("1=0"), "码未注册 → 1=0 fail-closed");
    }

    @Test
    void scopeQuery_ownerAnchor_appliesInColumn() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        PermActionRegistry.ActionDef def = def("customer:view", "customer", true, true);
        when(registry.of("customer:view")).thenReturn(def);
        AuthorizationService authSvc = mock(AuthorizationService.class);
        ScopeDecision d = new ScopeDecision();
        d.setVisibleUserIds(List.of(1L, 2L));
        d.setVisibleDeptIds(List.of());
        when(authSvc.scope("customer:view")).thenReturn(d);
        when(authSvc.dominantAnchorForQuery(eq("customer:view"), any()))
                .thenReturn(ScopeRequest.AnchorType.OWNER);
        ScopeQuery q = new ScopeQuery(authSvc, registry);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Object>();
        q.apply(wrapper, "customer:view");
        String seg = wrapper.getSqlSegment();
        assertTrue(seg.contains("owner_id"), "OWNER 锚按资源 owner_column 注入");
        assertTrue(seg.contains("IN"), seg);
    }

    @Test
    void scopeQuery_emptyScope_appliesFalsePredicateNotEmptyIn() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        AuthorizationService authSvc = mock(AuthorizationService.class);
        ScopeDecision d = ScopeDecision.none();
        when(authSvc.scope("customer:view")).thenReturn(d);
        when(authSvc.dominantAnchorForQuery(eq("customer:view"), any()))
                .thenReturn(ScopeRequest.AnchorType.OWNER);
        ScopeQuery q = new ScopeQuery(authSvc, registry);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Object>();
        q.apply(wrapper, "customer:view");
        String seg = wrapper.getSqlSegment();
        assertTrue(seg.contains("1=0"), "空集显式 1=0");
        assertFalse(seg.contains("IN ()"), "不得生成 IN () 语法错误(A6)");
    }

    @Test
    void scopeQuery_all_noCondition() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        AuthorizationService authSvc = mock(AuthorizationService.class);
        when(authSvc.scope("customer:view")).thenReturn(ScopeDecision.all());
        when(authSvc.dominantAnchorForQuery(eq("customer:view"), any()))
                .thenReturn(ScopeRequest.AnchorType.OWNER);
        ScopeQuery q = new ScopeQuery(authSvc, registry);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Object>();
        assertFalse(q.apply(wrapper, "customer:view"), "all → 不加条件(租户拦截器兜底)");
        String seg = wrapper.getSqlSegment();
        assertTrue(seg == null || seg.isEmpty(), "未生成任何条件片段");
    }
}
