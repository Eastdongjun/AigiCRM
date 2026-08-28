package com.ycmn.module.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.approval.entity.BizApproval;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.approval.service.ApprovalService;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.entity.BizCustomerIndicator;
import com.ycmn.module.customer.entity.BizCustomerLog;
import com.ycmn.module.customer.entity.BizInvoice;
import com.ycmn.module.customer.mapper.BizCustomerIndicatorMapper;
import com.ycmn.module.customer.mapper.BizCustomerLogMapper;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizInvoiceMapper;
import com.ycmn.module.customer.service.CustomerService;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysLog;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysLogMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
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
 *             指标达标阈值 100 万元；核心客户=指标分>=12 且开票达标
 * 【注意】只读服务，不落库；调用方（Controller）负责权限注解
 */
@Service
@RequiredArgsConstructor
public class StatisticsQueryService {

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
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysLogMapper sysLogMapper;
    private final BizApprovalMapper approvalMapper;

    /**
     * 看板汇总
     * 【需求】首页统计卡片：客户/公海/审批/到期提醒
     * 【方法】委托各模块 Service 做数据范围感知统计
     * 【业务规则】到期=expireDate 距今天数；todayExpiring 暂置 0
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
        data.put("todayExpiring", 0L);
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
        LocalDate cursor = today.withDayOfMonth(1);
        List<Map<String, Object>> fileDays = new ArrayList<>();
        while (!cursor.isAfter(today)) {
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
            cursor = cursor.plusDays(1);
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
        LocalDate start = today.withDayOfMonth(1);
        List<Map<String, Object>> fileWeeks = new ArrayList<>();
        int wn = 1;
        while (!start.isAfter(today)) {
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
            start = start.plusDays(7);
            wn++;
        }
        result.put("fileWeeks", fileWeeks);

        return result;
    }

    /**
     * 指标统计与排行
     * 【需求】个人/部门/大区指标卡片与排行榜
     * 【方法】全量加载客户+开票+指标，内存聚合；达标阈值 100 万元
     * 【业务规则】核心客户=指标分>=12 且开票达标；排行按总分降序、客户数降序
     */
    public Map<String, Object> indicatorStats() {
        Map<String, Object> data = new HashMap<>();

        // 【2026-08-27 数据范围修复】按当前用户数据范围过滤（admin=null 全量，其余按
        // role_perm_scope 解析：销售员 self=仅自己）。原实现三处全量查询绕过数据范围配置层，
        // 销售员可见全公司指标/排行，与其 self 配置矛盾。修复后以 scope 内客户为基准，
        // 指标/发票只取这些客户，覆盖数/排行/部门/办事处聚合自动收窄。
        List<Long> scopeUserIds = customerService.getCurrentScopeUserIds();
        List<BizCustomer> allCustomers = customerService.getAllActiveCustomers();
        if (scopeUserIds != null) {
            Set<Long> visibleOwnerIds = new HashSet<>(scopeUserIds);
            allCustomers = allCustomers.stream()
                    .filter(c -> visibleOwnerIds.contains(c.getOwnerId()))
                    .toList();
        }
        Map<Long, BizCustomer> customerMap = new HashMap<>();
        for (BizCustomer c : allCustomers) customerMap.put(c.getId(), c);

        List<BizCustomerIndicator> indicators = indicatorMapper.selectList(null).stream()
                .filter(ind -> customerMap.containsKey(ind.getCustomerId()))
                .toList();

        List<BizInvoice> allInvoices = invoiceMapper.selectList(null).stream()
                .filter(inv -> inv.getCustomerId() != null && customerMap.containsKey(inv.getCustomerId()))
                .toList();
        Map<Long, java.math.BigDecimal> invoiceTotals = new HashMap<>();
        for (BizInvoice inv : allInvoices) {
            if (inv.getCustomerId() != null && inv.getAmount() != null) {
                invoiceTotals.merge(inv.getCustomerId(), inv.getAmount(), java.math.BigDecimal::add);
            }
        }
        java.math.BigDecimal THRESHOLD = new java.math.BigDecimal("100");

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
            int s = safe(ind.getIndicator02()) + safe(ind.getIndicator03()) + safe(ind.getIndicator04())
                    + safe(ind.getIndicator05()) + safe(ind.getIndicator06());
            scoreSum += s;
            java.math.BigDecimal invTotal = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            boolean invoiceQualified = invTotal.compareTo(THRESHOLD) >= 0;
            if (s >= 12 && invoiceQualified) coreCount++;
        }
        int myQualifyCount = 0;
        for (BizCustomer c : myCustomers) {
            java.math.BigDecimal inv = invoiceTotals.getOrDefault(c.getId(), java.math.BigDecimal.ZERO);
            if (inv.compareTo(THRESHOLD) >= 0) myQualifyCount++;
        }

        data.put("coveredCount", coveredCount);
        data.put("coreCount", coreCount);
        data.put("avgScore", coveredCount > 0 ? Math.round(scoreSum / coveredCount * 10) / 10.0 : 0);
        data.put("qualifyRate", myCustomers.size() > 0 ? Math.round(myQualifyCount * 1000.0 / myCustomers.size()) / 10.0 : 0);

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
            int s = safe(ind.getIndicator02()) + safe(ind.getIndicator03()) + safe(ind.getIndicator04())
                    + safe(ind.getIndicator05()) + safe(ind.getIndicator06());
            java.math.BigDecimal invTotal = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            int core = (s >= 12 && invTotal.compareTo(THRESHOLD) >= 0) ? 1 : 0;
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
            int s = safe(ind.getIndicator02()) + safe(ind.getIndicator03()) + safe(ind.getIndicator04())
                    + safe(ind.getIndicator05()) + safe(ind.getIndicator06());
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
            if (deptId != null) cw.in(BizCustomer::getOfficeId, com.ycmn.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
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
        if (deptId != null) baseW.in(BizCustomer::getOfficeId, com.ycmn.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) baseW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(baseW);
        long totalCustomers = customerMapper.selectCount(baseW);

        LambdaQueryWrapper<BizCustomer> protW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getProtectionStatus, "active");
        if (officeId != null) protW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(protW);
        long inProtection = customerMapper.selectCount(protW);

        LambdaQueryWrapper<BizCustomer> expingW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getProtectionStatus, "expiring");
        if (officeId != null) expingW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(expingW);
        long expiring = customerMapper.selectCount(expingW);

        LambdaQueryWrapper<BizCustomer> expW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .lt(BizCustomer::getExpireDate, today);
        if (officeId != null) expW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(expW);
        long expired = customerMapper.selectCount(expW);

        final int finalYear = year;
        LocalDateTime yearStart = LocalDateTime.of(finalYear, 1, 1, 0, 0);
        LocalDateTime yearEnd = LocalDateTime.of(finalYear + 1, 1, 1, 0, 0);
        LambdaQueryWrapper<BizCustomer> newW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .ge(BizCustomer::getCreatedAt, yearStart).lt(BizCustomer::getCreatedAt, yearEnd);
        if (officeId != null) newW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(newW);
        long yearNew = customerMapper.selectCount(newW);

        LambdaQueryWrapper<BizCustomer> indW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .isNotNull(BizCustomer::getIndustry);
        if (officeId != null) indW.eq(BizCustomer::getOfficeId, officeId);
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
     * getCurrentScopeUserIds 返回 null=全量不过滤（admin）。
     */
    private void applyCustomerScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        List<Long> userIds = customerService.getCurrentScopeUserIds();
        if (userIds != null) wrapper.in(BizCustomer::getOwnerId, userIds);
    }

    private void applyFileScope(LambdaQueryWrapper<BizFile> wrapper) {
        List<Long> userIds = customerService.getCurrentScopeUserIds();
        if (userIds != null) wrapper.in(BizFile::getUploaderId, userIds);
    }

    private int safe(Integer v) { return v != null ? v : 0; }

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
