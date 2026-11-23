package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomerIndicator;
import com.aigicrm.module.customer.entity.BizCustomerStageAmount;
import com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper;
import com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户五项指标与核心客户判定。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出。核心三态只由本类 recalculate 写入；
 * 阶段开票/回款录入仍留在门面，判定阈值/纯函数由本类提供。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerIndicatorService {

    private final BizCustomerIndicatorMapper indicatorMapper;
    private final BizCustomerStageAmountMapper stageAmountMapper;
    private final SysConfigService configService;

    public BizCustomerIndicator getIndicator(Long customerId) {
        return indicatorMapper.selectOne(
                new LambdaQueryWrapper<BizCustomerIndicator>()
                        .eq(BizCustomerIndicator::getCustomerId, customerId));
    }

    @Transactional
    public void saveIndicator(Long customerId, BizCustomerIndicator data) {
        data.setCustomerId(customerId);
        data.setUpdatedBy(SecurityUtils.getCurrentUserId());
        // 【L2 2026-10-12 测试报告】五项指标每项只允许 0 到 3 的四档，前端只给四个选项；
        // 直连接口可越界入库，污染排行和核心判定的求和口径
        validateIndicatorScores(data);
        // 【裁决 2026-10-10】核心三态只能由核心判定写入；
        // 更新时跳过空字段，显式置空即屏蔽外部输入对核心标记的覆盖
        data.setCoreType(null);
        data.setIsCore(null);

        BizCustomerIndicator existing = getIndicator(customerId);
        if (existing != null) {
            data.setId(existing.getId());
            indicatorMapper.updateById(data);
        } else {
            indicatorMapper.insert(data);
        }
        recalculateCoreStatus(customerId);
    }

    /** 【L2】五项指标分值域校验: 每项四档(0-3), null 跳过(updateById 不覆盖 null 字段) */
    private void validateIndicatorScores(BizCustomerIndicator data) {
        if (outOfRange(data.getRelationshipScore()) || outOfRange(data.getIndicator02())
                || outOfRange(data.getIndicator03()) || outOfRange(data.getIndicator04())
                || outOfRange(data.getIndicator05())) {
            throw new BusinessException("五项指标分值须在 0-3 之间(每项四档)");
        }
    }

    private boolean outOfRange(Integer v) {
        return v != null && (v < 0 || v > 3);
    }

    @Transactional
    public void saveRelationshipScore(Long customerId, int score) {
        // 【L2 2026-10-12 测试报告】与 saveIndicator 同口径的 0-3 域校验
        if (score < 0 || score > 3) {
            throw new BusinessException("客情·商务关系深度分值须在 0-3 之间");
        }
        BizCustomerIndicator existing = getIndicator(customerId);
        if (existing != null) {
            existing.setRelationshipScore(score);
            existing.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.updateById(existing);
        } else {
            BizCustomerIndicator ind = new BizCustomerIndicator();
            ind.setCustomerId(customerId);
            ind.setRelationshipScore(score);
            ind.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.insert(ind);
        }
        // 【PR-17 2026-09-19 议题三裁决"即时计算"】客情分是核心客户五项之一,
        // 只改客情分跨过 12 分门槛时核心标记必须同步刷新——此前 saveIndicator 触发
        // 重算而本方法不触发,是口径双轨(诊断 F13 额外静态缺口,业务会裁决后为必做)。
        recalculateCoreStatus(customerId);
    }

    /** 【2026-11-03 用户令门槛配置化】核心客户判定双门槛 sys_config 键(万元):
     *  minor=争核心门槛(默认 50), major=真核心门槛(默认 100); seed 见迁移 V2026.11.03.1。
     *  键名跨包共用(StatisticsQueryService/SysConfigController), 判定与统计同源。 */
    public static final String CORE_MINOR_WAN_KEY = "customer.core.minor.wan";
    public static final String CORE_MAJOR_WAN_KEY = "customer.core.major.wan";
    private static final BigDecimal CORE_MINOR_WAN_DEFAULT = new BigDecimal("50");
    private static final BigDecimal CORE_MAJOR_WAN_DEFAULT = new BigDecimal("100");

    /** 读双门槛配置; 脏值/非正数回退默认(与 getConfigInt 同款回退模式, 门槛永不因配置脏值失效)。 */
    BigDecimal[] coreThresholds() {
        return new BigDecimal[]{
                configDecimal(CORE_MINOR_WAN_KEY, CORE_MINOR_WAN_DEFAULT),
                configDecimal(CORE_MAJOR_WAN_KEY, CORE_MAJOR_WAN_DEFAULT)};
    }

    private BigDecimal configDecimal(String key, BigDecimal defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                BigDecimal parsed = new BigDecimal(val.trim());
                if (parsed.signum() > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值:解析失败/非正数返回 defaultValue(注明吞因,消除 S1166)
            }
        }
        return defaultValue;
    }

    /**
     * 【裁决 2026-10-10 纯金额三态 + 2026-11-03 双门槛配置化】核心客户三态判定(五项评分不参与, 保留为客情参考)。
     * 年度口径 = 最近有数据年(全年行有数据时优先取全年行, 否则上半年+下半年合计):
     *   core  = 真核心: 基准年开票、回款均 >= 真核心门槛(customer.core.major.wan, 默认 100 万);
     *   quasi = 争核心: 均达标争核心门槛(customer.core.minor.wan, 默认 50 万), 未双达真核心门槛;
     *   ''    = 非核心: 任一低于争核心门槛(含无任何有数据阶段行; 开票、回款均 <= 0 的行不算数据)。
     * 门槛由管理员在系统配置修改, 保存后由 SysConfigController 触发 recalculateAllCoreStatuses 全量刷新。
     * 与 StatisticsQueryService.coreTypeOf / 迁移 V2026.11.03.1 回填同口径, 改动须三处同步。
     */
    private String computeCoreType(Long customerId) {
        List<BizCustomerStageAmount> rows = stageAmountMapper.selectList(
                new LambdaQueryWrapper<BizCustomerStageAmount>()
                        .eq(BizCustomerStageAmount::getCustomerId, customerId));
        BigDecimal[] thresholds = coreThresholds();
        return computeCoreTypeFromRows(rows, thresholds[0], thresholds[1]);
    }

    /** computeCoreType 的纯函数核(单户与全量刷新共用): rows = 该客户全部阶段行, 门槛为参数不落死值。 */
    static String computeCoreTypeFromRows(List<BizCustomerStageAmount> rows, BigDecimal minorWan, BigDecimal majorWan) {
        int baseYear = baseYearOf(rows);
        if (baseYear == 0) return "";
        BigDecimal[] amounts = annualAmountsOf(rows, baseYear);
        return coreTypeOf(amounts[0], amounts[1], minorWan, majorWan);
    }

    private static int baseYearOf(List<BizCustomerStageAmount> rows) {
        return rows.stream()
                .filter(r -> isPositiveAmount(r.getInvoiceAmount()) || isPositiveAmount(r.getPaymentAmount()))
                .mapToInt(BizCustomerStageAmount::getPeriodYear).max().orElse(0);
    }

    private static BigDecimal[] annualAmountsOf(List<BizCustomerStageAmount> rows, int baseYear) {
        BizCustomerStageAmount fy = rows.stream()
                .filter(r -> r.getPeriodYear() == baseYear && "FY".equals(r.getPeriodType())
                        && (isPositiveAmount(r.getInvoiceAmount()) || isPositiveAmount(r.getPaymentAmount())))
                .findFirst().orElse(null);
        if (fy != null) return new BigDecimal[]{fy.getInvoiceAmount(), fy.getPaymentAmount()};
        return rows.stream()
                .filter(r -> r.getPeriodYear() == baseYear && ("H1".equals(r.getPeriodType()) || "H2".equals(r.getPeriodType())))
                .reduce(new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO},
                        (acc, r) -> {
                            acc[0] = acc[0].add(r.getInvoiceAmount() == null ? BigDecimal.ZERO : r.getInvoiceAmount());
                            acc[1] = acc[1].add(r.getPaymentAmount() == null ? BigDecimal.ZERO : r.getPaymentAmount());
                            return acc;
                        },
                        (a, b) -> a);
    }

    private static String coreTypeOf(BigDecimal invoice, BigDecimal payment, BigDecimal minorWan, BigDecimal majorWan) {
        boolean invoiceMet = invoice != null && invoice.compareTo(majorWan) >= 0;
        boolean paymentMet = payment != null && payment.compareTo(majorWan) >= 0;
        if (invoiceMet && paymentMet) return "core";
        boolean invoiceQuasi = invoice != null && invoice.compareTo(minorWan) >= 0;
        boolean paymentQuasi = payment != null && payment.compareTo(minorWan) >= 0;
        return invoiceQuasi && paymentQuasi ? "quasi" : "";
    }

    static boolean isPositiveAmount(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /** 核心三态落库(含 is_core 兼容列同步)。判定与五项解耦后, 无指标行的客户也自动建行落标记。 */
    void recalculateCoreStatus(Long customerId) {
        String coreType = computeCoreType(customerId);
        BizCustomerIndicator indicator = getIndicator(customerId);
        if (indicator == null) {
            indicator = new BizCustomerIndicator();
            indicator.setCustomerId(customerId);
            indicator.setCoreType(coreType);
            indicator.setIsCore("core".equals(coreType) ? 1 : 0);
            indicator.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.insert(indicator);
            return;
        }
        indicator.setCoreType(coreType);
        indicator.setIsCore("core".equals(coreType) ? 1 : 0);
        indicatorMapper.updateById(indicator);
    }

    /**
     * 【2026-11-03 用户令"改门槛后自动刷新"】按当前双门槛配置全量刷新核心三态落库。
     * 触发: SysConfigController 保存双门槛配置变化后调用; 幂等可重放(只写有变化的行)。
     * 全量阶段行一次载入内存分组(数百户规模无需分页); 无指标行的客户自动建行、
     * 已无阶段数据的老行归位非核心(与单客户 recalculateCoreStatus 同语义)。
     * @return 发生变化的客户数
     */
    @Transactional
    public int recalculateAllCoreStatuses() {
        BigDecimal[] thresholds = coreThresholds();
        Map<Long, List<BizCustomerStageAmount>> rowsByCustomer = stageAmountMapper.selectList(null).stream()
                .filter(r -> r.getCustomerId() != null)
                .collect(Collectors.groupingBy(BizCustomerStageAmount::getCustomerId));
        Map<Long, BizCustomerIndicator> indicatorByCustomer = indicatorMapper.selectList(null).stream()
                .filter(i -> i.getCustomerId() != null)
                .collect(Collectors.toMap(BizCustomerIndicator::getCustomerId, i -> i, (a, b) -> a));
        Long userId = SecurityUtils.getCurrentUserId();
        int changed = 0;
        for (var entry : rowsByCustomer.entrySet()) {
            String coreType = computeCoreTypeFromRows(entry.getValue(), thresholds[0], thresholds[1]);
            changed += upsertCoreStatus(indicatorByCustomer.get(entry.getKey()), entry.getKey(), coreType, userId);
        }
        // 阶段数据已被清空的老行(如阶段行全删): 归位非核心, 防旧标签残留
        for (var entry : indicatorByCustomer.entrySet()) {
            if (rowsByCustomer.containsKey(entry.getKey())) continue;
            changed += upsertCoreStatus(entry.getValue(), entry.getKey(), "", userId);
        }
        return changed;
    }

    /** 单户核心三态落库(缺行插入/有变化更新, 返回 1=有变化 0=无变化)。 */
    private int upsertCoreStatus(BizCustomerIndicator indicator, Long customerId, String coreType, Long userId) {
        int isCore = "core".equals(coreType) ? 1 : 0;
        if (indicator == null) {
            indicator = new BizCustomerIndicator();
            indicator.setCustomerId(customerId);
            indicator.setCoreType(coreType);
            indicator.setIsCore(isCore);
            indicator.setUpdatedBy(userId);
            indicatorMapper.insert(indicator);
            return 1;
        }
        if (Objects.equals(indicator.getCoreType(), coreType) && Objects.equals(indicator.getIsCore(), isCore)) return 0;
        indicator.setCoreType(coreType);
        indicator.setIsCore(isCore);
        indicator.setUpdatedBy(userId);
        indicatorMapper.updateById(indicator);
        return 1;
    }

}
