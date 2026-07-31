package com.ycmn.module.statistics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.Result;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.entity.BizCustomerIndicator;
import com.ycmn.module.customer.entity.BizCustomerLog;
import com.ycmn.module.customer.entity.BizInvoice;
import com.ycmn.module.customer.mapper.BizCustomerIndicatorMapper;
import com.ycmn.module.customer.mapper.BizInvoiceMapper;
import com.ycmn.module.customer.mapper.BizCustomerLogMapper;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.service.CustomerService;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.approval.entity.BizApproval;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.approval.service.ApprovalService;
import com.ycmn.module.system.entity.SysLog;
import com.ycmn.module.system.mapper.SysLogMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

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

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<Map<String, Object>> dashboard() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> data = new HashMap<>();

        // 使用数据范围感知的统计方法
        data.put("myCustomers", customerService.countByScope());
        data.put("inProtection", customerService.countInProtectionByScope());
        data.put("expiring", customerService.countExpiringByScope());
        data.put("expired", customerService.countExpiredByScope());
        data.put("seaStats", seaService.statistics());
        data.put("approvalStats", approvalService.statistics(userId));

        // 即将到期的客户（受数据范围控制）
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

        return Result.ok(data);
    }

    @GetMapping("/daily-stats")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<Map<String, Object>> dailyStats() {
        Map<String, Object> result = new HashMap<>();
        // 按天统计本月新增客户
        result.put("customerDays", customerService.dailyNewCustomers());

        // 按天统计本月文件上传
        LocalDate today = LocalDate.now();
        LocalDate cursor = today.withDayOfMonth(1);
        List<Map<String, Object>> fileDays = new ArrayList<>();
        while (!cursor.isAfter(today)) {
            LambdaQueryWrapper<BizFile> fw = new LambdaQueryWrapper<>();
            fw.ge(BizFile::getCreatedAt, cursor.atStartOfDay())
              .lt(BizFile::getCreatedAt, cursor.plusDays(1).atStartOfDay());
            long count = fileMapper.selectCount(fw);
            Map<String, Object> item = new HashMap<>();
            item.put("day", cursor.getDayOfMonth());
            item.put("date", cursor.toString());
            item.put("value", count);
            fileDays.add(item);
            cursor = cursor.plusDays(1);
        }
        result.put("fileDays", fileDays);

        return Result.ok(result);
    }

    @GetMapping("/weekly-stats")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<Map<String, Object>> weeklyStats() {
        Map<String, Object> result = new HashMap<>();

        // 按周统计本月新增客户
        result.put("customerWeeks", customerService.weeklyNewCustomers());

        // 按周统计本月文件上传
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
            long count = fileMapper.selectCount(fw);
            Map<String, Object> item = new HashMap<>();
            item.put("label", isCurrent ? "本周" : "W" + wn);
            item.put("value", count);
            fileWeeks.add(item);
            start = start.plusDays(7);
            wn++;
        }
        result.put("fileWeeks", fileWeeks);

        return Result.ok(result);
    }

    @GetMapping("/indicator-stats")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<Map<String, Object>> indicatorStats() {
        Map<String, Object> data = new HashMap<>();

        // 加载所有有指标数据的客户
        List<BizCustomerIndicator> indicators = indicatorMapper.selectList(null);
        List<BizCustomer> allCustomers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>().eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea"));
        Map<Long, BizCustomer> customerMap = new HashMap<>();
        for (BizCustomer c : allCustomers) customerMap.put(c.getId(), c);

        // 计算每个客户的开票总额
        List<BizInvoice> allInvoices = invoiceMapper.selectList(null);
        Map<Long, java.math.BigDecimal> invoiceTotals = new HashMap<>();
        for (BizInvoice inv : allInvoices) {
            if (inv.getCustomerId() != null && inv.getAmount() != null) {
                invoiceTotals.merge(inv.getCustomerId(), inv.getAmount(), java.math.BigDecimal::add);
            }
        }
        java.math.BigDecimal THRESHOLD = new java.math.BigDecimal("100"); // 单位：万元

        // 当前用户的个人指标卡片
        Long currentUserId = SecurityUtils.getCurrentUserId();
        // 当前用户的所有客户
        List<BizCustomer> myCustomers = allCustomers.stream()
                .filter(c -> currentUserId.equals(c.getOwnerId())).toList();
        // 当前用户客户中有指标的
        Set<Long> myCustomerIds = myCustomers.stream().map(BizCustomer::getId).collect(java.util.stream.Collectors.toSet());
        List<BizCustomerIndicator> myIndicators = indicators.stream()
                .filter(ind -> myCustomerIds.contains(ind.getCustomerId())).toList();

        int coveredCount = myIndicators.size();
        int coreCount = 0;
        int qualifyCount = 0;
        double scoreSum = 0;
        for (BizCustomerIndicator ind : myIndicators) {
            int s = safe(ind.getIndicator02()) + safe(ind.getIndicator03()) + safe(ind.getIndicator04())
                    + safe(ind.getIndicator05()) + safe(ind.getIndicator06());
            scoreSum += s;
            java.math.BigDecimal invTotal = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            boolean invoiceQualified = invTotal.compareTo(THRESHOLD) >= 0;
            if (invoiceQualified) qualifyCount++;
            if (s >= 12 && invoiceQualified) coreCount++;
        }
        // 达标率 = 我的客户中开票≥100万的占比
        int myQualifyCount = 0;
        for (BizCustomer c : myCustomers) {
            java.math.BigDecimal inv = invoiceTotals.getOrDefault(c.getId(), java.math.BigDecimal.ZERO);
            if (inv.compareTo(THRESHOLD) >= 0) myQualifyCount++;
        }

        data.put("coveredCount", coveredCount);
        data.put("coreCount", coreCount);
        data.put("avgScore", coveredCount > 0 ? Math.round(scoreSum / coveredCount * 10) / 10.0 : 0);
        data.put("qualifyRate", myCustomers.size() > 0 ? Math.round(myQualifyCount * 1000.0 / myCustomers.size()) / 10.0 : 0);

        // 每个用户的全部客户数（不限于有指标的）
        Map<Long, Long> ownerCustomerCounts = new HashMap<>();
        for (BizCustomer c : allCustomers) {
            if (c.getOwnerId() != null) {
                ownerCustomerCounts.merge(c.getOwnerId(), 1L, Long::sum);
            }
        }

        // 个人排行：按 owner_id 聚合指标数据
        Map<Long, List<int[]>> ownerScores = new HashMap<>(); // ownerId -> list of [totalScore, isCore]
        for (BizCustomerIndicator ind : indicators) {
            BizCustomer c = customerMap.get(ind.getCustomerId());
            if (c == null || c.getOwnerId() == null) continue;
            int s = safe(ind.getIndicator02()) + safe(ind.getIndicator03()) + safe(ind.getIndicator04())
                    + safe(ind.getIndicator05()) + safe(ind.getIndicator06());
            java.math.BigDecimal invTotal = invoiceTotals.getOrDefault(ind.getCustomerId(), java.math.BigDecimal.ZERO);
            int core = (s >= 12 && invTotal.compareTo(THRESHOLD) >= 0) ? 1 : 0;
            ownerScores.computeIfAbsent(c.getOwnerId(), k -> new ArrayList<>()).add(new int[]{s, core});
        }

        // 所有有客户的用户都参与排行（包括没有指标数据的）
        Set<Long> allOwnerIds = new HashSet<>(ownerCustomerCounts.keySet());
        allOwnerIds.addAll(ownerScores.keySet());

        List<Map<String, Object>> personalRanking = new ArrayList<>();
        for (Long uid : allOwnerIds) {
            SysUser user = userMapper.selectById(uid);
            if (user == null) continue;
            String deptName = "";
            if (user.getDeptId() != null) {
                var dept = deptMapper.selectById(user.getDeptId());
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

        // 部门排行
        Map<String, int[]> deptAgg = new LinkedHashMap<>(); // deptName -> [totalScore, custCount, coreCount]
        for (var p : personalRanking) {
            String dn = (String) p.getOrDefault("deptName", "未知");
            int[] agg = deptAgg.computeIfAbsent(dn, k -> new int[4]);
            agg[0] += ((Number) p.get("totalScore")).intValue();
            agg[1] += ((Number) p.get("customerCount")).intValue();
            agg[2] += ((Number) p.get("coreCount")).intValue();
            agg[3] += 1; // 人数
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

        // 大区排行 = 按 office 聚合（同部门排行逻辑，用 officeId）
        Map<String, int[]> officeAgg = new LinkedHashMap<>();
        for (BizCustomerIndicator ind : indicators) {
            BizCustomer c = customerMap.get(ind.getCustomerId());
            if (c == null) continue;
            String officeName = "";
            if (c.getOfficeId() != null) {
                var dept = deptMapper.selectById(c.getOfficeId());
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

        return Result.ok(data);
    }

    private int safe(Integer v) { return v != null ? v : 0; }

    @GetMapping("/analysis")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<Map<String, Object>> analysis(@RequestParam(defaultValue = "0") int year,
                                                 @RequestParam(required = false) Long officeId) {
        if (year == 0) year = LocalDate.now().getYear();
        Map<String, Object> data = new HashMap<>();

        // 月度新增客户 (12个月)
        int[] monthlyCustomers = new int[12];
        for (int m = 1; m <= 12; m++) {
            LocalDateTime from = LocalDateTime.of(year, m, 1, 0, 0);
            LocalDateTime to = (m < 12) ? LocalDateTime.of(year, m + 1, 1, 0, 0) : LocalDateTime.of(year + 1, 1, 1, 0, 0);
            LambdaQueryWrapper<BizCustomer> cw = new LambdaQueryWrapper<>();
            cw.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
              .ge(BizCustomer::getCreatedAt, from).lt(BizCustomer::getCreatedAt, to);
            if (officeId != null) cw.eq(BizCustomer::getOfficeId, officeId);
            monthlyCustomers[m - 1] = customerMapper.selectCount(cw).intValue();
        }
        data.put("monthlyCustomers", monthlyCustomers);

        // 月度文件上传 (12个月)
        int[] monthlyFiles = new int[12];
        for (int m = 1; m <= 12; m++) {
            LocalDateTime from = LocalDateTime.of(year, m, 1, 0, 0);
            LocalDateTime to = (m < 12) ? LocalDateTime.of(year, m + 1, 1, 0, 0) : LocalDateTime.of(year + 1, 1, 1, 0, 0);
            LambdaQueryWrapper<BizFile> fw = new LambdaQueryWrapper<>();
            fw.ge(BizFile::getCreatedAt, from).lt(BizFile::getCreatedAt, to);
            monthlyFiles[m - 1] = fileMapper.selectCount(fw).intValue();
        }
        data.put("monthlyFiles", monthlyFiles);

        // 办事处对比（只取办事处 deptType=3）
        List<Map<String, Object>> officeComparison = new ArrayList<>();
        var allDepts = deptMapper.selectList(
                new LambdaQueryWrapper<com.ycmn.module.system.entity.SysDept>()
                        .eq(com.ycmn.module.system.entity.SysDept::getDeptType, 3));
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        for (var dept : allDepts) {
            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            w.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
             .eq(BizCustomer::getOfficeId, dept.getId());
            long total = customerMapper.selectCount(w);
            if (total == 0) continue;

            LambdaQueryWrapper<BizCustomer> aw = new LambdaQueryWrapper<>();
            aw.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "active")
              .eq(BizCustomer::getOfficeId, dept.getId());
            long active = customerMapper.selectCount(aw);

            LambdaQueryWrapper<BizCustomer> nw = new LambdaQueryWrapper<>();
            nw.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
              .eq(BizCustomer::getOfficeId, dept.getId())
              .ge(BizCustomer::getCreatedAt, monthStart);
            long monthNew = customerMapper.selectCount(nw);

            // 近5月每月新增
            int[] recent5 = new int[5];
            for (int i = 4; i >= 0; i--) {
                LocalDate md = LocalDate.now().minusMonths(i).withDayOfMonth(1);
                LocalDateTime mf = md.atStartOfDay();
                LocalDateTime mt = md.plusMonths(1).atStartOfDay();
                LambdaQueryWrapper<BizCustomer> rw = new LambdaQueryWrapper<>();
                rw.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                  .eq(BizCustomer::getOfficeId, dept.getId())
                  .ge(BizCustomer::getCreatedAt, mf).lt(BizCustomer::getCreatedAt, mt);
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

        // 概览统计（受年份+办事处筛选）
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<BizCustomer> baseW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");
        if (officeId != null) baseW.eq(BizCustomer::getOfficeId, officeId);

        long totalCustomers = customerMapper.selectCount(baseW);

        LambdaQueryWrapper<BizCustomer> protW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getProtectionStatus, "active");
        if (officeId != null) protW.eq(BizCustomer::getOfficeId, officeId);
        long inProtection = customerMapper.selectCount(protW);

        LambdaQueryWrapper<BizCustomer> expingW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getProtectionStatus, "expiring");
        if (officeId != null) expingW.eq(BizCustomer::getOfficeId, officeId);
        long expiring = customerMapper.selectCount(expingW);

        LambdaQueryWrapper<BizCustomer> expW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .lt(BizCustomer::getExpireDate, today);
        if (officeId != null) expW.eq(BizCustomer::getOfficeId, officeId);
        long expired = customerMapper.selectCount(expW);

        // 该年度新增客户数
        final int finalYear = year;
        LocalDateTime yearStart = LocalDateTime.of(finalYear, 1, 1, 0, 0);
        LocalDateTime yearEnd = LocalDateTime.of(finalYear + 1, 1, 1, 0, 0);
        LambdaQueryWrapper<BizCustomer> newW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .ge(BizCustomer::getCreatedAt, yearStart).lt(BizCustomer::getCreatedAt, yearEnd);
        if (officeId != null) newW.eq(BizCustomer::getOfficeId, officeId);
        long yearNew = customerMapper.selectCount(newW);

        // 行业分布（受办事处筛选）
        LambdaQueryWrapper<BizCustomer> indW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                .isNotNull(BizCustomer::getIndustry);
        if (officeId != null) indW.eq(BizCustomer::getOfficeId, officeId);
        List<BizCustomer> indCustomers = customerMapper.selectList(indW);
        Map<String, Long> industryStats = new LinkedHashMap<>();
        for (BizCustomer c : indCustomers) {
            industryStats.merge(c.getIndustry(), 1L, Long::sum);
        }
        data.put("industryStats", industryStats);

        data.put("totalCustomers", totalCustomers);
        data.put("inProtection", inProtection);
        data.put("expiring", expiring);
        data.put("expired", expired);
        data.put("yearNew", yearNew);

        return Result.ok(data);
    }

    @GetMapping("/team-activities")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<List<Map<String, Object>>> teamActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 客户跟进日志
        LambdaQueryWrapper<BizCustomerLog> logWrapper = new LambdaQueryWrapper<>();
        logWrapper.eq(BizCustomerLog::getUserId, currentUserId)
                .orderByDesc(BizCustomerLog::getCreatedAt).last("LIMIT 8");
        for (BizCustomerLog log : customerLogMapper.selectList(logWrapper)) {
            SysUser user = userMapper.selectById(log.getUserId());
            BizCustomer customer = customerMapper.selectById(log.getCustomerId());
            activities.add(buildActivity(
                    (log.getLogType() != null ? mapLogTypeLabel(log.getLogType()) : "跟进") +
                            " · " + (customer != null ? customer.getCustomerName() : ""),
                    user, log.getCreatedAt()));
        }

        // 2. 激励上传
        LambdaQueryWrapper<BizIncentiveRecord> incWrapper = new LambdaQueryWrapper<>();
        incWrapper.eq(BizIncentiveRecord::getUserId, currentUserId)
                .orderByDesc(BizIncentiveRecord::getCreatedAt).last("LIMIT 5");
        for (BizIncentiveRecord record : incentiveRecordMapper.selectList(incWrapper)) {
            SysUser user = userMapper.selectById(record.getUserId());
            BizIncentiveCategory cat = incentiveCategoryMapper.selectById(record.getCategoryId());
            activities.add(buildActivity(
                    "激励上传 · " + (cat != null ? cat.getCategoryName() : ""),
                    user, record.getCreatedAt()));
        }

        // 3. 文件上传
        LambdaQueryWrapper<BizFile> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(BizFile::getUploaderId, currentUserId)
                .orderByDesc(BizFile::getCreatedAt).last("LIMIT 5");
        for (BizFile file : fileMapper.selectList(fileWrapper)) {
            SysUser user = file.getUploaderId() != null ? userMapper.selectById(file.getUploaderId()) : null;
            activities.add(buildActivity(
                    "上传文件 · " + file.getFileName(),
                    user, file.getCreatedAt()));
        }

        // 4. 审批提交
        LambdaQueryWrapper<BizApproval> apprWrapper = new LambdaQueryWrapper<>();
        apprWrapper.eq(BizApproval::getApplicantId, currentUserId)
                .orderByDesc(BizApproval::getCreatedAt).last("LIMIT 5");
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

        // 5. 新增客户
        LambdaQueryWrapper<BizCustomer> custWrapper = new LambdaQueryWrapper<>();
        custWrapper.eq(BizCustomer::getOwnerId, currentUserId)
                .orderByDesc(BizCustomer::getCreatedAt).last("LIMIT 5");
        for (BizCustomer cust : customerMapper.selectList(custWrapper)) {
            SysUser user = cust.getOwnerId() != null ? userMapper.selectById(cust.getOwnerId()) : null;
            activities.add(buildActivity(
                    "新增客户 · " + cust.getCustomerName(),
                    user, cust.getCreatedAt()));
        }

        // 6. 系统操作日志（登录等）
        LambdaQueryWrapper<SysLog> sysWrapper = new LambdaQueryWrapper<>();
        sysWrapper.eq(SysLog::getUserId, currentUserId)
                .orderByDesc(SysLog::getCreatedAt).last("LIMIT 5");
        for (SysLog log : sysLogMapper.selectList(sysWrapper)) {
            SysUser user = log.getUserId() != null ? userMapper.selectById(log.getUserId()) : null;
            if (user == null && log.getUsername() != null) {
                // 登录日志只有username没有userId
                activities.add(buildActivitySimple(
                        log.getModule() + " · " + log.getAction(),
                        log.getUsername(), "", log.getCreatedAt()));
                continue;
            }
            activities.add(buildActivity(
                    log.getModule() + " · " + log.getAction(),
                    user, log.getCreatedAt()));
        }

        // 按时间排序，取最近15条
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

        return Result.ok(activities);
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

    private String mapLogType(String logType) {
        if (logType == null) return "follow";
        return switch (logType) {
            case "visit" -> "visit";
            case "meeting" -> "meeting";
            case "trial" -> "trial";
            default -> "follow";
        };
    }

    private String mapLogIcon(String logType) {
        if (logType == null) return "chat";
        return switch (logType) {
            case "visit" -> "location";
            case "meeting" -> "chat";
            case "trial" -> "document";
            default -> "chat";
        };
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
