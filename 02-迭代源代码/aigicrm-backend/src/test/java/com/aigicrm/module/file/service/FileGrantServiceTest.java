package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFileGrant;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileGrantMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 【2026-11-17 阶段二】biz_file_grant 统一授权引擎：DENY 优先、继承、过期、组织范围。 */
class FileGrantServiceTest {

    @BeforeEach
    void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizFileGrant.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), SysRole.class);
    }

    private final BizFileGrantMapper mapper = mock(BizFileGrantMapper.class);
    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final FileGrantService svc = new FileGrantService(mapper, roleMapper);

    private SecurityUser user(long id) {
        SecurityUser u = new SecurityUser();
        u.setUserId(id);
        u.setRoleCodes(List.of("06"));
        return u;
    }

    private BizFile file(long id, long folderId, long uploader, long orgId) {
        BizFile f = new BizFile();
        f.setId(id);
        f.setFolderId(folderId);
        f.setUploaderId(uploader);
        f.setOrgId(orgId);
        f.setFileScope("public");
        return f;
    }

    private BizFileGrant grant(String objectType, Long objectId, String action, String effect,
                               Integer inherit, Long scopeOrgId, LocalDateTime expireAt) {
        BizFileGrant g = new BizFileGrant();
        g.setSubjectType("USER");
        g.setSubjectId(2L);
        g.setObjectType(objectType);
        g.setObjectId(objectId);
        g.setAction(action);
        g.setEffect(effect);
        g.setInherit(inherit);
        g.setScopeOrgId(scopeOrgId);
        g.setExpireAt(expireAt);
        return g;
    }

    private void setField(String name, Object value) {
        try {
            Field f = FileGrantService.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(svc, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void folderGrantRecursesToChildFolders() {
        BizFileFolder child = new BizFileFolder();
        child.setId(2L);
        child.setParentId(1L);
        BizFileFolder root = new BizFileFolder();
        root.setId(1L);
        root.setParentId(0L);
        when(folderMapper.selectById(2L)).thenReturn(child);
        when(folderMapper.selectById(1L)).thenReturn(root);
        setField("folderMapper", folderMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FOLDER", 1L, "view", "ALLOW", 1, null, null)));
            assertEquals(FileGrantService.Decision.ALLOW,
                    svc.decideFile(file(10L, 2L, 3L, 5L), "view", 2L));
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FOLDER", 1L, "view", "ALLOW", 0, null, null)));
            assertEquals(FileGrantService.Decision.ABSTAIN,
                    svc.decideFile(file(10L, 2L, 3L, 5L), "view", 2L));
        }
    }

    @Test
    void scopeOrgUsesSubtreeAncestors() {
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(any(String.class), eq(Long.class), eq(11L)))
                .thenReturn(List.of(10L, 11L));
        setField("jdbc", jdbc);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FILE", 10L, "view", "ALLOW", 1, 10L, null)));
            assertEquals(FileGrantService.Decision.ALLOW,
                    svc.decideFile(file(10L, 1L, 3L, 11L), "view", 2L));
        }
    }

    @Test
    void decideFilesLoadsGrantsOnceForPage() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of());
            svc.decideFiles(List.of(file(10L, 1L, 3L, 5L), file(11L, 1L, 3L, 5L)), "view", 2L);
            verify(mapper, times(1)).selectList(any());
        }
    }

    @Test
    void nonAdminCannotCreateFileGrant() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            assertThrows(BusinessException.class, () -> svc.save(new FileGrantService.Command(
                    null, "USER", 2L, "FILE", 10L, "download", "ALLOW", 1, null, null, null)));
        }
    }

    @Test
    void denyWinsOverAllow() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FILE", 10L, "view", "ALLOW", 1, null, null),
                    grant("FILE", 10L, "view", "DENY", 1, null, null)));
            assertEquals(FileGrantService.Decision.DENY, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
        }
    }

    @Test
    void folderGrantInheritsToFileAndMatchesOrg() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FOLDER", 1L, "view", "ALLOW", 1, 5L, null)));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 1L, 3L, 6L), "view", 2L));
        }
    }

    @Test
    void expiredAndNonInheritedFolderGrantDoNotApply() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FOLDER", 1L, "view", "ALLOW", 0, null, null),
                    grant("FILE", 10L, "view", "ALLOW", 1, null, LocalDateTime.now().minusDays(1))));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
        }
    }

    @Test
    void spaceGrantMatchesFileScope() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            BizFileGrant g = grant("SPACE", null, "delete", "ALLOW", 1, null, null);
            g.setScopeLevel("public");
            when(mapper.selectList(any())).thenReturn(List.of(g));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 5L), "delete", 2L));
        }
    }

    @Test
    void roleSubjectIsResolvedFromRoleCodes() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            SysRole r = new SysRole();
            r.setId(7L);
            r.setRoleCode("06");
            r.setStatus(1);
            when(roleMapper.selectList(any())).thenReturn(List.of(r));
            BizFileGrant g = grant("FILE", 10L, "view", "ALLOW", 1, null, null);
            g.setSubjectType("ROLE");
            g.setSubjectId(7L);
            when(mapper.selectList(any())).thenReturn(List.of(g));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
        }
    }
}
