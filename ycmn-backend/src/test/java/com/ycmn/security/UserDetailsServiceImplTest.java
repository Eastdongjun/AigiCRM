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
    @Mock SysOrgMapper orgMapper;
    @Mock SysOrgAdminMapper orgAdminMapper;
    @Mock SysRoleOrgMapper roleOrgMapper;
    @Mock SysPositionMapper positionMapper;
    @Mock SysPermissionRuleMapper permissionRuleMapper;

    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userMapper, menuMapper, roleMapper, orgMapper,
                orgAdminMapper, roleOrgMapper, positionMapper, permissionRuleMapper);
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
}
