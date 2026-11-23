package com.aigicrm.module.file.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.file.entity.BizFile;
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
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 【2026-11-17 阶段二】法布施文件与版本回滚的“功能码 + 组织数据范围”双门测试。 */
class FabushiScopePermissionTest {

    @BeforeEach
    void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        for (Class<?> c : List.of(BizFolderTypeDefault.class, SysUser.class, SysDept.class,
                SysRole.class, BizFileFolder.class, BizFile.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), c);
        }
    }

    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final BizFolderTypeDefaultMapper grantMapper = mock(BizFolderTypeDefaultMapper.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FolderPermissionService svc =
            new FolderPermissionService(folderMapper, fileMapper, userMapper, deptMapper, roleMapper, grantMapper, scopeResolver);

    @BeforeEach
    void injectOptionalJdbc() {
        ReflectionTestUtils.setField(svc, "jdbc", jdbc);
    }

    private SecurityUser user(long id) {
        SecurityUser u = new SecurityUser();
        u.setUserId(id);
        u.setDataScope("all");
        return u;
    }

    private BizFile fabushiFile(long id, long uploader) {
        BizFile f = new BizFile();
        f.setId(id);
        f.setFileScope("fabushi");
        f.setUploaderId(uploader);
        return f;
    }

    @Test
    void fabushiViewUsesOwnScopeAndFailsClosedOutsideIt() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(() -> SecurityUtils.hasAuthority("fabushi:view")).thenReturn(true);
            when(scopeResolver.resolveModuleUsers(any(), eq("fabushi"), eq("file_view")))
                    .thenReturn(List.of(2L, 3L));

            assertEquals(Set.of(2L, 3L), svc.fabushiVisibleUploaderIds(2L));
            when(scopeResolver.canAccess(any())).thenReturn(true);
            assertTrue(svc.canViewFabushiFile(fabushiFile(10L, 3L), 2L));
            when(scopeResolver.canAccess(any())).thenReturn(false);
            assertFalse(svc.canViewFabushiFile(fabushiFile(11L, 4L), 2L));
        }
    }

    /** 【R2-FABUSHI-SCOPE-001 根因修复】已审核书籍 PDF 是社区内容：上传人不在本人范围也放行。 */
    @Test
    void communityBookPdfAllowedDespiteUploaderOutsideScope() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(() -> SecurityUtils.hasAuthority("fabushi:view")).thenReturn(true);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any())).thenReturn(1);
            when(scopeResolver.canAccess(any())).thenReturn(false); // 上传人(admin)不在本人范围

            assertTrue(svc.canViewFabushiFile(fabushiFile(10L, 4L), 2L),
                    "已审核书籍 PDF/已发布内容附件：fabushi:view 即放行，不受上传人数据范围限制");
        }
    }

    /** 未挂到已发布内容的普通法布施上传仍按上传人范围收口。 */
    @Test
    void nonCommunityFabushiFileStillUsesUploaderScope() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(() -> SecurityUtils.hasAuthority("fabushi:view")).thenReturn(true);
            when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any())).thenReturn(0);
            when(scopeResolver.canAccess(any())).thenReturn(false);

            assertFalse(svc.canViewFabushiFile(fabushiFile(10L, 4L), 2L),
                    "非社区内容仍走上传人范围，范围外拒绝");
        }
    }

    /** 社区内容放行不得压过统一授权引擎的 DENY。 */
    @Test
    void communityFileStillRespectsGrantDeny() {
        FileGrantService engine = mock(FileGrantService.class);
        ReflectionTestUtils.setField(svc, "fileGrantService", engine);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(() -> SecurityUtils.hasAuthority("fabushi:view")).thenReturn(true);
            when(engine.decideFile(any(), eq("view"), eq(2L))).thenReturn(FileGrantService.Decision.DENY);

            assertFalse(svc.canViewFabushiFile(fabushiFile(10L, 4L), 2L), "DENY 优先于社区放行");
            verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any());
        }
    }

    /** 无 fabushi:view 功能码时社区内容同样拒绝，且不做社区查询。 */
    @Test
    void communityFileRequiresFabushiViewAuthority() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(() -> SecurityUtils.hasAuthority("fabushi:view")).thenReturn(false);

            assertFalse(svc.canViewFabushiFile(fabushiFile(10L, 4L), 2L));
            verifyNoInteractions(jdbc);
        }
    }

    @Test
    void fabushiDeleteRequiresIndependentCodeAndScope() {        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(() -> SecurityUtils.hasAuthority("fabushi:file:delete")).thenReturn(false);
            assertFalse(svc.canDeleteFabushiFile(fabushiFile(10L, 3L), 2L));

            m.when(() -> SecurityUtils.hasAuthority("fabushi:file:delete")).thenReturn(true);
            when(scopeResolver.canAccess(any())).thenReturn(true);
            assertTrue(svc.canDeleteFabushiFile(fabushiFile(10L, 3L), 2L));

            when(scopeResolver.canAccess(any())).thenReturn(false);
            assertFalse(svc.canDeleteFabushiFile(fabushiFile(10L, 3L), 2L));
        }
    }

    @Test
    void versionRollbackUsesIndependentCodeAndScope() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(() -> SecurityUtils.hasAuthority("file:version:rollback")).thenReturn(true);
            when(scopeResolver.canAccess(any())).thenReturn(true);
            assertTrue(svc.canRollbackVersion(fabushiFile(10L, 3L), 2L));

            when(scopeResolver.canAccess(any())).thenReturn(false);
            assertFalse(svc.canRollbackVersion(fabushiFile(10L, 3L), 2L));
        }
    }
}
