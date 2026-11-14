package com.aigicrm.module.file.service;

import com.aigicrm.common.auth.AuthorizationService;
import com.aigicrm.common.auth.GrantReader;
import com.aigicrm.common.auth.PermActionRegistry;
import com.aigicrm.common.auth.ScopeSpecResolver;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【FILE-08 拍板 A 2026-11-14】私人空间收口负例钉（用户拍板: "普通同事不应该看到
 * 别人私人夹的文件"）。收口后语义=私人夹/私人文件仅 **本人 ∪ manager 链上级 ∪
 * 例外授权(ACL/类型授权)** 可见, 组织矩阵(部门档/用户档)一律不参与——旧轨新轨同判
 * (收口点在 grantGranted 之后、矩阵之前, 双轨皆然; 本测试用旧轨桩: 同部门同事的
 * 部门集**含**私人夹 org, 若收口被删此集会放行 → 红)。
 * 同时钉 public 不受影响(收的是私人, 不是把矩阵通道整个拔掉)。
 */
class FolderPrivateScopeTest {

    /** subordinateUserIds 用 LambdaQueryWrapper<SysUser>——测试环境需先初始化 MP 实体 lambda 缓存 */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    private final com.aigicrm.module.system.service.SysConfigService configService =
            mock(com.aigicrm.module.system.service.SysConfigService.class);
    private final com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService =
            mock(com.aigicrm.module.system.service.RolePermScopeService.class);

    /** 组装 FolderPermissionService: 数据侧 mapper 可控 + 解析侧真实内核(perm.read.file=old 走 legacyResolver mock) */
    private record Ctx(FolderPermissionService fps, com.aigicrm.module.file.mapper.BizFileFolderMapper folderMapper,
                       com.aigicrm.module.file.mapper.BizFileMapper fileMapper,
                       com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper grantMapper,
                       com.aigicrm.module.system.mapper.SysUserMapper userMapper,
                       com.aigicrm.module.system.mapper.SysDeptMapper deptMapper) {}

    private Ctx ctx(OrgScopeResolver resolverMock) {
        // 收口语义测试: FolderPermissionService 直接注入 resolver mock(resolveScope 部门/用户集可控),
        // 钉"private 早退在矩阵之前"——与轨道无关(双轨皆然), 不走真实内核链(那是 MatrixOrgChannel 测试的职责)
        com.aigicrm.module.system.mapper.SysUserMapper userMapper = mock(com.aigicrm.module.system.mapper.SysUserMapper.class);
        com.aigicrm.module.system.mapper.SysDeptMapper deptMapper = mock(com.aigicrm.module.system.mapper.SysDeptMapper.class);
        com.aigicrm.module.file.mapper.BizFileFolderMapper folderMapper =
                mock(com.aigicrm.module.file.mapper.BizFileFolderMapper.class);
        com.aigicrm.module.file.mapper.BizFileMapper fileMapper = mock(com.aigicrm.module.file.mapper.BizFileMapper.class);
        FolderPermissionService fps = new FolderPermissionService(folderMapper, fileMapper, userMapper, deptMapper,
                mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                mock(com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper.class), resolverMock);
        return new Ctx(fps, folderMapper, fileMapper,
                (com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper)
                        org.springframework.test.util.ReflectionTestUtils.getField(fps, "grantMapper"),
                userMapper, deptMapper);
    }

    private SecurityUser su(Long id) {
        SecurityUser u = new SecurityUser();
        u.setUserId(id);
        u.setPermissions(new HashSet<>(List.of("file:view")));
        u.setEmploymentOrgIds(List.of(10L));
        u.setPrimaryEmploymentOrgId(10L);
        return u;
    }

    @Test
    void privateFolder_sameDeptColleague_denied_upOwnerAllowed_publicStillMatrix() {
        OrgScopeResolver legacyResolver = mock(OrgScopeResolver.class);
        // 同部门同事(7)的部门集含 {10,11}——私人夹 org=10 也在此集(修复前因此放行)
        ScopeDecision deptView = new ScopeDecision();
        deptView.setVisibleDeptIds(List.of(10L, 11L));
        deptView.setVisibleUserIds(List.of(7L, 88L));
        when(legacyResolver.resolveScope(any())).thenReturn(deptView);
        when(legacyResolver.resolveScopeLegacy(any())).thenReturn(deptView);
        Ctx c = ctx(legacyResolver);

        BizFileFolder priv = new BizFileFolder();
        priv.setId(30L);
        priv.setFolderType("private");
        priv.setOwnerId(99L);
        priv.setOrgId(10L);
        when(c.folderMapper().selectById(30L)).thenReturn(priv);
        BizFileFolder pub = new BizFileFolder();
        pub.setId(40L);
        pub.setFolderType("public");
        pub.setOwnerId(99L);
        pub.setOrgId(10L);
        when(c.folderMapper().selectById(40L)).thenReturn(pub);
        SysUser owner = new SysUser(); // managerId/deptId=null → 非上级链
        when(c.userMapper().selectById(99L)).thenReturn(owner);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su(7L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of());
            // ① 同部门同事: 他人私人夹 view=false(收口核心; 修复前=true)
            assertFalse(c.fps().check(30L, FolderPermissionService.P_VIEW, 7L),
                    "私人夹(拍板A): 同部门同事不得因 dept 档看到他人私人夹");
            assertFalse(c.fps().check(30L, FolderPermissionService.P_UPLOAD, 7L),
                    "私人夹(拍板A): 同部门同事不得上传到他人私人夹");
            // ② public 夹不受影响: 部门矩阵通道照常(收的是私人不是公共)
            assertTrue(c.fps().check(40L, FolderPermissionService.P_VIEW, 7L),
                    "public 夹(同部门)仍走组织矩阵=可见(拍板A只收私人)");
            // ③ 本人恒可
            assertTrue(c.fps().check(30L, FolderPermissionService.P_VIEW, 99L),
                    "私人夹 owner 恒可");
            // ④ 上级链可: owner(99) 的 manager=7
            SysUser owned = new SysUser();
            owned.setId(99L);
            owned.setManagerId(7L);
            when(c.userMapper().selectById(99L)).thenReturn(owned);
            assertTrue(c.fps().check(30L, FolderPermissionService.P_VIEW, 7L),
                    "私人夹(拍板A): manager 链上级可见下属私人夹");
        }
    }

    @Test
    void privateFile_grantGranted_stillAllows_aclAndTypeGrant() {
        OrgScopeResolver legacyResolver = mock(OrgScopeResolver.class);
        ScopeDecision deptView = new ScopeDecision();
        deptView.setVisibleDeptIds(List.of(20L)); // 部门集不含私人夹 org=10(矩阵放不了行)
        when(legacyResolver.resolveScope(any())).thenReturn(deptView);
        when(legacyResolver.resolveScopeLegacy(any())).thenReturn(deptView);
        Ctx c = ctx(legacyResolver);

        BizFileFolder priv = new BizFileFolder();
        priv.setId(30L);
        priv.setFolderType("private");
        priv.setOwnerId(99L);
        priv.setOrgId(10L);
        when(c.folderMapper().selectById(30L)).thenReturn(priv);
        SysUser owner = new SysUser();
        when(c.userMapper().selectById(99L)).thenReturn(owner);

        // USER 级 FILE view 定向授权(objectType=FILE, folderType=private, folderId=null=全空间, all 范围, 永久)
        com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper grantMapper =
                (com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper)
                        org.springframework.test.util.ReflectionTestUtils.getField(c.fps(), "grantMapper");
        BizFolderTypeDefault grant = new BizFolderTypeDefault();
        grant.setObjectType(FolderPermissionService.OBJ_FILE);
        grant.setFolderType("private");
        grant.setSubjectType("USER");
        grant.setSubjectId(7L);
        grant.setPerms("view,download");
        grant.setScopeLevel("all");
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(grant));

        BizFile noFolderPrivate = new BizFile();
        noFolderPrivate.setId(500L);
        noFolderPrivate.setFileScope("private"); // 无夹私人文件
        noFolderPrivate.setUploaderId(99L);
        noFolderPrivate.setFolderId(null);
        noFolderPrivate.setOrgId(10L);
        when(c.fileMapper().selectById(500L)).thenReturn(noFolderPrivate);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su(7L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of());
            // 无授权时会 false(同部门部门集 {20} 不含 org=10; 无夹私人收口不进用户矩阵)
            assertTrue(c.fps().checkFile(500L, FolderPermissionService.P_VIEW, 7L),
                    "私人文件(拍板A): USER 级例外授权仍放行(grantGranted 在收口点之前)");
        }
    }

    @Test
    void privateFile_noGrant_matrixUserDenied_evenSameDept() {
        OrgScopeResolver legacyResolver = mock(OrgScopeResolver.class);
        ScopeDecision ownerView = new ScopeDecision();
        // 用户矩阵集含 uploader=99(修复前无夹私人文件经 matrixVisible(99) 放行同部门同事)
        ownerView.setVisibleUserIds(List.of(7L, 99L));
        when(legacyResolver.resolveScope(any())).thenReturn(ownerView);
        when(legacyResolver.resolveScopeLegacy(any())).thenReturn(ownerView);
        Ctx c = ctx(legacyResolver);

        com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper grantMapper =
                (com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper)
                        org.springframework.test.util.ReflectionTestUtils.getField(c.fps(), "grantMapper");
        when(grantMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        BizFile noFolderPrivate = new BizFile();
        noFolderPrivate.setId(501L);
        noFolderPrivate.setFileScope("private");
        noFolderPrivate.setUploaderId(99L);
        noFolderPrivate.setFolderId(null);
        noFolderPrivate.setOrgId(10L);
        when(c.fileMapper().selectById(501L)).thenReturn(noFolderPrivate);
        SysUser owner = new SysUser(); // 非上级链
        when(c.userMapper().selectById(99L)).thenReturn(owner);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su(7L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of());
            assertFalse(c.fps().checkFile(501L, FolderPermissionService.P_VIEW, 7L),
                    "私人无夹文件(拍板A): 同部门同事即使用户矩阵含上传人也不可见(收口核心; 修复前=true)");
        }
    }

    @Test
    void subordinateUserIds_bfsBoundedByManagerChain() {
        OrgScopeResolver legacyResolver = mock(OrgScopeResolver.class);
        Ctx c = ctx(legacyResolver);
        // 7 → 88 → 99 两层链; BFS 逐层查: 第1次(直下属)=88, 第2次(88 的下属)=99, 第3次(99 无下属)=空
        SysUser u88 = new SysUser(); u88.setId(88L); u88.setManagerId(7L);
        SysUser u99 = new SysUser(); u99.setId(99L); u99.setManagerId(88L);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        when(c.userMapper().selectList(any(Wrapper.class))).thenAnswer(inv -> {
            int n = calls.getAndIncrement();
            return switch (n) {
                case 0 -> List.of(u88);
                case 1 -> List.of(u99);
                default -> List.of();
            };
        });
        Set<Long> subs = c.fps().subordinateUserIds(7L);
        assertEquals(Set.of(88L, 99L), subs, "manager 链 BFS: 直接下属+隔级下属全量");
    }
}
