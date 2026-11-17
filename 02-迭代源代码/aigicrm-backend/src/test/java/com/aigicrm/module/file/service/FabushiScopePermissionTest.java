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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private final FolderPermissionService svc =
            new FolderPermissionService(folderMapper, fileMapper, userMapper, deptMapper, roleMapper, grantMapper, scopeResolver);

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

    @Test
    void fabushiDeleteRequiresIndependentCodeAndScope() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
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
