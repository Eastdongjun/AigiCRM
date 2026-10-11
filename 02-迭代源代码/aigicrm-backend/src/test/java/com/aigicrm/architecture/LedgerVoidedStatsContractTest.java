package com.aigicrm.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 【PR-18 统计口径源码契约】已作废(voided)台账不得计入任何统计口径。
 *
 * 口径全图(2026-09-20 活体盘点后固化, 防条件被误删回归; 2026-10-10 阶段总额制演进见下):
 *  1. 聚合 SQL 层: BizInvoiceMapper/BizPaymentMapper 的 SUM/COUNT 原生 SQL 带 status &lt;&gt; 'voided'
 *     (客户详情"历史明细"汇总仍用)
 *  2. 统计服务层: 【2026-10-10 裁决】指标统计金额源换阶段总额表(annualAmountsByCustomer),
 *     逐笔聚合退役——见 statisticsAggregationUsesStageAmountSource
 *  3. 保护期驱动: CustomerService 最近开票日两处查询(摘要+作废/删票回算)带 voided 排除——
 *     作废发票不得延长保护期(历史发票仍在库, 该守卫保留)
 *  4. 删除守卫刻意**保留**计数 voided(台账留痕不放开物理删除), 见 CustomerService 删除守卫注释
 */
class LedgerVoidedStatsContractTest {

    private String readSource(String relative, String label) {
        Path src = Paths.get(relative).toAbsolutePath().normalize();
        assertTrue(Files.exists(src), label + " 源文件不存在: " + src);
        try {
            return Files.readString(src);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + label + " 源码失败", e);
        }
    }

    private void assertExcludesVoided(String body, String label, int expectedOccurrences) {
        long n = body.split("voided", -1).length - 1;
        assertTrue(n >= expectedOccurrences,
                label + " 的 voided 排除口径缺失(期望出现≥" + expectedOccurrences + "处, 实际" + n + "处)");
    }

    @Test
    void mapperAggregateSqlExcludesVoided() {
        String invoice = readSource(
                "src/main/java/com/aigicrm/module/customer/mapper/BizInvoiceMapper.java", "BizInvoiceMapper");
        String payment = readSource(
                "src/main/java/com/aigicrm/module/customer/mapper/BizPaymentMapper.java", "BizPaymentMapper");
        // 每个 Mapper 的 SUM+COUNT 两条聚合 SQL 都要带排除 → 源文件内至少 2 处
        assertExcludesVoided(invoice, "BizInvoiceMapper 聚合 SQL", 2);
        assertExcludesVoided(payment, "BizPaymentMapper 聚合 SQL", 2);
    }

    /**
     * 【裁决 2026-10-10】指标统计金额源=阶段总额表(biz_customer_stage_amount), 不再逐笔聚合发票/回款
     * (逐笔录入入口同批下线改只读历史)。旧"聚合排除 voided"契约随之退役——阶段表编辑制无作废概念;
     * 本钉防止金额源回退到逐笔聚合(那会重新引入 voided 口径与"逐笔 vs 阶段"双轨)。
     */
    @Test
    void statisticsAggregationUsesStageAmountSource() {
        String body = readSource(
                "src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java",
                "StatisticsQueryService");
        assertFalse(body.contains(".ne(BizInvoice::getStatus, \"voided\")"),
                "指标统计不得再逐笔聚合发票(金额源已换阶段总额表)");
        assertFalse(body.contains(".ne(BizPayment::getStatus, \"voided\")"),
                "指标统计不得再逐笔聚合回款(金额源已换阶段总额表)");
        assertTrue(body.contains("annualAmountsByCustomer"),
                "指标统计金额源必须是阶段总额年度口径(annualAmountsByCustomer)");
    }

    @Test
    void latestInvoiceQueriesExcludeVoidedForProtectionPeriod() {
        String body = readSource(
                "src/main/java/com/aigicrm/module/customer/service/CustomerService.java", "CustomerService");
        // 摘要最近开票 + 作废/删票回算最近开票, 两处都不得由 voided 驱动保护期
        assertTrue(body.contains(".ne(BizInvoice::getStatus, \"voided\")"),
                "最近开票日查询必须排除 voided(保护期不受作废票驱动)");
    }
}
