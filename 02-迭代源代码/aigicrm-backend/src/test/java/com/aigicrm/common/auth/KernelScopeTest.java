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

    /** 【WP8 对账】显式新轨: 多授权行并集(SELF ∪ MY_DEPT_SUB 闭包); 未知码 fail-closed 空集 */
    @Test
    void scopeNewTrack_multiGrantUnion_andUnknownFailsClosed() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales"),
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "manager")));
        // 【四轮】多行并集的 DEPT 维在 ORG 锚验证(SELF 行贡献 owners, MY_DEPT_SUB 行贡献部门)
        when(rolePermScopeService.anchorOf(any(), eq("customer"), eq("view")))
                .thenReturn(ScopeRequest.AnchorType.ORG);
        AuthorizationService svc = auth(reader, registry);
        // 闭包: 10 → [11]; ORG 锚用户维 = usersInDepts({10,11,10}) = [7,8](A6 口径)。
        // varargs 桩规则: 通配 any(Object[]) 先注册兜底, 具体 eq(7L) 后注册优先覆盖
        when(jdbc.queryForList(anyString(), eq(Long.class),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(List.of(7L, 8L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(10L))).thenReturn(List.of(11L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(10L));
        // ORG 锚不追加上级/负责人开关(旧轨同款), 无需 switchFlags 桩
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:view");
            assertFalse(d.isAll());
            assertEquals(Set.of(10L, 11L), Set.copyOf(d.getVisibleDeptIds()),
                    "ORG 维: MY_DEPT_SUB 行贡献闭包{10,11}");
            assertEquals(Set.of(7L, 8L), Set.copyOf(d.getVisibleUserIds()),
                    "ORG 锚用户维=usersInDepts(部门维)={7,8}(A6 口径)");

            // 未知码 → fail-closed 空集
            assertTrue(svc.scopeNewTrack(su, "ghost:x").getVisibleUserIds().isEmpty());
        }
    }

    /** 【P0-1 修复负例】单 MY_DEPT 授权在 customer:view 下必须含部门用户(不再只有本人) */
    @Test
    void scopeNewTrack_myDept_expandsDeptUsersForOwnerAnchor() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        when(legacyResolver.switchFlags()).thenReturn(new boolean[]{false, false});
        // 部门 10 在职用户 = 7, 88(88=同部门同事)——usersInDepts({10}) 单元素 varargs
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(10L))).thenReturn(List.of(7L, 88L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L), eq(88L))).thenReturn(List.of(10L));
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:view");
            assertEquals(Set.of(7L, 88L), Set.copyOf(d.getVisibleUserIds()),
                    "MY_DEPT 客户域可见=部门在职用户(含同部门同事 88), 不得只剩本人");
        }
    }

    /** 【P0-2 修复负例】assertData: 可见集=[7] 时目标负责人 8 必须 false(旧 || 短路放行任意目标) */
    @Test
    void assertData_ownerOutsideScope_denied() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        when(configService.getValue("perm.read.customer")).thenReturn("new"); // 新轨(assertData 走 scope() 读开关)
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales")));
        when(legacyResolver.switchFlags()).thenReturn(new boolean[]{false, false});
        AuthorizationService svc = auth(reader, registry);
        SecurityUser su = suPerm(7L, "customer:view");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            assertTrue(svc.assertData("customer:view", 7L, null), "目标=本人 → true");
            assertFalse(svc.assertData("customer:view", 8L, null),
                    "目标负责人=8 不可见 → false(|| 短路漏洞已修)");
        }
    }

    /** 【P0-3 修复负例】CUSTOM includeChildren=false 只到本组织, 不展开子树; scope_config 精确按行 */
    @Test
    void scopeNewTrack_customStrictlyHonorsIncludeChildrenFalse() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("CUSTOM", "OWNER", "USER", "7",
                        "customer:view",
                        "[{\"orgId\":10,\"includeChildren\":false}]")));
        // 【四轮】CUSTOM 是 DEPT 维授权——includeChildren 语义在 ORG 锚(部门维)上验证
        when(rolePermScopeService.anchorOf(any(), eq("customer"), eq("view")))
                .thenReturn(ScopeRequest.AnchorType.ORG);
        AuthorizationService svc = auth(reader, registry);
        // 闭包若被调用: 10 → [11](断言 deptIds 不含 11 即证明未展开)
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(10L))).thenReturn(List.of(11L));
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:view");
            assertEquals(Set.of(10L), Set.copyOf(d.getVisibleDeptIds()),
                    "includeChildren=false 只到本组织(旧实现无条件 closure 越权放宽)");
            assertFalse(d.getVisibleDeptIds().contains(11L), "子部门 11 不得出现");
        }
    }

    /**
     * 【P1-1 四轮修复负例】MY_DEPT + ORG 锚 + 部门同事有兼职: 同事 B 的兼职部门 20 不得
     * 并入可见部门(deptsOfUsers 只对 nativeOwners 反查, 不对"部门展开出来的用户"二次反查)。
     * 查看 A(MY_DEPT, 任职 10); 同事 B(dept_id=10, 兼职 20)。可见部门必须只有 {10}。
     */
    @Test
    void scopeNewTrack_myDept_orgAnchor_colleagueMoonlightDeptNotIncluded() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT", "OWNER", "ROLE", "sales")));
        when(rolePermScopeService.anchorOf(any(), eq("customer"), eq("view")))
                .thenReturn(ScopeRequest.AnchorType.ORG);
        AuthorizationService svc = auth(reader, registry);
        // usersInDepts({10}) = [A=7, B=8](B 兼职 20); deptsOfUsers(A 的 native owners={7}) = [10]
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(10L), eq(10L))).thenReturn(List.of(7L, 8L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(10L));
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:view");
            assertEquals(Set.of(10L), Set.copyOf(d.getVisibleDeptIds()),
                    "MY_DEPT(ORG 锚): 同事 B 的兼职部门 20 不得并入(deptsOfUsers 仅对 nativeOwners)");
            assertFalse(d.getVisibleDeptIds().contains(20L), "兼职部门 20 越权可见");
        }
    }

    /** 【P2 四轮修复负例】未知 scope_type → fail-closed none(不静默降级为仅本人放行) */
    @Test
    void scopeNewTrack_unknownScopeType_failClosedNone() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("DIRTY_TYPE", "OWNER", "ROLE", "sales")));
        when(configService.getValue("perm.read.customer")).thenReturn("new");
        AuthorizationService svc = auth(reader, registry);
        SecurityUser su = suPerm(7L, "customer:view");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scope("customer:view");
            assertFalse(d.isAll());
            assertTrue(d.getVisibleUserIds() != null && d.getVisibleUserIds().isEmpty(),
                    "未知档 → fail-closed 空集(none), 不得静默按 SELF 放行本人");
        }
    }

    /**
     * 【文件专项 P0 修复负例】(file,manage) 槽五码共享槽配置:
     * 角色仅勾 file:upload(grant=MY_DEPT_SUB 来自槽) → resolveModuleUsers("file","manage")
     * 的双轨解析必须为 MY_DEPT_SUB(非 all/非 SELF)——修"scope_required=0 → all → 任意夹放行"。
     */
    @Test
    void scope_fileManageSlot_uploadOnlyGrant_resolvesSlotScope() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("file:upload")).thenReturn(
                new PermActionRegistry.ActionDef("file:upload", "file", "file", "uploader_id", null, true, false));
        when(registry.slotOf("file:upload")).thenReturn(new String[]{"file", "manage"});
        when(registry.codesOf("file", "manage")).thenReturn(List.of("file:upload"));
        when(configService.getValue("perm.read.file")).thenReturn("new");
        when(rolePermScopeService.anchorOf(any(), eq("file"), eq("manage")))
                .thenReturn(ScopeRequest.AnchorType.ORG);
        GrantReader reader = mock(GrantReader.class);
        // 槽聚合: file:upload 有 grant(MY_DEPT_SUB), 其余四码无
        when(reader.grantsOf(any(), eq("file:upload"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scope("file:upload");
            // ORG 锚断言(桩无关): MY_DEPT_SUB 授权 → 部门维={10}——钉"manage 槽范围非 all/非 none"
            assertFalse(d.isAll(), "槽范围不得回退 all");
            assertEquals(Set.of(10L), Set.copyOf(d.getVisibleDeptIds()),
                    "仅 file:upload 授权 → manage 槽范围(MY_DEPT_SUB)=部门 10");
        }
    }
    /** 【文件专项 P0 负例】槽隔离: upload 走 manage 槽、delete 走独立槽, 同角色双授权不串 */
    @Test
    void scope_fileFamily_uploadManage_deleteDelete_slotsIsolated() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("file:upload")).thenReturn(
                new PermActionRegistry.ActionDef("file:upload", "file", "file", "uploader_id", null, true, false));
        when(registry.of("file:delete")).thenReturn(
                new PermActionRegistry.ActionDef("file:delete", "file", "file", "uploader_id", null, true, false));
        when(registry.slotOf("file:upload")).thenReturn(new String[]{"file", "manage"});
        when(registry.slotOf("file:delete")).thenReturn(new String[]{"file", "delete"});
        when(registry.codesOf("file", "manage")).thenReturn(List.of("file:upload"));
        when(registry.codesOf("file", "delete")).thenReturn(List.of("file:delete"));
        when(configService.getValue("perm.read.file")).thenReturn("new");
        // usersInDepts({10}) = [7, 88](部门 10 在职用户, 含同事 88)
        // 闭包查询(子代=11) 与 usersInDepts 查询(闭包部门{10,11}在职=7,88) 精确区分;
        // deptsOfUsers({7}) 回查无桩 → null 被容错吞掉 → 空(本人兼职部门不放大)
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("sys_org_closure"), eq(Long.class), eq(10L)))
                .thenReturn(List.of(11L));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("dept_id IN"), eq(Long.class), eq(10L), eq(11L)))
                .thenReturn(List.of(7L, 88L));
        // 【FILE-07 锚点透传】配置锚=生产默认 OWNER(V2026.11.10.1 种子实测 file 三槽全 OWNER);
        // ORG 语义由**调用方请求锚**带入(matrixDeptSet 固定传 ORG)——本测试钉"请求锚不被配置锚吞掉"
        when(rolePermScopeService.anchorOf(any(), eq("file"), eq("manage")))
                .thenReturn(ScopeRequest.AnchorType.OWNER);
        when(rolePermScopeService.anchorOf(any(), eq("file"), eq("delete")))
                .thenReturn(ScopeRequest.AnchorType.OWNER);
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("file:upload"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales")));
        when(reader.grantsOf(any(), eq("file:delete"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            // ① 请求锚 ORG(matrixDeptSet 通道): 部门维=MY_DEPT_SUB 闭包{10,11}——配置 OWNER 不得吞掉请求 ORG
            ScopeDecision upOrg = svc.scopeForUser(su, "file:upload", ScopeRequest.AnchorType.ORG);
            assertEquals(Set.of(10L, 11L), Set.copyOf(upOrg.getVisibleDeptIds()),
                    "请求锚 ORG: 部门维=MY_DEPT_SUB 闭包{10,11}(FILE-07: 配置 OWNER 不吞请求 ORG)");
            assertEquals(Set.of(7L, 88L), Set.copyOf(upOrg.getVisibleUserIds()),
                    "ORG 锚用户维=闭包部门在职用户{7,88}(A6 口径)");
            // ② 默认(无请求锚)=配置锚 OWNER: 部门维恒空(镜像旧轨), 用户维=本人∪DEPT 型部门用户
            ScopeDecision up = svc.scopeForUser(su, "file:upload");
            assertTrue(up.getVisibleDeptIds().isEmpty(), "配置锚 OWNER: 部门维恒空(镜像旧轨 OWNER 分支)");
            assertEquals(Set.of(7L, 88L), Set.copyOf(up.getVisibleUserIds()),
                    "配置锚 OWNER 用户维=本人∪DEPT 型部门在职{7,88}");
            // ③ delete 走独立槽(SELF 授权): 仅本人 {7}——与 upload 的部门用户 {88} 互不串
            ScopeDecision del = svc.scopeForUser(su, "file:delete");
            assertEquals(List.of(7L), del.getVisibleUserIds(),
                    "delete(SELF 槽)=仅本人 {7}, 不含 upload 的部门用户 {88}——两槽互不串");
        }
    }

    /** 【FILE-07 P0 负例】新轨 resolveModuleUsers 恒 OWNER 维(旧轨固定 .anchor(OWNER) 同口径)——
     *  配置锚被管理员改成 ORG 时, 用户维不得漂移成"部门在职用户"丢本人/上级开关。 */
    @Test
    @SuppressWarnings("unchecked")
    void resolveModuleUsers_newTrack_alwaysOwnerAnchor() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("file:view")).thenReturn(
                new PermActionRegistry.ActionDef("file:view", "file", "file", "uploader_id", null, true, true));
        when(registry.codeOf("file", "view")).thenReturn("file:view");
        when(registry.slotOf("file:view")).thenReturn(new String[]{"file", "view"});
        when(registry.codesOf("file", "view")).thenReturn(List.of("file:view"));
        when(configService.getValue("perm.read.file")).thenReturn("new");
        // 闭包{11}; 部门{10,11}在职={7,88}; 上级开关开 → teamOf(7)={8}
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("sys_org_closure"), eq(Long.class), eq(10L)))
                .thenReturn(List.of(11L));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("dept_id IN"), eq(Long.class), eq(10L), eq(11L)))
                .thenReturn(List.of(7L, 88L));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("manager_id"), eq(Long.class), eq(7L)))
                .thenReturn(List.of(8L));
        // 配置锚故意 ORG(漂移源)——若 resolveModuleUsers 透传配置锚, ORG 分支用户维={7,88} 丢 {8}
        when(rolePermScopeService.anchorOf(any(), eq("file"), eq("view"))).thenReturn(ScopeRequest.AnchorType.ORG);
        when(legacyResolver.switchFlags()).thenReturn(new boolean[]{true, false}); // 上级开关开
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("file:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        org.springframework.beans.factory.ObjectProvider<AuthorizationService> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(svc);
        OrgScopeResolver resolver = new OrgScopeResolver(
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                configService,
                mock(com.aigicrm.module.system.mapper.SysRoleOrgMapper.class),
                jdbc, provider);
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        List<Long> owners = resolver.resolveModuleUsers(su, "file", "view");
        assertEquals(Set.of(7L, 8L, 88L), Set.copyOf(owners),
                "新轨 resolveModuleUsers 恒 OWNER 维: DEPT 型部门用户{7,88}+上级开关追加下属{8}——配置 ORG 不漂移");
    }

    /** 【FILE-07 P0 正例 / FILE-12 改生产码】resolveScope 请求锚 ORG 透传(配置 OWNER): ORG 通道
     *  部门维非空——FolderPermissionService.matrixDeptSet 的生产链路(修复前此处恒空集=全 403)。
     *  用生产码 file:acl:manage(V2026.11.10.1 实码; file:manage 已退役) + 槽聚合真实五码映射。 */
    @Test
    void resolveScope_newTrack_honorsRequestedOrgAnchor() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("file:acl:manage")).thenReturn(
                new PermActionRegistry.ActionDef("file:acl:manage", "file", "file", "uploader_id", null, true, false));
        when(registry.codeOf("file", "manage")).thenReturn("file:acl:manage");
        when(registry.slotOf("file:acl:manage")).thenReturn(new String[]{"file", "manage"});
        when(registry.codesOf("file", "manage")).thenReturn(List.of(
                "file:acl:manage", "file:folder:manage", "file:upload", "file:edit", "file:share"));
        when(configService.getValue("perm.read.file")).thenReturn("new");
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("sys_org_closure"), eq(Long.class), eq(10L)))
                .thenReturn(List.of(11L));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("dept_id IN"), eq(Long.class), eq(10L), eq(11L)))
                .thenReturn(List.of(7L, 88L));
        when(rolePermScopeService.anchorOf(any(), eq("file"), eq("manage")))
                .thenReturn(ScopeRequest.AnchorType.OWNER); // 生产默认(V2026.11.10.1 种子)
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("file:acl:manage"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        org.springframework.beans.factory.ObjectProvider<AuthorizationService> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(svc);
        OrgScopeResolver resolver = new OrgScopeResolver(
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                configService,
                mock(com.aigicrm.module.system.mapper.SysRoleOrgMapper.class),
                jdbc, provider);
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        ScopeRequest req = ScopeRequest.builder()
                .user(su).module("file").action("manage")
                .anchor(ScopeRequest.AnchorType.ORG).build();
        ScopeDecision d = resolver.resolveScope(req);
        assertEquals(Set.of(10L, 11L), Set.copyOf(d.getVisibleDeptIds()),
                "matrixDeptSet 生产链路: 请求 ORG + 配置 OWNER → 部门维=闭包{10,11}(修复前恒空=全 403)");
    }

    /** 【FILE-11 P2 负例】req.getAnchor()==null 时新轨默认 OWNER(与旧轨 resolveScopeLegacy
     *  同口径)——防未来调用方省略 anchor 时双轨静默分叉(新轨配置锚 vs 旧轨 OWNER)。 */
    @Test
    void resolveScope_newTrack_nullRequestAnchor_defaultsOwner_likeLegacy() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("file:view")).thenReturn(
                new PermActionRegistry.ActionDef("file:view", "file", "file", "uploader_id", null, true, true));
        when(registry.codeOf("file", "view")).thenReturn("file:view");
        when(registry.slotOf("file:view")).thenReturn(new String[]{"file", "view"});
        when(registry.codesOf("file", "view")).thenReturn(List.of("file:view"));
        when(configService.getValue("perm.read.file")).thenReturn("new");
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("sys_org_closure"), eq(Long.class), eq(10L)))
                .thenReturn(List.of(11L));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("dept_id IN"), eq(Long.class), eq(10L), eq(11L)))
                .thenReturn(List.of(7L, 88L));
        // 配置锚故意 ORG——null 请求锚必须兜底 OWNER(不吞配置锚也不漂向 ORG)
        when(rolePermScopeService.anchorOf(any(), eq("file"), eq("view"))).thenReturn(ScopeRequest.AnchorType.ORG);
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("file:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        org.springframework.beans.factory.ObjectProvider<AuthorizationService> provider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(svc);
        OrgScopeResolver resolver = new OrgScopeResolver(
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                configService,
                mock(com.aigicrm.module.system.mapper.SysRoleOrgMapper.class),
                jdbc, provider);
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        ScopeRequest req = ScopeRequest.builder()
                .user(su).module("file").action("view")
                .build(); // 不设 anchor
        ScopeDecision d = resolver.resolveScope(req);
        assertTrue(d.getVisibleDeptIds().isEmpty(),
                "null 请求锚 → OWNER 分支: 部门维恒空(镜像旧轨; 若吞配置锚 ORG 此处=闭包非空)");
        assertEquals(Set.of(7L, 88L), Set.copyOf(d.getVisibleUserIds()),
                "null 请求锚 → OWNER 分支: 用户维=本人∪DEPT 型部门在职{7,88}");
    }

    /** 【P1-3 三轮修复负例】导出无条件跟随列表授权(回填 SELF grant 不再架空别名, 卡片=列表=导出) */
    @Test
    void scopeNewTrack_export_alwaysFollowsViewAlias() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:export")).thenReturn(def("customer:export", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        // 回填/双写给 export 的 SELF 行存在——但别名必须无条件接管(否则导出=仅本人 ≠ 列表);
        // 该桩故意不被消费(别名接管后不读自身授权) → lenient
        org.mockito.Mockito.lenient().when(reader.grantsOf(any(), eq("customer:export"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales")));
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_DEPT_SUB", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        when(legacyResolver.switchFlags()).thenReturn(new boolean[]{false, false});
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(10L))).thenReturn(List.of(11L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(10L), eq(11L))).thenReturn(List.of(7L, 8L));
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:export");
            assertEquals(Set.of(7L, 8L), Set.copyOf(d.getVisibleUserIds()),
                    "导出范围=列表范围(view 别名无条件接管, SELF 架空行不生效)");
        }
    }

    /**
     * 【P0 四轮修复负例(三轮检查报告)】SELF(OWNER 锚) = 仅本人——两遍维度补齐曾把 SELF 放大成
     * "部门同事"(deptsOfUsers→usersInDepts 两跳), 此负例钉死: 兼职部门同事 99 不得出现。
     * 决策#2 只定义 MY_DEPT=全部任职组织; SELF 语义=仅本人。
     */
    @Test
    void scopeNewTrack_self_ownerAnchor_isSelfOnly() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        when(legacyResolver.switchFlags()).thenReturn(new boolean[]{false, false});
        // 任职: 主职 10 + 兼职 20; 20 部门同事=99; deptsOfUsers({7})=[10,20]
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(10L, 20L));
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L, 20L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:view");
            assertEquals(List.of(7L), d.getVisibleUserIds(),
                    "SELF(OWNER 锚)=仅本人, 不得经维度回灌放大成部门同事");
        }
    }

    /** 【P0 四轮修复负例】MY_TEAM(OWNER 锚) = 团队链, 不得放大成链上成员的整个部门 */
    @Test
    void scopeNewTrack_myTeam_ownerAnchor_isTeamChainOnly() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("MY_TEAM", "OWNER", "ROLE", "manager")));
        AuthorizationService svc = auth(reader, registry);
        when(legacyResolver.switchFlags()).thenReturn(new boolean[]{false, false});
        // 团队链 7→8; 8 的部门同事 88 不得出现
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(8L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(8L))).thenReturn(List.of());
        SecurityUser su = suPerm(7L, "customer:view");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:view");
            assertEquals(Set.of(7L, 8L), Set.copyOf(d.getVisibleUserIds()),
                    "MY_TEAM(OWNER 锚)=团队链 {7,8}, 不展开成员部门同事");
        }
    }

    /** 【四轮补】SELF 在 ORG 锚动作 = 本人任职部门(旧轨 resolveDeptScope('self')=deptId 同口径) */
    @Test
    void scopeNewTrack_self_orgAnchor_isOwnEmploymentDepts() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales")));
        AuthorizationService svc = auth(reader, registry);
        when(rolePermScopeService.anchorOf(any(), eq("customer"), eq("view")))
                .thenReturn(ScopeRequest.AnchorType.ORG);
        // ORG 锚不追加上级/负责人开关(旧轨同款), 无需 switchFlags 桩
        // deptsOfUsers({7}) = [10, 20](主职+兼职); usersInDepts([10,20]) = [7,99]。
        // Mockito 可匹配多桩取"最新注册"——通配桩必须先注册, 具体 eq(7L) 后注册覆盖之。
        when(jdbc.queryForList(anyString(), eq(Long.class),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(List.of(7L, 99L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(10L, 20L));
        SecurityUser su = suPerm(7L, "customer:view");
        su.setEmploymentOrgIds(List.of(10L, 20L));
        su.setPrimaryEmploymentOrgId(10L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            ScopeDecision d = svc.scopeNewTrack(su, "customer:view");
            System.out.println("[diag] dept=" + d.getVisibleDeptIds() + " owner=" + d.getVisibleUserIds());
            assertEquals(Set.of(10L, 20L), Set.copyOf(d.getVisibleDeptIds()),
                    "SELF(ORG 锚)=本人任职部门(与旧轨 self→deptId 同口径)");
            assertEquals(Set.of(7L, 99L), Set.copyOf(d.getVisibleUserIds()),
                    "ORG 锚用户维=部门在职用户(A6 口径, 镜像旧轨)");
        }
    }

    /** 【WP8 对账】双轨显式对比: 新轨 view_like 动作受上级开关放宽, 写动作不受(锁定决策#8 内核面) */
    @Test
    void scopeNewTrack_switchAppliesOnlyToViewLike() {
        PermActionRegistry registry = mock(PermActionRegistry.class);
        when(registry.of("customer:view")).thenReturn(def("customer:view", "customer", true, true));
        when(registry.of("customer:delete")).thenReturn(def("customer:delete", "customer", true, false));
        GrantReader reader = mock(GrantReader.class);
        when(reader.grantsOf(any(), eq("customer:view"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales")));
        when(reader.grantsOf(any(), eq("customer:delete"))).thenReturn(List.of(
                new GrantReader.GrantRow("SELF", "OWNER", "ROLE", "sales")));
        when(legacyResolver.switchFlags()).thenReturn(new boolean[]{true, false}); // 上级开关开
        AuthorizationService svc = auth(reader, registry);
        // team 链: 7 → 8
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(7L))).thenReturn(List.of(8L));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(8L))).thenReturn(List.of());
        SecurityUser su = suPerm(7L, "customer:view");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            var view = svc.scopeNewTrack(su, "customer:view");
            assertTrue(view.getVisibleUserIds().containsAll(List.of(7L, 8L)),
                    "view_like + 开关开 → 并入下属链");

            var del = svc.scopeNewTrack(su, "customer:delete");
            assertEquals(List.of(7L), del.getVisibleUserIds(),
                    "写动作(delete) 开关开也不放宽——验收红线 customer|delete=self");
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
