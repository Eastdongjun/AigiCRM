package com.ycmn.module.system.service;

import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysOrgAdminMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【安全回归 H3】组织管理员越权守卫 canManageUser：
 * 管理员的"改密/删除/离职"操作必须校验目标用户是否在本人可管理组织内，
 * 非管理员/非组织管理员一律拒绝。防止组织管理员越权操作其他组织用户。
 */
class OrgAdminServiceSecurityTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysOrgAdminMapper orgAdminMapper = mock(SysOrgAdminMapper.class);
    private final org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    private final OrgAdminService svc = new OrgAdminService(orgAdminMapper, userMapper,
            mock(com.ycmn.security.PermissionCacheService.class), jdbc);

    private SysUser user(long id, Long orgId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setOrgId(orgId);
        return u;
    }

    @Test
    void admin_canManageAnyUser() {
        try (MockedStatic<SecurityUtils> s = mockStatic(SecurityUtils.class)) {
            s.when(SecurityUtils::isAdmin).thenReturn(true);
            s.when(SecurityUtils::isOrgAdmin).thenReturn(false); // admin 即使非 orgAdmin 也恒可
            when(userMapper.selectById(9L)).thenReturn(user(9L, 3L));
            assertTrue(svc.canManageUser(9L));
        }
    }

    @Test
    void nonAdmin_nonOrgAdmin_cannotManage() {
        try (MockedStatic<SecurityUtils> s = mockStatic(SecurityUtils.class)) {
            s.when(SecurityUtils::isAdmin).thenReturn(false);
            s.when(SecurityUtils::isOrgAdmin).thenReturn(false);
            assertFalse(svc.canManageUser(9L));
        }
    }

    @Test
    void orgAdmin_canManageUserInManagedOrg() {
        try (MockedStatic<SecurityUtils> s = mockStatic(SecurityUtils.class)) {
            s.when(SecurityUtils::isAdmin).thenReturn(false);
            s.when(SecurityUtils::isOrgAdmin).thenReturn(true);
            s.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of(1L, 3L));
            when(userMapper.selectById(9L)).thenReturn(user(9L, 3L));
            assertTrue(svc.canManageUser(9L));
        }
    }

    @Test
    void orgAdmin_cannotManageUserOutsideManagedOrg() {
        try (MockedStatic<SecurityUtils> s = mockStatic(SecurityUtils.class)) {
            s.when(SecurityUtils::isAdmin).thenReturn(false);
            s.when(SecurityUtils::isOrgAdmin).thenReturn(true);
            s.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of(1L)); // 仅组织1
            when(userMapper.selectById(9L)).thenReturn(user(9L, 3L)); // 目标在组织3
            assertFalse(svc.canManageUser(9L));
        }
    }

    @Test
    void orgAdmin_targetUserMissing_rejects() {
        try (MockedStatic<SecurityUtils> s = mockStatic(SecurityUtils.class)) {
            s.when(SecurityUtils::isAdmin).thenReturn(false);
            s.when(SecurityUtils::isOrgAdmin).thenReturn(true);
            when(userMapper.selectById(999L)).thenReturn(null);
            assertFalse(svc.canManageUser(999L));
        }
    }

    @Test
    void orgAdmin_targetUserNoOrg_rejects() {
        try (MockedStatic<SecurityUtils> s = mockStatic(SecurityUtils.class)) {
            s.when(SecurityUtils::isAdmin).thenReturn(false);
            s.when(SecurityUtils::isOrgAdmin).thenReturn(true);
            when(userMapper.selectById(9L)).thenReturn(user(9L, null));
            assertFalse(svc.canManageUser(9L));
        }
    }

    @Test
    void orgAdmin_cannotManageAdminRoleUser_evenInManagedOrg() {
        // 【2026-08-15 安全审计 H1 回归】目标即使在本组织内, 只要持有 admin 角色, 组织管理员一律不可操作
        // (否则经 reset-password 拿到临时口令即接管全系统)
        try (MockedStatic<SecurityUtils> s = mockStatic(SecurityUtils.class)) {
            s.when(SecurityUtils::isAdmin).thenReturn(false);
            s.when(SecurityUtils::isOrgAdmin).thenReturn(true);
            s.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of(3L));
            when(userMapper.selectById(9L)).thenReturn(user(9L, 3L)); // 目标在本组织
            when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.eq(Integer.class),
                    org.mockito.ArgumentMatchers.any())).thenReturn(1); // 但持 admin 角色
            assertFalse(svc.canManageUser(9L));
        }
    }
}
