package com.ycmn.module.file.service;

import com.ycmn.common.BusinessException;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.entity.BizFolderAcl;
import com.ycmn.module.file.mapper.BizFileFolderMapper;
import com.ycmn.module.file.mapper.BizFolderAclMapper;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FolderAclServiceTest {

    private final BizFolderAclMapper aclMapper = mock(BizFolderAclMapper.class);
    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final FolderPermissionService permissionService = mock(FolderPermissionService.class);
    private final FolderAclService svc = new FolderAclService(aclMapper, folderMapper, roleMapper, permissionService);

    private static final Long USER_ID = 42L;
    private static final Long FOLDER_ID = 100L;

    // ---------- requireEdit：无 P_EDIT 权限一律拒绝 ----------

    @Test
    void withoutEditPermission_allAclOperationsThrow403() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(false);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.listAcl(FOLDER_ID));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.P_VIEW)));
            assertThrows(BusinessException.class, () -> svc.removeAcl(FOLDER_ID, 3L));
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, 0));
            verify(permissionService, times(4)).check(eq(FOLDER_ID), eq(FolderPermissionService.P_EDIT), eq(USER_ID));
            verifyNoInteractions(aclMapper, folderMapper, roleMapper);
        }
    }

    @Test
    void nullFolderId_throwsBeforeCheck() {
        assertThrows(BusinessException.class, () -> svc.saveAcl(null, 3L, List.of(FolderPermissionService.P_VIEW)));
    }

    // ---------- saveAcl：白名单校验 + 插入 ----------

    @Test
    void saveAcl_validPerms_insertsWithCurrentUser() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        when(aclMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD));
            ArgumentCaptor<BizFolderAcl> cap = ArgumentCaptor.forClass(BizFolderAcl.class);
            verify(aclMapper).insert(cap.capture());
            BizFolderAcl saved = cap.getValue();
            assertEquals(FOLDER_ID, saved.getFolderId());
            assertEquals(3L, saved.getRoleId());
            assertEquals("view,upload", saved.getPerms());
            assertEquals(USER_ID, saved.getCreatedBy());
            assertNotNull(saved.getCreatedAt());
        }
    }

    @Test
    void saveAcl_duplicate_upsertsByUpdate() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        BizFolderAcl existing = new BizFolderAcl();
        existing.setId(7L); existing.setFolderId(FOLDER_ID); existing.setRoleId(3L); existing.setPerms("view");
        when(aclMapper.selectOne(any())).thenReturn(existing);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.P_VIEW, FolderPermissionService.P_DOWNLOAD));
            verify(aclMapper).updateById(existing);
            verify(aclMapper, never()).insert(any(BizFolderAcl.class));
            assertEquals("view,download", existing.getPerms());
        }
    }

    @Test
    void saveAcl_nullRole_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, null, List.of(FolderPermissionService.P_VIEW)));
        }
    }

    @Test
    void saveAcl_emptyOrNullPerms_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, null));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of()));
            verifyNoInteractions(aclMapper);
        }
    }

    @Test
    void saveAcl_permOutsideWhitelist_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            // 非法权限项（含空串，空串会破坏公共 view 兜底，必须拒绝）
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of("admin")));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.P_VIEW, "x")));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of("")));
            verifyNoInteractions(aclMapper);
        }
    }

    // ---------- removeAcl ----------

    @Test
    void removeAcl_deletesByFolderAndRole() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.removeAcl(FOLDER_ID, 3L);
            verify(aclMapper).delete(any());
        }
    }

    // ---------- setDownloadable ----------

    @Test
    void setDownloadable_validValue_updatesFolder() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        BizFileFolder folder = new BizFileFolder(); folder.setId(FOLDER_ID); folder.setDownloadable(1);
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.setDownloadable(FOLDER_ID, 0);
            assertEquals(0, folder.getDownloadable());
            verify(folderMapper).updateById(folder);
        }
    }

    @Test
    void setDownloadable_invalidValue_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, 2));
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, null));
            verifyNoInteractions(folderMapper);
        }
    }

    @Test
    void setDownloadable_folderNotFound_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, 0));
        }
    }

    // ---------- listAcl ----------

    @Test
    void listAcl_returnsRoleRowsWithPerms() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        BizFolderAcl acl = new BizFolderAcl();
        acl.setId(1L); acl.setFolderId(FOLDER_ID); acl.setRoleId(3L); acl.setPerms("view,download");
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        SysRole role = new SysRole(); role.setId(3L); role.setRoleName("销售员"); role.setRoleCode("sales");
        when(roleMapper.selectById(3L)).thenReturn(role);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            List<Map<String, Object>> rows = svc.listAcl(FOLDER_ID);
            assertEquals(1, rows.size());
            Map<String, Object> row = rows.get(0);
            assertEquals(3L, row.get("roleId"));
            assertEquals("销售员", row.get("roleName"));
            assertEquals(List.of("view", "download"), row.get("perms"));
        }
    }

    @Test
    void listAcl_unknownRole_fallsBackToUnknownName() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        BizFolderAcl acl = new BizFolderAcl();
        acl.setId(1L); acl.setFolderId(FOLDER_ID); acl.setRoleId(99L); acl.setPerms("view");
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        when(roleMapper.selectById(99L)).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertEquals("未知角色", svc.listAcl(FOLDER_ID).get(0).get("roleName"));
        }
    }

    // ---------- permissions ----------

    @Test
    void permissions_returnsPermsAndDownloadable() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(FOLDER_ID); folder.setDownloadable(0);
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
        when(permissionService.permsFor(FOLDER_ID, USER_ID)).thenReturn(List.of(FolderPermissionService.P_VIEW));
        Map<String, Object> row = svc.permissions(FOLDER_ID, USER_ID);
        assertEquals(List.of(FolderPermissionService.P_VIEW), row.get("perms"));
        assertEquals(0, row.get("downloadable"));
    }

    @Test
    void permissions_downloadableNull_defaultsToOne() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(FOLDER_ID); folder.setDownloadable(null);
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
        when(permissionService.permsFor(FOLDER_ID, USER_ID)).thenReturn(List.of(FolderPermissionService.P_VIEW));
        assertEquals(1, svc.permissions(FOLDER_ID, USER_ID).get("downloadable"));
    }

    @Test
    void permissions_folderMissing_returnsEmptyPerms() {
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(null);
        Map<String, Object> row = svc.permissions(FOLDER_ID, USER_ID);
        assertEquals(List.of(), row.get("perms"));
        assertEquals(1, row.get("downloadable"));
    }

    // ---------- saveAclFromBody：controller 畸形输入解析（Task 5 审查遗留）----------

    @Test
    void saveAclFromBody_malformedBody_throwsParamErrorWithoutTouchingMappers() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            // body 缺失
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID, null));
            // roleId 缺失
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID, Map.of("perms", List.of("view"))));
            // roleId 非数字
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID,
                    Map.<String, Object>of("roleId", "abc", "perms", List.of("view"))));
            // perms 缺失
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID,
                    Map.<String, Object>of("roleId", 3)));
            // perms 非数组
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID,
                    Map.<String, Object>of("roleId", 3, "perms", "view")));
            // 不触碰任何 mapper
            verify(aclMapper, never()).insert(any(BizFolderAcl.class));
            verify(aclMapper, never()).updateById(any(BizFolderAcl.class));
            verify(aclMapper, never()).selectOne(any());
        }
    }

    @Test
    void saveAclFromBody_validBody_parsesAndSaves() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.P_EDIT, USER_ID)).thenReturn(true);
        when(aclMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.saveAclFromBody(FOLDER_ID, Map.<String, Object>of("roleId", 3, "perms", List.of("view", "upload")));
            ArgumentCaptor<BizFolderAcl> cap = ArgumentCaptor.forClass(BizFolderAcl.class);
            verify(aclMapper).insert(cap.capture());
            assertEquals(3L, cap.getValue().getRoleId());
            assertEquals("view,upload", cap.getValue().getPerms());
            assertEquals(USER_ID, cap.getValue().getCreatedBy());
        }
    }
}
