package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFileGrant;
import com.aigicrm.module.file.mapper.BizFileGrantMapper;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 【A2 Batch-3 · 用户拍板 #4】双写故障注入集成测试：把 @Transactional 的声明性保障转为实证。
 *
 * 真实链路：H2 内存库(MODE=MySQL) + 真实 DataSourceTransactionManager + 真实
 * FolderTypeDefaultService + spy LegacyFileGrantSyncService 注入故障——
 * ① 正向：saveGrant → 旧表落库 + 新表双写行落库（真实 SQL 全链）；
 * ② 故障：syncUpsert 抛错 → 异常传播且**旧表写入同事务回滚**（行数 0，不允许“旧表成功/新表失败”）；
 * ③ 故障：syncDelete 抛错（removeGrant 路径）→ 旧表删除同样回滚（行仍在）。
 */
@SpringJUnitConfig(LegacyFileGrantSyncTxTest.Ctx.class)
class LegacyFileGrantSyncTxTest {

    @org.springframework.transaction.annotation.EnableTransactionManagement
    @SpringBootConfiguration
    @Import({TxDbConfig.class, ServiceConfig.class})
    static class Ctx {
    }

    /** H2 内存库(MySQL 模式) + 事务管理器 + MP SqlSessionFactory + 双 mapper 扫描。 */
    @TestConfiguration
    static class TxDbConfig {
        @Bean
        DataSource dataSource() {
            org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
            ds.setURL("jdbc:h2:mem:a2dualwrite;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        DataSourceInitializer dataSourceInitializer(DataSource ds) {
            DataSourceInitializer init = new DataSourceInitializer();
            init.setDataSource(ds);
            String ddl = """
                    CREATE TABLE IF NOT EXISTS biz_folder_type_default (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      folder_type VARCHAR(20) NOT NULL,
                      object_type VARCHAR(10) NOT NULL DEFAULT 'FILE',
                      folder_id BIGINT NULL,
                      scope_org_id BIGINT NULL,
                      scope_level VARCHAR(10) NULL,
                      subject_type VARCHAR(10) NOT NULL DEFAULT 'ROLE',
                      subject_id BIGINT NOT NULL,
                      perms VARCHAR(500) NOT NULL,
                      expires_at DATETIME NULL,
                      created_by BIGINT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      tenant_id BIGINT NOT NULL DEFAULT 1
                    );
                    CREATE TABLE IF NOT EXISTS biz_file_grant (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      tenant_id BIGINT NOT NULL DEFAULT 1,
                      subject_type VARCHAR(10) NOT NULL,
                      subject_id BIGINT NOT NULL,
                      object_type VARCHAR(10) NOT NULL,
                      object_id BIGINT NULL,
                      space_type VARCHAR(20) NULL,
                      action VARCHAR(30) NOT NULL,
                      effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
                      inherit TINYINT NOT NULL DEFAULT 1,
                      scope_org_id BIGINT NULL,
                      scope_level VARCHAR(20) NULL,
                      scope_mode VARCHAR(10) NOT NULL DEFAULT 'SUBTREE',
                      expire_at DATETIME NULL,
                      created_by BIGINT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      legacy_ref VARCHAR(120) NULL,
                      source VARCHAR(20) NOT NULL DEFAULT 'manual'
                    );
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
        SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
            fb.setDataSource(ds);
            MybatisConfiguration cfg = new MybatisConfiguration();
            cfg.setMapUnderscoreToCamelCase(true);
            fb.setConfiguration(cfg);
            // getMapper 前必须注册 mapper 接口(TableInfo 亦在此初始化)
            cfg.addMapper(BizFolderTypeDefaultMapper.class);
            cfg.addMapper(BizFileGrantMapper.class);
            return fb.getObject();
        }
    }

    /** 服务装配：真实双写服务 + spy 注入点；其余协作 mock。 */
    @TestConfiguration
    static class ServiceConfig {
        @Bean
        BizFolderTypeDefaultMapper folderTypeDefaultMapper(SqlSessionFactory sf) {
            return MapperFactoryBeanProxy.newProxy(sf, BizFolderTypeDefaultMapper.class);
        }

        @Bean
        BizFileGrantMapper fileGrantMapper(SqlSessionFactory sf) {
            return MapperFactoryBeanProxy.newProxy(sf, BizFileGrantMapper.class);
        }

        @Bean BizFileFolderMapper folderMapper() { return mock(BizFileFolderMapper.class); }
        @Bean SysRoleMapper roleMapper() { return mock(SysRoleMapper.class); }
        @Bean SysUserMapper userMapper() { return mock(SysUserMapper.class); }

        @Bean
        LegacyFileGrantSyncService legacySync(BizFileGrantMapper mapper) {
            return spy(new LegacyFileGrantSyncService(mapper));
        }

        @Bean
        FolderTypeDefaultService service(BizFolderTypeDefaultMapper grantMapper,
                                         BizFileFolderMapper folderMapper,
                                         SysRoleMapper roleMapper,
                                         SysUserMapper userMapper,
                                         LegacyFileGrantSyncService legacySync) {
            return new FolderTypeDefaultService(grantMapper, folderMapper, roleMapper, userMapper,
                    mock(com.aigicrm.security.SecurityAuditService.class), legacySync);
        }
    }

    /** Mapper 代理：SqlSessionTemplate 集成 Spring 事务（事务内复用绑定连接——回滚语义的关键）。 */
    static final class MapperFactoryBeanProxy {
        static <T> T newProxy(SqlSessionFactory sf, Class<T> type) {
            return new org.mybatis.spring.SqlSessionTemplate(sf).getMapper(type);
        }
    }

    @Autowired FolderTypeDefaultService svc;
    @Autowired LegacyFileGrantSyncService legacySync;
    @Autowired BizFolderTypeDefaultMapper oldMapper;
    @Autowired BizFileGrantMapper grantMapper;
    @Autowired SysRoleMapper roleMapper;

    @BeforeEach
    void cleanTables() {
        oldMapper.delete(null);
        grantMapper.delete(null);
        reset(legacySync); // spy reset 后 unstubbed 默认真实行为(正向用例走真实 SQL)
        reset(roleMapper);
        com.aigicrm.module.system.entity.SysRole role = new com.aigicrm.module.system.entity.SysRole();
        role.setId(9L);
        role.setRoleCode("06");
        role.setStatus(1);
        when(roleMapper.selectById(9L)).thenReturn(role); // saveGrant 的 ROLE 主体校验
    }

    private void seedOneRow() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.saveGrant("public", "FILE", null, null, null, "ROLE", 9L,
                    List.of("view", "download"), LocalDateTime.now().plusYears(1));
        }
    }

    @Test
    void happyPathPersistsOldAndNewTablesAtomically() {
        seedOneRow();
        assertEquals(1, oldMapper.selectCount(null), "旧表落库");
        assertEquals(2, grantMapper.selectCount(null), "新表双写两动作行(view/download)");
        List<BizFileGrant> rows = grantMapper.selectList(null);
        assertTrue(rows.stream().allMatch(g -> "dual_write".equals(g.getSource())
                && g.getLegacyRef().startsWith("ftd:")), "dual_write + legacy_ref 前缀");
        assertTrue(rows.stream().anyMatch(g -> "view".equals(g.getAction())));
        assertTrue(rows.stream().anyMatch(g -> "download".equals(g.getAction())));
    }

    @Test
    void syncUpsertFailureRollsBackOldTableWrite() {
        doThrow(new RuntimeException("注入: 新表 upsert 失败")).when(legacySync).syncUpsert(any());
        RuntimeException ex = assertThrows(RuntimeException.class, this::seedOneRow);
        assertTrue(ex.getMessage().contains("新表 upsert 失败"), "异常应传播(事务标记回滚)");
        assertEquals(0, oldMapper.selectCount(null), "旧表写入同事务回滚——不允许旧表成功/新表失败");
        assertEquals(0, grantMapper.selectCount(null), "新表无残留");
    }

    @Test
    void syncDeleteFailureRollsBackOldTableDelete() {
        seedOneRow();
        Long oldId = oldMapper.selectList(null).get(0).getId();
        doThrow(new RuntimeException("注入: 新表删除失败")).when(legacySync).syncDelete(any());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            assertThrows(RuntimeException.class, () -> svc.removeGrant(oldId));
        }
        assertEquals(1, oldMapper.selectCount(null), "旧表删除回滚——行仍在");
        assertEquals(2, grantMapper.selectCount(null), "新表双写行仍在");
    }
}
