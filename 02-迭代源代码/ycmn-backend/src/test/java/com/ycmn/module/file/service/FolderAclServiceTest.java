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

/**
 * 【九项权限模型 v2 2026-08-28】同步修复：ACL 白名单/管理入口升级 9 项新码，
 * stub 与断言全部改新码；新增 listRoles 轻量角色视图测试。
 */
class FolderAclServiceTest {

    private final BizFolderAclMapper aclMapper = mock(BizFolderAclMapper.class);
    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final FolderPermissionService permissionService = mock(FolderPermissionService.class);
    private final FolderAclService svc = new FolderAclService(aclMapper, folderMapper, roleMapper, permissionService);

    private static final Long USER_ID = 42L;
    private static final Long FOLDER_ID = 100L;

    // ---------- requireEdit：无 folder:rename 权限一律拒绝 ----------

    @Test
    void withoutEditPermission_allAclOperationsThrow403() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(false);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.listAcl(FOLDER_ID));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.FILE_VIEW)));
            assertThrows(BusinessException.class, () -> svc.removeAcl(FOLDER_ID, 3L));
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, 0));
            verify(permissionService, times(4)).check(eq(FOLDER_ID), eq(FolderPermissionService.FOLDER_RENAME), eq(USER_ID));
            verifyNoInteractions(aclMapper, folderMapper, roleMapper);
        }
    }

    @Test
    void nullFolderId_throwsBeforeCheck() {
        assertThrows(BusinessException.class, () -> svc.saveAcl(null, 3L, List.of(FolderPermissionService.FILE_VIEW)));
    }

    // ---------- saveAcl：白名单校验 + 插入 ----------

    @Test
    void saveAcl_validPerms_insertsWithCurrentUser() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        when(aclMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.FILE_VIEW, FolderPermissionService.FILE_UPLOAD));
            ArgumentCaptor<BizFolderAcl> cap = ArgumentCaptor.forClass(BizFolderAcl.class);
            verify(aclMapper).insert(cap.capture());
            BizFolderAcl saved = cap.getValue();
            assertEquals(FOLDER_ID, saved.getFolderId());
            assertEquals(3L, saved.getRoleId());
            assertEquals("file:view,file:upload", saved.getPerms());
            assertEquals(USER_ID, saved.getCreatedBy());
            assertNotNull(saved.getCreatedAt());
        }
    }

    @Test
    void saveAcl_duplicate_upsertsByUpdate() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        BizFolderAcl existing = new BizFolderAcl();
        existing.setId(7L); existing.setFolderId(FOLDER_ID); existing.setRoleId(3L); existing.setPerms("file:view");
        when(aclMapper.selectOne(any())).thenReturn(existing);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.FILE_VIEW, FolderPermissionService.FILE_DOWNLOAD));
            verify(aclMapper).updateById(existing);
            verify(aclMapper, never()).insert(any(BizFolderAcl.class));
            assertEquals("file:view,file:download", existing.getPerms());
        }
    }

    @Test
    void saveAcl_nullRole_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, null, List.of(FolderPermissionService.FILE_VIEW)));
        }
    }

    @Test
    void saveAcl_emptyOrNullPerms_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, null));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of()));
            verifyNoInteractions(aclMapper);
        }
    }

    @Test
    void saveAcl_permOutsideWhitelist_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            // 非法权限项（含空串、旧 6 项废弃码，空串会破坏公共 view 兜底，必须拒绝）
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of("admin")));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of(FolderPermissionService.FILE_VIEW, "x")));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of("")));
            assertThrows(BusinessException.class, () -> svc.saveAcl(FOLDER_ID, 3L, List.of("upload")));
            verifyNoInteractions(aclMapper);
        }
    }

    // ---------- removeAcl ----------

    @Test
    void removeAcl_deletesByFolderAndRole() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.removeAcl(FOLDER_ID, 3L);
            verify(aclMapper).delete(any());
        }
    }

    // ---------- setDownloadable ----------

    @Test
    void setDownloadable_validValue_updatesFolder() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
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
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, 2));
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, null));
            verifyNoInteractions(folderMapper);
        }
    }

    @Test
    void setDownloadable_folderNotFound_throws() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertThrows(BusinessException.class, () -> svc.setDownloadable(FOLDER_ID, 0));
        }
    }

    // ---------- listAcl ----------

    @Test
    void listAcl_returnsRoleRowsWithPerms() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        BizFolderAcl acl = new BizFolderAcl();
        acl.setId(1L); acl.setFolderId(FOLDER_ID); acl.setRoleId(3L); acl.setPerms("file:view,file:download");
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
            assertEquals(List.of("file:view", "file:download"), row.get("perms"));
        }
    }

    @Test
    void listAcl_unknownRole_fallsBackToUnknownName() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        BizFolderAcl acl = new BizFolderAcl();
        acl.setId(1L); acl.setFolderId(FOLDER_ID); acl.setRoleId(99L); acl.setPerms("file:view");
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        when(roleMapper.selectById(99L)).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            assertEquals("未知角色", svc.listAcl(FOLDER_ID).get(0).get("roleName"));
        }
    }

    // ---------- listRoles（2026-08-28 新增：ACL 弹窗角色下拉轻量视图） ----------

    @Test
    void listRoles_returnsIdAndNameOnly() {
        SysRole r1 = new SysRole(); r1.setId(9L); r1.setRoleName("行政"); r1.setRoleCode("05");
        SysRole r2 = new SysRole(); r2.setId(11L); r2.setRoleName("销售员"); r2.setRoleCode("06");
        when(roleMapper.selectList(any())).thenReturn(List.of(r1, r2));
        List<Map<String, Object>> rows = svc.listRoles();
        assertEquals(2, rows.size());
        assertEquals(9L, rows.get(0).get("id"));
        assertEquals("行政", rows.get(0).get("roleName"));
        assertNull(rows.get(0).get("roleCode"));
    }

    // ---------- permissions ----------

    @Test
    void permissions_returnsPermsAndDownloadable() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(FOLDER_ID); folder.setDownloadable(0);
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
        when(permissionService.permsFor(FOLDER_ID, USER_ID)).thenReturn(List.of(FolderPermissionService.FILE_VIEW));
        Map<String, Object> row = svc.permissions(FOLDER_ID, USER_ID);
        assertEquals(List.of(FolderPermissionService.FILE_VIEW), row.get("perms"));
        assertEquals(0, row.get("downloadable"));
    }

    @Test
    void permissions_downloadableNull_defaultsToOne() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(FOLDER_ID); folder.setDownloadable(null);
        when(folderMapper.selectById(FOLDER_ID)).thenReturn(folder);
        when(permissionService.permsFor(FOLDER_ID, USER_ID)).thenReturn(List.of(FolderPermissionService.FILE_VIEW));
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
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            // body 缺失
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID, null));
            // roleId 缺失
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID, Map.of("perms", List.of("file:view"))));
            // roleId 非数字
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID,
                    Map.<String, Object>of("roleId", "abc", "perms", List.of("file:view"))));
            // perms 缺失
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID,
                    Map.<String, Object>of("roleId", 3)));
            // perms 非数组
            assertThrows(BusinessException.class, () -> svc.saveAclFromBody(FOLDER_ID,
                    Map.<String, Object>of("roleId", 3, "perms", "file:view")));
            // 不触碰任何 mapper
            verify(aclMapper, never()).insert(any(BizFolderAcl.class));
            verify(aclMapper, never()).updateById(any(BizFolderAcl.class));
            verify(aclMapper, never()).selectOne(any());
        }
    }

    @Test
    void saveAclFromBody_validBody_parsesAndSaves() {
        when(permissionService.check(FOLDER_ID, FolderPermissionService.FOLDER_RENAME, USER_ID)).thenReturn(true);
        when(aclMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            svc.saveAclFromBody(FOLDER_ID, Map.<String, Object>of("roleId", 3, "perms", List.of("file:view", "file:upload")));
            ArgumentCaptor<BizFolderAcl> cap = ArgumentCaptor.forClass(BizFolderAcl.class);
            verify(aclMapper).insert(cap.capture());
            assertEquals(3L, cap.getValue().getRoleId());
            assertEquals("file:view,file:upload", cap.getValue().getPerms());
            assertEquals(USER_ID, cap.getValue().getCreatedBy());
        }
    }
}
