package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserOrgMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.PermissionCacheService;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P0 2026-10-17 用户创建/更新越界】回归钉：
 * 非 admin 建人/改人时，目标 deptId/officeId（前端不传 orgId）必须在调用者可见范围内；
 * 授权角色必须是调用者权限子集，禁止借用户管理横向提权。
 */
class SysUserOrgBoundaryTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SysConfigService configService = mock(SysConfigService.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SeaService seaService = mock(SeaService.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysUserOrgMapper userOrgMapper = mock(SysUserOrgMapper.class);
    private final PermissionCacheService permissionCacheService = mock(PermissionCacheService.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final SecurityAuditService securityAudit = mock(SecurityAuditService.class);
    private final com.aigicrm.module.common.service.FormDraftService formDraftService =
            mock(com.aigicrm.module.common.service.FormDraftService.class);

    private SysUserService service;

    @BeforeEach
    void setUp() {
        service = new SysUserService(userMapper, userRoleMapper, passwordEncoder, configService,
                customerMapper, shareMapper, jdbc, roleMapper, seaService, deptMapper, userOrgMapper,
                permissionCacheService, jwtTokenProvider, scopeResolver, securityAudit, formDraftService);
    }

    private SysUser createPayload(Long deptId, Long officeId, Long roleId) {
        SysUser user = new SysUser();
        user.setUsername("u_" + deptId + "_" + officeId);
        user.setRealName("测试用户");
        user.setPhone("13800000000");
        user.setDataScopeOverride("self");
        user.setDeptId(deptId);
        user.setOfficeId(officeId);
        user.setRoleId(roleId);
        return user;
    }

    private SysDept dept(Long id, Long groupId) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setGroupId(groupId);
        return dept;
    }

    private SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode("sales");
        role.setDataScope("self");
        return role;
    }

    private SecurityUser caller(Set<String> permissions) {
        SecurityUser user = new SecurityUser();
        user.setUserId(7L);
        user.setDeptId(100L);
        user.setDataScope("self");
        user.setPermissions(permissions);
        return user;
    }

    @Test
    void create_withDeptOutsideVisibleScope_rejected() {
        when(roleMapper.selectById(11L)).thenReturn(role(11L));
        when(deptMapper.selectById(200L)).thenReturn(dept(200L, 1L));
        when(scopeResolver.resolveVisibleDeptIds(any())).thenReturn(List.of(100L));

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(caller(Set.of()));
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of());
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertThrows(BusinessException.class,
                    () -> service.create(createPayload(200L, null, 11L)),
                    "目标部门不在调用者可见范围内应拒绝");
        }
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void create_withOfficeOutsideVisibleScope_rejected() {
        when(roleMapper.selectById(11L)).thenReturn(role(11L));
        when(deptMapper.selectById(100L)).thenReturn(dept(100L, 1L));
        when(scopeResolver.resolveVisibleDeptIds(any())).thenReturn(List.of(100L));

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(caller(Set.of()));
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of());
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertThrows(BusinessException.class,
                    () -> service.create(createPayload(100L, 300L, 11L)),
                    "目标办事处不在调用者可见范围内应拒绝");
        }
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void create_withVisibleDept_passes() {
        when(roleMapper.selectById(11L)).thenReturn(role(11L));
        when(deptMapper.selectById(100L)).thenReturn(dept(100L, 1L));
        when(scopeResolver.resolveVisibleDeptIds(any())).thenReturn(List.of(100L));

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(caller(Set.of()));
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of());
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertDoesNotThrow(() -> service.create(createPayload(100L, 100L, 11L)));
        }
        verify(userMapper).insert(any(SysUser.class));
    }

    @Test
    void create_withRoleExceedingCallerPermissions_rejected() {
        when(roleMapper.selectById(11L)).thenReturn(role(11L));
        when(deptMapper.selectById(100L)).thenReturn(dept(100L, 1L));
        when(scopeResolver.resolveVisibleDeptIds(any())).thenReturn(List.of(100L));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(11L)))
                .thenReturn(List.of("system:role:edit"));

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(caller(Set.of("system:user:add")));
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of());
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(createPayload(100L, 100L, 11L)));
            assertEquals("无权授予权限: system:role:edit", ex.getMessage());
        }
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void update_withDeptOutsideVisibleScope_rejected() {
        SysUser existing = new SysUser();
        existing.setId(5L);
        when(userMapper.selectById(5L)).thenReturn(existing);
        when(deptMapper.selectById(200L)).thenReturn(dept(200L, 1L));
        when(scopeResolver.resolveVisibleDeptIds(any())).thenReturn(List.of(100L));

        SysUser payload = new SysUser();
        payload.setId(5L);
        payload.setDeptId(200L);
        payload.setDataScopeOverride("self");

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(caller(Set.of()));
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of());
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertThrows(BusinessException.class, () -> service.update(payload),
                    "更新目标部门越界应拒绝");
        }
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void update_withOfficeOutsideVisibleScope_rejected() {
        SysUser existing = new SysUser();
        existing.setId(5L);
        when(userMapper.selectById(5L)).thenReturn(existing);
        when(deptMapper.selectById(100L)).thenReturn(dept(100L, 1L));
        when(scopeResolver.resolveVisibleDeptIds(any())).thenReturn(List.of(100L));

        SysUser payload = new SysUser();
        payload.setId(5L);
        payload.setDeptId(100L);
        payload.setOfficeId(300L);
        payload.setDataScopeOverride("self");

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(caller(Set.of()));
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of());
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertThrows(BusinessException.class, () -> service.update(payload),
                    "更新目标办事处越界应拒绝");
        }
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void update_withManagedOrgSubtree_passesForOrgAdmin() {
        SysUser existing = new SysUser();
        existing.setId(5L);
        when(userMapper.selectById(5L)).thenReturn(existing);
        when(deptMapper.selectById(200L)).thenReturn(dept(200L, 1L));
        when(deptMapper.selectDescendantIds(50L)).thenReturn(List.of(50L, 200L));
        when(scopeResolver.resolveVisibleDeptIds(any())).thenReturn(List.of(100L));

        SysUser payload = new SysUser();
        payload.setId(5L);
        payload.setDeptId(200L);
        payload.setDataScopeOverride("self");

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(caller(Set.of()));
            sec.when(SecurityUtils::getCurrentManagedOrgIds).thenReturn(List.of(50L));
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            assertDoesNotThrow(() -> service.update(payload));
        }
        verify(userMapper).updateById(any(SysUser.class));
    }
}
