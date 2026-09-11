package com.aigicrm.module.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.service.ApprovalService;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerIndicator;
import com.aigicrm.module.customer.entity.BizCustomerLog;
import com.aigicrm.module.customer.entity.BizInvoice;
import com.aigicrm.module.customer.entity.BizPayment;
import com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper;
import com.aigicrm.module.customer.mapper.BizCustomerLogMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizInvoiceMapper;
import com.aigicrm.module.customer.mapper.BizPaymentMapper;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计查询服务
 *
 * 【需求】统计看板/日报/周报/指标排行/经营分析/团队动态的数据查询与组装
 * 【方法】聚合多个模块 Mapper 查询后在内存组装，不跨服务写操作
 * 【业务规则】数据范围由 customerService/seaService/approvalService 感知；
 *             指标达标阈值 100 万元；核心客户=指标分>=12 且开票、回款均达标
 * 【注意】只读服务，不落库；调用方（Controller）负责权限注解
 */
@Service
@RequiredArgsConstructor
public class StatisticsQueryService {

    private static final java.math.BigDecimal CORE_THRESHOLD = new java.math.BigDecimal("100");

    static boolean isCoreCustomer(int score, java.math.BigDecimal invoiceTotal,
                                  java.math.BigDecimal paymentTotal) {
        return score >= 12
                && invoiceTotal.compareTo(CORE_THRESHOLD) >= 0
                && paymentTotal.compareTo(CORE_THRESHOLD) >= 0;
    }

    static int indicatorScore(BizCustomerIndicator indicator) {
        if (indicator == null) return 0;
        return safeValue(indicator.getRelationshipScore()) + safeValue(indicator.getIndicator02())
                + safeValue(indicator.getIndicator03()) + safeValue(indicator.getIndicator04())
                + safeValue(indicator.getIndicator05());
    }

    private static int safeValue(Integer value) {
        return value != null ? value : 0;
    }

    static List<LocalDate> monthDaysThrough(LocalDate today) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = today.withDayOfMonth(1);
        while (!cursor.isAfter(today)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    static List<LocalDate> monthWeekStartsThrough(LocalDate today) {
        List<LocalDate> starts = new ArrayList<>();
        LocalDate cursor = today.withDayOfMonth(1);
        while (!cursor.isAfter(today)) {
            starts.add(cursor);
            cursor = cursor.plusDays(7);
        }
        return starts;
    }

    private final CustomerService customerService;
    private final SeaService seaService;
    private final ApprovalService approvalService;
    private final BizCustomerMapper customerMapper;
    private final BizCustomerLogMapper customerLogMapper;
    private final BizIncentiveRecordMapper incentiveRecordMapper;
    private final BizIncentiveCategoryMapper incentiveCategoryMapper;
    private final BizFileMapper fileMapper;
    private final BizCustomerIndicatorMapper indicatorMapper;
    private final BizInvoiceMapper invoiceMapper;
    private final BizPaymentMapper paymentMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysLogMapper sysLogMapper;
    private final BizApprovalMapper approvalMapper;

    /**
     * 看板汇总
     * 【需求】首页统计卡片：客户/公海/审批/到期提醒
     * 【方法】委托各模块 Service 做数据范围感知统计
     * 【业务规则】到期=expireDate 距今天数；todayExpiring 为当日到期且仍在客户范围内的数量
     */
    public Map<String, Object> dashboard() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> data = new HashMap<>();

        data.put("myCustomers", customerService.countByScope());
        data.put("inProtection", customerService.countInProtectionByScope());
        data.put("expiring", customerService.countExpiringByScope());
        data.put("expired", customerService.countExpiredByScope());
        data.put("seaStats", seaService.statistics());
        data.put("approvalStats", approvalService.statistics(userId));

        LocalDate today = LocalDate.now();
        List<BizCustomer> expiringList = customerService.getExpiringByScope(5);
        List<Map<String, Object>> expiringDetails = new ArrayList<>();
        for (BizCustomer c : expiringList) {
            Map<String, Object> item = new HashMap<>();
            item.put("customerName", c.getCustomerName());
            item.put("officeName", c.getOfficeName() != null ? c.getOfficeName() : "");
            long days = c.getExpireDate() != null ?
                    java.time.temporal.ChronoUnit.DAYS.between(today, c.getExpireDate()) : 0;
            item.put("daysLeft", days);
            expiringDetails.add(item);
        }
        data.put("expiringDetails", expiringDetails);
        data.put("todayExpiring", customerService.countExpiringTodayByScope());
        data.put("monthNew", customerService.countNewThisMonth());

        return data;
    }

    /**
     * 按天统计（本月）
     * 【需求】日报：本月每天新增客户与文件上传数
     * 【方法】客户走 customerService.dailyNewCustomers；文件按天轮询 fileMapper 计数
     */
    public Map<String, Object> dailyStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("customerDays", customerService.dailyNewCustomers());

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> fileDays = new ArrayList<>();
        for (LocalDate cursor : monthDaysThrough(today)) {
            LambdaQueryWrapper<BizFile> fw = new LambdaQueryWrapper<>();
            fw.ge(BizFile::getCreatedAt, cursor.atStartOfDay())
              .lt(BizFile::getCreatedAt, cursor.plusDays(1).atStartOfDay());
            applyFileScope(fw);
            long count = fileMapper.selectCount(fw);
            Map<String, Object> item = new HashMap<>();
            item.put("day", cursor.getDayOfMonth());
            item.put("date", cursor.toString());
            item.put("value", count);
            fileDays.add(item);
        }
        result.put("fileDays", fileDays);

        return result;
    }

    /**
     * 按周统计（本月）
     * 【需求】周报：本月每周新增客户与文件上传数
     * 【方法】客户走 customerService.weeklyNewCustomers；文件按周分段计数
     */
    public Map<String, Object> weeklyStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("customerWeeks", customerService.weeklyNewCustomers());

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> fileWeeks = new ArrayList<>();
        int wn = 1;
        for (LocalDate start : monthWeekStartsThrough(today)) {
            LocalDate end = start.plusDays(6);
            boolean isCurrent = !end.isBefore(today);
            if (end.isAfter(today)) end = today;
            LambdaQueryWrapper<BizFile> fw = new LambdaQueryWrapper<>();
            fw.ge(BizFile::getCreatedAt, start.atStartOfDay())
              .lt(BizFile::getCreatedAt, end.plusDays(1).atStartOfDay());
            applyFileScope(fw);
            long count = fileMapper.selectCount(fw);
            Map<String, Object> item = new HashMap<>();
            item.put("label", isCurrent ? "本周" : "W" + wn);
            item.put("value", count);
            fileWeeks.add(item);
            wn++;
        }
        result.put("fileWeeks", fileWeeks);

        return result;
    }

    /**
     * 指标统计与排行
     * 【需求】个人/部门/大区指标卡片与排行榜
     * 【方法】全量加载客户+开票+指标，内存聚合；达标阈值 100 万元
     * 【业务规则】核心客户=指标分>=12 且开票、回款均达标；排行按总分降序、客户数降序
     */
    public Map<String, Object> indicatorStats() {
        Map<String, Object> data = new HashMap<>();

        List<BizCustomerIndicator> indicators = indicatorMapper.selectList(null);
        // 【2026-08-08 设计规则：核心客户六项指标全员可见】公司级指标展示，不做数据范围过滤。
        List<BizCustomer> allCustomers = customerService.getAllActiveCustomers();
        Map<Long, BizCustomer> customerMap = new HashMap<>();
        for (BizCustomer c : allCustomers) customerMap.put(c.getId(), c);

        List<BizInvoice> allInvoices = invoiceMapper.selectList(null);
        Map<Long, java.math.BigDecimal> invoiceTotals = new HashMap<>();
        for (BizInvoice inv : allInvoices) {
            if (inv.getCustomerId() != null && inv.getAmount() != null) {
                invoiceTotals.merge(inv.getCustomerId(), inv.getAmount(), java.math.BigDecimal::add);
            }
        }
        java.math.BigDecimal THRESHOLD = CORE_THRESHOLD;
        List<BizPayment> allPayments = paymentMapper.selectList(null);
        Map<Long, java.math.BigDecimal> paymentTotals = new HashMap<>();
        for (BizPayment payment : allPayments) {
            if (payment.getCustomerId() != null && payment.getPaymentAmount() != null) {
                paymentTotals.merge(payment.getCustomerId(), payment.getPaymentAmount(), java.math.BigDecimal::add);
            }
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<BizCustomer> myCustomers = allCustomers.stream()
                .filter(c -> currentUserId.equals(c.getOwnerId())).toList();
        Set<Long> myCustomerIds = myCustomers.stream().map(BizCustomer::getId).collect(java.util.stream.Collectors.toSet());
        List<BizCustomerIndicator> myIndicators = indicators.stream()
                .filter(ind -> myCustomerIds.contains(ind.getCustomerId())).toList();

        int coveredCount = myIndicators.size();
        int coreCount = 0;
        double scoreSum = 0;
        for (BizCustomerIndicator ind : myIndicators) {
            int s = indicatorScore(ind);
            scoreSum += s;
            java.math.BigDecimal invTotal = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            java.math.BigDecimal paymentTotal = paymentTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            if (isCoreCustomer(s, invTotal, paymentTotal)) coreCount++;
        }
        int myQualifyCount = 0;
        for (BizCustomer c : myCustomers) {
            java.math.BigDecimal inv = invoiceTotals.getOrDefault(c.getId(), java.math.BigDecimal.ZERO);
            java.math.BigDecimal payment = paymentTotals.getOrDefault(c.getId(), java.math.BigDecimal.ZERO);
            if (inv.compareTo(THRESHOLD) >= 0 && payment.compareTo(THRESHOLD) >= 0) myQualifyCount++;
        }

        data.put("coveredCount", coveredCount);
        data.put("coreCount", coreCount);
        data.put("avgScore", coveredCount > 0 ? Math.round(scoreSum / coveredCount * 10) / 10.0 : 0);
        data.put("qualifyRate", myCustomers.size() > 0 ? Math.round(myQualifyCount * 1000.0 / myCustomers.size()) / 10.0 : 0);

        List<Map<String, Object>> customerRanking = new ArrayList<>();
        for (BizCustomerIndicator ind : indicators) {
            BizCustomer c = customerMap.get(ind.getCustomerId());
            if (c == null) continue;
            int s = indicatorScore(ind);
            if (s <= 0) continue;
            java.math.BigDecimal invTotal = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            java.math.BigDecimal paymentTotal = paymentTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerId", ind.getCustomerId());
            row.put("customerName", c.getCustomerName());
            row.put("ownerName", c.getOwnerName());
            row.put("industry", c.getIndustry());
            row.put("devType", c.getDevType());
            row.put("invoiceTotal", invTotal);
            row.put("paymentTotal", paymentTotal);
            row.put("relationshipScore", ind.getRelationshipScore() != null ? ind.getRelationshipScore() : 0);
            row.put("indicator02", ind.getIndicator02() != null ? ind.getIndicator02() : 0);
            row.put("indicator03", ind.getIndicator03() != null ? ind.getIndicator03() : 0);
            row.put("indicator04", ind.getIndicator04() != null ? ind.getIndicator04() : 0);
            row.put("indicator05", ind.getIndicator05() != null ? ind.getIndicator05() : 0);
            row.put("totalScore", s);
            row.put("core", isCoreCustomer(s, invTotal, paymentTotal));
            customerRanking.add(row);
        }
        customerRanking.sort((a, b) -> {
            int cmp = Integer.compare(((Number) b.get("totalScore")).intValue(), ((Number) a.get("totalScore")).intValue());
            if (cmp != 0) return cmp;
            return String.valueOf(a.getOrDefault("customerName", "")).compareTo(String.valueOf(b.getOrDefault("customerName", "")));
        });
        data.put("customerRanking", customerRanking);

        Map<Long, Long> ownerCustomerCounts = new HashMap<>();
        for (BizCustomer c : allCustomers) {
            if (c.getOwnerId() != null) {
                ownerCustomerCounts.merge(c.getOwnerId(), 1L, Long::sum);
            }
        }

        Map<Long, List<int[]>> ownerScores = new HashMap<>();
        for (BizCustomerIndicator ind : indicators) {
            BizCustomer c = customerMap.get(ind.getCustomerId());
            if (c == null || c.getOwnerId() == null) continue;
            int s = indicatorScore(ind);
            java.math.BigDecimal invTotal = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            java.math.BigDecimal paymentTotal = paymentTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            int core = isCoreCustomer(s, invTotal, paymentTotal) ? 1 : 0;
            ownerScores.computeIfAbsent(c.getOwnerId(), k -> new ArrayList<>()).add(new int[]{s, core});
        }

        Set<Long> allOwnerIds = new HashSet<>(ownerCustomerCounts.keySet());
        allOwnerIds.addAll(ownerScores.keySet());

        // 【修复 FEAT-20260804-INDICATOR-N1】批量预取用户和部门，避免循环内逐条 selectById
        Map<Long, SysUser> userById = allOwnerIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(allOwnerIds).stream()
                        .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));
        Set<Long> deptIdsNeeded = new HashSet<>();
        for (SysUser u : userById.values()) if (u.getDeptId() != null) deptIdsNeeded.add(u.getDeptId());
        Map<Long, SysDept> deptById = deptIdsNeeded.isEmpty() ? Map.of()
                : deptMapper.selectByIds(deptIdsNeeded).stream()
                        .collect(java.util.stream.Collectors.toMap(SysDept::getId, d -> d));

        List<Map<String, Object>> personalRanking = new ArrayList<>();
        for (Long uid : allOwnerIds) {
            SysUser user = userById.get(uid);
            if (user == null) continue;
            String deptName = "";
            if (user.getDeptId() != null) {
                var dept = deptById.get(user.getDeptId());
                if (dept != null) deptName = dept.getDeptName();
            }
            List<int[]> scores = ownerScores.getOrDefault(uid, List.of());
            long custCount = ownerCustomerCounts.getOrDefault(uid, 0L);
            int cores = scores.stream().mapToInt(s -> s[1]).sum();
            int totalScore = scores.stream().mapToInt(s -> s[0]).sum();
            int maxScore = scores.stream().mapToInt(s -> s[0]).max().orElse(0);
            double avgScoreP = !scores.isEmpty() ? Math.round(totalScore * 10.0 / scores.size()) / 10.0 : 0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", uid);
            row.put("realName", user.getRealName());
            row.put("deptName", deptName);
            row.put("customerCount", custCount);
            row.put("coreCount", cores);
            row.put("avgScore", avgScoreP);
            row.put("maxScore", maxScore);
            row.put("totalScore", totalScore);
            row.put("level", cores > 0 ? "优秀" : "一般");
            personalRanking.add(row);
        }
        personalRanking.sort((a, b) -> {
            int cmp = Integer.compare(((Number) b.get("totalScore")).intValue(), ((Number) a.get("totalScore")).intValue());
            if (cmp != 0) return cmp;
            return Long.compare(((Number) b.get("customerCount")).longValue(), ((Number) a.get("customerCount")).longValue());
        });
        data.put("personalRanking", personalRanking);

        Map<String, int[]> deptAgg = new LinkedHashMap<>();
        for (var p : personalRanking) {
            String dn = (String) p.getOrDefault("deptName", "未知");
            int[] agg = deptAgg.computeIfAbsent(dn, k -> new int[4]);
            agg[0] += ((Number) p.get("totalScore")).intValue();
            agg[1] += ((Number) p.get("customerCount")).intValue();
            agg[2] += ((Number) p.get("coreCount")).intValue();
            agg[3] += 1;
        }
        List<Map<String, Object>> deptRanking = new ArrayList<>();
        for (var e : deptAgg.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deptName", e.getKey());
            row.put("totalScore", e.getValue()[0]);
            row.put("customerCount", e.getValue()[1]);
            row.put("coreCount", e.getValue()[2]);
            row.put("memberCount", e.getValue()[3]);
            row.put("avgScore", e.getValue()[1] > 0 ? Math.round(e.getValue()[0] * 10.0 / e.getValue()[1]) / 10.0 : 0);
            deptRanking.add(row);
        }
        deptRanking.sort((a, b) -> Integer.compare(((Number) b.get("totalScore")).intValue(), ((Number) a.get("totalScore")).intValue()));
        data.put("deptRanking", deptRanking);

        // 批量预取办事处，避免循环内逐条 selectById
        Set<Long> officeIdsNeeded = new HashSet<>();
        for (BizCustomer c : allCustomers) if (c.getOfficeId() != null) officeIdsNeeded.add(c.getOfficeId());
        Map<Long, SysDept> officeById = officeIdsNeeded.isEmpty() ? Map.of()
                : deptMapper.selectByIds(officeIdsNeeded).stream()
                        .collect(java.util.stream.Collectors.toMap(SysDept::getId, d -> d));

        Map<String, int[]> officeAgg = new LinkedHashMap<>();
        for (BizCustomerIndicator ind : indicators) {
            BizCustomer c = customerMap.get(ind.getCustomerId());
            if (c == null) continue;
            String officeName = "";
            if (c.getOfficeId() != null) {
                var dept = officeById.get(c.getOfficeId());
                if (dept != null) officeName = dept.getDeptName();
            }
            int s = indicatorScore(ind);
            java.math.BigDecimal invT = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            int core = (s >= 12 && invT.compareTo(THRESHOLD) >= 0) ? 1 : 0;
            int[] agg = officeAgg.computeIfAbsent(officeName.isEmpty() ? "未知" : officeName, k -> new int[3]);
            agg[0] += s;
            agg[1] += 1;
            agg[2] += core;
        }
        List<Map<String, Object>> regionRanking = new ArrayList<>();
        for (var e : officeAgg.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("regionName", e.getKey());
            row.put("totalScore", e.getValue()[0]);
            row.put("customerCount", e.getValue()[1]);
            row.put("coreCount", e.getValue()[2]);
            row.put("avgScore", e.getValue()[1] > 0 ? Math.round(e.getValue()[0] * 10.0 / e.getValue()[1]) / 10.0 : 0);
            regionRanking.add(row);
        }
        regionRanking.sort((a, b) -> Integer.compare(((Number) b.get("totalScore")).intValue(), ((Number) a.get("totalScore")).intValue()));
        data.put("regionRanking", regionRanking);

        return data;
    }

    /**
     * 客户排行
     * 【需求】统一展示客户对象的四项激励、客情商务关系深度、核心客户六项指标排行
     * 【业务规则】三类排行均为客户口径，按分值降序；0 分客户不展示；核心客户保持 >=12 且开票/回款均 >=100 万
     */
    public Map<String, Object> customerRankings(String period) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<BizCustomer> customers = customerService.getAllActiveCustomers();
        Map<Long, BizCustomer> customerMap = new HashMap<>();
        for (BizCustomer c : customers) customerMap.put(c.getId(), c);

        List<BizIncentiveCategory> categories = incentiveCategoryMapper.selectList(null);
        Map<Long, BizIncentiveCategory> categoryMap = new HashMap<>();
        for (BizIncentiveCategory category : categories) categoryMap.put(category.getId(), category);
        List<Map<String, Object>> incentiveParents = categories.stream()
                .filter(category -> category.getParentId() != null && category.getParentId() == 0)
                .sorted((a, b) -> Integer.compare(safeValue(a.getSortOrder()), safeValue(b.getSortOrder())))
                .map(category -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", category.getId());
                    row.put("categoryName", category.getCategoryName());
                    return row;
                })
                .toList();

        LambdaQueryWrapper<BizIncentiveRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.isNotNull(BizIncentiveRecord::getCustomerId);
        if (period != null && !period.isBlank()) recordWrapper.eq(BizIncentiveRecord::getPeriod, period);
        List<BizIncentiveRecord> records = incentiveRecordMapper.selectList(recordWrapper);
        Map<Long, Map<String, Object>> incentiveRows = new LinkedHashMap<>();
        for (BizIncentiveRecord record : records) {
            BizCustomer customer = customerMap.get(record.getCustomerId());
            if (customer == null) continue;
            Map<String, Object> row = incentiveRows.computeIfAbsent(record.getCustomerId(), id -> customerBaseRow(customer));
            int score = safeValue(record.getScore());
            row.put("totalScore", ((Number) row.getOrDefault("totalScore", 0)).intValue() + score);
            row.put("recordCount", ((Number) row.getOrDefault("recordCount", 0)).intValue() + 1);
            @SuppressWarnings("unchecked")
            Map<Long, int[]> byParent = (Map<Long, int[]>) row.computeIfAbsent("byParent", key -> new LinkedHashMap<Long, int[]>());
            Long parentId = rootCategoryId(record.getCategoryId(), categoryMap);
            if (parentId != null) {
                int[] summary = byParent.computeIfAbsent(parentId, key -> new int[2]);
                summary[0] += 1;
                summary[1] += score;
            }
        }
        List<Map<String, Object>> incentiveRanking = incentiveRows.values().stream()
                .filter(row -> ((Number) row.getOrDefault("totalScore", 0)).intValue() > 0)
                .map(this::serializeIncentiveRankingRow)
                .sorted(this::compareTotalScoreThenCustomer)
                .toList();

        Map<String, Object> indicatorStats = indicatorStats();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indicatorRanking = (List<Map<String, Object>>) indicatorStats.getOrDefault("customerRanking", List.of());
        List<Map<String, Object>> relationshipRanking = indicatorRanking.stream()
                .filter(row -> ((Number) row.getOrDefault("relationshipScore", 0)).intValue() > 0)
                .sorted((a, b) -> {
                    int cmp = Integer.compare(((Number) b.getOrDefault("relationshipScore", 0)).intValue(),
                            ((Number) a.getOrDefault("relationshipScore", 0)).intValue());
                    if (cmp != 0) return cmp;
                    return String.valueOf(a.getOrDefault("customerName", "")).compareTo(String.valueOf(b.getOrDefault("customerName", "")));
                })
                .toList();

        data.put("period", period);
        data.put("incentiveParents", incentiveParents);
        data.put("incentiveRanking", incentiveRanking);
        data.put("relationshipRanking", relationshipRanking);
        data.put("indicatorRanking", indicatorRanking);
        data.put("coreCount", indicatorRanking.stream().filter(row -> Boolean.TRUE.equals(row.get("core"))).count());
        return data;
    }

    private Map<String, Object> customerBaseRow(BizCustomer customer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customerId", customer.getId());
        row.put("customerName", customer.getCustomerName());
        row.put("ownerName", customer.getOwnerName());
        row.put("industry", customer.getIndustry());
        row.put("totalScore", 0);
        row.put("recordCount", 0);
        return row;
    }

    private Long rootCategoryId(Long categoryId, Map<Long, BizIncentiveCategory> categoryMap) {
        Long cursor = categoryId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            BizIncentiveCategory category = categoryMap.get(cursor);
            if (category == null) return null;
            Long parentId = category.getParentId();
            if (parentId == null || parentId == 0) return category.getId();
            cursor = parentId;
        }
        return null;
    }

    private Map<String, Object> serializeIncentiveRankingRow(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>(source);
        @SuppressWarnings("unchecked")
        Map<Long, int[]> raw = (Map<Long, int[]>) row.remove("byParent");
        Map<String, Map<String, Integer>> serialized = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<Long, int[]> entry : raw.entrySet()) {
                Map<String, Integer> value = new LinkedHashMap<>();
                value.put("count", entry.getValue()[0]);
                value.put("score", entry.getValue()[1]);
                serialized.put(String.valueOf(entry.getKey()), value);
            }
        }
        row.put("byParent", serialized);
        return row;
    }

    private int compareTotalScoreThenCustomer(Map<String, Object> a, Map<String, Object> b) {
        int cmp = Integer.compare(((Number) b.getOrDefault("totalScore", 0)).intValue(),
                ((Number) a.getOrDefault("totalScore", 0)).intValue());
        if (cmp != 0) return cmp;
        return String.valueOf(a.getOrDefault("customerName", "")).compareTo(String.valueOf(b.getOrDefault("customerName", "")));
    }

    /**
     * 经营分析
     * 【需求】年度月度新增/文件、办事处对比、概览统计、行业分布
     * 【方法】按年/办事处条件查询并聚合；officeId 筛选时所有统计同步收窄
     */
    public Map<String, Object> analysis(int year, Long officeId, Long deptId) {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = new HashMap<>();

        int[] monthlyCustomers = new int[12];
        for (int m = 1; m <= 12; m++) {
            LocalDateTime from = LocalDateTime.of(year, m, 1, 0, 0);
            LocalDateTime to = (m < 12) ? LocalDateTime.of(year, m + 1, 1, 0, 0) : LocalDateTime.of(year + 1, 1, 1, 0, 0);
            LambdaQueryWrapper<BizCustomer> cw = new LambdaQueryWrapper<>();
            cw.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
              .ge(BizCustomer::getCreatedAt, from).lt(BizCustomer::getCreatedAt, to);
            if (deptId != null) cw.in(BizCustomer::getOfficeId, com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
            else if (officeId != null) cw.eq(BizCustomer::getOfficeId, officeId);
            applyCustomerScope(cw);
            monthlyCustomers[m - 1] = customerMapper.selectCount(cw).intValue();
        }
        data.put("monthlyCustomers", monthlyCustomers);

        int[] monthlyFiles = new int[12];
        for (int m = 1; m <= 12; m++) {
            LocalDateTime from = LocalDateTime.of(year, m, 1, 0, 0);
            LocalDateTime to = (m < 12) ? LocalDateTime.of(year, m + 1, 1, 0, 0) : LocalDateTime.of(year + 1, 1, 1, 0, 0);
            LambdaQueryWrapper<BizFile> fw = new LambdaQueryWrapper<>();
            fw.ge(BizFile::getCreatedAt, from).lt(BizFile::getCreatedAt, to);
            applyFileScope(fw);
            monthlyFiles[m - 1] = fileMapper.selectCount(fw).intValue();
        }
        data.put("monthlyFiles", monthlyFiles);

        List<Map<String, Object>> officeComparison = new ArrayList<>();
        var allDepts = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getDeptType, 3));
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        for (var dept : allDepts) {
            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            w.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
             .eq(BizCustomer::getOfficeId, dept.getId());
            applyCustomerScope(w);
            long total = customerMapper.selectCount(w);
            if (total == 0) continue;

            LambdaQueryWrapper<BizCustomer> aw = new LambdaQueryWrapper<>();
            aw.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "active")
              .eq(BizCustomer::getOfficeId, dept.getId());
            applyCustomerScope(aw);
            long active = customerMapper.selectCount(aw);

            LambdaQueryWrapper<BizCustomer> nw = new LambdaQueryWrapper<>();
            nw.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
              .eq(BizCustomer::getOfficeId, dept.getId())
              .ge(BizCustomer::getCreatedAt, monthStart);
            applyCustomerScope(nw);
            long monthNew = customerMapper.selectCount(nw);

            int[] recent5 = new int[5];
            for (int i = 4; i >= 0; i--) {
                LocalDate md = LocalDate.now().minusMonths(i).withDayOfMonth(1);
                LocalDateTime mf = md.atStartOfDay();
                LocalDateTime mt = md.plusMonths(1).atStartOfDay();
                LambdaQueryWrapper<BizCustomer> rw = new LambdaQueryWrapper<>();
                rw.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                  .eq(BizCustomer::getOfficeId, dept.getId())
                  .ge(BizCustomer::getCreatedAt, mf).lt(BizCustomer::getCreatedAt, mt);
                applyCustomerScope(rw);
                recent5[4 - i] = customerMapper.selectCount(rw).intValue();
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("officeName", dept.getDeptName());
            row.put("customerCount", total);
            row.put("monthNew", monthNew);
            row.put("activeRate", total > 0 ? Math.round(active * 1000.0 / total) / 10.0 : 0);
            row.put("recent5", recent5);
            officeComparison.add(row);
        }
        officeComparison.sort((a, b) -> Long.compare((long) b.get("customerCount"), (long) a.get("customerCount")));
        data.put("officeComparison", officeComparison);

        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<BizCustomer> baseW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");
        if (deptId != null) baseW.in(BizCustomer::getOfficeId, com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) baseW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(baseW);
        long totalCustomers = customerMapper.selectCount(baseW);

        LambdaQueryWrapper<BizCustomer> protW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getProtectionStatus, "active");
        if (deptId != null) protW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) protW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(protW);
        long inProtection = customerMapper.selectCount(protW);

        LambdaQueryWrapper<BizCustomer> expingW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getProtectionStatus, "expiring");
        if (deptId != null) expingW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) expingW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(expingW);
        long expiring = customerMapper.selectCount(expingW);

        LambdaQueryWrapper<BizCustomer> expW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .lt(BizCustomer::getExpireDate, today);
        if (deptId != null) expW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) expW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(expW);
        long expired = customerMapper.selectCount(expW);

        LambdaQueryWrapper<BizCustomer> todayExpiringW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getExpireDate, today);
        if (deptId != null) todayExpiringW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) todayExpiringW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(todayExpiringW);
        long todayExpiring = customerMapper.selectCount(todayExpiringW);

        final int finalYear = year;
        LocalDateTime yearStart = LocalDateTime.of(finalYear, 1, 1, 0, 0);
        LocalDateTime yearEnd = LocalDateTime.of(finalYear + 1, 1, 1, 0, 0);
        LambdaQueryWrapper<BizCustomer> newW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .ge(BizCustomer::getCreatedAt, yearStart).lt(BizCustomer::getCreatedAt, yearEnd);
        if (deptId != null) newW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) newW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(newW);
        long yearNew = customerMapper.selectCount(newW);

        LambdaQueryWrapper<BizCustomer> indW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .isNotNull(BizCustomer::getIndustry);
        if (deptId != null) indW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) indW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(indW);
        List<BizCustomer> indCustomers = customerMapper.selectList(indW);
        Map<String, Long> industryStats = new LinkedHashMap<>();
        for (BizCustomer c : indCustomers) {
            // 行业为空不参与分布统计(待用户补全后自动计入), 图表不显示"未填写"
            if (c.getIndustry() == null || c.getIndustry().isBlank()) continue;
            industryStats.merge(c.getIndustry().trim(), 1L, Long::sum);
        }
        data.put("industryStats", industryStats);

        data.put("totalCustomers", totalCustomers);
        data.put("inProtection", inProtection);
        data.put("expiring", expiring);
        data.put("expired", expired);
        data.put("todayExpiring", todayExpiring);
        data.put("yearNew", yearNew);

        return data;
    }

    /**
     * 团队动态
     * 【需求】首页团队动态流：跟进/激励/文件/审批/新增客户/系统日志，取最近 15 条
     * 【方法】六个来源各自 LIMIT 查询后按时间倒序合并
     */
    public List<Map<String, Object>> teamActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        // 【2026-08-08 设计规则（最终）：统计按账号数据范围】团队动态按当前账号数据范围过滤
        // （管理员全量 / 负责人本组织 / 销售本人）。visible=null 表示全量不过滤。
        List<Long> visibleUserIds = customerService.getCurrentScopeUserIds();
        boolean scopeAll = (visibleUserIds == null);

        LambdaQueryWrapper<BizCustomerLog> logWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) logWrapper.in(BizCustomerLog::getUserId, visibleUserIds);
        logWrapper.orderByDesc(BizCustomerLog::getCreatedAt).last("LIMIT 8");
        for (BizCustomerLog log : customerLogMapper.selectList(logWrapper)) {
            SysUser user = userMapper.selectById(log.getUserId());
            BizCustomer customer = customerMapper.selectById(log.getCustomerId());
            activities.add(buildActivity(
                    (log.getLogType() != null ? mapLogTypeLabel(log.getLogType()) : "跟进") +
                            " · " + (customer != null ? customer.getCustomerName() : ""),
                    user, log.getCreatedAt()));
        }

        LambdaQueryWrapper<BizIncentiveRecord> incWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) incWrapper.in(BizIncentiveRecord::getUserId, visibleUserIds);
        incWrapper.orderByDesc(BizIncentiveRecord::getCreatedAt).last("LIMIT 5");
        // 循环变量避开受限标识符 record（SonarLint S6213）
        for (BizIncentiveRecord incRecord : incentiveRecordMapper.selectList(incWrapper)) {
            SysUser user = userMapper.selectById(incRecord.getUserId());
            BizIncentiveCategory cat = incentiveCategoryMapper.selectById(incRecord.getCategoryId());
            activities.add(buildActivity(
                    "激励上传 · " + (cat != null ? cat.getCategoryName() : ""),
                    user, incRecord.getCreatedAt()));
        }

        LambdaQueryWrapper<BizFile> fileWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) fileWrapper.in(BizFile::getUploaderId, visibleUserIds);
        fileWrapper.orderByDesc(BizFile::getCreatedAt).last("LIMIT 5");
        for (BizFile file : fileMapper.selectList(fileWrapper)) {
            SysUser user = file.getUploaderId() != null ? userMapper.selectById(file.getUploaderId()) : null;
            activities.add(buildActivity(
                    "上传文件 · " + file.getFileName(),
                    user, file.getCreatedAt()));
        }

        LambdaQueryWrapper<BizApproval> apprWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) apprWrapper.in(BizApproval::getApplicantId, visibleUserIds);
        apprWrapper.orderByDesc(BizApproval::getCreatedAt).last("LIMIT 5");
        for (BizApproval appr : approvalMapper.selectList(apprWrapper)) {
            SysUser user = appr.getApplicantId() != null ? userMapper.selectById(appr.getApplicantId()) : null;
            BizCustomer customer = appr.getCustomerId() != null ? customerMapper.selectById(appr.getCustomerId()) : null;
            String typeText = switch (appr.getApprovalType()) {
                case "claim" -> "捞取申请";
                case "transfer" -> "转让申请";
                case "extend" -> "延期申请";
                case "storage" -> "入库申请";
                default -> "审批";
            };
            activities.add(buildActivity(
                    typeText + " · " + (customer != null ? customer.getCustomerName() : ""),
                    user, appr.getCreatedAt()));
        }

        LambdaQueryWrapper<BizCustomer> custWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) custWrapper.in(BizCustomer::getOwnerId, visibleUserIds);
        custWrapper.orderByDesc(BizCustomer::getCreatedAt).last("LIMIT 5");
        for (BizCustomer cust : customerMapper.selectList(custWrapper)) {
            SysUser user = cust.getOwnerId() != null ? userMapper.selectById(cust.getOwnerId()) : null;
            activities.add(buildActivity(
                    "新增客户 · " + cust.getCustomerName(),
                    user, cust.getCreatedAt()));
        }

        LambdaQueryWrapper<SysLog> sysWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) sysWrapper.in(SysLog::getUserId, visibleUserIds);
        sysWrapper.orderByDesc(SysLog::getCreatedAt).last("LIMIT 5");
        for (SysLog log : sysLogMapper.selectList(sysWrapper)) {
            SysUser user = log.getUserId() != null ? userMapper.selectById(log.getUserId()) : null;
            if (user == null && log.getUsername() != null) {
                activities.add(buildActivitySimple(
                        log.getModule() + " · " + log.getAction(),
                        log.getUsername(), "", log.getCreatedAt()));
                continue;
            }
            activities.add(buildActivity(
                    log.getModule() + " · " + log.getAction(),
                    user, log.getCreatedAt()));
        }

        activities.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("time");
            LocalDateTime tb = (LocalDateTime) b.get("time");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        if (activities.size() > 15) {
            activities = activities.subList(0, 15);
        }

        return activities;
    }

    /**
     * 【2026-08-08 设计规则（最终）：统计按账号数据范围】统计模块数据按当前账号数据范围过滤
     * （管理员全量 / 负责人本组织 / 销售本人，含系统级开关）。覆盖 dashboard/经营分析/日报周报等查询。
     * ⚠️ 例外: 核心客户指标(indicatorStats)按设计为"六项指标全员可见", 明确不做数据范围过滤
     * （见该方法内注释）。getCurrentScopeUserIds 返回 null=全量不过滤。
     */
    private void applyCustomerScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        List<Long> userIds = customerService.getCurrentScopeUserIds();
        if (userIds != null) wrapper.in(BizCustomer::getOwnerId, userIds);
    }

    private void applyFileScope(LambdaQueryWrapper<BizFile> wrapper) {
        List<Long> userIds = customerService.getCurrentScopeUserIds();
        if (userIds != null) wrapper.in(BizFile::getUploaderId, userIds);
    }

    private Map<String, Object> buildActivity(String title, SysUser user, LocalDateTime time) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("user", user != null ? user.getRealName() : "");
        String deptName = "";
        if (user != null && user.getOfficeId() != null) {
            var dept = deptMapper.selectById(user.getOfficeId());
            if (dept != null) deptName = dept.getDeptName();
        }
        item.put("dept", deptName);
        item.put("time", time);
        return item;
    }

    private Map<String, Object> buildActivitySimple(String title, String userName, String deptName, LocalDateTime time) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("user", userName);
        item.put("dept", deptName);
        item.put("time", time);
        return item;
    }

    private String mapLogTypeLabel(String logType) {
        if (logType == null) return "跟进";
        return switch (logType) {
            case "visit" -> "终端考察";
            case "meeting" -> "技术交流会";
            case "trial" -> "产品试点报告";
            default -> "跟进";
        };
    }
}
