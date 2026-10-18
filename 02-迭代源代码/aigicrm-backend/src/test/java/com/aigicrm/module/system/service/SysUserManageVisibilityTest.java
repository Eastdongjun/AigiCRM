package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserOrgMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.PermissionCacheService;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【审查 perm-3 2026-10-18】岗位分配目标用户可见性守卫（与人员详情同口径）：
 * 非 admin 跨部门拒绝、同部门通过、组织管理员管辖子树通过、admin 放行、本人放行。
 */
class SysUserManageVisibilityTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);

    private SysUserService service;

    @BeforeEach
    void setUp() {
        service = new SysUserService(userMapper, mock(SysUserRoleMapper.class),
                mock(PasswordEncoder.class), mock(SysConfigService.class),
                mock(BizCustomerMapper.class), mock(BizCustomerShareMapper.class),
                mock(JdbcTemplate.class), mock(SysRoleMapper.class), mock(SeaService.class),
                deptMapper, mock(SysUserOrgMapper.class), mock(PermissionCacheService.class),
                mock(JwtTokenProvider.class), scopeResolver, mock(SecurityAuditService.class),
                mock(com.aigicrm.module.common.service.FormDraftService.class));
    }

    private static SysUser userOf(Long id, Long deptId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setDeptId(deptId);
        return u;
    }

    @Test
    void crossDeptTarget_rejected() {
        when(userMapper.selectById(9L)).thenReturn(userOf(9L, 200L));
        when(scopeResolver.resolveVisibleDeptIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(100L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isOrgAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.assertCanManageUser(9L));
            org.junit.jupiter.api.Assertions.assertEquals("无权操作该用户", ex.getMessage());
        }
    }

    @Test
    void sameDeptTarget_passes() {
        when(userMapper.selectById(9L)).thenReturn(userOf(9L, 100L));
        when(scopeResolver.resolveVisibleDeptIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(100L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            assertDoesNotThrow(() -> service.assertCanManageUser(9L));
        }
    }

    @Test
    void orgAdminManagedSubtree_passes() {
        when(userMapper.selectById(9L)).thenReturn(userOf(9L, 200L));
        when(scopeResolver.resolveVisibleDeptIds(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(100L));
        when(deptMapper.selectDescendantIds(50L)).thenReturn(List.of(50L, 200L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isOrgAdmin).thenReturn(true);
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of(50L));

            assertDoesNotThrow(() -> service.assertCanManageUser(9L));
        }
    }

    @Test
    void admin_passesWithoutBoundary() {
        when(userMapper.selectById(9L)).thenReturn(userOf(9L, 200L));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);

            assertDoesNotThrow(() -> service.assertCanManageUser(9L));
        }
    }

    @Test
    void self_passesEvenOutsideVisibleDepts() {
        when(userMapper.selectById(7L)).thenReturn(userOf(7L, 200L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            assertDoesNotThrow(() -> service.assertCanManageUser(7L));
        }
    }

    @Test
    void missingTarget_rejected() {
        when(userMapper.selectById(9L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            assertThrows(BusinessException.class, () -> service.assertCanManageUser(9L));
        }
    }
}
