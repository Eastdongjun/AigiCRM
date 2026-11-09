package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.module.file.entity.BizFile;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFolderAcl;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFolderAclMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FolderPermissionServiceTest {

    /** MyBatis-Plus 3.5.17(Boot 4)起纯 mock 环境必须显式注册实体 lambda 缓存（升级回归修复） */
    @BeforeEach
    void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFolderAcl.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysDept.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysRole.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFileFolder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFile.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFolderTypeDefault.class);
    }

    private final BizFolderAclMapper aclMapper = mock(BizFolderAclMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final BizFolderTypeDefaultMapper typeDefaultMapper = mock(BizFolderTypeDefaultMapper.class);
    private final FolderPermissionService svc = new FolderPermissionService(aclMapper, folderMapper, fileMapper, userMapper, deptMapper, roleMapper, typeDefaultMapper);

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
    void managerChain_managerIdSet_deptLeaderNotConsulted() {
        // M-3：设计 §6 收紧——lower 的 manager_id 非空时不再走部门 leader 链（此前为超集更宽松）
        // upper=8：manager 链（7→null 不命中）；dept9.leader=8 本可命中，但因 manager_id 非空被跳过 → false
        SysUser lower = new SysUser(); lower.setId(42L); lower.setManagerId(7L); lower.setDeptId(9L);
        SysUser manager = new SysUser(); manager.setId(7L); manager.setManagerId(null);
        SysDept dept9 = new SysDept(); dept9.setId(9L); dept9.setLeaderId(8L);
        when(userMapper.selectById(42L)).thenReturn(lower);
        when(userMapper.selectById(7L)).thenReturn(manager);
        when(deptMapper.selectById(9L)).thenReturn(dept9);
        assertFalse(svc.isManagerChain(8L, 42L));
        verify(deptMapper, never()).selectById(any());
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
            // 【文件权限 v2】8 细词: 6 伞词表 + rename/replace
            assertEquals(8, perms.size());
            assertTrue(perms.containsAll(List.of(FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
                    FolderPermissionService.P_EDIT, FolderPermissionService.P_DELETE,
                    FolderPermissionService.P_SHARE, FolderPermissionService.P_DOWNLOAD,
                    FolderPermissionService.P_RENAME, FolderPermissionService.P_REPLACE)));
        }
    }

    @Test
    void permsFor_owner_returnsAll() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(42L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertEquals(8, svc.permsFor(100L, 42L).size());
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
            assertEquals(8, res.get(100L).size());
            assertEquals(8, res.get(101L).size());
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

    // === 【2026-11-09 文件权限 v2】类型授权层(角色|人 × 空间 × 动作, 实时生效) + 动作细分 ===

    private BizFolderTypeDefault typeRow(String folderType, String subjectType, Long subjectId, String perms) {
        BizFolderTypeDefault d = new BizFolderTypeDefault();
        d.setFolderType(folderType);
        d.setSubjectType(subjectType);
        d.setSubjectId(subjectId);
        d.setPerms(perms);
        return d;
    }

    @Test
    void typeGrant_userSubject_grantsActionAcrossSpace() {
        // USER 行点名 42 → 公共夹上 rename 放行(无夹主/上级/ACL 介入), replace 未授权仍拒
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of());
        when(typeDefaultMapper.selectList(any())).thenReturn(List.of(typeRow("public", "USER", 42L, "rename")));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of());
            assertTrue(svc.check(100L, FolderPermissionService.P_RENAME, 42L), "USER 类型授权含 rename 应放行改名");
            assertFalse(svc.check(100L, FolderPermissionService.P_REPLACE, 42L), "未授权 replace 应拒绝(动作独立)");
            assertFalse(svc.check(100L, FolderPermissionService.P_DELETE, 42L), "未授权 delete 应拒绝");
        }
    }

    @Test
    void typeGrant_roleSubject_matchesViaRoleCode() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of());
        when(typeDefaultMapper.selectList(any())).thenReturn(List.of(typeRow("public", "ROLE", 9L, "replace,delete")));
        SysRole role = new SysRole(); role.setId(9L); role.setRoleCode("05");
        when(roleMapper.selectById(9L)).thenReturn(role);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("05"));
            assertTrue(svc.check(100L, FolderPermissionService.P_REPLACE, 42L), "ROLE 行按角色码匹配应放行 replace");
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("06"));
            assertFalse(svc.check(100L, FolderPermissionService.P_REPLACE, 42L), "角色不匹配不放行");
        }
    }

    @Test
    void typeGrant_privateSpace_appliesBeyondOwnerAndAcl() {
        // 私人夹: admin 勾某人×私人×view → 该人可看全空间私人夹(高权限动作, 设计允许)
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("private");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of());
        when(typeDefaultMapper.selectList(any())).thenReturn(List.of(typeRow("private", "USER", 42L, "view")));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of());
            assertTrue(svc.check(100L, FolderPermissionService.P_VIEW, 42L));
            assertFalse(svc.check(100L, FolderPermissionService.P_UPLOAD, 42L), "只勾 view 不越权 upload");
        }
    }

    @Test
    void legacyAclEdit_expandsToRenameAndReplace() {
        // 存量 ACL 'edit' 伞: v2 拆词后改名/替换都必须继续放行(读时展开, 数据不回填)
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        BizFolderAcl acl = new BizFolderAcl();
        acl.setFolderId(100L); acl.setRoleId(3L); acl.setPerms("edit");
        SysRole role = new SysRole(); role.setId(3L); role.setRoleCode("sales");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        when(roleMapper.selectById(3L)).thenReturn(role);
        when(typeDefaultMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("sales"));
            assertTrue(svc.check(100L, FolderPermissionService.P_RENAME, 42L), "存量 edit 授权须继续覆盖改名");
            assertTrue(svc.check(100L, FolderPermissionService.P_REPLACE, 42L), "存量 edit 授权须继续覆盖替换");
        }
    }

    @Test
    void permsFor_typeGrant_emitsBothVocabularies() {
        // 类型授权 rename(细词) → permsFor 输出补发 edit 伞词: MP includes('edit') 的改名按钮零改动兼容
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("public");
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(aclMapper.selectList(any())).thenReturn(List.of());
        when(typeDefaultMapper.selectList(any())).thenReturn(List.of(typeRow("public", "USER", 42L, "rename")));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of());
            List<String> perms = svc.permsFor(100L, 42L);
            assertTrue(perms.containsAll(List.of("rename", "edit", "view", "download")),
                    "细词 rename + 兼容伞词 edit + 公共兜底 view/download 都须在: " + perms);
        }
    }

    @Test
    void permsForBatch_typeGrantCachedOncePerType() {
        // 同空间多夹: 类型授权只解析一次(防 N+1 回归)
        BizFileFolder f1 = new BizFileFolder(); f1.setId(100L); f1.setOwnerId(99L); f1.setFolderType("public");
        BizFileFolder f2 = new BizFileFolder(); f2.setId(101L); f2.setOwnerId(98L); f2.setFolderType("public");
        when(aclMapper.selectList(any())).thenReturn(List.of());
        when(typeDefaultMapper.selectList(any())).thenReturn(List.of(typeRow("public", "USER", 42L, "upload")));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of());
            Map<Long, List<String>> res = svc.permsForBatch(List.of(f1, f2), 42L);
            assertTrue(res.get(100L).contains("upload"));
            assertTrue(res.get(101L).contains("upload"));
            verify(typeDefaultMapper, atMost(1)).selectList(any());
        }
    }

    @Test
    void csvHas_editUmbrellaSemantics() {
        // 词表展开口径: edit⇒rename|replace 均真; 反向 rename/replace 命中 edit; 其他动作精确匹配
        assertTrue(FolderPermissionService.csvHas("edit", FolderPermissionService.P_RENAME));
        assertTrue(FolderPermissionService.csvHas("edit", FolderPermissionService.P_REPLACE));
        assertTrue(FolderPermissionService.csvHas("rename", FolderPermissionService.P_EDIT));
        assertTrue(FolderPermissionService.csvHas("replace", FolderPermissionService.P_EDIT));
        assertFalse(FolderPermissionService.csvHas("rename", FolderPermissionService.P_REPLACE));
        assertFalse(FolderPermissionService.csvHas("view,download", FolderPermissionService.P_UPLOAD));
        assertTrue(FolderPermissionService.csvHas("view,download", FolderPermissionService.P_DOWNLOAD));
    }

}
