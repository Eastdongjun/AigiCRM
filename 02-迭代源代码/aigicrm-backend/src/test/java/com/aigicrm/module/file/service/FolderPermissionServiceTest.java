package com.aigicrm.module.file.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.module.file.entity.BizFile;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 【2026-11-10 文件权限终版】组织底座判定链回归:
 * ①上传者本人恒可管 ②上级链 ③组织矩阵(view/manage/delete 三档) ④例外授权(未过期命中/过期拒绝)
 * ⑤公共兜底已删除(拍板①B: 看也按组织收窄) ⑥建夹门槛=授权 FOLDER:create ⑦ACL 转换行(edit伞)读时展开。
 */
class FolderPermissionServiceTest {

    @BeforeEach
    void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFolderTypeDefault.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysDept.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysRole.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFileFolder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFile.class);
    }

    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final BizFolderTypeDefaultMapper grantMapper = mock(BizFolderTypeDefaultMapper.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final FolderPermissionService svc =
            new FolderPermissionService(folderMapper, fileMapper, userMapper, deptMapper, roleMapper, grantMapper, scopeResolver);

    /** 统一静态桩: 非 admin + 可控 currentUser(matrixSet 需要非 null SecurityUser) */
    private MockedStatic<SecurityUtils> stub(MockedStatic<SecurityUtils> m, SecurityUser user, boolean admin) {
        m.when(SecurityUtils::isAdmin).thenReturn(admin);
        m.when(SecurityUtils::getCurrentUser).thenReturn(user);
        m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("05"));
        return m;
    }

    private BizFolderTypeDefault grant(String objectType, String folderType, Long folderId, String subjectType, Long subjectId, String perms, LocalDateTime expires) {
        BizFolderTypeDefault g = new BizFolderTypeDefault();
        g.setObjectType(objectType);
        g.setFolderType(folderType);
        g.setFolderId(folderId);
        g.setSubjectType(subjectType);
        g.setSubjectId(subjectId);
        g.setPerms(perms);
        g.setExpiresAt(expires);
        return g;
    }

    private BizFileFolder folder(long id, Long owner, String type) {
        BizFileFolder f = new BizFileFolder();
        f.setId(id); f.setOwnerId(owner); f.setFolderType(type);
        return f;
    }

    /** ORG 锚决策桩: visibleDeptIds=给定集(空=fail-closed) */
    private com.aigicrm.common.scope.ScopeDecision deptDecision(java.util.Collection<Long> deptIds) {
        com.aigicrm.common.scope.ScopeDecision d = new com.aigicrm.common.scope.ScopeDecision();
        d.setVisibleDeptIds(deptIds == null ? null : new java.util.ArrayList<>(deptIds));
        return d;
    }

    private BizFile file(long id, Long folderId, Long uploader) {
        BizFile f = new BizFile();
        f.setId(id); f.setFolderId(folderId); f.setUploaderId(uploader);
        return f;
    }

    // ===== ① 本人恒可管 + 公共兜底已删除 =====

    @Test
    void uploaderSelfAlwaysManages_publicFallbackRemoved() {
        BizFile mine = file(9L, 100L, 42L);
        BizFile others = file(10L, 100L, 99L);
        when(fileMapper.selectById(9L)).thenReturn(mine);
        when(fileMapper.selectById(10L)).thenReturn(others);
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, null, "public"));
        when(grantMapper.selectList(any())).thenReturn(List.of());
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of(42L)); // OWNER 矩阵=仅本人
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of())); // ORG 矩阵=空(fail-closed)
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            // 本人: 全动作恒可
            assertTrue(svc.checkFile(9L, FolderPermissionService.P_RENAME, 42L));
            assertTrue(svc.checkFile(9L, FolderPermissionService.P_DELETE, 42L));
            assertTrue(svc.checkFile(9L, FolderPermissionService.P_VIEW, 42L));
            // 他人99: 矩阵 self=只含42 → 不可见——【拍板①B】公共兜底删除, 同部门/全公司不再白看
            assertFalse(svc.checkFile(10L, FolderPermissionService.P_VIEW, 42L), "公共兜底已删除: 他人文件默认不可见");
            assertFalse(svc.checkFile(10L, FolderPermissionService.P_DOWNLOAD, 42L));
        }
    }

    // ===== ③ 组织矩阵: manage 档位覆盖他人文件 =====

    @Test
    void orgMatrix_folderAnchor_manageDeptGrantsOthersFiles() {
        // 【拍板②】文件跟夹走: 夹 org=5; manage/view 档含 5 → 他人文件可看可改名; delete 档不含 → 不可删
        BizFileFolder f = folder(100L, null, "public"); f.setOrgId(5L);
        when(fileMapper.selectById(10L)).thenReturn(file(10L, 100L, 99L));
        when(folderMapper.selectById(100L)).thenReturn(f);
        when(grantMapper.selectList(any())).thenReturn(List.of());
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveScope(any())).thenAnswer(inv -> {
            com.aigicrm.common.scope.ScopeRequest r = inv.getArgument(0);
            return "manage".equals(r.getAction()) || "view".equals(r.getAction())
                    ? deptDecision(List.of(5L)) : deptDecision(List.of());
        });
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertTrue(svc.checkFile(10L, FolderPermissionService.P_VIEW, 42L), "夹org∈view档→可见");
            assertTrue(svc.checkFile(10L, FolderPermissionService.P_RENAME, 42L), "夹org∈manage档→可改名他人文件");
            assertFalse(svc.checkFile(10L, FolderPermissionService.P_DELETE, 42L), "delete档不含夹org→不可删");
        }
    }

    // ===== ④ 例外授权: 命中/过期/定向 =====

    @Test
    void grant_hitNotExpired_expiredRejected_folderScoped() {
        when(fileMapper.selectById(10L)).thenReturn(file(10L, 100L, 99L));
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, null, "public"));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of(42L)); // OWNER 矩阵仅本人
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of())); // ORG 矩阵空
        // 未过期 USER 授权(定向夹100, view)
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "public", 100L, "USER", 42L, "view", LocalDateTime.now().plusDays(7))));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertTrue(svc.checkFile(10L, FolderPermissionService.P_VIEW, 42L), "未过期定向授权→可见");
            assertFalse(svc.checkFile(10L, FolderPermissionService.P_DELETE, 42L), "授权只有 view→不可删");
        }
        // 过期后自动收回
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "public", 100L, "USER", 42L, "view", LocalDateTime.now().minusDays(1))));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertFalse(svc.checkFile(10L, FolderPermissionService.P_VIEW, 42L), "过期授权自动收回");
        }
    }

    @Test
    void grant_aclConvertedEditUmbrella_stillCoversRenameReplace() {
        // ACL 转换行: perms='view,upload,rename,replace,delete' (迁移把 edit 归一为细词) → 词表命中
        when(fileMapper.selectById(10L)).thenReturn(file(10L, 100L, 99L));
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, null, "public"));
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of(42L));
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "public", 100L, "ROLE", 3L, "view,upload,rename,replace,delete", LocalDateTime.now().plusYears(1))));
        SysRole role = new SysRole(); role.setId(3L); role.setRoleCode("05");
        when(roleMapper.selectById(3L)).thenReturn(role);
        SecurityUser u = mock(SecurityUser.class);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertTrue(svc.checkFile(10L, FolderPermissionService.P_RENAME, 42L), "ACL转换行细词命中改名");
            assertTrue(svc.checkFile(10L, FolderPermissionService.P_REPLACE, 42L));
            assertTrue(svc.checkFile(10L, FolderPermissionService.P_DELETE, 42L));
        }
    }

    // ===== ⑥ 建夹门槛 =====

    @Test
    void canCreateFolder_grantGated_privateFree_leaderCreatesPublic() {
        SecurityUser u = mock(SecurityUser.class);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FOLDER", "public", null, "USER", 42L, "create", LocalDateTime.now().plusDays(30))));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertTrue(svc.canCreateFolder("public", 42L), "授权含 FOLDER:create→可建公共夹");
            assertTrue(svc.canCreateFolder("private", 43L), "拍板④: 私人夹全员可建");
            assertFalse(svc.canCreateFolder("public", 43L), "无授权非负责人→不可建公共夹");
        }
        // 负责人制: 43 是部门5 的 leader → 可建公共夹, leaderOrgId=5
        SysDept d5 = new SysDept(); d5.setId(5L); d5.setParentId(0L); d5.setLeaderId(43L);
        when(deptMapper.selectList(any())).thenReturn(List.of(d5));
        when(deptMapper.selectById(5L)).thenReturn(d5);
        assertTrue(svc.canCreateFolder("public", 43L), "组织负责人→可建本组织公共夹");
        org.junit.jupiter.api.Assertions.assertEquals(5L, svc.leaderOrgId(43L));
    }

    // ===== ② 上级链 =====

    @Test
    void managerChain_viaManagerId() {
        SysUser lower = new SysUser(); lower.setId(99L); lower.setManagerId(42L);
        when(userMapper.selectById(99L)).thenReturn(lower);
        assertTrue(svc.isManagerChain(42L, 99L));
    }

    @Test
    void managerChain_managesSubordinateFiles() {
        when(fileMapper.selectById(10L)).thenReturn(file(10L, 100L, 99L));
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, null, "public"));
        when(grantMapper.selectList(any())).thenReturn(List.of());
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of(42L));
        SysUser lower = new SysUser(); lower.setId(99L); lower.setManagerId(42L);
        when(userMapper.selectById(99L)).thenReturn(lower);
        SecurityUser u = mock(SecurityUser.class);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertTrue(svc.checkFile(10L, FolderPermissionService.P_DELETE, 42L), "上级链→可管下属文件(含删)");
        }
    }

    // ===== 文件夹链: 创建人/组织矩阵 =====

    @Test
    void folder_creatorFullAndOrgMatrixRename() {
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, 77L, "public"));
        when(grantMapper.selectList(any())).thenReturn(List.of());
        BizFileFolder f100 = folder(100L, 77L, "public"); f100.setOrgId(5L);
        when(folderMapper.selectById(100L)).thenReturn(f100);
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveScope(any())).thenAnswer(inv -> {
            com.aigicrm.common.scope.ScopeRequest r = inv.getArgument(0);
            return "manage".equals(r.getAction()) ? deptDecision(List.of(5L)) : deptDecision(List.of());
        });
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertTrue(svc.check(100L, FolderPermissionService.P_RENAME, 77L), "创建人全权");
            assertTrue(svc.check(100L, FolderPermissionService.P_RENAME, 42L), "夹org∈manage档→可改名");
            assertFalse(svc.check(100L, FolderPermissionService.P_DELETE, 42L), "delete档不含夹org→不可删");
        }
    }

    // ===== 词表展开 =====

    @Test
    void csvHas_editUmbrellaSemantics() {
        assertTrue(FolderPermissionService.csvHas("edit", FolderPermissionService.P_RENAME));
        assertTrue(FolderPermissionService.csvHas("edit", FolderPermissionService.P_REPLACE));
        assertTrue(FolderPermissionService.csvHas("rename", FolderPermissionService.P_EDIT));
        assertFalse(FolderPermissionService.csvHas("rename", FolderPermissionService.P_REPLACE));
        assertTrue(FolderPermissionService.csvHas("view,download", FolderPermissionService.P_DOWNLOAD));
    }

    @Test
    void permsForBatch_grantsAndMatrix_cached() {
        BizFileFolder f1 = folder(100L, 77L, "public");
        // 【终版口径】夹内文件动作(view/download)命中 FILE 组定向授权
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "public", null, "USER", 42L, "view,download", LocalDateTime.now().plusDays(1))));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of(42L));
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            Map<Long, List<String>> res = svc.permsForBatch(List.of(f1), 42L);
            assertTrue(res.get(100L).containsAll(List.of("view", "download")), "授权行并入按钮视图: " + res.get(100L));
            verify(scopeResolver, atMost(3)).resolveScope(any());
        }
    }

    // ===== 【T1 2026-11-12】按钮视图词表: 授权对象细词输出 + 特权 ALL_PERMS 不变 =====

    @Test
    void permsForBatch_granteeGetsFineGrainedTokens_noUmbrellaBackfill() {
        // 类型级 FILE grant 给 USER 42 仅 rename,replace → 按钮视图如实输出细词;
        // 伞词 edit 不在后端回填(方案B: PC/MP 消费端双词兼容 edit∨rename), 非特权视图无 edit
        BizFileFolder f = folder(100L, 99L, "public"); f.setOrgId(5L);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "public", null, "USER", 42L, "rename,replace", null)));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of()); // 矩阵 fail-closed
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            List<String> perms = svc.permsForBatch(List.of(f), 42L).get(100L);
            assertTrue(perms.containsAll(List.of("rename", "replace")), "授权细词入视图: " + perms);
            assertFalse(perms.contains("edit"), "非特权不回填伞词 edit(消费端双词兼容)");
            assertFalse(perms.contains("delete"), "未授权动作不得点亮");
        }
    }

    @Test
    void permsForBatch_privilegedAllPermsUnchanged() {
        // admin / 夹主输出 ALL_PERMS(含 edit 伞+细词全量)——特权视图与历史一致
        BizFileFolder f = folder(100L, 42L, "public");
        SecurityUser u = mock(SecurityUser.class);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, true);
            List<String> adminPerms = svc.permsForBatch(List.of(f), 42L).get(100L);
            assertTrue(adminPerms.containsAll(List.of("edit", "rename", "replace", "delete", "share", "upload")));
        }
        when(grantMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            List<String> ownerPerms = svc.permsForBatch(List.of(f), 42L).get(100L);
            assertTrue(ownerPerms.containsAll(List.of("edit", "rename", "replace")), "夹主=本人恒管 ALL_PERMS");
        }
    }

    // ===== 【T4 2026-11-12】无夹文件空间锚: 类型授权不跨空间 =====

    @Test
    void checkFile_folderless_publicGrantMustNotHitIncentiveAttachment() {
        // ① public 类型授权 + incentive 无夹附件(激励) → false(修前 folderType=null 跳过类型约束=越权)
        BizFile att = file(1L, null, 99L); att.setFileScope("incentive");
        when(fileMapper.selectById(1L)).thenReturn(att);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "public", null, "USER", 42L, "delete", null)));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of()); // OWNER 锚空
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertFalse(svc.checkFile(1L, FolderPermissionService.P_DELETE, 42L),
                    "public 空间类型授权不得命中激励附件(空间锚)");
        }
    }

    @Test
    void checkFile_folderless_incentiveGrantHitsIncentiveAttachment() {
        // ② incentive 类型授权 + incentive 无夹附件 → true("激励附件只吃 incentive 类型授权"口径成立)
        BizFile att = file(2L, null, 99L); att.setFileScope("incentive");
        when(fileMapper.selectById(2L)).thenReturn(att);
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "incentive", null, "USER", 42L, "delete", null)));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of());
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertTrue(svc.checkFile(2L, FolderPermissionService.P_DELETE, 42L));
        }
    }

    @Test
    void checkFile_typedGrantMustNotCrossFolderType() {
        // ③ 类型行授权(folderId=null) + 有夹文件夹类型不符 → false(类型约束对本空间夹内文件生效)
        BizFile f = file(3L, 100L, 99L);
        when(fileMapper.selectById(3L)).thenReturn(f);
        when(folderMapper.selectById(100L)).thenReturn(folder(100L, 98L, "contract"));
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "case", null, "USER", 42L, "delete", null)));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of());
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            assertFalse(svc.checkFile(3L, FolderPermissionService.P_DELETE, 42L),
                    "case 类型授权不得命中 contract 夹内文件");
        }
    }

    // ===== 【T2 2026-11-12】行级 permsForFiles: 类型级授权直达根目录文件行按钮 =====

    @Test
    void permsForFiles_typeGrantLightsRootFiles() {
        // 类型级 public FILE grant 给 USER 42(rename,replace) → 根目录无夹文件(fileScope=public,
        // 他人上传)行级输出含细词; 非授权用户 43 行级不含; 授权对象未授权动作(delete)不点亮
        BizFile root = file(11L, null, 99L); root.setFileScope("public");
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FILE", "public", null, "USER", 42L, "rename,replace", null)));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of()); // 矩阵 fail-closed
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            List<String> grantee = svc.permsForFiles(List.of(root), 42L).get(11L);
            assertTrue(grantee.containsAll(List.of("rename", "replace")), "类型授权入行级视图: " + grantee);
            assertFalse(grantee.contains("delete"), "未授权动作不点亮");
            List<String> other = svc.permsForFiles(List.of(root), 43L).get(11L);
            assertFalse(other.contains("rename"), "非授权对象不得点亮");
            assertFalse(other.contains("delete"));
        }
    }

    @Test
    void permsForFiles_uploaderSelfGetsAllRowActions() {
        // 上传者本人=全行级动作(与 checkFile 本人短路同源); admin 同
        BizFile mine = file(12L, null, 42L); mine.setFileScope("private");
        when(grantMapper.selectList(any())).thenReturn(List.of());
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of());
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            List<String> own = svc.permsForFiles(List.of(mine), 42L).get(12L);
            assertTrue(own.containsAll(List.of("rename", "replace", "delete", "share", "download", "view")),
                    "本人恒管=全行级动作");
        }
    }

    // ===== 【T3 2026-11-12】夹行 FOLDER 授权进按钮视图(folderActions 分字段) =====

    @Test
    void folderActionsForBatch_folderGrantLightsActions_fileGrantMustNot() {
        // FOLDER 行授权(rename,delete)点亮夹行动作; FILE 行授权(同动作词)不得误点亮夹动作(词义分离)
        BizFileFolder f = folder(200L, 99L, "public");
        when(folderMapper.selectById(200L)).thenReturn(f); // check() 内部按 id 重查夹
        when(grantMapper.selectList(any())).thenReturn(List.of(
                grant("FOLDER", "public", null, "USER", 42L, "rename,delete", null),
                grant("FILE", "public", null, "USER", 42L, "rename", null)));
        SecurityUser u = mock(SecurityUser.class);
        when(scopeResolver.resolveModuleUsers(any(), eq("file"), any())).thenReturn(List.of());
        when(scopeResolver.resolveScope(any())).thenReturn(deptDecision(List.of()));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stub(m, u, false);
            List<String> actions = svc.folderActionsForBatch(List.of(f), 42L).get(200L);
            assertTrue(actions.containsAll(List.of("rename", "delete")), "FOLDER 授权点亮夹行动作: " + actions);
            // 对照: 若只剩 FILE 行(夹授权删除), 夹动作不得被文件授权点亮
            when(grantMapper.selectList(any())).thenReturn(List.of(
                    grant("FILE", "public", null, "USER", 42L, "rename,delete", null)));
            List<String> onlyFile = svc.folderActionsForBatch(List.of(f), 42L).get(200L);
            assertTrue(onlyFile.isEmpty(), "FILE 行授权不得点亮夹动作(词义分离): " + onlyFile);
        }
    }
}
