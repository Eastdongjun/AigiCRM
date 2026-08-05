package com.ycmn.module.file.service;

import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.entity.BizFolderAcl;
import com.ycmn.module.file.mapper.BizFileFolderMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.file.mapper.BizFolderAclMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FolderPermissionServiceTest {

    private final BizFolderAclMapper aclMapper = mock(BizFolderAclMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final FolderPermissionService svc = new FolderPermissionService(aclMapper, folderMapper, fileMapper, userMapper, deptMapper, roleMapper);

    @Test
    void admin_hasAllPermissions() {
        SysUser admin = new SysUser(); admin.setId(1L); admin.setManagerId(null);
        when(userMapper.selectById(1L)).thenReturn(admin);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            assertTrue(svc.check(100L, FolderPermissionService.P_DELETE, 1L));
        }
    }

    @Test
    void owner_hasFullPermissions() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(42L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.check(100L, FolderPermissionService.P_EDIT, 42L));
        }
    }

    @Test
    void managerChain_viaManagerId() {
        // lower=42 的 manager_id=7，upper=7 → true
        SysUser lower = new SysUser(); lower.setId(42L); lower.setManagerId(7L);
        SysUser upper = new SysUser(); upper.setId(7L);
        when(userMapper.selectById(42L)).thenReturn(lower);
        when(userMapper.selectById(7L)).thenReturn(upper);
        assertTrue(svc.isManagerChain(7L, 42L));
    }

    @Test
    void managerChain_viaDeptLeader() {
        // lower=42 manager_id 为空，dept=9，dept9.leader=7 → true
        SysUser lower = new SysUser(); lower.setId(42L); lower.setManagerId(null); lower.setDeptId(9L);
        SysDept dept9 = new SysDept(); dept9.setId(9L); dept9.setParentId(2L); dept9.setLeaderId(7L);
        SysDept dept2 = new SysDept(); dept2.setId(2L); dept2.setParentId(0L);
        when(userMapper.selectById(42L)).thenReturn(lower);
        when(deptMapper.selectById(9L)).thenReturn(dept9);
        when(deptMapper.selectById(2L)).thenReturn(dept2);
        assertTrue(svc.isManagerChain(7L, 42L));
    }

    @Test
    void aclRole_grantsConfiguredPermission() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("private");
        BizFolderAcl acl = new BizFolderAcl();
        acl.setFolderId(100L); acl.setRoleId(3L); acl.setPerms("view,upload");
        SysRole role = new SysRole(); role.setId(3L); role.setRoleCode("sales");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        when(roleMapper.selectById(3L)).thenReturn(role);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("sales")); // 用户角色含 sales → ACL 生效
            assertTrue(svc.check(100L, FolderPermissionService.P_UPLOAD, 42L));
        }
    }

    @Test
    void publicFolder_viewOnlyForOthers() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.check(100L, FolderPermissionService.P_VIEW, 42L));
            assertFalse(svc.check(100L, FolderPermissionService.P_EDIT, 42L));
            assertFalse(svc.check(100L, FolderPermissionService.P_UPLOAD, 42L));
        }
    }

    @Test
    void publicFolder_downloadAllowedForOthers() {
        // 修复轮 I-4a：公共文件夹 download 兜底（实际是否可下载由 downloadById 开关约束）
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.check(100L, FolderPermissionService.P_DOWNLOAD, 42L));
        }
    }

    @Test
    void privateFolder_deniedWithoutOwnerManagerOrAcl() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("private");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertFalse(svc.check(100L, FolderPermissionService.P_VIEW, 42L));
        }
    }

    @Test
    void checkFile_uploaderGetsFullPermissionWhenNoFolder() {
        BizFile file = new BizFile(); file.setId(9L); file.setFolderId(null); file.setUploaderId(42L);
        when(fileMapper.selectById(9L)).thenReturn(file);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.checkFile(9L, FolderPermissionService.P_DELETE, 42L));
        }
    }

    @Test
    void checkFile_withFolder_delegatesToFolder() {
        BizFile file = new BizFile(); file.setId(9L); file.setFolderId(100L);
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(42L);
        when(fileMapper.selectById(9L)).thenReturn(file);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.checkFile(9L, FolderPermissionService.P_EDIT, 42L));
        }
    }

    @Test
    void permsFor_admin_returnsAll() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            List<String> perms = svc.permsFor(100L, 1L);
            assertEquals(6, perms.size());
            assertTrue(perms.containsAll(List.of(FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
                    FolderPermissionService.P_EDIT, FolderPermissionService.P_DELETE,
                    FolderPermissionService.P_SHARE, FolderPermissionService.P_DOWNLOAD)));
        }
    }

    @Test
    void permsFor_owner_returnsAll() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(42L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertEquals(6, svc.permsFor(100L, 42L).size());
        }
    }

    @Test
    void permsFor_aclPermissions_union() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        BizFolderAcl acl = new BizFolderAcl();
        acl.setFolderId(100L); acl.setRoleId(3L); acl.setPerms("view,download");
        SysRole role = new SysRole(); role.setId(3L); role.setRoleCode("sales");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        when(roleMapper.selectById(3L)).thenReturn(role);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("sales"));
            assertEquals(List.of("view", "download"), svc.permsFor(100L, 42L));
        }
    }

    @Test
    void permsFor_publicWithoutAcl_returnsViewAndDownload() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertEquals(List.of("view", "download"), svc.permsFor(100L, 42L));
        }
    }

    @Test
    void permsFor_publicWithAclWithoutView_userStillSeesFolder() {
        // 修复轮 I-3：公共文件夹配置 ACL（无 view）后，未匹配/匹配角色的普通用户仍必须可见
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        BizFolderAcl acl = new BizFolderAcl();
        acl.setFolderId(100L); acl.setRoleId(3L); acl.setPerms("upload,edit");
        SysRole role = new SysRole(); role.setId(3L); role.setRoleCode("sales");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        when(roleMapper.selectById(3L)).thenReturn(role);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            // 角色匹配：ACL 权限 + 公共兜底 view/download
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("sales"));
            List<String> matched = svc.permsFor(100L, 42L);
            assertTrue(matched.containsAll(List.of(FolderPermissionService.P_VIEW, FolderPermissionService.P_DOWNLOAD, "upload", "edit")));
            // 角色不匹配：公共兜底仍保证可见
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("other"));
            List<String> unmatched = svc.permsFor(100L, 42L);
            assertEquals(List.of("view", "download"), unmatched);
        }
    }

    // === permsForBatch（getFolderTree 批量权限，修复轮 I-2a）===

    @Test
    void permsForBatch_admin_returnsAllForEveryFolder() {
        BizFileFolder f1 = new BizFileFolder(); f1.setId(100L); f1.setOwnerId(99L); f1.setFolderType("private");
        BizFileFolder f2 = new BizFileFolder(); f2.setId(101L); f2.setOwnerId(98L); f2.setFolderType("public");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            Map<Long, List<String>> res = svc.permsForBatch(List.of(f1, f2), 1L);
            assertEquals(2, res.size());
            assertTrue(res.get(100L).containsAll(List.of(FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
                    FolderPermissionService.P_EDIT, FolderPermissionService.P_DELETE,
                    FolderPermissionService.P_SHARE, FolderPermissionService.P_DOWNLOAD)));
            assertTrue(res.get(101L).containsAll(List.of(FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
                    FolderPermissionService.P_EDIT, FolderPermissionService.P_DELETE,
                    FolderPermissionService.P_SHARE, FolderPermissionService.P_DOWNLOAD)));
        }
    }

    @Test
    void permsForBatch_ownerGetsAll_sameOwnerCachedOnce() {
        BizFileFolder f1 = new BizFileFolder(); f1.setId(100L); f1.setOwnerId(42L); f1.setFolderType("private");
        BizFileFolder f2 = new BizFileFolder(); f2.setId(101L); f2.setOwnerId(42L); f2.setFolderType("private");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            Map<Long, List<String>> res = svc.permsForBatch(List.of(f1, f2), 42L);
            assertEquals(6, res.get(100L).size());
            assertEquals(6, res.get(101L).size());
            // 同一 owner 只做一次链判断（避免 N+1 的防回归）
            verify(userMapper, atMost(1)).selectById(42L);
        }
    }

    @Test
    void permsForBatch_aclUnionAndPublicFallback() {
        BizFileFolder pub = new BizFileFolder(); pub.setId(100L); pub.setOwnerId(99L); pub.setFolderType("public");
        BizFileFolder priv = new BizFileFolder(); priv.setId(101L); priv.setOwnerId(99L); priv.setFolderType("private");
        BizFolderAcl acl1 = new BizFolderAcl(); acl1.setFolderId(100L); acl1.setRoleId(3L); acl1.setPerms("upload,edit");
        BizFolderAcl acl2 = new BizFolderAcl(); acl2.setFolderId(101L); acl2.setRoleId(3L); acl2.setPerms("view,download");
        SysRole role = new SysRole(); role.setId(3L); role.setRoleCode("sales");
        when(aclMapper.selectList(any())).thenReturn(List.of(acl1, acl2));
        when(roleMapper.selectById(3L)).thenReturn(role);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("sales"));
            Map<Long, List<String>> res = svc.permsForBatch(List.of(pub, priv), 42L);
            // 公共：ACL(upload/edit) + 兜底 view/download
            assertTrue(res.get(100L).containsAll(List.of("upload", "edit", "view", "download")));
            // 私有：仅 ACL 授权
            assertEquals(List.of("view", "download"), res.get(101L));
            // 角色不匹配 → 公共兜底 view/download，私有空
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("other"));
            res = svc.permsForBatch(List.of(pub, priv), 42L);
            assertEquals(List.of("view", "download"), res.get(100L));
            assertTrue(res.get(101L).isEmpty());
        }
    }

    @Test
    void permsForBatch_emptyInput_returnsEmptyMap() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.permsForBatch(List.of(), 42L).isEmpty());
        }
    }

    // 辅助: 构造用户
    private SysUser aUser(Long id) { SysUser u = new SysUser(); u.setId(id); return u; }
}
