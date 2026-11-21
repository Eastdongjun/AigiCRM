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
                    null, "USER", 2L, "FILE", 10L, null, "download", "ALLOW", 1, null, null, null, null)));
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
        // 【F-1 修复连带】folderAncestors 判存后入集：需注入 folderMapper stub（folder 1 存在、parent=0）
        BizFileFolder folder1 = new BizFileFolder();
        folder1.setId(1L);
        folder1.setParentId(0L);
        when(folderMapper.selectById(1L)).thenReturn(folder1);
        setField("folderMapper", folderMapper);
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
        // 【A2】SPACE 匹配改读 space_type（原用例绑 scopeLevel='public'，随 spaceType 转正同步改造）
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            BizFileGrant g = grant("SPACE", null, "delete", "ALLOW", 1, null, null);
            g.setSpaceType("public");
            when(mapper.selectList(any())).thenReturn(List.of(g));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 5L), "delete", 2L));
            // 空间类型不匹配 → ABSTAIN
            BizFileGrant priv = grant("SPACE", null, "delete", "ALLOW", 1, null, null);
            priv.setSpaceType("private");
            when(mapper.selectList(any())).thenReturn(List.of(priv));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 1L, 3L, 5L), "delete", 2L));
        }
    }

    @Test
    void spaceGrantNullTypeMatchesAnySpace() {
        // §2.7：space_type=NULL（旧 blank 迁移行）=任意空间，public/private 文件均命中
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            BizFileGrant g = grant("SPACE", null, "view", "ALLOW", 1, null, null);
            g.setSpaceType(null);
            when(mapper.selectList(any())).thenReturn(List.of(g));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
            BizFile priv = file(10L, 1L, 3L, 5L);
            priv.setFileScope("private");
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(priv, "view", 2L));
        }
    }

    @Test
    void saveRejectsSpaceWithoutTypeAndBadVocab() {
        // §2.6/§2.7：SPACE 必填 spaceType 且限词表；scopeLevel 不再当空间类型
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(1L));
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            assertThrows(BusinessException.class, () -> svc.save(new FileGrantService.Command(
                    null, "USER", 2L, "SPACE", null, null, "view", "ALLOW", 0, null, null, null, null)));
            assertThrows(BusinessException.class, () -> svc.save(new FileGrantService.Command(
                    null, "USER", 2L, "SPACE", null, "foo", "view", "ALLOW", 0, null, null, null, null)));
            assertThrows(BusinessException.class, () -> svc.save(new FileGrantService.Command(
                    null, "USER", 2L, "FOLDER", 7L, "public", "view", "ALLOW", 1, null, null, null, null)),
                    "非 SPACE 行不得携带 spaceType");
        }
    }

    @Test
    void scopeModeRelativeCoversByViewerLevel() {
        // RELATIVE 口径=旧 scopeCovers：self=上传人本人；region=档位部门集含目标 org（resolver null=档位集）
        com.aigicrm.common.scope.OrgScopeResolver resolver =
                mock(com.aigicrm.common.scope.OrgScopeResolver.class);
        setField("scopeResolver", resolver);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            SecurityUser viewer = user(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(viewer);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            when(roleMapper.selectList(any())).thenReturn(List.of());
            // self：目标上传人=当前用户 → ALLOW；他人 → ABSTAIN
            BizFileGrant self = grant("FILE", 10L, "view", "ALLOW", 1, null, null);
            self.setScopeMode(FileGrantService.SCOPE_RELATIVE);
            self.setScopeLevel("self");
            when(mapper.selectList(any())).thenReturn(List.of(self));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 2L, 5L), "view", 2L));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
            // region：resolveDeptScope(viewer, region) 含 5 → ALLOW；不含 6 → ABSTAIN；null=不限 → ALLOW
            BizFileGrant region = grant("FILE", 10L, "view", "ALLOW", 1, null, null);
            region.setScopeMode(FileGrantService.SCOPE_RELATIVE);
            region.setScopeLevel("region");
            when(mapper.selectList(any())).thenReturn(List.of(region));
            when(resolver.resolveDeptScope(viewer, "region")).thenReturn(List.of(5L, 7L));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 1L, 3L, 6L), "view", 2L));
            when(resolver.resolveDeptScope(viewer, "region")).thenReturn(null);
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 6L), "view", 2L));
        }
    }

    @Test
    void scopeModeExactDoesNotCoverSubtree() {
        // EXACT=绝对组织精确：闭包含 10→11 也不放大（与 SUBTREE 对照）
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(any(String.class), eq(Long.class), eq(11L))).thenReturn(List.of(10L, 11L));
        setField("jdbc", jdbc);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            BizFileGrant exact = grant("FILE", 10L, "view", "ALLOW", 1, 10L, null);
            exact.setScopeMode(FileGrantService.SCOPE_EXACT);
            when(mapper.selectList(any())).thenReturn(List.of(exact));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 1L, 3L, 11L), "view", 2L),
                    "EXACT 精确匹配：文件 org=11 ≠ scope 10，闭包祖先不放大");
            exact.setScopeMode(FileGrantService.SCOPE_SUBTREE);
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 11L), "view", 2L),
                    "SUBTREE 子树匹配：闭包 11→10 命中");
        }
    }

    @Test
    void legacyModeBypassesEngine() {
        // 【A2 §2.8】mode=legacy：decide* 恒 ABSTAIN（完全旧链），不查授权表
        setField("grantMode", "legacy");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FILE", 10L, "view", "ALLOW", 1, null, null)));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
            verify(mapper, never()).selectList(any());
            assertTrue(svc.engineBypassed());
            assertTrue(svc.legacyFallback(), "mode=legacy 完全旧链（旧表照读）");
        }
    }

    @Test
    void dualAndNewModesKeepEngineOn() {
        // dual/new：引擎开启（差异只在调用方是否回落旧表）
        setField("grantMode", "new");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FILE", 10L, "view", "ALLOW", 1, null, null)));
            assertEquals(FileGrantService.Decision.ALLOW, svc.decideFile(file(10L, 1L, 3L, 5L), "view", 2L));
            assertFalse(svc.engineBypassed());
            assertFalse(svc.legacyFallback(), "mode=new 不再回落旧表");
        }
        setField("grantMode", "dual");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            assertFalse(svc.engineBypassed());
            assertTrue(svc.legacyFallback(), "mode=dual ABSTAIN 回落旧表");
        }
    }

    @Test
    void softDeletedAnchorFolderIsNotAncestor() {
        // 【F-1 修复，QC 拍板 #5】软删夹不得作为授权锚点：grant 在 A(1) 上，A 已删（selectById=null）
        // → 祖先集不含 1 → 子夹 B(2) 内文件 ABSTAIN（运行时新预期=403）
        BizFileFolder childB = new BizFileFolder();
        childB.setId(2L);
        childB.setParentId(1L);
        when(folderMapper.selectById(2L)).thenReturn(childB);
        when(folderMapper.selectById(1L)).thenReturn(null); // 软删（逻辑删过滤）
        setField("folderMapper", folderMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(user(2L));
            when(roleMapper.selectList(any())).thenReturn(List.of());
            when(mapper.selectList(any())).thenReturn(List.of(
                    grant("FOLDER", 1L, "view", "ALLOW", 1, null, null)));
            assertEquals(FileGrantService.Decision.ABSTAIN, svc.decideFile(file(10L, 2L, 3L, 5L), "view", 2L),
                    "软删锚点夹上的存量授权失效（fail-closed）");
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

    @Test
    void nullSubjectTypeIs400Not500() {
        // 【2026-11-21 PM-D1】空 body 保存授权：subjectType=null 不得触发
        // Set.of(...).contains(null) 的 NPE（HTTP 500），应返回 400 业务异常。
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            BusinessException e = assertThrows(BusinessException.class, () -> svc.save(
                    new FileGrantService.Command(null, null, 2L, "FILE", 10L, null,
                            "view", "ALLOW", 1, null, null, null, null)));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("主体类型"));
        }
    }

    @Test
    void nullObjectTypeIs400Not500() {
        // 【2026-11-21 PM-D1】subjectType 合法但 objectType=null 同样要 400，不是 NPE 500。
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            BusinessException e = assertThrows(BusinessException.class, () -> svc.save(
                    new FileGrantService.Command(null, "USER", 2L, null, null, null,
                            "view", "ALLOW", 1, null, null, null, null)));
            assertEquals(400, e.getCode());
            assertTrue(e.getMessage().contains("对象类型"));
        }
    }
}
