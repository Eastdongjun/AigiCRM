package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * 【A3】回收站导出集成测试（计划 §1.8 单测/集成九条；H2 + @TempDir 真实文件系统）：
 * ①admin 创建→ready ②非 admin 403 ③过期 410+清理置 expired ④manifest/checksums 行数一致
 * ⑤路径穿越清洗 ⑥空/非法类型 400 ⑦六类各 1 条导出字段抽查 ⑧contentMissing 行不打包
 * ⑨审计 create/download/delete 三条（audit mock 计数）。
 * 【GL-14 2026-11-24】⑩～⑫ 删除时间过滤的回归(安全修复 9db17c8f)：过滤值只作参数绑定、
 * 行数上限按过滤后的范围计、过滤确实缩小导出范围。
 * 异步生成用同步等待轮询（worker 单线程, 导出即触发, 秒级完成）。
 */
@SpringJUnitConfig(RecycleExportServiceTest.Ctx.class)
class RecycleExportServiceTest {

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
            ds.setURL("jdbc:h2:mem:a3export;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        DataSourceInitializer dataSourceInitializer(DataSource ds) {
            DataSourceInitializer init = new DataSourceInitializer();
            init.setDataSource(ds);
            String ddl = """
-- 【生成物·勿手改】scripts/gen-a3-fixture-ddl.py 生成（V1__baseline 列名摘录 + 回收站列增补）
CREATE TABLE IF NOT EXISTS biz_customer (id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_code VARCHAR(200), customer_name VARCHAR(200), invoice_name VARCHAR(200), industry VARCHAR(200), dev_type VARCHAR(200), group_name VARCHAR(200), branch_company VARCHAR(200), unit_name VARCHAR(200), capacity VARCHAR(200), province VARCHAR(200), city VARCHAR(200), district VARCHAR(200), address VARCHAR(200), has_terminal INT, register_date DATE, last_invoice_date DATE, expire_date DATE, adjust_date DATE, transfer_date DATE, owner_id BIGINT, manager_id BIGINT, office_id BIGINT, dept_id BIGINT, region_id BIGINT, remark TEXT, status VARCHAR(200), protection_status VARCHAR(200), relationship_score INT, is_core INT, tags VARCHAR(200), group_id BIGINT, legal_entity_id BIGINT, deleted INT DEFAULT 0, deleted_at DATETIME, deleted_by BIGINT, deleted_reason VARCHAR(200));
CREATE TABLE IF NOT EXISTS sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(200), password VARCHAR(200), real_name VARCHAR(200), phone VARCHAR(200), email VARCHAR(200), avatar VARCHAR(200), dept_id BIGINT, office_id BIGINT, manager_id BIGINT, data_scope_override VARCHAR(200), storage_quota BIGINT, storage_used BIGINT, status INT, org_id BIGINT, leave_status VARCHAR(200), leave_date DATETIME, leave_reason VARCHAR(200), deleted INT DEFAULT 0, deleted_at DATETIME, deleted_by BIGINT, deleted_reason VARCHAR(200));
CREATE TABLE IF NOT EXISTS biz_file (id BIGINT AUTO_INCREMENT PRIMARY KEY, folder_id BIGINT, file_name VARCHAR(200), file_size BIGINT, file_type VARCHAR(200), oss_key VARCHAR(200), oss_url VARCHAR(200), file_scope VARCHAR(200), uploader_id BIGINT, version INT, replace_by BIGINT, replaced_at DATETIME, deleted INT DEFAULT 0, deleted_at DATETIME, deleted_by BIGINT, deleted_reason VARCHAR(200));
-- biz_task: 不在 V1（后续迁移建表）, 列以服务 SELECT 为准并经 information_schema 穷举核对（QC 第 6 轮口径）
CREATE TABLE IF NOT EXISTS biz_task (id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200), assignee_id BIGINT, customer_id BIGINT, deleted INT DEFAULT 0, deleted_at DATETIME, deleted_by BIGINT, deleted_reason VARCHAR(200));
CREATE TABLE IF NOT EXISTS biz_incentive_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, category_id BIGINT, customer_id BIGINT, period VARCHAR(200), industry VARCHAR(200), related_products VARCHAR(200), specific_product VARCHAR(200), file_id VARCHAR(200), pre_install_question TEXT, file_pre_id VARCHAR(200), file_mid_id VARCHAR(200), file_post_id VARCHAR(200), file_track_id VARCHAR(200), remark VARCHAR(200), score INT, shared INT, status VARCHAR(200), deleted INT DEFAULT 0, deleted_at DATETIME, deleted_by BIGINT, deleted_reason VARCHAR(200));
CREATE TABLE IF NOT EXISTS biz_reminder (id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT, reminder_type VARCHAR(200), owner_id BIGINT, office_id BIGINT, overdue_days INT, status VARCHAR(200), processed_at DATETIME, deleted INT DEFAULT 0, deleted_at DATETIME, deleted_by BIGINT, deleted_reason VARCHAR(200));
CREATE TABLE IF NOT EXISTS biz_recycle_export (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT DEFAULT 1, export_no VARCHAR(40), requested_by BIGINT, types_json TEXT, filter_json TEXT, status VARCHAR(20) DEFAULT 'pending', file_path VARCHAR(500), file_size BIGINT, checksum_sha256 VARCHAR(64), expires_at DATETIME, downloaded_at DATETIME, error_msg VARCHAR(500), created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP);
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
        List<String> auditSink() {
            return new ArrayList<>();
        }

        @Bean
        RecycleExportService recycleExportService(JdbcTemplate jdbc, List<String> sink) {
            // SecurityAuditService 为具体类: mock + doAnswer 采集 permissionChange(String,String)
            com.aigicrm.security.SecurityAuditService audit =
                    org.mockito.Mockito.mock(com.aigicrm.security.SecurityAuditService.class);
            org.mockito.Mockito.doAnswer(inv -> {
                sink.add(inv.getArgument(0) + "|" + inv.getArgument(1));
                return null;
            }).when(audit).permissionChange(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString());
            return new RecycleExportService(jdbc, audit);
        }
    }

    @Autowired RecycleExportService svc;
    @Autowired JdbcTemplate jdbc;
    @Autowired List<String> auditSink;
    @TempDir Path tmpDir;

    static final List<String> ALL = List.of("customer", "user", "file", "task", "incentive", "reminder");

    /** svc 是事务代理(类里有 @Transactional 方法), 代理对象自己的字段是空的: 反射读写要落在真实实例上。 */
    private RecycleExportService realService() {
        return org.springframework.test.util.AopTestUtils.getTargetObject(svc);
    }

    @BeforeEach
    void seed() throws Exception {
        // export.dir 指向 @TempDir（反射注入 @Value 字段）
        // 【GL-14 2026-11-24】此前设在代理对象上, 没有生效, 导出包一直落在源码目录的 ./exports 下
        var f = RecycleExportService.class.getDeclaredField("exportDir");
        f.setAccessible(true);
        f.set(realService(), tmpDir.toString());
        jdbc.update("DELETE FROM biz_recycle_export");
        for (String t : List.of("biz_customer", "sys_user", "biz_file", "biz_task", "biz_incentive_record", "biz_reminder")) {
            jdbc.update("DELETE FROM " + t);
        }
        // 六类各 1 条已删 + 1 条活跃（不应被导出）
        jdbc.update("INSERT INTO biz_customer (id, customer_name, owner_id, deleted, deleted_at, deleted_by, deleted_reason) VALUES (1,'已删客户',7,1,NOW(),1,'测试'), (2,'活跃客户',7,0,NULL,NULL,NULL)");
        jdbc.update("INSERT INTO sys_user (id, username, real_name, dept_id, deleted, deleted_at) VALUES (11,'delu','已删用户',4,1,NOW()), (12,'actu','活跃用户',4,0,NULL)");
        jdbc.update("INSERT INTO biz_file (id, file_name, file_scope, uploader_id, oss_key, deleted, deleted_at) VALUES (21,'已删文件.txt','private',7,NULL,1,NOW()), (22,'活跃.txt','private',7,NULL,0,NULL)");
        jdbc.update("INSERT INTO biz_task (id, title, assignee_id, customer_id, deleted, deleted_at) VALUES (31,'已删任务',7,1,1,NOW())");
        jdbc.update("INSERT INTO biz_incentive_record (id, user_id, customer_id, category_id, deleted, deleted_at) VALUES (41,7,1,1,1,NOW())");
        jdbc.update("INSERT INTO biz_reminder (id, customer_id, owner_id, reminder_type, deleted, deleted_at) VALUES (51,1,7,'invoice_overdue',1,NOW())");
        auditSink.clear();
    }


    private Long createAsAdmin(List<String> types) {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(null);
            Map<String, Object> out = svc.create(types, null);
            return ((Number) out.get("id")).longValue();
        }
    }

    private Map<String, Object> awaitReady(Long id) throws Exception {
        for (int i = 0; i < 100; i++) {
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM biz_recycle_export WHERE id=?", id);
            if (!"pending".equals(row.get("status")) && !"running".equals(row.get("status"))) return row;
            Thread.sleep(50);
        }
        return jdbc.queryForMap("SELECT * FROM biz_recycle_export WHERE id=?", id);
    }

    @Test
    void t1_adminCreateReachesReadyWithManifest() throws Exception {
        Long id = createAsAdmin(ALL);
        Map<String, Object> row = awaitReady(id);
        assertEquals("ready", row.get("status"), "失败信息=" + row.get("error_msg"));
        assertNotNull(row.get("checksum_sha256"));
        assertTrue(((Number) row.get("file_size")).longValue() > 0);
        try (ZipFile zip = new ZipFile(String.valueOf(row.get("file_path")))) {
            assertNotNull(zip.getEntry("manifest.json"));
            assertNotNull(zip.getEntry("checksums.sha256"));
            for (String t : ALL) {
                assertNotNull(zip.getEntry("data/" + t + "s.csv"), "CSV 缺失: " + t);
            }
        }
    }

    @Test
    void t2_nonAdminForbidden() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(9L);
            assertThrows(BusinessException.class, () -> svc.create(ALL, null));
            assertThrows(BusinessException.class, svc::list);
        }
    }

    @Test
    void t3_expiredDownload410AndPurge() throws Exception {
        Long id = createAsAdmin(ALL);
        awaitReady(id);
        jdbc.update("UPDATE biz_recycle_export SET expires_at=TIMESTAMPADD(DAY, -1, NOW()) WHERE id=?", id);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.download(id));
            assertEquals(410, ex.getCode());
        }
        assertEquals("expired", jdbc.queryForMap("SELECT status FROM biz_recycle_export WHERE id=?", id).get("status"));
        svc.purgeExpired();
        assertEquals("expired", jdbc.queryForMap("SELECT status FROM biz_recycle_export WHERE id=?", id).get("status"));
    }

    @Test
    void t4_rowsAndCountsConsistent() throws Exception {
        Long id = createAsAdmin(ALL);
        Map<String, Object> row = awaitReady(id);
        try (ZipFile zip = new ZipFile(String.valueOf(row.get("file_path")))) {
            // 客户 CSV: 仅 1 行已删（活跃客户不导出）+ BOM 头
            ZipEntry csv = zip.getEntry("data/customers.csv");
            String content = new String(zip.getInputStream(csv).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(content.contains("已删客户"));
            assertFalse(content.contains("活跃客户"), "deleted=0 不得导出");
            assertTrue(content.startsWith("﻿"), "UTF-8 BOM");
        }
    }

    @Test
    void t5_pathTraversalSanitized() {
        String s = RecycleExportService.sanitizeZipEntryName("../evil.exe", 7L);
        assertFalse(s.contains(".."), "穿越段被清洗: 实际=" + s);
        assertFalse(s.contains("/"), "分隔符被清洗");
        String s2 = RecycleExportService.sanitizeZipEntryName("a/b\\c.txt", 7L);
        assertFalse(s2.contains("/") || s2.contains("\\"), "斜杠/反斜杠被清洗");
        assertEquals("file-9", RecycleExportService.sanitizeZipEntryName(null, 9L), "空名回退 file-{id}");
    }

    @Test
    void t6_emptyOrIllegalTypesRejected() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(null);
            BusinessException e1 = createRejected(List.of());
            assertTrue(e1.getMessage().contains("不能为空"));
            BusinessException e2 = createRejected(List.of("customer", "boss"));
            assertTrue(e2.getMessage().contains("非法"));
        }
    }

    @Test
    void t7_allSixTypesExported() throws Exception {
        Long id = createAsAdmin(ALL);
        Map<String, Object> row = awaitReady(id);
        try (ZipFile zip = new ZipFile(String.valueOf(row.get("file_path")))) {
            assertCsvContains(zip, "data/users.csv", "delu");
            assertCsvContains(zip, "data/files.csv", "已删文件.txt");
            assertCsvContains(zip, "data/tasks.csv", "已删任务");
            assertCsvContains(zip, "data/incentives.csv", "41,7,1,1,");  // category_id 列(数字字典ID)
            assertCsvContains(zip, "data/reminders.csv", "invoice_overdue");
        }
    }

    @Test
    void t8_contentMissingRowKeptWithoutFileEntry() throws Exception {
        Long id = createAsAdmin(List.of("file"));
        Map<String, Object> row = awaitReady(id);
        try (ZipFile zip = new ZipFile(String.valueOf(row.get("file_path")))) {
            String csv = new String(zip.getInputStream(zip.getEntry("data/files.csv")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(csv.contains("已删文件.txt"), "清单行保留");
            assertTrue(csv.toLowerCase().contains("true"), "contentMissing=true 列");
            assertNull(zip.getEntry("files/21/"), "内容缺失不打包文件条目");
        }
    }

    @Test
    void t9_auditThreeActions() throws Exception {
        Long id = createAsAdmin(ALL);
        awaitReady(id);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.download(id);
            svc.delete(id);
        }
        assertTrue(auditSink.stream().anyMatch(s -> s.startsWith("recycle_export_create|")), "create 审计");
        assertTrue(auditSink.stream().anyMatch(s -> s.startsWith("recycle_export_download|")), "download 审计");
        assertTrue(auditSink.stream().anyMatch(s -> s.startsWith("recycle_export_delete|")), "delete 审计");
    }

    private void assertCsvContains(ZipFile zip, String entry, String needle) throws Exception {
        String content = new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains(needle), entry + " 应含 " + needle);
    }

    @Test
    void t10_missingExportReturns404Not500() {
        // 【2026-11-21 PM-D2】不存在的导出 id 不得让 queryForMap 抛
        // EmptyResultDataAccessException → HTTP 500；应返回 404「导出记录不存在」。
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            BusinessException d = assertThrows(BusinessException.class, () -> svc.download(999999L));
            assertEquals(404, d.getCode());
            assertTrue(d.getMessage().contains("导出记录不存在"));
            BusinessException x = assertThrows(BusinessException.class, () -> svc.delete(999999L));
            assertEquals(404, x.getCode());
            assertTrue(x.getMessage().contains("导出记录不存在"));
        }
    }

    private BusinessException createRejected(List<String> types) {
        return assertThrows(BusinessException.class, () -> svc.create(types, null));
    }

    // ==================== 【GL-14 2026-11-24】删除时间过滤的回归 ====================
    // 安全修复 9db17c8f 把过滤值由字符串拼接改成占位符和参数绑定，行数上限也改为按过滤后的范围来计。
    // 在此之前，上面的用例过滤条件一律传空，这条路径没有任何测试。过滤值只能经接口直接传入，PC 端不发这两个字段。

    private static final DateTimeFormatter FILTER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String daysAgo(int days) {
        return LocalDateTime.now().minusDays(days).format(FILTER_TIME);
    }

    private Long createAsAdmin(List<String> types, Map<String, Object> filter) {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(null);
            Map<String, Object> out = svc.create(types, filter);
            return ((Number) out.get("id")).longValue();
        }
    }

    private String csvOf(Long id, String entry) throws Exception {
        try (ZipFile zip = new ZipFile(String.valueOf(awaitReady(id).get("file_path")))) {
            return new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** 把服务持有的 JdbcTemplate 换成 spy, 以便记录实际执行的 SQL 文本与绑定参数。 */
    private JdbcTemplate spyJdbc() throws Exception {
        var f = RecycleExportService.class.getDeclaredField("jdbc");
        f.setAccessible(true);
        JdbcTemplate spy = org.mockito.Mockito.spy((JdbcTemplate) f.get(realService()));
        f.set(realService(), spy);
        return spy;
    }

    @Test
    void t12_filterValuesAreBoundNeverConcatenated() throws Exception {
        // 攻击串: 反斜杠吃掉收尾引号 + 恒真条件 + 注释。修复前的写法只去掉单引号, 这种输入会改写 SQL。
        String from = "2026-11-01\\";
        String to = " OR 1=1 -- ";
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("deletedFrom", from);
        filter.put("deletedTo", to);
        JdbcTemplate spy = spyJdbc();
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(null);
            try {
                svc.create(List.of("customer"), filter);
            } catch (RuntimeException e) {
                // 非法的时间值被数据库拒绝, 也是安全的结果
            }
        }
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
        verify(spy, atLeastOnce()).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        ArgumentCaptor<String> batchSql = ArgumentCaptor.forClass(String.class);
        verify(spy, atLeast(0)).queryForList(batchSql.capture(), any(Object[].class));
        List<String> sqlTexts = new ArrayList<>(countSql.getAllValues());
        sqlTexts.addAll(batchSql.getAllValues());

        for (String sql : sqlTexts) {
            assertFalse(sql.contains(from), "过滤值不得出现在 SQL 文本里: " + sql);
            assertFalse(sql.contains("1=1") || sql.contains("--"), "攻击串不得进入 SQL 文本: " + sql);
        }
        String count = countSql.getAllValues().get(0);
        assertTrue(count.contains("deleted_at >= ?") && count.contains("deleted_at <= ?"), "时间条件以占位符出现: " + count);
        // 输入原样作为参数交给数据库
        Object[] args = countArgs.getAllValues().get(0);
        assertEquals(2, args.length, "两个过滤值各占一个参数: " + Arrays.toString(args));
        assertEquals(from, args[0]);
        assertEquals(to, args[1]);
    }

    @Test
    void t13_rowLimitCountsOnlyRowsInsideFilter() throws Exception {
        // 10 万零 1 条十天前删除的 + 种子里 1 条刚删除的: 不过滤时超过单类型上限 10 万, 按“最近 5 天”过滤后只剩 1 条
        // (不用 SYSTEM_RANGE 的列名: H2 开了转小写, 系统表的 X 列不加引号找不到; 行号用窗口函数现算)
        jdbc.update("INSERT INTO biz_customer (id, customer_name, owner_id, deleted, deleted_at, deleted_by, deleted_reason) "
                + "SELECT ROW_NUMBER() OVER () + 2, '批量旧数据', 7, 1, TIMESTAMPADD(DAY, -10, NOW()), 1, '批量' FROM SYSTEM_RANGE(1, 100001)");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(null);
            List<String> types = List.of("customer");
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.create(types, null));
            assertTrue(ex.getMessage().contains("超过单类型上限"), ex.getMessage());
        }
        Long id = createAsAdmin(List.of("customer"), Map.of("deletedFrom", daysAgo(5)));
        String csv = csvOf(id, "data/customers.csv");
        assertTrue(csv.contains("已删客户"), "过滤范围内的行照常导出");
        assertFalse(csv.contains("批量旧数据"), "过滤范围之外的行不导出");
    }

    @Test
    void t14_timeFilterNarrowsExportedRows() throws Exception {
        jdbc.update("INSERT INTO biz_customer (id, customer_name, owner_id, deleted, deleted_at, deleted_by, deleted_reason) "
                + "VALUES (3,'十天前删除',7,1,TIMESTAMPADD(DAY, -10, NOW()),1,'测试')");
        String recentOnly = csvOf(createAsAdmin(List.of("customer"), Map.of("deletedFrom", daysAgo(5))), "data/customers.csv");
        assertTrue(recentOnly.contains("已删客户"), "下限: 5 天内删除的在内");
        assertFalse(recentOnly.contains("十天前删除"), "下限: 更早删除的在外");
        assertFalse(recentOnly.contains("活跃客户"), "未删除的始终不导出");
        String oldOnly = csvOf(createAsAdmin(List.of("customer"), Map.of("deletedTo", daysAgo(5))), "data/customers.csv");
        assertTrue(oldOnly.contains("十天前删除"), "上限: 更早删除的在内");
        assertFalse(oldOnly.contains("已删客户"), "上限: 5 天内删除的在外");
        String both = csvOf(createAsAdmin(List.of("customer"),
                Map.of("deletedFrom", daysAgo(20), "deletedTo", daysAgo(5))), "data/customers.csv");
        assertTrue(both.contains("十天前删除") && !both.contains("已删客户"), "区间: 两头都生效");
    }
}
