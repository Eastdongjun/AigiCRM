package com.ycmn.common.scope;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        List<Long> u = r.resolve(su(1L, "dept", 11L));
        assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(u), "环数据不误伤、不无限递归");
        // 恰 3 次子树查询（13 的子 11 已收集，不再递归查 11）
        verify(deptMapper, times(3)).selectList(any(LambdaQueryWrapper.class));
        verify(userMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    // ---- all / self ----

    @Test
    void allReturnsNull() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        assertNull(r.resolve(su(1L, "all", 11L)));
    }

    @Test
    void selfReturnsOnlySelf() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        List<Long> u = r.resolve(suMulti(1L, List.of("team", "dept"), 11L));
        // team={1,2,3} ∪ dept={1,2,5} = {1,2,3,5}
        assertEquals(Set.of(1L, 2L, 3L, 5L), new HashSet<>(u), "并集=两角色各自可见集合并集");
    }

    /** 任一角色 all → 本集团不过滤（null 短路），不做无谓解析 */
    @Test
    void unionShortCircuitsOnAll() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
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
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        List<Long> u = r.resolve(su(1L, "org", 11L));
        assertEquals(Set.of(1L, 7L), new HashSet<>(u), "org 仅本部门，不含子部门用户");
        verify(deptMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }
}
