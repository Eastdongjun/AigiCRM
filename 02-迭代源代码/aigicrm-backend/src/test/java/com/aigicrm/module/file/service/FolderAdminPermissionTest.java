package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * 【admin 权限不再隐式全通】权限中心取消 file:delete/file:share/file:edit 后，
 * admin 不能靠 isAdmin() 继续做这些动作；数据面仍保持 admin 全通，只收口动作码。
 */
class FolderAdminPermissionTest {

    private final FolderPermissionService svc = new FolderPermissionService(
            mock(BizFileFolderMapper.class), mock(BizFileMapper.class), mock(SysUserMapper.class),
            mock(SysDeptMapper.class), mock(SysRoleMapper.class), mock(BizFolderTypeDefaultMapper.class),
            mock(com.aigicrm.common.scope.OrgScopeResolver.class));

    @Test
    void adminWithoutDeleteCode_cannotDeleteFolder() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(() -> SecurityUtils.hasAuthority("file:delete")).thenReturn(false);
            assertFalse(svc.check(1L, FolderPermissionService.P_DELETE, 7L));
            assertFalse(svc.checkFile(1L, FolderPermissionService.P_DELETE, 7L));
            m.when(() -> SecurityUtils.hasAuthority("file:delete")).thenReturn(true);
            assertTrue(svc.check(1L, FolderPermissionService.P_DELETE, 7L));
            assertTrue(svc.checkFile(1L, FolderPermissionService.P_DELETE, 7L));
        }
    }

    @Test
    void adminWithoutFolderManage_cannotCreateFolder() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(() -> SecurityUtils.hasAuthority("file:folder:manage")).thenReturn(false);
            assertFalse(svc.canCreateFolder("public", 7L));
            m.when(() -> SecurityUtils.hasAuthority("file:folder:manage")).thenReturn(true);
            assertTrue(svc.canCreateFolder("public", 7L));
        }
    }

    @Test
    void adminPerms_onlyReflectHeldCodes() {
        BizFile file = new BizFile();
        file.setId(11L);
        BizFileFolder folder = new BizFileFolder();
        folder.setId(21L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(() -> SecurityUtils.hasAuthority("file:view")).thenReturn(true);
            m.when(() -> SecurityUtils.hasAuthority("file:delete")).thenReturn(false);
            m.when(() -> SecurityUtils.hasAuthority("file:edit")).thenReturn(false);
            m.when(() -> SecurityUtils.hasAuthority("file:share")).thenReturn(false);
            m.when(() -> SecurityUtils.hasAuthority("file:upload")).thenReturn(false);

            assertEquals(List.of("view", "download"), svc.permsForFiles(List.of(file), 7L).get(11L));
            assertEquals(List.of("view", "download"), svc.permsForBatch(List.of(folder), 7L).get(21L));
        }
    }
}
