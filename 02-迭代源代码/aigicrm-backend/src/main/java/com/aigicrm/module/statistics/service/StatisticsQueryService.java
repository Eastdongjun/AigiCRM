package com.aigicrm.module.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.service.ApprovalService;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerIndicator;
import com.aigicrm.module.customer.entity.BizCustomerLog;
import com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper;
import com.aigicrm.module.customer.mapper.BizCustomerLogMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
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
import com.aigicrm.module.system.service.SysConfigService;
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
 *             【裁决 2026-10-10】核心三态=纯金额门槛(年度开票/回款双>=100万=core 真核心,
 *             有数据未双达标=quasi 争核心, 无数据=非核心); 五项评分不再参与判定, 保留为客情参考;
 *             金额源=阶段总额(biz_customer_stage_amount)年度口径, 逐笔发票/回款录入已下线改只读历史
 * 【注意】只读服务，不落库；调用方（Controller）负责权限注解
 */
@Service
@RequiredArgsConstructor
public class StatisticsQueryService {

    /** 【2026-11-03 用户令门槛配置化】核心客户判定双门槛 sys_config 键(万元, 键名与 CustomerService/迁移 V2026.11.03.1 同源)。 */
    static final String CORE_MINOR_WAN_KEY = CustomerService.CORE_MINOR_WAN_KEY;
    static final String CORE_MAJOR_WAN_KEY = CustomerService.CORE_MAJOR_WAN_KEY;

    /** 【裁决 2026-10-10 纯金额三态 + 2026-11-03 双门槛配置化】核心三态:
     *  core=真核心(年度开票、回款均>=真核心门槛) / quasi=争核心(均>=争核心门槛, 未双达真核心门槛) / ''=非核心(任一低于争核心门槛)。
     *  门槛为参数不落死值(管理员在系统配置修改, 保存后全量刷新); 仅对"有阶段数据"的客户调用, 无数据客户即非核心。 */
    static String coreTypeOf(java.math.BigDecimal invoice, java.math.BigDecimal payment,
                             java.math.BigDecimal minorWan, java.math.BigDecimal majorWan) {
        boolean invoiceMet = invoice != null && invoice.compareTo(majorWan) >= 0;
        boolean paymentMet = payment != null && payment.compareTo(majorWan) >= 0;
        if (invoiceMet && paymentMet) return "core";
        boolean invoiceQuasi = invoice != null && invoice.compareTo(minorWan) >= 0;
        boolean paymentQuasi = payment != null && payment.compareTo(minorWan) >= 0;
        return invoiceQuasi && paymentQuasi ? "quasi" : "";
    }

    /** 每请求读一次双门槛配置(脏值/非正数回退默认 50/100); 统计接口同时回传给前端做门槛文案。 */
    private java.math.BigDecimal[] coreThresholds() {
        return new java.math.BigDecimal[]{
                configDecimal(CORE_MINOR_WAN_KEY, new java.math.BigDecimal("50")),
                configDecimal(CORE_MAJOR_WAN_KEY, new java.math.BigDecimal("100"))};
    }

    private java.math.BigDecimal configDecimal(String key, java.math.BigDecimal defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                java.math.BigDecimal parsed = new java.math.BigDecimal(val.trim());
                if (parsed.signum() > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值(注明吞因, 与 CustomerService.configDecimal 同款)
            }
        }
        return defaultValue;
    }

    /** 阶段行"有数据" = 开票额或回款额任一 > 0(两值均 0 的行不算数据, 不驱动判定) */
    static boolean hasStageAmount(com.aigicrm.module.customer.entity.BizCustomerStageAmount r) {
        return (r.getInvoiceAmount() != null && r.getInvoiceAmount().signum() > 0)
                || (r.getPaymentAmount() != null && r.getPaymentAmount().signum() > 0);
    }

    private static java.math.BigDecimal nvl(java.math.BigDecimal v) { return v == null ? java.math.BigDecimal.ZERO : v; }

    /**
     * 【裁决 2026-10-10】阶段总额年度口径: 每客户 → [年度开票额, 年度回款额]。
     * 年度 = 最近有数据年(有数据行的最大 period_year); 该年全年行(FY)有数据时取全年行两值
     * (全年行优先, 与上下半年独立录入可不相等), 否则上半年+下半年合计。
     * 与 CustomerService.computeCoreType / 迁移 V2026.10.10.1 回填同口径, 改动须三处同步。
     */
    static Map<Long, java.math.BigDecimal[]> annualAmountsByCustomer(
            List<com.aigicrm.module.customer.entity.BizCustomerStageAmount> rows) {
        Map<Long, Map<Integer, List<com.aigicrm.module.customer.entity.BizCustomerStageAmount>>> byCustomerYear = new HashMap<>();
        for (com.aigicrm.module.customer.entity.BizCustomerStageAmount r : rows) {
            if (r.getCustomerId() == null) continue;
            byCustomerYear.computeIfAbsent(r.getCustomerId(), k -> new HashMap<>())
                    .computeIfAbsent(r.getPeriodYear(), k -> new ArrayList<>()).add(r);
        }
        Map<Long, java.math.BigDecimal[]> result = new HashMap<>();
        for (var e : byCustomerYear.entrySet()) {
            int baseYear = 0;
            for (var ye : e.getValue().entrySet()) {
                if (ye.getValue().stream().anyMatch(StatisticsQueryService::hasStageAmount)) {
                    baseYear = Math.max(baseYear, ye.getKey());
                }
            }
            if (baseYear == 0) continue;
            List<com.aigicrm.module.customer.entity.BizCustomerStageAmount> yearRows = e.getValue().get(baseYear);
            var fy = yearRows.stream()
                    .filter(r -> "FY".equals(r.getPeriodType()) && hasStageAmount(r)).findFirst().orElse(null);
            java.math.BigDecimal inv;
            java.math.BigDecimal pay;
            if (fy != null) {
                inv = nvl(fy.getInvoiceAmount());
                pay = nvl(fy.getPaymentAmount());
            } else {
                List<com.aigicrm.module.customer.entity.BizCustomerStageAmount> halves = yearRows.stream()
                        .filter(r -> "H1".equals(r.getPeriodType()) || "H2".equals(r.getPeriodType())).toList();
                inv = halves.stream().map(r -> nvl(r.getInvoiceAmount())).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                pay = halves.stream().map(r -> nvl(r.getPaymentAmount())).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            }
            result.put(e.getKey(), new java.math.BigDecimal[]{inv, pay});
        }
        return result;
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
    /** 【WP1.5 2026-11-13】客户可见性单一源(customer:view 口径), (statistics,view) 数据范围退役 */
    private final com.aigicrm.module.customer.service.CustomerVisibilityService customerVisibilityService;
    // 【G2 2026-09-21】统计口径数据范围: 修复全员口径在租户插件关闭时跨集团泄露(审计Top1)
    private final com.aigicrm.common.scope.OrgScopeResolver orgScopeResolver;
    private final SeaService seaService;
    private final ApprovalService approvalService;
    private final BizCustomerMapper customerMapper;
    private final BizCustomerLogMapper customerLogMapper;
    private final BizIncentiveRecordMapper incentiveRecordMapper;
    private final BizIncentiveCategoryMapper incentiveCategoryMapper;
    // 【2026-09-23】客户排行四项服务聚合与激励模块分享制同口径(applyShareVisibility),复用其授权判定
    private final com.aigicrm.module.incentive.service.IncentiveService incentiveService;
    private final BizFileMapper fileMapper;
    private final BizCustomerIndicatorMapper indicatorMapper;
    // 【裁决 2026-10-10】判定金额源换 biz_customer_stage_amount; 逐笔 invoice/payment mapper 不再用于判定
    private final com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper stageAmountMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysLogMapper sysLogMapper;
    private final BizApprovalMapper approvalMapper;
    // 【容量加固 2026-10-06】看板范围签名需要与 applyDataScope 同源的锚点解析(见 dashboardScopeKey)
    private final com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService;
    // 【2026-11-03 门槛配置化】核心三态双门槛从 sys_config 读(键同 CustomerService, 不落死值)
    private final SysConfigService configService;

    /**
     * 看板汇总
     * 【需求】首页统计卡片：客户/公海/审批/到期提醒
     * 【方法】委托各模块 Service 做数据范围感知统计
     * 【业务规则】到期=expireDate 距今天数；todayExpiring 为当日到期且仍在客户范围内的数量
     * 【容量加固 2026-10-06】缓存三段演进：
     *   ①30s 用户级 TTL 缓存（当日首版）：600 用户级从 13s 劣化拉回全绿；
     *   ②TTL 0~50% 随机抖动：防"整 30s 齐过期→集中重算"的 miss 风暴；
     *   ③【本轮】同范围用户共享缓存条目：统计口径实际由 applyDataScope 的
     *     (角色锚点 × 可见用户/部门集合 × 规则引擎条件) 决定，同范围用户结果完全一致——
     *     缓存键从 userId 换成"范围签名"，1000 用户若只有 N 个不同范围，miss 率降为 1/N。
     *     范围签名取 ScopeDecision 的可见集合（OWNER 锚点=可见用户集 / ORG 锁点=可见部门集 / all），
     *     并入集团前缀与规则引擎条件指纹（admin 与非 admin 同取，无规则时=固定值 all，见 dashboardScopeKey
     *     的 C1/C2 2026-10-08 注释）。签名相同⇒12 路统计 SQL 结果必然相同。
     *     仪表盘计数容忍 ≤30s 陈旧（与权限范围缓存同口径），写操作后最迟 30s 反映。
     * 【不变量】expiringDetails 的"今日剩余天数"按天粒度变化——TTL 内跨日瞬间最多陈旧 30s，可接受。
     */
    private static final long DASHBOARD_TTL_MS = 30_000L;
    private record DashboardCacheEntry(Map<String, Object> value, long expireAt) {}
    private final java.util.concurrent.ConcurrentHashMap<String, DashboardCacheEntry> dashboardCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    public Map<String, Object> dashboard() {
        Long userId = SecurityUtils.getCurrentUserId();
        // 用户级缓存: 审批卡片(approvalStats 按 userId 过滤)与公海卡片(seaStats 按用户本人
        // 部门/池可见度判定)——【体检 P1 2026-10-12】seaStats 从共享段移入用户段: 公海池可见性
        // 是范围签名之外的独立维度, 同签名不同池可见度的用户会在 TTL 内互串公海统计;
        // 其余 7 项为范围决定, 走共享缓存。
        String scopeKey = dashboardScopeKey();
        DashboardCacheEntry hit = dashboardCache.get(scopeKey);
        UserStatsCacheEntry userHit = userStatsCache.get(userId);
        boolean dashFresh = hit != null && System.currentTimeMillis() < hit.expireAt();
        boolean userFresh = userHit != null && System.currentTimeMillis() < userHit.expireAt();
        if (dashFresh && userFresh) {
            return mergeDashboard(hit.value(), userHit.value());
        }

        Map<String, Object> shared = dashFresh ? hit.value() : computeDashboardShared();
        Map<String, Object> userStats = userFresh ? userHit.value() : computeDashboardUser(userId);
        if (!dashFresh) {
            // 【防过期风暴】TTL 加 0~50% 随机抖动：同范围条目过期时刻各异，miss 速率平滑
            long ttl = DASHBOARD_TTL_MS / 2 + (long) (Math.random() * (DASHBOARD_TTL_MS / 2));
            dashboardCache.put(scopeKey, new DashboardCacheEntry(shared, System.currentTimeMillis() + ttl));
        }
        if (!userFresh) {
            long ttl = DASHBOARD_TTL_MS / 2 + (long) (Math.random() * (DASHBOARD_TTL_MS / 2));
            userStatsCache.put(userId, new UserStatsCacheEntry(userStats, System.currentTimeMillis() + ttl));
        }
        return mergeDashboard(shared, userStats);
    }

    /** 共享段与用户段合并为新 Map（不改动缓存内条目，防止返回对象被序列化/复用时交叉污染） */
    private Map<String, Object> mergeDashboard(Map<String, Object> shared, Map<String, Object> userStats) {
        Map<String, Object> data = new HashMap<>(shared);
        data.putAll(userStats);
        return data;
    }

    /**
     * 看板范围签名：与 applyDataScope 的判定输入一一对应——
     * 集团前缀 × (admin / 可见集合) × 规则引擎条件指纹。
     * 【C2 2026-10-08】所有签名并入集团前缀(GroupScopeUtil.tenantCachePrefix，取值与租户拦截器
     *   getTenantId/resolveTenantId 同口径):原 "all"/"own:..." 等键无集团维度，第二集团上线后
     *   同键跨集团互串(取不到集团=fail-closed 固定前缀 "g-none:"，不与任何真实集团互串)。
     * 【C1 2026-10-08】非 admin 分支同样并入规则引擎指纹:applyDataScope 对非 admin 在可见集合之外
     *   还会追加 applyRuleEngineFilter(同一 evaluateForEntity(rules, user, "customer") 维度)，
     *   同可见集合不同规则的两用户统计口径不同，原签名漏该维度会互相命中对方口径；无规则=固定值 "all"
     *   (与 admin 分支同一取法)。
     * admin → "adm:" + 规则指纹（无规则即纯 all）；
     * 非 admin → 解析 ScopeDecision：all ⇒ "all"；ORG 锚点 ⇒ "org:"+有序 deptIds；OWNER 锚点 ⇒ "own:"+有序 userIds，
     * 均以 "|"+规则指纹 收尾。
     * 同签名用户对全部 8 个范围统计的结果必然一致（这些查询只依赖上述可见集合与规则条件）。
     */
    String dashboardScopeKey() { // 包可见:供同包单测直接断言键维度(C1/C2)
        var user = SecurityUtils.getCurrentUser();
        if (user == null) return "anon";
        // 【C2】集团前缀:与 MybatisPlusConfig 租户拦截器取 tenant_id 同口径(currentGroupId→最小集团→fail-closed)
        String groupPrefix = com.aigicrm.common.scope.GroupScopeUtil.tenantCachePrefix();
        // 【C1】规则引擎指纹:与 applyDataScope→applyRuleEngineFilter 同一维度,admin 与非 admin 都并入
        String ruleFp = ruleFingerprint(user);
        if (SecurityUtils.isAdmin()) {
            return groupPrefix + "adm:" + ruleFp;
        }
        com.aigicrm.common.scope.ScopeRequest req = com.aigicrm.common.scope.ScopeRequest.builder()
                .user(user)
                .module("customer")
                .action("view")
                .anchor(rolePermScopeService.anchorOf(user, "customer", "view"))
                .build();
        com.aigicrm.common.scope.ScopeDecision d = orgScopeResolver.resolveScope(req);
        String base;
        if (d.isAll()) {
            base = "all";
        } else if (req.getAnchor() == com.aigicrm.common.scope.ScopeRequest.AnchorType.ORG) {
            java.util.List<Long> ids = d.getVisibleDeptIds();
            base = (ids == null || ids.isEmpty()) ? "org:none"
                    : "org:" + ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        } else {
            java.util.List<Long> ids = d.getVisibleUserIds();
            base = (ids == null || ids.isEmpty()) ? "own:none"
                    : "own:" + ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        }
        return groupPrefix + base + "|" + ruleFp;
    }

    /**
     * 规则引擎指纹:PermissionRuleEngine.evaluateForEntity(rules, user, "customer") 的 WHERE 指纹，
     * 无规则(或无 customer 实体匹配规则)时=固定值 "all"——与 CustomerService.applyDataScope 尾部
     * 追加的 applyRuleEngineFilter 完全同维度，签名漏掉该维度会让同可见集合不同规则的用户互串统计。
     */
    private static String ruleFingerprint(com.aigicrm.security.SecurityUser user) {
        if (user.getPermissionRules() == null || user.getPermissionRules().isEmpty()) return "all";
        String ruleWhere = com.aigicrm.common.engine.PermissionRuleEngine
                .evaluateForEntity(user.getPermissionRules(), user, "customer");
        return (ruleWhere == null || ruleWhere.isEmpty()) ? "all" : Integer.toHexString(ruleWhere.hashCode());
    }

    /** 【体检 P1 2026-10-12】用户级看板缓存段(approvalStats+seaStats, 均为签名外用户维度);
     *  取代原仅审批的 ApprovalCacheEntry(两维度并入同一段, 缓存条目数不变) */
    private record UserStatsCacheEntry(Map<String, Object> value, long expireAt) {}
    private final java.util.concurrent.ConcurrentHashMap<Long, UserStatsCacheEntry> userStatsCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Map<String, Object> computeDashboardUser(Long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("approvalStats", approvalService.statistics(userId));
        data.put("seaStats", seaService.statistics());
        return data;
    }

    /** 范围共享的 7 项统计（不含 approvalStats/seaStats 两个用户级维度） */
    private Map<String, Object> computeDashboardShared() {
        Map<String, Object> data = new HashMap<>();

        data.put("myCustomers", customerService.countByScope());
        data.put("inProtection", customerService.countInProtectionByScope());
        data.put("expiring", customerService.countExpiringByScope());
        data.put("expired", customerService.countExpiredByScope());

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

    /**
     * 【G2 2026-09-21】统计可见客户过滤(安全默认口径)。
     * 【WP1.5 2026-11-13】改走 CustomerVisibilityService 单一源(customer:view 来源动作):
     * (statistics, view) 数据范围按锁定决策#7 退役, statistics:view 只做入口。
     * 口径不变: admin 全量; 客户按 ownerId 过滤; 未分配负责人的客户保持全员可见。
     */
    private List<BizCustomer> visibleCustomers(List<BizCustomer> all) {
        return customerVisibilityService.filterVisible(all);
    }

    public Map<String, Object> indicatorStats() {
        Map<String, Object> data = new HashMap<>();

        List<BizCustomerIndicator> indicators = indicatorMapper.selectList(null);
        // 【注释修正 2026-11-08】原注释"全员可见不过滤"与代码矛盾, 已按 G2 实际口径改写:
        // 指标随客户可见范围过滤(visibleCustomers: admin 全量; 其余按 statistics 模块数据范围
        // 解析可见人员并按 ownerId 过滤, 未分配负责人的客户保持可见)——统计范围必须 ≤ 明细可见范围。
        List<BizCustomer> allCustomers = visibleCustomers(customerService.getAllActiveCustomers());
        Map<Long, BizCustomer> customerMap = new HashMap<>();
        for (BizCustomer c : allCustomers) customerMap.put(c.getId(), c);
        // 【G2 2026-09-21】指标随客户可见性同步收口——coveredCount/avgScore/排行均以指标为口径,
        // 只滤客户不滤指标时 coveredCount 仍为全量(实测暴露), 越权指标行必须在此剔除
        indicators = indicators.stream()
                .filter(ind -> ind.getCustomerId() != null && customerMap.containsKey(ind.getCustomerId()))
                .collect(java.util.stream.Collectors.toList());

        // 【裁决 2026-10-10】核心判定金额源换阶段总额年度口径(纯金额三态, 五项评分不再参与);
        // 逐笔发票/回款录入入口已下线改只读历史, 不再作为判定数据源
        Map<Long, java.math.BigDecimal[]> annualAmounts = annualAmountsByCustomer(
                stageAmountMapper.selectList(null));
        // 【2026-11-03 门槛配置化】双门槛读一次配置贯穿本请求; 一并回传给前端做门槛文案(不落死值)
        java.math.BigDecimal[] coreThresholds = coreThresholds();

        // 【2026-09-13 修复"卡片全 0 而表格有数据"的口径不一致】
        // 此前四张卡片只统计"当前登录用户自己负责的客户"（myCustomers/myIndicators），而下方「客户排行」是
        // 全公司口径 —— 同一页面两套范围：登录人（如 admin）名下客户恰好没有指标记录时，卡片 4 个 0 而表格有数据。
        // 现卡片与排行统一为同一"统计可见范围"口径(G2: admin=全公司, 其余按 statistics 数据范围过滤,
        // 见上方 visibleCustomers 注释), 以及卡片文案(覆盖客户数/真核心数/争核心数/指标平均分)保持一致。
        int coveredCount = indicators.size();
        int coreCount = 0;
        int quasiCount = 0;
        double scoreSum = 0;
        for (BizCustomerIndicator ind : indicators) {
            int s = indicatorScore(ind);
            scoreSum += s;
            java.math.BigDecimal[] ann = annualAmounts.get(ind.getCustomerId());
            if (ann != null) {
                String ct = coreTypeOf(ann[0], ann[1], coreThresholds[0], coreThresholds[1]);
                if ("core".equals(ct)) coreCount++;
                else if ("quasi".equals(ct)) quasiCount++;
            }
        }
        int qualifyCount = 0;
        for (BizCustomer c : allCustomers) {
            java.math.BigDecimal[] ann = annualAmounts.get(c.getId());
            if (ann != null && ann[0].compareTo(coreThresholds[1]) >= 0 && ann[1].compareTo(coreThresholds[1]) >= 0) qualifyCount++;
        }

        data.put("coveredCount", coveredCount);
        data.put("coreCount", coreCount);
        data.put("quasiCount", quasiCount);
        data.put("coreMinorWan", coreThresholds[0]);
        data.put("coreMajorWan", coreThresholds[1]);
        data.put("avgScore", coveredCount > 0 ? Math.round(scoreSum / coveredCount * 10) / 10.0 : 0);
        data.put("qualifyRate", allCustomers.size() > 0 ? Math.round(qualifyCount * 1000.0 / allCustomers.size()) / 10.0 : 0);

        List<Map<String, Object>> customerRanking = new ArrayList<>();
        for (BizCustomerIndicator ind : indicators) {
            BizCustomer c = customerMap.get(ind.getCustomerId());
            if (c == null) continue;
            int s = indicatorScore(ind);
            if (s <= 0) continue;
            java.math.BigDecimal[] ann = annualAmounts.get(ind.getCustomerId());
            java.math.BigDecimal invTotal = ann != null ? ann[0] : java.math.BigDecimal.ZERO;
            java.math.BigDecimal paymentTotal = ann != null ? ann[1] : java.math.BigDecimal.ZERO;
            String coreType = ann != null ? coreTypeOf(invTotal, paymentTotal, coreThresholds[0], coreThresholds[1]) : "";
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
            row.put("core", "core".equals(coreType));
            row.put("coreType", coreType);
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
            java.math.BigDecimal[] ann = annualAmounts.get(ind.getCustomerId());
            int core = (ann != null && "core".equals(coreTypeOf(ann[0], ann[1], coreThresholds[0], coreThresholds[1]))) ? 1 : 0;
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
            java.math.BigDecimal[] annT = annualAmounts.get(ind.getCustomerId());
            int core = (annT != null && "core".equals(coreTypeOf(annT[0], annT[1], coreThresholds[0], coreThresholds[1]))) ? 1 : 0;
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
     * 【需求】统一展示客户对象的四项服务、客情商务关系深度、核心客户六项指标排行
     * 【业务规则】三类排行均为客户口径，按分值降序；0 分客户不展示；
     *             核心三态=年度开票/回款双>=100万为真核心, 有数据未双达标为争核心(裁决 2026-10-10)
     */
    public Map<String, Object> customerRankings(String period) {
        Map<String, Object> data = new LinkedHashMap<>();

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
        // 【口径统一 2026-11-02 用户裁决选项一】四项服务客户排行=客户可见范围(与客情/六项指标 G2 同基线):
        // 你能看到哪些客户, 三类排行就按哪些客户算。曾于 2026-09-23 由 G2 改分享制(销售反馈
        // "客户排行只看得到自己的"), 2026-11-02 用户拍板三类排行统一客户可见范围基线——单页丰富度
        // 让位于全系统数据边界一致; 分享记录引用的范围外客户不再进入本排行。
        // (激励个人排行维持公司级聚合=2026-09-24"允许暴露"裁决, 不属客户维度, 不受本口径影响)
        Map<Long, BizCustomer> customerMap = new HashMap<>();
        for (BizCustomer c : visibleCustomers(customerService.getAllActiveCustomers())) customerMap.put(c.getId(), c);
        if (!customerMap.isEmpty()) fillCustomerOwnerNames(customerMap.values());
        List<BizIncentiveRecord> records = incentiveRecordMapper.selectList(recordWrapper).stream()
                .filter(r -> r.getCustomerId() != null && customerMap.containsKey(r.getCustomerId()))
                .collect(java.util.stream.Collectors.toList());
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
        data.put("coreCount", indicatorRanking.stream().filter(row -> "core".equals(row.get("coreType"))).count());
        data.put("quasiCount", indicatorRanking.stream().filter(row -> "quasi".equals(row.get("coreType"))).count());
        // 【2026-11-03 门槛配置化】排行页门槛文案同源回传(与 indicatorStats 同一份配置读数)
        data.put("coreMinorWan", indicatorStats.get("coreMinorWan"));
        data.put("coreMajorWan", indicatorStats.get("coreMajorWan"));
        return data;
    }

    /** 批量回填客户 ownerName(exist=false 字段,批量取客户后统一补负责人名) */
    private void fillCustomerOwnerNames(java.util.Collection<BizCustomer> customers) {
        Set<Long> ownerIds = new HashSet<>();
        for (BizCustomer c : customers) {
            if (c.getOwnerId() != null) ownerIds.add(c.getOwnerId());
        }
        if (ownerIds.isEmpty()) return;
        Map<Long, String> nameById = new HashMap<>();
        for (SysUser u : userMapper.selectBatchIds(ownerIds)) nameById.put(u.getId(), u.getRealName());
        for (BizCustomer c : customers) {
            if (c.getOwnerId() != null) c.setOwnerName(nameById.get(c.getOwnerId()));
        }
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
            cw.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
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
        // 【WP1.5 2026-11-13】办事处列表先按可见范围过滤(customer:view): 范围外办事处不再进入对比循环
        // (此前靠 total==0 跳过, 范围外每处白跑 3 条 count; 输出口径不变, 只列有可见客户的办事处)
        java.util.Set<Long> visibleOfficeIds = null;
        {
            List<Long> visOwners = customerVisibilityService.visibleOwnerIds();
            if (visOwners != null) {
                visibleOfficeIds = new java.util.HashSet<>();
                if (!visOwners.isEmpty()) {
                    LambdaQueryWrapper<BizCustomer> vwc = new LambdaQueryWrapper<>();
                    vwc.eq(BizCustomer::getDeleted, 0)
                       .notIn(BizCustomer::getStatus, "sea", "archived")
                       .isNotNull(BizCustomer::getOfficeId)
                       .in(BizCustomer::getOwnerId, visOwners);
                    for (BizCustomer vc : customerMapper.selectList(vwc)) {
                        visibleOfficeIds.add(vc.getOfficeId());
                    }
                }
            }
        }
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        for (var dept : allDepts) {
            if (visibleOfficeIds != null && !visibleOfficeIds.contains(dept.getId())) continue; // 【WP1.5】范围外办事处不入列表
            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
             .eq(BizCustomer::getOfficeId, dept.getId());
            applyCustomerScope(w);
            long total = customerMapper.selectCount(w);
            if (total == 0) continue;

            LambdaQueryWrapper<BizCustomer> aw = new LambdaQueryWrapper<>();
            // 【体检 P1 2026-10-12】活跃率分子补 notIn(sea/archived)与分母/同方法 inProtection 对齐:
            // 归档不清 protectionStatus, 不排时分子可含归档客户(虚高, 极端时 >100%)
            aw.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "active")
              .notIn(BizCustomer::getStatus, "sea", "archived")
              .eq(BizCustomer::getOfficeId, dept.getId());
            applyCustomerScope(aw);
            long active = customerMapper.selectCount(aw);

            LambdaQueryWrapper<BizCustomer> nw = new LambdaQueryWrapper<>();
            nw.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
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
                rw.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
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
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived"); // B5 统一口径: 排sea+archived
        if (deptId != null) baseW.in(BizCustomer::getOfficeId, com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) baseW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(baseW);
        long totalCustomers = customerMapper.selectCount(baseW);

        LambdaQueryWrapper<BizCustomer> protW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .eq(BizCustomer::getProtectionStatus, "active");
        if (deptId != null) protW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) protW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(protW);
        long inProtection = customerMapper.selectCount(protW);

        LambdaQueryWrapper<BizCustomer> expingW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .eq(BizCustomer::getProtectionStatus, "expiring");
        if (deptId != null) expingW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) expingW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(expingW);
        long expiring = customerMapper.selectCount(expingW);

        LambdaQueryWrapper<BizCustomer> expW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .lt(BizCustomer::getExpireDate, today);
        if (deptId != null) expW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) expW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(expW);
        long expired = customerMapper.selectCount(expW);

        LambdaQueryWrapper<BizCustomer> todayExpiringW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
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
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .ge(BizCustomer::getCreatedAt, yearStart).lt(BizCustomer::getCreatedAt, yearEnd);
        if (deptId != null) newW.in(BizCustomer::getOfficeId,
                com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        else if (officeId != null) newW.eq(BizCustomer::getOfficeId, officeId);
        applyCustomerScope(newW);
        long yearNew = customerMapper.selectCount(newW);

        LambdaQueryWrapper<BizCustomer> indW = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
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
        // 【WP1.5 2026-11-13】口径切换 customer:view(可见人员=客户可见负责人集合)
        List<Long> visibleUserIds = customerVisibilityService.visibleOwnerIds();
        boolean scopeAll = (visibleUserIds == null);
        // 【A6 2026-10-20 审计】空集守卫: ORG 锚点已在 OrgScopeResolver 展开为部门在职用户,
        // 但部门无在职用户/解析兜底返回空集时, in(空集合) 生成 IN () 语法错误(500)——
        // 六路来源同口径恒空, 直接返回空列表, 不再各发一条恒空查询(语义等同 1=0)。
        if (!scopeAll && visibleUserIds.isEmpty()) {
            return activities;
        }

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
     * 【注释修正 2026-11-08】核心客户指标(indicatorStats)自 G2 起同受统计可见范围过滤
     * （visibleCustomers 按客户可见范围过滤, 见该方法内注释）, 原"六项指标全员可见不过滤"例外表述作废。
     * getCurrentScopeUserIds 返回 null=全量不过滤。
     * 【A6 2026-10-20 审计】空集(非 null 空集合)显式 1=0: MyBatis-Plus 3.5.17 的 in(空集合)
     * 生成 IN () 语法错误→统计接口 500。空集语义=范围内无可见用户, 恒空结果, 不得报错也不得放行全量。
     */
    private void applyCustomerScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        // 【WP1.5 2026-11-13】来源动作统一 (customer, view)——(statistics,view) 退役(锁定决策#7)
        List<Long> userIds = customerVisibilityService.visibleOwnerIds();
        if (userIds == null) return; // all：不过滤
        if (userIds.isEmpty()) {
            wrapper.apply("1=0");
            return;
        }
        wrapper.in(BizCustomer::getOwnerId, userIds);
    }

    /** 同 applyCustomerScope 的空集守卫(上传者维度)【A6】 */
    private void applyFileScope(LambdaQueryWrapper<BizFile> wrapper) {
        // 【WP1.5】同口径切换 customer:view(WP4 再按来源动作细分为 file:view)
        List<Long> userIds = customerVisibilityService.visibleOwnerIds();
        if (userIds == null) return; // all：不过滤
        if (userIds.isEmpty()) {
            wrapper.apply("1=0");
            return;
        }
        wrapper.in(BizFile::getUploaderId, userIds);
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
