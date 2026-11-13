package com.aigicrm.common.scope;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.module.common.service.ScopeService;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * ScopeService 测试（范围感知筛选器）。
 * 策略：service 单测为主（mock resolver 输出），另含一个真实 resolver 的 self 集成用例；
 * wrapper 条件断言复用 OrgScopeResolverTest 的 thenAnswer 模式（列名 → 值列表）。
 * 覆盖：self 仅自己 / deptId 过滤 / all 返回全部 / 空范围空列表 / 部门树组装（全量、部分、提升、排序）。
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ScopeServiceTest {

    @Mock OrgScopeResolver resolver;
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

    private SysUser user(long id, Long deptId, String realName) {
        SysUser u = new SysUser();
        u.setId(id); u.setDeptId(deptId); u.setRealName(realName);
        return u;
    }

    private SysDept dept(long id, Long parentId, String name) {
        return dept(id, parentId, name, 0, 1);
    }

    private SysDept dept(long id, Long parentId, String name, int sortOrder) {
        return dept(id, parentId, name, sortOrder, 1);
    }

    private SysDept dept(long id, Long parentId, String name, int sortOrder, int status) {
        SysDept d = new SysDept();
        d.setId(id); d.setParentId(parentId); d.setDeptName(name);
        d.setSortOrder(sortOrder); d.setStatus(status);
        return d;
    }

    private SecurityUser su(Long userId, String scope, Long deptId) {
        SecurityUser s = new SecurityUser();
        s.setUserId(userId); s.setDataScope(scope); s.setDeptId(deptId);
        return s;
    }

    /** 解析 wrapper 条件为 列名→值列表（IN 展开为多值，eq 为单值） */
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

    // ---- visibleUsers ----

    /** self：真实 resolver 集成，仅返回本人（brief Step1 用例，补齐 selectByIds stub） */
    @Test
    void selfScopeReturnsOnlySelf() {
        OrgScopeResolver realResolver = new OrgScopeResolver(userMapper, deptMapper, null,
                mock(com.aigicrm.module.system.mapper.SysRoleOrgMapper.class), null, null); // configService=null → 开关不生效，保持仅本人
        when(userMapper.selectByIds(anyList())).thenReturn(List.of(user(1L, 11L, "本人")));
        ScopeService svc = new ScopeService(realResolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<Map<String, Object>> users = svc.visibleUsers(su(1L, "self", 11L), null);
        assertEquals(1, users.size()); // 仅自己
        assertEquals(1L, users.get(0).get("id"));
        assertEquals("本人", users.get(0).get("realName"));
        assertEquals(11L, users.get(0).get("deptId"));
    }

    /** deptId 过滤：U 中按部门 ∩ 过滤 */
    @Test
    void deptIdFiltersVisibleUsers() {
        when(resolver.resolve(any())).thenReturn(List.of(1L, 2L));
        when(userMapper.selectByIds(anyList()))
                .thenReturn(List.of(user(1L, 11L, "甲"), user(2L, 12L, "乙")));
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<Map<String, Object>> users = svc.visibleUsers(su(1L, "office", 11L), 12L);
        assertEquals(1, users.size());
        assertEquals(2L, users.get(0).get("id"));
    }

    /** 可见列表按 realName 升序 */
    @Test
    void visibleUsersSortedByRealName() {
        when(resolver.resolve(any())).thenReturn(List.of(1L, 2L));
        when(userMapper.selectByIds(anyList()))
                .thenReturn(List.of(user(2L, 12L, "B员工"), user(1L, 11L, "A员工")));
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<Map<String, Object>> users = svc.visibleUsers(su(1L, "office", 11L), null);
        assertEquals(2, users.size());
        assertEquals("A员工", users.get(0).get("realName"));
        assertEquals("B员工", users.get(1).get("realName"));
    }

    /** all：全量启用用户（status=1），deptId 为空不生成 dept 条件
     * 【PR-01 P1-09】all=当前集团全量，仅平台 admin 为跨集团全量；本用例覆盖 admin 分支 */
    @Test
    void allScopeReturnsAllEnabledUsers() {
        when(resolver.resolve(any())).thenReturn(null);
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertEquals(List.of(1L), columnValues(w).get("status"), "all 分支必须过滤 status=1");
            assertNull(columnValues(w).get("dept_id"), "deptId 为空不得生成 dept 条件");
            return List.of(user(1L, 11L, "甲"), user(2L, 12L, "乙"));
        });
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        SecurityUser admin = su(1L, "all", 11L);
        admin.setRoleCodes(List.of("admin"));
        assertEquals(2, svc.visibleUsers(admin, null).size());
    }

    /** 非 admin 的 all 收敛为当前集团（P1-09）：无集团归属直接空集，不查库 */
    @Test
    void allScopeNonAdminWithoutGroupReturnsEmpty() {
        when(resolver.resolve(any())).thenReturn(null);
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        assertTrue(svc.visibleUsers(su(1L, "all", 11L), null).isEmpty());
        verify(userMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    /** all + deptId：条件必须进入 wrapper（SQL 层过滤）
     * 【PR-01 P1-09】本用例覆盖 admin 跨集团全量分支（非 admin 走集团成员过滤） */
    @Test
    void allScopeWithDeptIdFiltersInWrapper() {
        when(resolver.resolve(any())).thenReturn(null);
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysUser> w = inv.getArgument(0);
            assertEquals(List.of(1L), columnValues(w).get("status"), "必须过滤 status=1");
            assertEquals(List.of(12L), columnValues(w).get("dept_id"), "deptId 必须进 wrapper");
            return List.of(user(2L, 12L, "乙"));
        });
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        SecurityUser admin = su(1L, "all", 11L);
        admin.setRoleCodes(List.of("admin"));
        List<Map<String, Object>> users = svc.visibleUsers(admin, 12L);
        assertEquals(1, users.size());
        assertEquals(2L, users.get(0).get("id"));
    }

    /** 空范围（无用户上下文）：直接空列表，不查库 */
    @Test
    void emptyScopeReturnsEmpty() {
        when(resolver.resolve(any())).thenReturn(List.of());
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        assertTrue(svc.visibleUsers(null, null).isEmpty());
        verify(userMapper, never()).selectByIds(anyList());
    }

    // ---- visibleDeptTree ----

    /** all + admin：完整部门树（status=1，根 parentId=0） */
    @Test
    void deptTreeAllReturnsFullTreeForAdmin() {
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(null);
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertEquals(List.of(1L), columnValues(w).get("status"), "部门查询必须过滤 status=1");
            return List.of(dept(1L, 0L, "集团"), dept(11L, 1L, "大区"), dept(12L, 1L, "办事处"));
        });
        SecurityUser su = su(1L, "all", 11L);
        su.setRoleCodes(List.of("admin"));
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<SysDept> tree = svc.visibleDeptTree(su);
        assertEquals(1, tree.size());
        assertEquals("集团", tree.get(0).getDeptName());
        assertEquals(2, tree.get(0).getChildren().size());
    }

    /** all + 非 admin：收窄为本人部门+祖先链，远处部门不出现（FEAT-20260804-DEPTS-DATASCOPE 口径） */
    @Test
    void deptTreeAllNarrowedForNonAdmin() {
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(null);
        when(deptMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<SysDept> w = inv.getArgument(0);
            assertEquals(List.of(1L), columnValues(w).get("status"), "部门查询必须过滤 status=1");
            return List.of(dept(1L, 0L, "集团"), dept(11L, 1L, "大区A"), dept(12L, 1L, "大区B"));
        });
        SecurityUser su = su(1L, "all", 11L); // 无 admin 角色
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<SysDept> tree = svc.visibleDeptTree(su);
        assertEquals(1, tree.size());
        assertEquals("集团", tree.get(0).getDeptName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("大区A", tree.get(0).getChildren().get(0).getDeptName(), "远处部门 12 不得出现");
    }

    /** 部分可见集合：selectByIds 批量查 + 按 parent_id 组装（children 挂接） */
    @Test
    void deptTreePartialAssemblesByParentId() {
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(List.of(1L, 11L, 12L));
        when(deptMapper.selectByIds(anyList())).thenReturn(List.of(
                dept(11L, 1L, "大区"), dept(1L, 0L, "集团"), dept(12L, 11L, "办事处")));
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<SysDept> tree = svc.visibleDeptTree(su(1L, "dept", 11L));
        assertEquals(1, tree.size());
        assertEquals("集团", tree.get(0).getDeptName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("大区", tree.get(0).getChildren().get(0).getDeptName());
        assertEquals(1, tree.get(0).getChildren().get(0).getChildren().size());
        assertEquals("办事处", tree.get(0).getChildren().get(0).getChildren().get(0).getDeptName());
        verify(deptMapper).selectByIds(List.of(1L, 11L, 12L));
    }

    /** parent 不可见的节点提升为顶层（不产生孤儿丢弃） */
    @Test
    void deptTreePromotesNodesWhoseParentInvisible() {
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(List.of(1L, 12L));
        when(deptMapper.selectByIds(anyList()))
                .thenReturn(List.of(dept(12L, 99L, "办事处"), dept(1L, 0L, "集团")));
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<SysDept> tree = svc.visibleDeptTree(su(1L, "dept", 11L));
        assertEquals(2, tree.size(), "parent 99 不可见，12 应提升为顶层");
        assertEquals("集团", tree.get(0).getDeptName());
        assertEquals("办事处", tree.get(1).getDeptName());
    }

    /** 层内按 sortOrder 升序 */
    @Test
    void deptTreeOrdersChildrenBySortOrder() {
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(List.of(1L, 11L, 12L));
        when(deptMapper.selectByIds(anyList())).thenReturn(List.of(
                dept(11L, 1L, "B部", 2), dept(12L, 1L, "A部", 1), dept(1L, 0L, "集团", 0)));
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<SysDept> tree = svc.visibleDeptTree(su(1L, "dept", 11L));
        assertEquals("A部", tree.get(0).getChildren().get(0).getDeptName());
        assertEquals("B部", tree.get(0).getChildren().get(1).getDeptName());
    }

    /** partial 分支必须过滤 status=1：禁用部门不进入可见树（与 all 分支口径一致） */
    @Test
    void deptTreePartialExcludesDisabledDept() {
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(List.of(1L, 11L));
        when(deptMapper.selectByIds(anyList())).thenReturn(List.of(
                dept(1L, 0L, "集团"), dept(11L, 1L, "禁用大区", 0, 0)));
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        List<SysDept> tree = svc.visibleDeptTree(su(1L, "dept", 11L));
        assertEquals(1, tree.size());
        assertEquals("集团", tree.get(0).getDeptName());
        List<SysDept> children = tree.get(0).getChildren();
        assertTrue(children == null || children.isEmpty(), "禁用部门 11 不得出现在可见树");
    }

    /** 空可见集合：空树，不查库 */
    @Test
    void deptTreeEmptyScopeReturnsEmpty() {
        when(resolver.resolveVisibleDeptIds(any())).thenReturn(List.of());
        ScopeService svc = new ScopeService(resolver, userMapper, deptMapper, mock(org.springframework.jdbc.core.JdbcTemplate.class));
        assertTrue(svc.visibleDeptTree(su(1L, "self", 11L)).isEmpty());
        verify(deptMapper, never()).selectByIds(anyList());
    }
}
