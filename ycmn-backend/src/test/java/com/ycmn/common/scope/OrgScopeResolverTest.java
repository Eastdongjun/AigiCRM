package com.ycmn.common.scope;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import com.ycmn.security.SecurityUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OrgScopeResolver 测试。
 * 真实断言策略：thenAnswer 解析调用传入的真实 LambdaQueryWrapper 的 SQL 条件
 * （getSqlSegment 占位符与 paramNameValuePairs 插入顺序对齐），按条件分发 mock 数据；
 * verify 锁定每层查询次数。覆盖子树遍历、环保护、team 5 层上限与 status 过滤口径。
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class OrgScopeResolverTest {

    @Mock SysUserMapper userMapper;
    @Mock SysDeptMapper deptMapper;
    @Mock SysConfigService configService;

    /** 开关 OFF（保持既有范围行为；未 stub 时 Mockito 布尔默认 false 等同 OFF） */
    private void switchesOff() {
        when(configService.getBooleanValue(anyString(), anyBoolean())).thenReturn(false);
    }

    /** 开关 ON（系统级默认规则生效） */
    private void switchesOn() {
        when(configService.getBooleanValue(anyString(), anyBoolean())).thenReturn(true);
    }

    /** 列表达式：col IN (?,…) 或 col = ? / col <> ? 等（占位符为 #{ew.paramNameValuePairs.MPGENVALn} 命名引用） */
    private static final Pattern COL_EXPR = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*)\\s*((?:IN\\s*\\([^)]*\\))|(?:=|<>|!=|>|<|>=|<=)\\s*[^)\\s]+)");

    /** #{ew.paramNameValuePairs.MPGENVALn} 命名引用，取短名 MPGENVALn */
    private static final Pattern REF_EXPR = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([^}]+)}");

    /** getSqlSegment() 需要 lambda 列名缓存：注册实体 TableInfo（纯单测无 MyBatis 容器） */
    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysDept.class);
    }

    // ---- helpers ----

    private SysUser user(long id, Long deptId, Long managerId) {
        SysUser u = new SysUser();
        u.setId(id); u.setDeptId(deptId); u.setManagerId(managerId);
        return u;
    }

    private SysDept dept(long id, Long parentId) {
        SysDept d = new SysDept();
        d.setId(id); d.setParentId(parentId);
        return d;
    }

    private SecurityUser su(Long userId, String scope, Long deptId) {
        SecurityUser s = new SecurityUser();
        s.setUserId(userId); s.setDataScope(scope); s.setDeptId(deptId);
        return s;
    }

    /**
     * 解析 wrapper 条件为 列名→值列表（IN 展开为多值，eq 为单值）。
     * 注意：getSqlSegment() 先渲染（触发参数填充），再按 #{} 引用名从 paramNameValuePairs 精确取值
     * （该 map 的 key 顺序与 SQL 占位符顺序不对齐，不能按序对齐）。
     */
    private Map<String, List<Long>> columnValues(LambdaQueryWrapper<?> w) {
        Map<String, List<Long>> out = new HashMap<>();
        String sql = w.getSqlSegment();
        if (sql == null || sql.isBlank()) return out;
        Map<String, Object> params = w.getParamNameValuePairs();
        Matcher colM = COL_EXPR.matcher(sql);
        while (colM.find()) {
            String col = colM.group(1).toLowerCase(Locale.ROOT);
            List<Long> colVals = new ArrayList<>();
            Matcher refM = REF_EXPR.matcher(colM.group(2));
            while (refM.find()) {
                Object v = params.get(refM.group(1));
                if (v instanceof Number num) colVals.add(num.longValue());
            }
            out.put(col, colVals);
        }
        return out;
    }

    /** 所有用户/部门查询必须带 status=1 过滤（team 与 dept/region/office 口径一致） */
    private void assertStatusFiltered(LambdaQueryWrapper<?> w) {
        assertEquals(List.of(1L), columnValues(w).get("status"),
                "查询必须过滤 status=1（team 与 dept 口径一致）");
    }

    // ---- team ----

    @Test
    void teamResolvesWholeChain() {
        // A(1) → B(2) → C(3) → D(4)（manager_id 链 3 层），A 可见 {1,2,3,4}
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> managers = new HashSet<>(columnValues(w).getOrDefault("manager_id", List.of()));
            if (managers.contains(1L)) return List.of(user(2, 11L, 1L));
            if (managers.contains(2L)) return List.of(user(3, 12L, 2L));
            if (managers.contains(3L)) return List.of(user(4, 12L, 3L));
            return List.of();
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "team", 11L));
        assertEquals(Set.of(1L, 2L, 3L, 4L), new HashSet<>(u));
        // 4 层链：in(1) → in(2) → in(3) → in(4) 空，逐层各查一次
        verify(userMapper, times(4)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void teamRespectsMaxDepthFive() {
        // A(1) 有连续 6 级下级 B(2)…G(7)：本人+5 级下级可见，第 6 级 G(7) 不可见
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> managers = new HashSet<>(columnValues(w).getOrDefault("manager_id", List.of()));
            for (long k = 1; k <= 6; k++) {
                if (managers.contains(k)) return List.of(user(k + 1, 11L, k));
            }
            return List.of();
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "team", 11L));
        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L, 6L), new HashSet<>(u));
        assertFalse(u.contains(7L), "第 6 级下级（本人+6）不可见");
        // TEAM_MAX_DEPTH=5：恰 5 次查询（本人 + 5 级下级），不查第 6 层
        verify(userMapper, times(5)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void teamQueryFiltersStatus() {
        // team 查询必须带 status=1（与 dept 口径一致），无下级时只查 1 层即止
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            return List.of();
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        assertEquals(Set.of(1L), new HashSet<>(r.resolve(su(1L, "team", 11L))));
        verify(userMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    // ---- dept ----

    @Test
    void deptResolvesSubtreeUsers() {
        // 部门 11 有子 12；A(1) 在 11，B(2) 在 12
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(11L)) return List.of(dept(12, 11L));
            return List.of();
        });
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> deptIds = new HashSet<>(columnValues(w).getOrDefault("dept_id", List.of()));
            assertTrue(deptIds.contains(11L) && deptIds.contains(12L),
                    "usersInDepts 必须按收集到的子树 deptIds 查询");
            return List.of(user(2, 12L, null));
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "dept", 11L));
        assertEquals(Set.of(1L, 2L), new HashSet<>(u));
        verify(deptMapper, times(2)).selectList(any(LambdaQueryWrapper.class)); // 查 11、12 的子
        verify(userMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void deptSubtreeCycleIsSafe() {
        // 环数据：11→12→13→11。环保护下必须终止且只收集 {11,12,13}，不重复递归
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(11L)) return List.of(dept(12, 11L));
            if (parents.contains(12L)) return List.of(dept(13, 12L));
            if (parents.contains(13L)) return List.of(dept(11, 13L)); // 环回 11
            return List.of();
        });
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            return List.of(user(2, 12L, null), user(3, 13L, null));
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "dept", 11L));
        assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(u), "环数据不误伤、不无限递归");
        // 恰 3 次子树查询（13 的子 11 已收集，不再递归查 11）
        verify(deptMapper, times(3)).selectList(any(LambdaQueryWrapper.class));
        verify(userMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    // ---- all / self ----

    @Test
    void allReturnsNull() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        assertNull(r.resolve(su(1L, "all", 11L)));
    }

    @Test
    void selfReturnsOnlySelf() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        assertEquals(Set.of(1L), new HashSet<>(r.resolve(su(1L, "self", 11L))));
    }

    // ---- 多角色并集（设计 §4.2）----

    private SecurityUser suMulti(Long userId, List<String> scopes, Long deptId) {
        SecurityUser s = new SecurityUser();
        s.setUserId(userId);
        s.setDataScope(scopes.get(0)); // 单值兼容字段（min-rank 结果，仅向后兼容）
        s.setDataScopes(new ArrayList<>(scopes));
        s.setDeptId(deptId);
        return s;
    }

    /** team+dept 双角色：并集 = team 下级集 ∪ dept 部门树集（各自解析后合并去重） */
    @Test
    void unionOfTeamAndDeptMergesBothSets() {
        // user1 deptId=11。team：1→2→3（manager 链）；dept：部门 11 子树 {11,12}
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Map<String, List<Long>> cols = columnValues(w);
            if (cols.containsKey("manager_id")) {
                Set<Long> managers = new HashSet<>(cols.get("manager_id"));
                if (managers.contains(1L)) return List.of(user(2, 12L, 1L));
                if (managers.contains(2L)) return List.of(user(3, 12L, 2L));
                return List.of();
            }
            Set<Long> deptIds = new HashSet<>(cols.getOrDefault("dept_id", List.of()));
            assertTrue(deptIds.contains(11L) && deptIds.contains(12L),
                    "dept 分支必须按子树 deptIds 查询");
            return List.of(user(2, 12L, 1L), user(5, 11L, null));
        });
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(11L)) return List.of(dept(12, 11L));
            return List.of();
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(suMulti(1L, List.of("team", "dept"), 11L));
        // team={1,2,3} ∪ dept={1,2,5} = {1,2,3,5}
        assertEquals(Set.of(1L, 2L, 3L, 5L), new HashSet<>(u), "并集=两角色各自可见集合并集");
    }

    /** 任一角色 all → 本集团不过滤（null 短路），不做无谓解析 */
    @Test
    void unionShortCircuitsOnAll() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        assertNull(r.resolve(suMulti(1L, List.of("all", "team"), 11L)));
        verify(userMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    /** 单角色（dataScopes 空/单码）仍走单值路径，不受并集影响 */
    @Test
    void singleRoleIgnoresUnionPath() {
        SecurityUser s = suMulti(1L, List.of("dept"), 11L);
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(11L)) return List.of(dept(12, 11L));
            return List.of();
        });
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            return List.of(user(2, 12L, null));
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(s); // dataScopes size=1 → 单值路径 dataScope=dept
        assertEquals(Set.of(1L, 2L), new HashSet<>(u));
    }

    // ---- 旧码兼容：org / org_and_children（组织管理员升级路径仍产出，必须可解析）----

    @Test
    void orgAndChildrenResolvesDeptTreeIncludingSubdepts() {
        // 旧码 org_and_children = 本组织及下级 → 部门树含子级（与 dept 同口径）
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(11L)) return List.of(dept(12, 11L));
            return List.of();
        });
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> deptIds = new HashSet<>(columnValues(w).getOrDefault("dept_id", List.of()));
            assertTrue(deptIds.containsAll(List.of(11L, 12L)),
                    "org_and_children 必须按子树 deptIds 查询");
            return List.of(user(2, 12L, null));
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "org_and_children", 11L));
        assertEquals(Set.of(1L, 2L), new HashSet<>(u), "org_and_children 组织管理员可见部门树（含子级）");
    }

    @Test
    void orgResolvesDeptOnlyWithoutChildren() {
        // 旧码 org = 仅本部门（不含子级）：部门 11 有子 12，但 org 不得包含 12 的用户
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> deptIds = new HashSet<>(columnValues(w).getOrDefault("dept_id", List.of()));
            assertEquals(Set.of(11L), deptIds, "org 只查询本部门，不递归子部门");
            return List.of(user(7, 11L, null));
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "org", 11L));
        assertEquals(Set.of(1L, 7L), new HashSet<>(u), "org 仅本部门，不含子部门用户");
        verify(deptMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    // ---- 系统级开关（2026-08-07）：上级看递归下属链 + 组织负责人看本组织子树 ----

    @Test
    void selfWithSuperiorSwitchAppendsTeam() {
        // 开关1 ON、开关2 OFF：self 范围 + 上级开关 → 可见递归下属链
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_SUPERIOR_SEE_SUBORDINATE), anyBoolean())).thenReturn(true);
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_ORG_LEADER_SEE_ORG), anyBoolean())).thenReturn(false);
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> managers = new HashSet<>(columnValues(w).getOrDefault("manager_id", List.of()));
            if (managers.contains(1L)) return List.of(user(2, 11L, 1L));
            if (managers.contains(2L)) return List.of(user(3, 11L, 2L));
            return List.of();
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "self", 11L));
        assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(u), "self+上级开关: 可见递归下属链(≤5层)");
        verify(userMapper, times(3)).selectList(any(LambdaQueryWrapper.class)); // in(1)→in(2)→in(3)空
    }

    @Test
    void selfWithLeaderSwitchAppendsLedOrgSubtree() {
        // 开关2 ON、开关1 OFF：本人领导 dept 11(根) 有子 12，可见整棵子树用户
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_SUPERIOR_SEE_SUBORDINATE), anyBoolean())).thenReturn(false);
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_ORG_LEADER_SEE_ORG), anyBoolean())).thenReturn(true);
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            if (columnValues(w).containsKey("leader_id")) return List.of(dept(11, null));
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(11L)) return List.of(dept(12, 11L));
            if (parents.contains(12L)) return List.of();
            return List.of();
        });
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> deptIds = new HashSet<>(columnValues(w).getOrDefault("dept_id", List.of()));
            assertTrue(deptIds.containsAll(List.of(11L, 12L)), "负责人子树必须含全部子部门");
            return List.of(user(2, 12L, null), user(3, 11L, null));
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "self", 11L));
        assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(u), "self+负责人开关: 可见负责组织整棵子树");
    }

    @Test
    void leaderMultiLevelOrgSubtree() {
        // 负责人领导大区根 21，嵌套 办事处22 → 部门23：整棵子树递归可见
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_SUPERIOR_SEE_SUBORDINATE), anyBoolean())).thenReturn(false);
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_ORG_LEADER_SEE_ORG), anyBoolean())).thenReturn(true);
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            if (columnValues(w).containsKey("leader_id")) return List.of(dept(21, 1L));
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(21L)) return List.of(dept(22, 21L));
            if (parents.contains(22L)) return List.of(dept(23, 22L));
            if (parents.contains(23L)) return List.of();
            return List.of();
        });
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> deptIds = new HashSet<>(columnValues(w).getOrDefault("dept_id", List.of()));
            assertTrue(deptIds.containsAll(List.of(21L, 22L, 23L)), "负责人整棵子树递归");
            return List.of(user(4, 21L, null), user(5, 22L, null), user(6, 23L, null));
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "self", 21L));
        assertEquals(Set.of(1L, 4L, 5L, 6L), new HashSet<>(u), "大区负责人看整棵子树(递归)");
    }

    @Test
    void configMissingDefaultsToOn() {
        // sys_config 无行(getValue null) → getBooleanValue 返回默认值 true → 开关生效
        when(configService.getBooleanValue(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> managers = new HashSet<>(columnValues(w).getOrDefault("manager_id", List.of()));
            if (managers.contains(1L)) return List.of(user(2, 11L, 1L));
            return List.of();
        });
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            return List.of(); // 无负责组织
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "self", 11L));
        assertEquals(Set.of(1L, 2L), new HashSet<>(u), "配置缺失→默认 ON，上级可见下级");
        verify(configService).getBooleanValue(OrgScopeResolver.KEY_SUPERIOR_SEE_SUBORDINATE, true);
    }

    @Test
    void bothSwitchesOffKeepsBase() {
        switchesOff();
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        assertEquals(Set.of(1L), new HashSet<>(r.resolve(su(1L, "self", 11L))), "两开关关: self 保持仅本人");
        verify(userMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(deptMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void allScopeIgnoresSwitches() {
        // all 在追加开关前短路 → 不读开关、不产生任何查询（即便开关配置存在）
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        assertNull(r.resolve(su(1L, "all", 11L)), "all 范围忽略开关仍不过滤");
        verify(userMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(deptMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void teamScopeDoesNotDoubleResolve() {
        // 开关1 ON + team 范围：isTeamScoped 守卫避免重复 resolveTeam，查询次数不翻倍
        switchesOn();
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> managers = new HashSet<>(columnValues(w).getOrDefault("manager_id", List.of()));
            if (managers.contains(1L)) return List.of(user(2, 11L, 1L));
            if (managers.contains(2L)) return List.of(user(3, 11L, 2L));
            return List.of();
        });
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            return List.of(); // 无负责组织
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(su(1L, "team", 11L));
        assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(u));
        // team 解析一次: in(1)→in(2)→in(3) 3次；守卫避免第2次 team 查询
        verify(userMapper, times(3)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void unionWithSwitchesAppends() {
        // 多角色并集 base 上追加开关集合：self∪dept={1,5}，开关1 追加 team{1,2} → {1,5,2}
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_SUPERIOR_SEE_SUBORDINATE), anyBoolean())).thenReturn(true);
        when(configService.getBooleanValue(eq(OrgScopeResolver.KEY_ORG_LEADER_SEE_ORG), anyBoolean())).thenReturn(false);
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertStatusFiltered(w);
            Set<Long> parents = new HashSet<>(columnValues(w).getOrDefault("parent_id", List.of()));
            if (parents.contains(11L)) return List.of(dept(12, 11L));
            return List.of();
        });
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertStatusFiltered(w);
            if (columnValues(w).containsKey("manager_id")) {
                Set<Long> managers = new HashSet<>(columnValues(w).get("manager_id"));
                if (managers.contains(1L)) return List.of(user(2, 12L, 1L));
                return List.of();
            }
            return List.of(user(5, 11L, null)); // dept 子树用户
        });
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        List<Long> u = r.resolve(suMulti(1L, List.of("self", "dept"), 11L));
        assertEquals(Set.of(1L, 5L, 2L), new HashSet<>(u), "并集 base 上追加开关集合");
    }

    // ===== P2-1: role_perm_scope.scope_level 接入运行时解析（2026-08-11） =====

    /** scopeConfigs 精确匹配 (module,action) 时覆盖角色 dataScope */
    @Test
    void resolveScope_usesScopeConfigExactMatch() {
        SecurityUser s = su(1L, "self", 11L);
        com.ycmn.common.scope.ScopeConfig cfg = new com.ycmn.common.scope.ScopeConfig();
        cfg.setModule("customer"); cfg.setAction("view"); cfg.setScopeLevel("all"); cfg.setAnchor("OWNER");
        s.setScopeConfigs(new java.util.ArrayList<>(java.util.List.of(cfg)));

        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        com.ycmn.common.scope.ScopeRequest req = com.ycmn.common.scope.ScopeRequest.builder()
                .user(s).module("customer").action("view").anchor(com.ycmn.common.scope.ScopeRequest.AnchorType.OWNER)
                .build();
        assertNull(r.resolveScope(req).getVisibleUserIds(), "配置 customer:view=all 应覆盖 dataScope=self 返回全量");
    }

    /** scopeConfigs 通配 `* *` 匹配任意模块, 精确匹配优先 */
    @Test
    void resolveScope_scopeConfigWildcardFallsBackWhenNoExact() {
        SecurityUser s = su(1L, "self", 11L);
        com.ycmn.common.scope.ScopeConfig wildcard = new com.ycmn.common.scope.ScopeConfig();
        wildcard.setModule("*"); wildcard.setAction("*"); wildcard.setScopeLevel("all"); wildcard.setAnchor("OWNER");
        s.setScopeConfigs(new java.util.ArrayList<>(java.util.List.of(wildcard)));

        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        com.ycmn.common.scope.ScopeRequest req = com.ycmn.common.scope.ScopeRequest.builder()
                .user(s).module("approval").action("view").anchor(com.ycmn.common.scope.ScopeRequest.AnchorType.OWNER)
                .build();
        assertNull(r.resolveScope(req).getVisibleUserIds(), "通配 * * 匹配 approval:view 返回全量");
    }

    /** 未配置 scopeConfigs 时回退角色 dataScope（默认语义不变） */
    @Test
    void resolveScope_fallsBackToDataScopeWhenNoConfig() {
        SecurityUser s = su(1L, "self", 11L); // 无 scopeConfigs
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        com.ycmn.common.scope.ScopeRequest req = com.ycmn.common.scope.ScopeRequest.builder()
                .user(s).module("customer").action("view").anchor(com.ycmn.common.scope.ScopeRequest.AnchorType.OWNER)
                .build();
        assertEquals(Set.of(1L), new HashSet<>(r.resolveScope(req).getVisibleUserIds()),
                "无配置时按 dataScope=self 解析(兼容现状)");
    }

    /** USER 级 `* *` 通配优先于 ROLE 级精确配置（主体层级 USER>ROLE, 如 NM100 场景） */
    @Test
    void resolveScope_userWildcardOverridesRoleExact() {
        SecurityUser s = su(1L, "self", 11L);
        com.ycmn.common.scope.ScopeConfig roleExact = new com.ycmn.common.scope.ScopeConfig();
        roleExact.setAssigneeType("ROLE"); roleExact.setModule("customer"); roleExact.setAction("view");
        roleExact.setScopeLevel("region"); roleExact.setAnchor("OWNER");
        com.ycmn.common.scope.ScopeConfig userWildcard = new com.ycmn.common.scope.ScopeConfig();
        userWildcard.setAssigneeType("USER"); userWildcard.setModule("*"); userWildcard.setAction("*");
        userWildcard.setScopeLevel("custom"); userWildcard.setAnchor("OWNER");
        // 装载序 USER 在前(loadScopeConfigs: USER→POSITION→ROLE)
        s.setScopeConfigs(new java.util.ArrayList<>(java.util.List.of(userWildcard, roleExact)));

        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper, configService,
                mock(com.ycmn.module.system.mapper.SysRoleOrgMapper.class));
        com.ycmn.common.scope.ScopeRequest req = com.ycmn.common.scope.ScopeRequest.builder()
                .user(s).module("customer").action("view").anchor(com.ycmn.common.scope.ScopeRequest.AnchorType.OWNER)
                .build();
        // custom 解析走 managedOrgIds; 未装载时 resolveCustom 回退 loadRoleOrgs(mock 空) → 仅本人
        assertEquals(Set.of(1L), new HashSet<>(r.resolveScope(req).getVisibleUserIds()),
                "USER 级 * * 通配(custom)应覆盖 ROLE 级 customer view(region), 而非取 region 全大区");
    }
}
