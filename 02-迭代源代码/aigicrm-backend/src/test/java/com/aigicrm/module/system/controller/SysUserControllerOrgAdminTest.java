package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.service.OrgAdminService;
import com.aigicrm.module.system.service.SysUserService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SysUserControllerOrgAdminTest {

    private final SysUserService userService = mock(SysUserService.class);
    private final OrgAdminService orgAdminService = mock(OrgAdminService.class);
    private final SysUserController controller = new SysUserController(userService, orgAdminService);

    @Test
    void create_rejectsOrganizationOutsideManagedScopeBeforeServiceCall() {
        SysUser user = new SysUser();
        user.setOrgId(99L);
        when(orgAdminService.canManageOrg(99L)).thenReturn(false);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isOrgAdmin).thenReturn(true);

            assertThrows(BusinessException.class, () -> controller.create(user));
        }

        verifyNoInteractions(userService);
    }

    @Test
    void update_rejectsUserOutsideManagedScopeBeforeServiceCall() {
        when(orgAdminService.canManageUser(7L)).thenReturn(false);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isOrgAdmin).thenReturn(true);

            assertThrows(BusinessException.class, () -> controller.update(7L, new SysUser()));
        }

        verifyNoInteractions(userService);
    }

    @Test
    void delete_rejectsUserOutsideManagedScopeBeforeServiceCall() {
        when(orgAdminService.canManageUser(7L)).thenReturn(false);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isOrgAdmin).thenReturn(true);

            assertThrows(BusinessException.class, () -> controller.delete(7L));
        }

        verifyNoInteractions(userService);
    }

    /** 【P1 2026-11-15】仅持 system:user:add 码、非平台 admin/非组织管理员 → 入口即拒绝 */
    @Test
    void create_rejectsCodeOnlyNonOrgAdminBeforeServiceCall() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::isOrgAdmin).thenReturn(false);

            assertThrows(BusinessException.class, () -> controller.create(new SysUser()));
        }

        verifyNoInteractions(userService);
    }

    /** 平台 admin 不受组织管理员限制 */
    @Test
    void create_allowsPlatformAdmin() {
        when(userService.create(any(SysUser.class))).thenReturn("Temp@123456");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);

            controller.create(new SysUser());
        }

        verify(userService).create(any(SysUser.class));
    }
}
