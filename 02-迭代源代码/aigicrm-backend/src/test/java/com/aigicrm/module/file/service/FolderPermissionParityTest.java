package com.aigicrm.module.file.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.file.mapper.BizFileGrantMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 【Batch-4 #1 按钮视图收口】parity 测试（H2 全真链：真 FileGrantService + 真 mapper load 过滤）。
 * 红线=每动作按钮显示与 API 判定 parity，三模式一致：
 * ①new 模式收窄修复（引擎 ALLOW 按钮亮，旧表空不丢）；
 * ②per-action DENY 压按钮（矩阵亮也压）；
 * ③按钮 ⇔ checkFile 逐动作 parity（六动作全断言）；
 * ④legacy 一致（引擎旁路=现状）；
 * ⑤整页两文件批量输出（计数断言由 FileGrantServiceTest.decideFilesLoadsGrantsOnceForPage 覆盖）；
 * ⑥夹行按钮引擎命中亮/DENY 压。
 * 教训留痕：mock 提取 wrapper 动作集不可靠（MPGENVAL 形态差异）——真 SQL 过滤后 parity 永真。
 */
@SpringJUnitConfig(FolderPermissionParityTest.Ctx.class)
class FolderPermissionParityTest {

    @EnableTransactionManagement
    @SpringBootConfiguration
    @Import(DbCfg.class)
    static class Ctx {
    }

    @TestConfiguration
    static class DbCfg {
        @Bean
        DataSource dataSource() {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:mem:b4parity;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        DataSourceInitializer dataSourceInitializer(DataSource ds) {
            DataSourceInitializer init = new DataSourceInitializer();
            init.setDataSource(ds);
            String ddl = """
                    CREATE TABLE IF NOT EXISTS biz_file_grant (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, subject_type VARCHAR(10), subject_id BIGINT, object_type VARCHAR(10), object_id BIGINT, space_type VARCHAR(20), action VARCHAR(30), effect VARCHAR(10) DEFAULT 'ALLOW', inherit TINYINT DEFAULT 1, scope_org_id BIGINT, scope_level VARCHAR(20), scope_mode VARCHAR(10) DEFAULT 'SUBTREE', expire_at DATETIME, created_by BIGINT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, legacy_ref VARCHAR(120), source VARCHAR(20) DEFAULT 'manual');
                    CREATE TABLE IF NOT EXISTS biz_file_folder (id BIGINT AUTO_INCREMENT PRIMARY KEY, folder_name VARCHAR(100), folder_type VARCHAR(20), parent_id BIGINT DEFAULT 0, org_id BIGINT, owner_id BIGINT, deleted INT DEFAULT 0);
                    CREATE TABLE IF NOT EXISTS biz_file (id BIGINT AUTO_INCREMENT PRIMARY KEY, folder_id BIGINT, file_name VARCHAR(200), file_scope VARCHAR(20), uploader_id BIGINT, org_id BIGINT, deleted INT DEFAULT 0);
                    
                    CREATE TABLE IF NOT EXISTS sys_dept (id BIGINT AUTO_INCREMENT PRIMARY KEY, dept_name VARCHAR(50), parent_id BIGINT DEFAULT 0, dept_type INT, leader_id BIGINT, status INT DEFAULT 1, deleted INT DEFAULT 0);
                    CREATE TABLE IF NOT EXISTS sys_role (id BIGINT AUTO_INCREMENT PRIMARY KEY, role_name VARCHAR(50), role_code VARCHAR(50), description VARCHAR(200), data_scope VARCHAR(20), status INT DEFAULT 1, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, deleted INT DEFAULT 0);
                    CREATE TABLE IF NOT EXISTS biz_folder_type_default (id BIGINT AUTO_INCREMENT PRIMARY KEY, folder_type VARCHAR(20), object_type VARCHAR(10) DEFAULT 'FILE', folder_id BIGINT, scope_org_id BIGINT, scope_level VARCHAR(10), subject_type VARCHAR(10) DEFAULT 'ROLE', subject_id BIGINT, perms VARCHAR(500), expires_at DATETIME, created_by BIGINT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, tenant_id BIGINT DEFAULT 1);
                    """;
            ResourceDatabasePopulator pop = new ResourceDatabasePopulator();
            pop.addScript(new ByteArrayResource(ddl.getBytes()));
            pop.setSeparator(";");
            init.setDatabasePopulator(pop);
            return init;
        }

        @Bean
        PlatformTransactionManager txManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
            fb.setDataSource(ds);
            MybatisConfiguration cfg = new MybatisConfiguration();
            cfg.setMapUnderscoreToCamelCase(true);
            fb.setConfiguration(cfg);
            cfg.addMapper(BizFileGrantMapper.class);
            cfg.addMapper(BizFolderTypeDefaultMapper.class);
            return fb.getObject();
        }

        @Bean
        OrgScopeResolver scopeResolver() {
            OrgScopeResolver resolver = mock(OrgScopeResolver.class);
            when(resolver.resolveScope(any(), any())).thenReturn(java.util.Optional.of(List.of()));
            when(resolver.resolveDeptScope(any(), any())).thenReturn(List.of());
            when(resolver.resolveModuleUsers(any(), any(), any())).thenReturn(List.of());
            return resolver;
        }

        @Bean
        FileGrantService fileGrantService(SqlSessionFactory sf, OrgScopeResolver scopeResolver) {
            var t = new org.mybatis.spring.SqlSessionTemplate(sf);
            FileGrantService engine = new FileGrantService(t.getMapper(BizFileGrantMapper.class),
                    mock(com.aigicrm.module.system.mapper.SysRoleMapper.class));
            setField(engine, "scopeResolver", scopeResolver);
            setField(engine, "grantMode", "dual");
            return engine;
        }

        @Bean
        com.aigicrm.module.file.mapper.BizFileMapper fileMapper() {
            com.aigicrm.module.file.mapper.BizFileMapper m = mock(com.aigicrm.module.file.mapper.BizFileMapper.class);
            return m;
        }

        @Bean
        FolderPermissionService folderPermissionService(SqlSessionFactory sf, OrgScopeResolver sr,
                                                        FileGrantService engine,
                                                        com.aigicrm.module.file.mapper.BizFileMapper fileMapper) {
            var t = new org.mybatis.spring.SqlSessionTemplate(sf);
            FolderPermissionService svc = new FolderPermissionService(
                    mock(com.aigicrm.module.file.mapper.BizFileFolderMapper.class),
                    fileMapper,
                    mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                    mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                    mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                    t.getMapper(BizFolderTypeDefaultMapper.class), sr);
            setField(svc, "fileGrantService", engine);
            return svc;
        }

        static void setField(Object target, String name, Object value) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Autowired FolderPermissionService svc;
    @Autowired FileGrantService engine;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.aigicrm.module.file.mapper.BizFileMapper fileMapper;

    static final long CUR = 2L;
    static final long UP = 3L;

    @org.junit.jupiter.api.BeforeEach
    void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        List<Class<?>> entities = List.of(com.aigicrm.module.file.entity.BizFileGrant.class,
                com.aigicrm.module.system.entity.SysRole.class,
                com.aigicrm.module.file.entity.BizFolderTypeDefault.class);
        for (Class<?> c : entities) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), c);
        }
    }

    @BeforeEach
    void seed() {
        reset(fileMapper);
        for (String t : List.of("biz_file_grant", "biz_file", "biz_file_folder",
                "biz_folder_type_default")) {
            jdbc.update("DELETE FROM " + t);
        }
        setMode("dual");
    }

    private void setMode(String mode) {
        DbCfg.setField(engine, "grantMode", mode);
    }

    private SecurityUser cur() {
        SecurityUser u = new SecurityUser();
        u.setUserId(CUR);
        u.setRoleCodes(List.of("06"));
        return u;
    }

    private void grant(String action, String effect, String objectType, Long objectId, String spaceType) {
        jdbc.update("INSERT INTO biz_file_grant (tenant_id, subject_type, subject_id, object_type, object_id, space_type, action, effect, inherit, scope_mode) "
                + "VALUES (1,'USER',2,?,?,?,?,?,0,'ANY')", objectType, objectId, spaceType, action, effect);
    }

    private com.aigicrm.module.file.entity.BizFile file(long id) {
        var f = new com.aigicrm.module.file.entity.BizFile();
        f.setId(id);
        f.setFolderId(null);
        f.setUploaderId(UP);
        f.setOrgId(5L);
        f.setFileScope("public");
        return f;
    }

    @Test
    void t1_newModeEngineAllowLightsButtons() {
        setMode("new");
        jdbc.update("INSERT INTO biz_file (id, folder_id, file_name, file_scope, uploader_id, org_id) VALUES (10,NULL,'f','public',3,5)");
        grant("view", "ALLOW", "FILE", 10L, null);
        grant("rename", "ALLOW", "SPACE", null, "public");
        grant("share", "ALLOW", "SPACE", null, "public");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(cur());
            List<String> perms = svc.permsForFiles(List.of(file(10L)), CUR).get(10L);
            assertTrue(perms.contains("view") && perms.contains("rename") && perms.contains("share"),
                    "new 模式引擎 ALLOW 按钮亮(收窄修复): " + perms);
            assertFalse(perms.contains("delete"));
        }
    }

    @Test
    void t2_engineDenySuppressesEvenIfMatrixWouldAllow() {
        jdbc.update("INSERT INTO biz_file (id, folder_id, file_name, file_scope, uploader_id, org_id) VALUES (10,NULL,'f','public',3,5)");
        grant("delete", "DENY", "SPACE", null, "public");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(cur());
            List<String> perms = svc.permsForFiles(List.of(file(10L)), CUR).get(10L);
            assertFalse(perms.contains("delete"), "per-action DENY 压按钮: " + perms);
        }
    }

    @Test
    void t3_parityButtonEqualsCheckFile() {
        setMode("new");
        jdbc.update("INSERT INTO biz_file (id, folder_id, file_name, file_scope, uploader_id, org_id) VALUES (10,NULL,'f','public',3,5)");
        grant("view", "ALLOW", "FILE", 10L, null);
        grant("rename", "ALLOW", "SPACE", null, "public");
        grant("share", "ALLOW", "SPACE", null, "public");
        when(fileMapper.selectById(10L)).thenReturn(file(10L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(cur());
            List<String> perms = svc.permsForFiles(List.of(file(10L)), CUR).get(10L);
            for (String action : List.of("view", "download", "rename", "replace", "share", "delete")) {
                assertEquals(svc.checkFile(10L, action, CUR), perms.contains(action),
                        "parity 破裂 action=" + action + " perms=" + perms);
            }
        }
    }

    @Test
    void t4_legacyModeKeepsLegacyBehavior() {
        setMode("legacy");
        jdbc.update("INSERT INTO biz_file (id, folder_id, file_name, file_scope, uploader_id, org_id) VALUES (10,NULL,'f','public',3,5)");
        grant("view", "ALLOW", "FILE", 10L, null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(cur());
            List<String> perms = svc.permsForFiles(List.of(file(10L)), CUR).get(10L);
            assertFalse(perms.contains("view"), "legacy=引擎旁路(现状): " + perms);
        }
    }

    @Test
    void t5_batchOutputsAllRows() {
        jdbc.update("INSERT INTO biz_file (id, folder_id, file_name, file_scope, uploader_id, org_id) VALUES (10,NULL,'a','public',3,5),(11,NULL,'b','public',3,5)");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(cur());
            Map<Long, List<String>> out = svc.permsForFiles(List.of(file(10L), file(11L)), CUR);
            assertTrue(out.containsKey(10L) && out.containsKey(11L));
        }
    }

    @Test
    void t6_folderBatchButtonsHonorEngine() {
        jdbc.update("INSERT INTO biz_file_folder (id, folder_name, folder_type, parent_id, org_id) VALUES (7,'d','public',0,5)");
        grant("view", "ALLOW", "SPACE", null, "public");
        grant("delete", "DENY", "SPACE", null, "public");
        var folder = new com.aigicrm.module.file.entity.BizFileFolder();
        folder.setId(7L);
        folder.setFolderType("public");
        folder.setOrgId(5L);
        folder.setParentId(0L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(cur());
            List<String> perms = svc.permsForBatch(List.of(folder), CUR).get(7L);
            assertTrue(perms.contains("view"), "夹行按钮引擎命中亮: " + perms);
            assertFalse(perms.contains("delete"), "夹行 per-action DENY 压按钮");
        }
    }
}
