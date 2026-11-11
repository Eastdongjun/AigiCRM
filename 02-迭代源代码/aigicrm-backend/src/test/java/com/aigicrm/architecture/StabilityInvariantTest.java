package com.aigicrm.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 【稳定性不变量】防崩溃第一道防线(2026-09-24):把踩过的事故写成机器断言,构建期自动执行。
 * 每条不变量注释标明"源自何事故"——修改断言前先读事故,理解规则为何存在。
 * 模式沿用 PermissionMatrixTest 的源码扫描;脆字符串断言是有意的:规则被改动时测试变红,
 * 迫使改动者有意识地更新断言(而不是静默漂移)。
 */
class StabilityInvariantTest {

    private static final Path BACKEND_ROOT = Paths.get("src/main/java").toAbsolutePath().normalize();

    private static String readSource(String relative, String label) {
        try {
            return Files.readString(BACKEND_ROOT.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取 " + label + ": " + e.getMessage(), e);
        }
    }

    /**
     * INV-MIG-001|迁移 SQL 禁止按硬编码行 id 定位(祖父条款:历史文件 allowlist)
     * 源自事故 V2026.09.21.1:按测试库实测 id 写 DELETE,legacy 生产库 id 错位 → 误删 task 权限行 → 全员 403。
     * 迁移必须按 permission 码/唯一键定位;历史违规不可改(flyway 禁改历史),新违规在此拦截。
     */
    @Test
    void migrations_mustNotLocateRowsByHardcodedId() throws IOException {
        // 2026-09-24 基线实测的存量违规(含 21.1 事故本身);仅此豁免,新增即红。
        // 注:V21 的跨行 IN 列表是本测试首跑抓出的——逐行 grep 会漏跨行,故用整文件匹配。
        List<String> grandfathered = List.of(
                "V12__cleanup_role_menu_directory_rows.sql",
                "V14__restore_top_level_group_menu_bindings.sql",
                "V17__bind_permission_codes_to_roles.sql",
                "V18__incentive_grant_table.sql",
                "V21__cleanup_dead_menu_permissions.sql",
                "V25__add_finance_view_permission.sql",
                "V2026.09.11.3__restore_file_mutation_permission_bindings.sql",
                "V2026.09.21.1__menu_hygiene_dedup.sql");
        // WHERE/AND/OR 语境下 按 xxx_id = 数字 或 IN (数字 定位行(剥离 -- 注释行)
        Pattern rowIdLocator = Pattern.compile(
                "(?i)(?:where|and|or)\\s+(?:[a-z_]+\\.)?(?:[a-z_]*_id|id)\\s*(?:=|in\\s*\\()\\s*\\d");
        Path migrationDir = Paths.get("src/main/resources/db/migration").toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                String name = p.getFileName().toString();
                if (grandfathered.contains(name)) continue;
                StringBuilder effective = new StringBuilder();
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (!line.trim().startsWith("--")) effective.append(line).append('\n');
                }
                if (rowIdLocator.matcher(effective).find()) violations.add(name);
            }
        }
        assertTrue(violations.isEmpty(),
                "INV-MIG-001 违规(迁移按硬编码行 id 定位,历史事故来源): " + violations
                        + " —— 改按 permission 码/唯一键定位;若为历史基线遗漏请加入祖父清单并注明");
    }

    /**
     * INV-MIG-002|迁移 INSERT 禁止显式行 id(WHERE 定位之外的盲区补丁)
     * 源自 2026-11-11 审计 N2: V2026.11.11.2 首版 "SELECT 9047+n.i" 显式 id 插入菜单行,
     * 既违背自身"零硬编码 id"承诺, 又在"某行缺失但 id 段被占"的库上主键冲突; 而 INV-MIG-001
     * 的 WHERE/AND/OR 语境正则对 INSERT 列表里的显式 id 零命中(机器盲区)。
     * 例外:唯一种子 V2026.09.11.6 的确定性字典 id(9010..9120 段)是刻意设计, 祖父豁免。
     */
    @Test
    void migrations_mustNotInsertExplicitMenuRowIds() throws IOException {
        List<String> grandfathered = List.of(
                "V2026.09.11.6__seed_permission_dictionary_and_roles.sql");
        // INSERT INTO sys_menu/sys_role_menu ... SELECT <3位以上数字>(, 或 +) —— SELECT 列表以
        // 行 id 字面量开头(含 "9047 + n.i" 算式形态); COALESCE(...) 等函数开头不误伤。
        Pattern explicitIdInsert = Pattern.compile(
                "(?is)insert\\s+into\\s+`?(sys_menu|sys_role_menu)`?[^;]{0,400}?select\\s+\\d{3,}\\s*(?:,|\\+)");
        Path migrationDir = Paths.get("src/main/resources/db/migration").toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                String name = p.getFileName().toString();
                if (grandfathered.contains(name)) continue;
                StringBuilder effective = new StringBuilder();
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (!line.trim().startsWith("--")) effective.append(line).append('\n');
                }
                if (explicitIdInsert.matcher(effective).find()) violations.add(name);
            }
        }
        assertTrue(violations.isEmpty(),
                "INV-MIG-002 违规(迁移 INSERT 显式行 id, 与 id 段漂移的库冲突): " + violations
                        + " —— id 交给 AUTO_INCREMENT, 行按 permission 自然键定位; 种子外新增即红");
    }

    /**
     * INV-TENANT-001|租户 fail-closed 语义不可移除
     * 源自事故 2026-09-23 NM005:非 admin 无集团 → tenant_id = NULL 恒假 → 查询恒空(admin 豁免曾掩盖全员不可见)。
     * NullValue 返回与 admin 豁免是这条防线的两根柱子,动它必须先过三态影响评估。
     */
    @Test
    void tenantInterceptor_keepsFailClosedSemantics() {
        String src = readSource("com/aigicrm/config/MybatisPlusConfig.java", "MybatisPlusConfig");
        assertTrue(src.contains("return new net.sf.jsqlparser.expression.NullValue();"),
                "INV-TENANT-001: 空集团必须返回 NullValue(tenant_id = NULL 恒假,fail-closed)");
    }

    /**
     * INV-AUDIT-001|越权 403 必须落结构化安全审计
     * 源自排查之痛:曾误以为 403 不落日志(文档错误,2026-09-24 更正);BS-A09 的统一出口是审计完整性的关键接线。
     */
    @Test
    void accessDenied_isAudited() {
        String src = readSource("com/aigicrm/config/SecurityConfig.java", "SecurityConfig");
        assertTrue(src.contains(".accessDeniedHandler(this::auditAccessDenied)"),
                "INV-AUDIT-001: 403 必须经 auditAccessDenied 汇入安全审计(BS-A09)");
    }

    /**
     * INV-AUDIT-002|关键删除必须留审计快照(裁决 2026-09-24:删除必须留记录)
     * 客户/发票/回款三类删除各有快照调用;新增可删除的实体时在此补钉。
     */
    @Test
    void deletions_leaveAuditTrail() {
        String src = readSource("com/aigicrm/module/customer/service/CustomerService.java", "CustomerService");
        assertTrue(src.contains("logRecordDeletion(\"客户\", \"删除客户\""), "客户删除留痕");
        assertTrue(src.contains("logFinanceDeletion(\"发票\""), "发票删除留痕");
        assertTrue(src.contains("logFinanceDeletion(\"回款\""), "回款删除留痕");
    }

    /**
     * INV-FINANCE-001|最近开票日=纯推导字段,全库唯一写点在 CustomerService 的五处固定位置
     * 源自裁决 2026-09-24:人工直改与发票重算双写互相覆盖;允许 create 清空/protect 回填旧值/refresh 推导。
     * 【2026-10-10 裁决演进】逐笔开票录入入口下线后新增第 4 写点: saveStageAmount 阶段总额接力
     * (开票额>0 时以阶段末日期前移刷新, 只前进不后退, 不晚于今天)——保护期由阶段录入接力。
     * 【2026-11-02 裁决B 演进】新增第 5 写点: applyClaimReset 捞取重置显式置空
     * (捞取=新周期重新起算, 老票基准不延续; 由 SeaService.completeClaim 唯一调用)。
     * Controller 中的唯一出现必须是导入模板样例行(sample),不得出现在任何真实客户写路径。
     */
    @Test
    void lastInvoiceDate_singleWriterInService() {
        String service = readSource("com/aigicrm/module/customer/service/CustomerService.java", "CustomerService");
        String controller = readSource("com/aigicrm/module/customer/controller/CustomerController.java", "CustomerController");
        assertEquals(5, countOccurrences(service, "setLastInvoiceDate"),
                "INV-FINANCE-001: CustomerService 内写点应恰为 5(create 清空/protect 回填/refresh 推导/阶段总额接力/捞取重置置空),多了=出现绕过推导的写入");
        assertEquals(1, countOccurrences(controller, "setLastInvoiceDate"));
        assertEquals(1, countOccurrences(controller, "sample.setLastInvoiceDate"),
                "Controller 唯一出现必须是导入模板样例行");
    }

    /**
     * INV-SCOPE-001|统计客户排行口径单源:三类排行(四项服务/客情/六项指标)统一客户可见范围基线
     * 演进: 2026-09-24 分享制单源(applyShareVisibility 复用) → 【2026-11-02 用户裁决选项一】三类排行
     * 统一"客户可见范围"(visibleCustomers 同一入口, 与客情/指标 G2 同基线)——统计侧不得自造过滤,
     * 也不得回退分享制(单页丰富度让位于全系统数据边界一致)。
     */
    @Test
    void incentiveVisibility_singleSourceOfTruth() {
        String src = readSource("com/aigicrm/module/statistics/service/StatisticsQueryService.java", "StatisticsQueryService");
        assertTrue(src.contains("visibleCustomers(customerService.getAllActiveCustomers())"),
                "INV-SCOPE-001: 客户排行三类必须统一走 visibleCustomers 客户可见范围基线(2026-11-02 裁决选项一)");
        assertTrue(!src.contains("applyShareVisibility(recordWrapper)"),
                "INV-SCOPE-001: 统计客户排行不得回退分享制过滤");
    }

    /**
     * INV-DEDUP-001|到期提醒去重判据双写方同源
     * 源自事故 2026-09-23:凌晨任务与页面补生成两套去重口径 → 401 条 pending 积压。
     * 两个写入方(ReminderService/CustomerExpireTask)必须保持同一判据(pending 存在或今日已处理不建)。
     */
    @Test
    void expiringDedup_bothWritersSameRule() {
        String reminder = readSource("com/aigicrm/module/reminder/service/ReminderService.java", "ReminderService");
        String task = readSource("com/aigicrm/task/CustomerExpireTask.java", "CustomerExpireTask");
        assertTrue(reminder.contains("getProcessedAt, todayStart") || reminder.contains("BizReminder::getProcessedAt"),
                "INV-DEDUP-001: ensureExpiring 判据须含 processedAt(今日已处理不建)");
        assertTrue(task.contains("BizReminder::getProcessedAt"),
                "INV-DEDUP-001: 凌晨任务判据须与 ensureExpiring 同源(processedAt 口径)");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
