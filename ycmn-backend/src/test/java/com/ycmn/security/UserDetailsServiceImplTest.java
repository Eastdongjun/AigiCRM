package com.ycmn.security;

import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UserDetailsServiceImpl 数据范围测试。
 * 聚焦 Task 8 的"用户级覆盖"分支（角色聚合后 data_scope_override 覆盖 dataScope）：
 *   - override 非空 → 覆盖角色级 dataScope
 *   - override 为 null / 空白 → 跟随角色聚合结果
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock SysUserMapper userMapper;
    @Mock SysMenuMapper menuMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock SysDeptMapper deptMapper;
    @Mock SysOrgAdminMapper orgAdminMapper;
    @Mock SysRoleOrgMapper roleOrgMapper;
    @Mock SysPositionMapper positionMapper;
    @Mock SysPermissionRuleMapper permissionRuleMapper;
    @Mock PermissionCacheService permissionCacheService;

    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userMapper, menuMapper, roleMapper, deptMapper,
                orgAdminMapper, roleOrgMapper, mock(com.ycmn.module.system.mapper.SysUserOrgMapper.class),
                positionMapper, permissionRuleMapper, permissionCacheService,
                mock(com.ycmn.module.system.service.RolePermScopeService.class));
    }

    /** 构造基础用户：id=1、正常状态、无组织归属（跳过 2b 的 org 查询） */
    private SysUser baseUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("test01");
        user.setPassword("x");
        user.setRealName("测试");
        user.setStatus(1);
        return user;
    }

    /** 构造角色：dataScope=dept（角色级默认"本部门及以下"） */
    private SysRole roleWithDeptScope() {
        SysRole role = new SysRole();
        role.setId(10L);
        role.setRoleCode("sales");
        role.setDataScope("dept");
        return role;
    }

    private void stubCommonLoad(SysUser user, SysRole role) {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(menuMapper.selectPermissionsByUserId(1L)).thenReturn(Set.of("system:user:view"));
        when(roleMapper.selectRolesByUserId(1L)).thenReturn(List.of(role));
        when(positionMapper.selectByUserId(1L)).thenReturn(List.of());
        when(permissionRuleMapper.selectByRoleId(role.getId())).thenReturn(List.of());
        when(orgAdminMapper.selectManagedOrgIds(1L)).thenReturn(List.of());
    }

    @Test
    void overrideWinsWhenNonBlank() {
        SysUser user = baseUser();
        user.setDataScopeOverride("team");
        stubCommonLoad(user, roleWithDeptScope());

        SecurityUser securityUser = (SecurityUser) service.loadUserByUsername("1");

        // 用户级 override 覆盖角色级 dept
        assertEquals("team", securityUser.getDataScope());
    }

    @Test
    void followsRoleWhenOverrideNull() {
        SysUser user = baseUser();
        user.setDataScopeOverride(null);
        stubCommonLoad(user, roleWithDeptScope());

        SecurityUser securityUser = (SecurityUser) service.loadUserByUsername("1");

        // 未配置 override → 跟随角色聚合结果
        assertEquals("dept", securityUser.getDataScope());
    }

    @Test
    void followsRoleWhenOverrideBlank() {
        SysUser user = baseUser();
        user.setDataScopeOverride("   ");
        stubCommonLoad(user, roleWithDeptScope());

        SecurityUser securityUser = (SecurityUser) service.loadUserByUsername("1");

        // 空白 override 视为未配置 → 跟随角色聚合结果
        assertEquals("dept", securityUser.getDataScope());
    }

    // ==================== I2：多角色并集数据收集（设计 §4.2） ====================

    /** 双角色（dept+team）：dataScopes 收集所有角色 code；dataScope 单值保留 min-rank（dept） */
    @Test
    void multiRoleCollectsAllScopeCodes() {
        SysUser user = baseUser();
        SysRole deptRole = roleWithDeptScope();
        SysRole teamRole = new SysRole();
        teamRole.setId(11L);
        teamRole.setRoleCode("manager");
        teamRole.setDataScope("team");

        when(userMapper.selectById(1L)).thenReturn(user);
        when(menuMapper.selectPermissionsByUserId(1L)).thenReturn(Set.of("system:user:view"));
        when(roleMapper.selectRolesByUserId(1L)).thenReturn(List.of(deptRole, teamRole));
        when(positionMapper.selectByUserId(1L)).thenReturn(List.of());
        when(permissionRuleMapper.selectByRoleId(deptRole.getId())).thenReturn(List.of());
        when(permissionRuleMapper.selectByRoleId(teamRole.getId())).thenReturn(List.of());
        when(orgAdminMapper.selectManagedOrgIds(1L)).thenReturn(List.of());

        SecurityUser securityUser = (SecurityUser) service.loadUserByUsername("1");

        assertEquals(List.of("dept", "team"), securityUser.getDataScopes(),
                "dataScopes 必须收集所有角色的 code（OrgScopeResolver 并集用）");
        assertEquals("dept", securityUser.getDataScope(),
                "单值兼容字段保留 min-rank 结果：dept(4) < team(7)");
    }

    /** 用户级覆盖存在 → dataScopes 清空，并集路径失效（用户级 > 角色级，整体取代） */
    @Test
    void overrideClearsMultiRoleDataScopes() {
        SysUser user = baseUser();
        user.setDataScopeOverride("team");
        SysRole deptRole = roleWithDeptScope();
        SysRole teamRole = new SysRole();
        teamRole.setId(11L);
        teamRole.setRoleCode("manager");
        teamRole.setDataScope("team");

        when(userMapper.selectById(1L)).thenReturn(user);
        when(menuMapper.selectPermissionsByUserId(1L)).thenReturn(Set.of("system:user:view"));
        when(roleMapper.selectRolesByUserId(1L)).thenReturn(List.of(deptRole, teamRole));
        when(positionMapper.selectByUserId(1L)).thenReturn(List.of());
        when(permissionRuleMapper.selectByRoleId(deptRole.getId())).thenReturn(List.of());
        when(permissionRuleMapper.selectByRoleId(teamRole.getId())).thenReturn(List.of());
        when(orgAdminMapper.selectManagedOrgIds(1L)).thenReturn(List.of());

        SecurityUser securityUser = (SecurityUser) service.loadUserByUsername("1");

        assertEquals("team", securityUser.getDataScope());
        assertTrue(securityUser.getDataScopes().isEmpty(),
                "用户级覆盖应清空并集列表，OrgScopeResolver 走单值路径（覆盖整体取代角色并集）");
    }

    // ==================== Redis 权限缓存：命中重组（2026-08-09） ====================

    /** cache-hit 时从 PermissionContext 重组 SecurityUser：authorities 由 permissions 重建、字段完整 */
    @Test
    void cacheHit_rebuildsSecurityUserFromContext() {
        SysUser user = baseUser();
        when(userMapper.selectById(1L)).thenReturn(user);

        // 构造缓存上下文（模拟已缓存）
        PermissionContext ctx = new PermissionContext();
        ctx.setUserId(1L);
        ctx.setUsername("test01");
        ctx.setRealName("测试");
        ctx.setDataScope("team");
        ctx.setRoleCodes(List.of("sales"));
        ctx.setPermissions(Set.of("system:user:view", "customer:view"));
        when(permissionCacheService.get(1L)).thenReturn(ctx);

        SecurityUser securityUser = (SecurityUser) service.loadUserByUsername("1");

        // 重组字段正确
        assertEquals("team", securityUser.getDataScope());
        assertEquals(1L, securityUser.getUserId());
        assertEquals("test01", securityUser.getUsername());
        assertEquals(1, securityUser.getRoleCodes().size());
        // authorities 由 permissions 重建（SimpleGrantedAuthority 不落 Redis）
        assertEquals(2, securityUser.getAuthorities().size());
        assertTrue(securityUser.getAuthorities().stream()
                .anyMatch(a -> "customer:view".equals(a.getAuthority())));
    }
}
