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
}
