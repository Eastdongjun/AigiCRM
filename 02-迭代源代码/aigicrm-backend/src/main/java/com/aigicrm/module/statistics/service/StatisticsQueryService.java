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
import java.util.concurrent.ThreadLocalRandom;
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
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_ARCHIVED = "archived";
    private static final String LIT_QUASI = "quasi";
    private static final String LIT_COREMINORWAN = "coreMinorWan";
    private static final String LIT_COREMAJORWAN = "coreMajorWan";
    private static final String LIT_CORECOUNT = "coreCount";
    private static final String LIT_CORETYPE = "coreType";
    private static final String LIT_CUSTOMER = "customer";
    private static final String LIT_EXPIRING = "expiring";
    private static final String LIT_CUSTOMERNAME = "customerName";
    private static final String LIT_AVGSCORE = "avgScore";
    private static final String LIT_RELATIONSHIPSCORE = "relationshipScore";
    private static final String LIT_TOTALSCORE = "totalScore";
    private static final String LIT_DEPTNAME = "deptName";
    private static final String LIT_CUSTOMERCOUNT = "customerCount";
    private static final String LIT_RECORDCOUNT = "recordCount";
    private static final String LIT_BYPARENT = "byParent";
    private static final String LIT_LIMIT_5 = "LIMIT 5";


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
        return invoiceQuasi && paymentQuasi ? LIT_QUASI : "";
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
        Map<Long, Map<Integer, List<com.aigicrm.module.customer.entity.BizCustomerStageAmount>>> byCustomerYear = groupByCustomerYear(rows);
        Map<Long, java.math.BigDecimal[]> result = new HashMap<>();
        for (var e : byCustomerYear.entrySet()) {
            int baseYear = baseYearOf(e.getValue());
            if (baseYear == 0) continue;
            result.put(e.getKey(), annualPair(e.getValue().get(baseYear)));
        }
        return result;
    }

    private static Map<Long, Map<Integer, List<com.aigicrm.module.customer.entity.BizCustomerStageAmount>>> groupByCustomerYear(
            List<com.aigicrm.module.customer.entity.BizCustomerStageAmount> rows) {
        Map<Long, Map<Integer, List<com.aigicrm.module.customer.entity.BizCustomerStageAmount>>> out = new HashMap<>();
        for (com.aigicrm.module.customer.entity.BizCustomerStageAmount r : rows) {
            if (r.getCustomerId() == null) continue;
            out.computeIfAbsent(r.getCustomerId(), k -> new HashMap<>())
                    .computeIfAbsent(r.getPeriodYear(), k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    private static int baseYearOf(Map<Integer, List<com.aigicrm.module.customer.entity.BizCustomerStageAmount>> byYear) {
        int baseYear = 0;
        for (var ye : byYear.entrySet()) {
            if (ye.getValue().stream().anyMatch(StatisticsQueryService::hasStageAmount)) {
                baseYear = Math.max(baseYear, ye.getKey());
            }
        }
        return baseYear;
    }

    private static java.math.BigDecimal[] annualPair(List<com.aigicrm.module.customer.entity.BizCustomerStageAmount> yearRows) {
        var fy = yearRows.stream()
                .filter(r -> "FY".equals(r.getPeriodType()) && hasStageAmount(r)).findFirst().orElse(null);
        if (fy != null) {
            return new java.math.BigDecimal[]{nvl(fy.getInvoiceAmount()), nvl(fy.getPaymentAmount())};
        }
        List<com.aigicrm.module.customer.entity.BizCustomerStageAmount> halves = yearRows.stream()
                .filter(r -> "H1".equals(r.getPeriodType()) || "H2".equals(r.getPeriodType())).toList();
        java.math.BigDecimal inv = halves.stream().map(r -> nvl(r.getInvoiceAmount())).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal pay = halves.stream().map(r -> nvl(r.getPaymentAmount())).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return new java.math.BigDecimal[]{inv, pay};
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
    // 【WP4/WP5 2026-11-13】权限版本号(缓存签名即时失效: 保存授权→统计换轨不等 TTL)
    private final com.aigicrm.common.auth.PermEpochService permEpochService;

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
            long ttl = ThreadLocalRandom.current().nextLong(DASHBOARD_TTL_MS / 2, DASHBOARD_TTL_MS);
            dashboardCache.put(scopeKey, new DashboardCacheEntry(shared, System.currentTimeMillis() + ttl));
        }
        if (!userFresh) {
            long ttl = ThreadLocalRandom.current().nextLong(DASHBOARD_TTL_MS / 2, DASHBOARD_TTL_MS);
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
     *   还会追加 applyRuleEngineFilter(同一 evaluateForEntity(rules, user, LIT_CUSTOMER) 维度)，
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
        // 【WP4/WP5 2026-11-13】权限版本号入签名: 任何权限写点(epoch bump)后共享缓存段立即失效——
        // "保存授权→统计口径即时换轨"(卡片=列表=导出 同 epoch), 不再等 30s TTL 陈旧窗
        String epochPrefix = "e" + permEpochService.current() + ":";
        // 【C2】集团前缀:与 MybatisPlusConfig 租户拦截器取 tenant_id 同口径(currentGroupId→最小集团→fail-closed)
        String groupPrefix = com.aigicrm.common.scope.GroupScopeUtil.tenantCachePrefix();
        // 【C1】规则引擎指纹:与 applyDataScope→applyRuleEngineFilter 同一维度,admin 与非 admin 都并入
        String ruleFp = ruleFingerprint(user);
        com.aigicrm.common.scope.ScopeRequest req = com.aigicrm.common.scope.ScopeRequest.builder()
                .user(user)
                .module(LIT_CUSTOMER)
                .action("view")
                .anchor(rolePermScopeService.anchorOf(user, LIT_CUSTOMER, "view"))
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
        return epochPrefix + groupPrefix + base + "|" + ruleFp;
    }

    /**
     * 规则引擎指纹:PermissionRuleEngine.evaluateForEntity(rules, user, LIT_CUSTOMER) 的 WHERE 指纹，
     * 无规则(或无 customer 实体匹配规则)时=固定值 "all"——与 CustomerService.applyDataScope 尾部
     * 追加的 applyRuleEngineFilter 完全同维度，签名漏掉该维度会让同可见集合不同规则的用户互串统计。
     */
    private static String ruleFingerprint(com.aigicrm.security.SecurityUser user) {
        if (user.getPermissionRules() == null || user.getPermissionRules().isEmpty()) return "all";
        String ruleWhere = com.aigicrm.common.engine.PermissionRuleEngine
                .evaluateForEntity(user.getPermissionRules(), user, LIT_CUSTOMER);
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
        data.put(LIT_EXPIRING, customerService.countExpiringByScope());
        data.put("expired", customerService.countExpiredByScope());

        LocalDate today = LocalDate.now();
        List<BizCustomer> expiringList = customerService.getExpiringByScope(5);
        List<Map<String, Object>> expiringDetails = new ArrayList<>();
        for (BizCustomer c : expiringList) {
            Map<String, Object> item = new HashMap<>();
            item.put(LIT_CUSTOMERNAME, c.getCustomerName());
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
        IndicatorContext ctx = loadIndicatorContext();
        IndicatorCards cards = computeIndicatorCards(ctx);

        data.put("coveredCount", cards.coveredCount());
        data.put(LIT_CORECOUNT, cards.coreCount());
        data.put("quasiCount", cards.quasiCount());
        data.put(LIT_COREMINORWAN, ctx.coreThresholds()[0]);
        data.put(LIT_COREMAJORWAN, ctx.coreThresholds()[1]);
        data.put(LIT_AVGSCORE, cards.avgScore());
        data.put("qualifyRate", cards.qualifyRate());

        data.put("customerRanking", buildCustomerRanking(ctx));

        List<Map<String, Object>> personalRanking = buildPersonalRanking(ctx);
        data.put("personalRanking", personalRanking);
        data.put("deptRanking", buildDeptRanking(personalRanking));
        data.put("regionRanking", buildRegionRanking(ctx));
        return data;
    }

    /**
     * 本次请求共享的指标上下文：可见客户/指标、年度金额、核心门槛。
     * 普通载体类而非 record——内含数组值 Map，record 生成的 equals/hashCode 会按引用比较数组内容
     * (Sonar java:S6218)，而本类只作方法间传参、从不参与比较。
     */
    private static final class IndicatorContext {
        private final List<BizCustomerIndicator> indicators;
        private final List<BizCustomer> allCustomers;
        private final Map<Long, BizCustomer> customerMap;
        private final Map<Long, java.math.BigDecimal[]> annualAmounts;
        private final java.math.BigDecimal[] coreThresholds;

        IndicatorContext(List<BizCustomerIndicator> indicators, List<BizCustomer> allCustomers,
                         Map<Long, BizCustomer> customerMap, Map<Long, java.math.BigDecimal[]> annualAmounts,
                         java.math.BigDecimal[] coreThresholds) {
            this.indicators = indicators;
            this.allCustomers = allCustomers;
            this.customerMap = customerMap;
            this.annualAmounts = annualAmounts;
            this.coreThresholds = coreThresholds;
        }

        List<BizCustomerIndicator> indicators() { return indicators; }
        List<BizCustomer> allCustomers() { return allCustomers; }
        Map<Long, BizCustomer> customerMap() { return customerMap; }
        Map<Long, java.math.BigDecimal[]> annualAmounts() { return annualAmounts; }
        java.math.BigDecimal[] coreThresholds() { return coreThresholds; }
    }

    /** 顶部四张卡片口径（覆盖数/真核心/争核心/平均分/达标率）。 */
    private record IndicatorCards(int coveredCount, int coreCount, int quasiCount,
                                  double avgScore, double qualifyRate) {}

    private IndicatorContext loadIndicatorContext() {
        List<BizCustomerIndicator> indicators = indicatorMapper.selectList(null);
        // 【注释修正 2026-11-08】指标随客户可见范围过滤(visibleCustomers: admin 全量; 其余按 statistics 模块
        // 数据范围解析可见人员并按 ownerId 过滤, 未分配负责人的客户保持可见)——统计范围必须 ≤ 明细可见范围。
        List<BizCustomer> allCustomers = visibleCustomers(customerService.getAllActiveCustomers());
        Map<Long, BizCustomer> customerMap = new HashMap<>();
        for (BizCustomer c : allCustomers) customerMap.put(c.getId(), c);
        // 【排行负责人修复 2026-11-17】ownerName 是 exist=false 字段，必须批量回填
        if (!customerMap.isEmpty()) fillCustomerOwnerNames(customerMap.values());
        // 【G2 2026-09-21】指标随客户可见性同步收口——coveredCount/avgScore/排行均以指标为口径
        indicators = indicators.stream()
                .filter(ind -> ind.getCustomerId() != null && customerMap.containsKey(ind.getCustomerId()))
                .collect(java.util.stream.Collectors.toList());

        // 【裁决 2026-10-10】核心判定金额源换阶段总额年度口径(纯金额三态, 五项评分不再参与)
        Map<Long, java.math.BigDecimal[]> annualAmounts = annualAmountsByCustomer(
                stageAmountMapper.selectList(null));
        // 【2026-11-03 门槛配置化】双门槛读一次配置贯穿本请求
        java.math.BigDecimal[] coreThresholds = coreThresholds();
        return new IndicatorContext(indicators, allCustomers, customerMap, annualAmounts, coreThresholds);
    }

    private IndicatorCards computeIndicatorCards(IndicatorContext ctx) {
        // 【2026-09-13 修复"卡片全 0 而表格有数据"的口径不一致】卡片与排行统一为同一"统计可见范围"口径
        int coveredCount = ctx.indicators().size();
        int coreCount = 0;
        int quasiCount = 0;
        double scoreSum = 0;
        for (BizCustomerIndicator ind : ctx.indicators()) {
            java.util.Objects.requireNonNull(ind, "indicator");
            scoreSum += indicatorScore(ind);
            java.math.BigDecimal[] ann = ctx.annualAmounts().get(ind.getCustomerId());
            if (ann != null) {
                String ct = coreTypeOf(ann[0], ann[1], ctx.coreThresholds()[0], ctx.coreThresholds()[1]);
                if ("core".equals(ct)) coreCount++;
                else if (LIT_QUASI.equals(ct)) quasiCount++;
            }
        }
        int qualifyCount = countQualified(ctx);
        double avgScore = coveredCount > 0 ? Math.round(scoreSum / coveredCount * 10) / 10.0 : 0;
        double qualifyRate = !ctx.allCustomers().isEmpty()
                ? Math.round(qualifyCount * 1000.0 / ctx.allCustomers().size()) / 10.0 : 0;
        return new IndicatorCards(coveredCount, coreCount, quasiCount, avgScore, qualifyRate);
    }

    private int countQualified(IndicatorContext ctx) {
        int qualifyCount = 0;
        for (BizCustomer c : ctx.allCustomers()) {
            if (isQualified(c, ctx.annualAmounts(), ctx.coreThresholds()[1])) qualifyCount++;
        }
        return qualifyCount;
    }

    private boolean isQualified(BizCustomer c, Map<Long, java.math.BigDecimal[]> annualAmounts,
                                java.math.BigDecimal majorThreshold) {
        java.math.BigDecimal[] ann = annualAmounts.get(c.getId());
        return ann != null && ann[0].compareTo(majorThreshold) >= 0 && ann[1].compareTo(majorThreshold) >= 0;
    }

    private List<Map<String, Object>> buildCustomerRanking(IndicatorContext ctx) {
        List<Map<String, Object>> customerRanking = new ArrayList<>();
        for (BizCustomerIndicator ind : ctx.indicators()) {
            BizCustomer c = ctx.customerMap().get(ind.getCustomerId());
            int s = indicatorScore(ind);
            if (c != null && s > 0) {
                customerRanking.add(buildCustomerRankingRow(ind, c, s, ctx.annualAmounts(), ctx.coreThresholds()));
            }
        }
        customerRanking.sort((a, b) -> {
            int cmp = Integer.compare(((Number) b.get(LIT_TOTALSCORE)).intValue(), ((Number) a.get(LIT_TOTALSCORE)).intValue());
            if (cmp != 0) return cmp;
            return String.valueOf(a.getOrDefault(LIT_CUSTOMERNAME, "")).compareTo(String.valueOf(b.getOrDefault(LIT_CUSTOMERNAME, "")));
        });
        return customerRanking;
    }

    private Map<String, Object> buildCustomerRankingRow(BizCustomerIndicator ind, BizCustomer c, int s,
                                                        Map<Long, java.math.BigDecimal[]> annualAmounts,
                                                        java.math.BigDecimal[] coreThresholds) {
        java.math.BigDecimal[] ann = annualAmounts.get(ind.getCustomerId());
        java.math.BigDecimal invTotal = ann != null ? ann[0] : java.math.BigDecimal.ZERO;
        java.math.BigDecimal paymentTotal = ann != null ? ann[1] : java.math.BigDecimal.ZERO;
        String coreType = ann != null ? coreTypeOf(invTotal, paymentTotal, coreThresholds[0], coreThresholds[1]) : "";
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customerId", ind.getCustomerId());
        row.put(LIT_CUSTOMERNAME, c.getCustomerName());
        row.put("ownerName", c.getOwnerName());
        row.put("industry", c.getIndustry());
        row.put("devType", c.getDevType());
        row.put("invoiceTotal", invTotal);
        row.put("paymentTotal", paymentTotal);
        row.put(LIT_RELATIONSHIPSCORE, ind.getRelationshipScore() != null ? ind.getRelationshipScore() : 0);
        row.put("indicator02", ind.getIndicator02() != null ? ind.getIndicator02() : 0);
        row.put("indicator03", ind.getIndicator03() != null ? ind.getIndicator03() : 0);
        row.put("indicator04", ind.getIndicator04() != null ? ind.getIndicator04() : 0);
        row.put("indicator05", ind.getIndicator05() != null ? ind.getIndicator05() : 0);
        row.put(LIT_TOTALSCORE, s);
        row.put("core", "core".equals(coreType));
        row.put(LIT_CORETYPE, coreType);
        return row;
    }

    private List<Map<String, Object>> buildPersonalRanking(IndicatorContext ctx) {
        Map<Long, Long> ownerCustomerCounts = countOwnerCustomers(ctx.allCustomers());
        Map<Long, List<int[]>> ownerScores = scoreByOwner(ctx);
        Set<Long> allOwnerIds = new HashSet<>(ownerCustomerCounts.keySet());
        allOwnerIds.addAll(ownerScores.keySet());

        // 【修复 FEAT-20260804-INDICATOR-N1】批量预取用户和部门，避免循环内逐条 selectById
        Map<Long, SysUser> userById = loadUsers(allOwnerIds);
        Map<Long, SysDept> deptById = loadUserDepts(userById.values());

        List<Map<String, Object>> personalRanking = new ArrayList<>();
        for (Long uid : allOwnerIds) {
            SysUser user = userById.get(uid);
            if (user != null) {
                personalRanking.add(buildPersonalRow(uid, user, deptById, ownerScores, ownerCustomerCounts));
            }
        }
        personalRanking.sort((a, b) -> {
            int cmp = Integer.compare(((Number) b.get(LIT_TOTALSCORE)).intValue(), ((Number) a.get(LIT_TOTALSCORE)).intValue());
            if (cmp != 0) return cmp;
            return Long.compare(((Number) b.get(LIT_CUSTOMERCOUNT)).longValue(), ((Number) a.get(LIT_CUSTOMERCOUNT)).longValue());
        });
        return personalRanking;
    }

    private Map<Long, Long> countOwnerCustomers(List<BizCustomer> allCustomers) {
        Map<Long, Long> ownerCustomerCounts = new HashMap<>();
        for (BizCustomer c : allCustomers) {
            if (c.getOwnerId() != null) {
                ownerCustomerCounts.merge(c.getOwnerId(), 1L, Long::sum);
            }
        }
        return ownerCustomerCounts;
    }

    private Map<Long, List<int[]>> scoreByOwner(IndicatorContext ctx) {
        Map<Long, List<int[]>> ownerScores = new HashMap<>();
        for (BizCustomerIndicator ind : ctx.indicators()) {
            BizCustomer c = ctx.customerMap().get(ind.getCustomerId());
            if (c != null && c.getOwnerId() != null) {
                int s = indicatorScore(ind);
                java.math.BigDecimal[] ann = ctx.annualAmounts().get(ind.getCustomerId());
                int core = isCoreAmount(ann, ctx.coreThresholds()) ? 1 : 0;
                ownerScores.computeIfAbsent(c.getOwnerId(), k -> new ArrayList<>()).add(new int[]{s, core});
            }
        }
        return ownerScores;
    }

    private boolean isCoreAmount(java.math.BigDecimal[] ann, java.math.BigDecimal[] coreThresholds) {
        return ann != null && "core".equals(coreTypeOf(ann[0], ann[1], coreThresholds[0], coreThresholds[1]));
    }

    private Map<Long, SysUser> loadUsers(Set<Long> allOwnerIds) {
        return allOwnerIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(allOwnerIds).stream()
                        .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));
    }

    private Map<Long, SysDept> loadUserDepts(java.util.Collection<SysUser> users) {
        Set<Long> deptIdsNeeded = new HashSet<>();
        for (SysUser u : users) {
            if (u.getDeptId() != null) deptIdsNeeded.add(u.getDeptId());
        }
        return loadDepts(deptIdsNeeded);
    }

    private Map<Long, SysDept> loadDepts(Set<Long> deptIdsNeeded) {
        return deptIdsNeeded.isEmpty() ? Map.of()
                : deptMapper.selectByIds(deptIdsNeeded).stream()
                        .collect(java.util.stream.Collectors.toMap(SysDept::getId, d -> d));
    }

    private Map<String, Object> buildPersonalRow(Long uid, SysUser user, Map<Long, SysDept> deptById,
                                                 Map<Long, List<int[]>> ownerScores,
                                                 Map<Long, Long> ownerCustomerCounts) {
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
        row.put(LIT_DEPTNAME, deptName);
        row.put(LIT_CUSTOMERCOUNT, custCount);
        row.put(LIT_CORECOUNT, cores);
        row.put(LIT_AVGSCORE, avgScoreP);
        row.put("maxScore", maxScore);
        row.put(LIT_TOTALSCORE, totalScore);
        row.put("level", cores > 0 ? "优秀" : "一般");
        return row;
    }

    private List<Map<String, Object>> buildDeptRanking(List<Map<String, Object>> personalRanking) {
        Map<String, int[]> deptAgg = new LinkedHashMap<>();
        for (var p : personalRanking) {
            String dn = (String) p.getOrDefault(LIT_DEPTNAME, "未知");
            int[] agg = deptAgg.computeIfAbsent(dn, k -> new int[4]);
            agg[0] += ((Number) p.get(LIT_TOTALSCORE)).intValue();
            agg[1] += ((Number) p.get(LIT_CUSTOMERCOUNT)).intValue();
            agg[2] += ((Number) p.get(LIT_CORECOUNT)).intValue();
            agg[3] += 1;
        }
        List<Map<String, Object>> deptRanking = new ArrayList<>();
        for (var e : deptAgg.entrySet()) {
            deptRanking.add(buildDeptRow(e.getKey(), e.getValue()));
        }
        deptRanking.sort((a, b) -> Integer.compare(((Number) b.get(LIT_TOTALSCORE)).intValue(), ((Number) a.get(LIT_TOTALSCORE)).intValue()));
        return deptRanking;
    }

    private Map<String, Object> buildDeptRow(String deptName, int[] agg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(LIT_DEPTNAME, deptName);
        row.put(LIT_TOTALSCORE, agg[0]);
        row.put(LIT_CUSTOMERCOUNT, agg[1]);
        row.put(LIT_CORECOUNT, agg[2]);
        row.put("memberCount", agg[3]);
        row.put(LIT_AVGSCORE, agg[1] > 0 ? Math.round(agg[0] * 10.0 / agg[1]) / 10.0 : 0);
        return row;
    }

    private List<Map<String, Object>> buildRegionRanking(IndicatorContext ctx) {
        // 批量预取办事处，避免循环内逐条 selectById
        Set<Long> officeIdsNeeded = new HashSet<>();
        for (BizCustomer c : ctx.allCustomers()) {
            if (c.getOfficeId() != null) officeIdsNeeded.add(c.getOfficeId());
        }
        Map<Long, SysDept> officeById = loadDepts(officeIdsNeeded);

        Map<String, int[]> officeAgg = new LinkedHashMap<>();
        for (BizCustomerIndicator ind : ctx.indicators()) {
            BizCustomer c = ctx.customerMap().get(ind.getCustomerId());
            if (c != null) {
                String officeName = officeNameOf(c, officeById);
                int s = indicatorScore(ind);
                java.math.BigDecimal[] annT = ctx.annualAmounts().get(ind.getCustomerId());
                int core = isCoreAmount(annT, ctx.coreThresholds()) ? 1 : 0;
                int[] agg = officeAgg.computeIfAbsent(officeName.isEmpty() ? "未知" : officeName, k -> new int[3]);
                agg[0] += s;
                agg[1] += 1;
                agg[2] += core;
            }
        }
        List<Map<String, Object>> regionRanking = new ArrayList<>();
        for (var e : officeAgg.entrySet()) {
            regionRanking.add(buildRegionRow(e.getKey(), e.getValue()));
        }
        regionRanking.sort((a, b) -> Integer.compare(((Number) b.get(LIT_TOTALSCORE)).intValue(), ((Number) a.get(LIT_TOTALSCORE)).intValue()));
        return regionRanking;
    }

    private String officeNameOf(BizCustomer c, Map<Long, SysDept> officeById) {
        if (c.getOfficeId() == null) return "";
        var dept = officeById.get(c.getOfficeId());
        return dept != null ? dept.getDeptName() : "";
    }

    private Map<String, Object> buildRegionRow(String regionName, int[] agg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("regionName", regionName);
        row.put(LIT_TOTALSCORE, agg[0]);
        row.put(LIT_CUSTOMERCOUNT, agg[1]);
        row.put(LIT_CORECOUNT, agg[2]);
        row.put(LIT_AVGSCORE, agg[1] > 0 ? Math.round(agg[0] * 10.0 / agg[1]) / 10.0 : 0);
        return row;
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
            row.put(LIT_TOTALSCORE, ((Number) row.getOrDefault(LIT_TOTALSCORE, 0)).intValue() + score);
            row.put(LIT_RECORDCOUNT, ((Number) row.getOrDefault(LIT_RECORDCOUNT, 0)).intValue() + 1);
            @SuppressWarnings("unchecked")
            Map<Long, int[]> byParent = (Map<Long, int[]>) row.computeIfAbsent(LIT_BYPARENT, key -> new LinkedHashMap<Long, int[]>());
            Long parentId = rootCategoryId(record.getCategoryId(), categoryMap);
            if (parentId != null) {
                int[] summary = byParent.computeIfAbsent(parentId, key -> new int[2]);
                summary[0] += 1;
                summary[1] += score;
            }
        }
        List<Map<String, Object>> incentiveRanking = incentiveRows.values().stream()
                .filter(row -> ((Number) row.getOrDefault(LIT_TOTALSCORE, 0)).intValue() > 0)
                .map(this::serializeIncentiveRankingRow)
                .sorted(this::compareTotalScoreThenCustomer)
                .toList();

        Map<String, Object> indicatorStats = indicatorStats();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> indicatorRanking = (List<Map<String, Object>>) indicatorStats.getOrDefault("customerRanking", List.of());
        List<Map<String, Object>> relationshipRanking = indicatorRanking.stream()
                .filter(row -> ((Number) row.getOrDefault(LIT_RELATIONSHIPSCORE, 0)).intValue() > 0)
                .sorted((a, b) -> {
                    int cmp = Integer.compare(((Number) b.getOrDefault(LIT_RELATIONSHIPSCORE, 0)).intValue(),
                            ((Number) a.getOrDefault(LIT_RELATIONSHIPSCORE, 0)).intValue());
                    if (cmp != 0) return cmp;
                    return String.valueOf(a.getOrDefault(LIT_CUSTOMERNAME, "")).compareTo(String.valueOf(b.getOrDefault(LIT_CUSTOMERNAME, "")));
                })
                .toList();

        data.put("period", period);
        data.put("incentiveParents", incentiveParents);
        data.put("incentiveRanking", incentiveRanking);
        data.put("relationshipRanking", relationshipRanking);
        data.put("indicatorRanking", indicatorRanking);
        data.put(LIT_CORECOUNT, indicatorRanking.stream().filter(row -> "core".equals(row.get(LIT_CORETYPE))).count());
        data.put("quasiCount", indicatorRanking.stream().filter(row -> LIT_QUASI.equals(row.get(LIT_CORETYPE))).count());
        // 【2026-11-03 门槛配置化】排行页门槛文案同源回传(与 indicatorStats 同一份配置读数)
        data.put(LIT_COREMINORWAN, indicatorStats.get(LIT_COREMINORWAN));
        data.put(LIT_COREMAJORWAN, indicatorStats.get(LIT_COREMAJORWAN));
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
        row.put(LIT_CUSTOMERNAME, customer.getCustomerName());
        row.put("ownerName", customer.getOwnerName());
        row.put("industry", customer.getIndustry());
        row.put(LIT_TOTALSCORE, 0);
        row.put(LIT_RECORDCOUNT, 0);
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
        Map<Long, int[]> raw = (Map<Long, int[]>) row.remove(LIT_BYPARENT);
        Map<String, Map<String, Integer>> serialized = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<Long, int[]> entry : raw.entrySet()) {
                Map<String, Integer> value = new LinkedHashMap<>();
                value.put("count", entry.getValue()[0]);
                value.put("score", entry.getValue()[1]);
                serialized.put(String.valueOf(entry.getKey()), value);
            }
        }
        row.put(LIT_BYPARENT, serialized);
        return row;
    }

    private int compareTotalScoreThenCustomer(Map<String, Object> a, Map<String, Object> b) {
        int cmp = Integer.compare(((Number) b.getOrDefault(LIT_TOTALSCORE, 0)).intValue(),
                ((Number) a.getOrDefault(LIT_TOTALSCORE, 0)).intValue());
        if (cmp != 0) return cmp;
        return String.valueOf(a.getOrDefault(LIT_CUSTOMERNAME, "")).compareTo(String.valueOf(b.getOrDefault(LIT_CUSTOMERNAME, "")));
    }

    /**
     * 经营分析
     * 【需求】年度月度新增/文件、办事处对比、概览统计、行业分布
     * 【方法】按年/办事处条件查询并聚合；officeId 筛选时所有统计同步收窄
     */
    public Map<String, Object> analysis(int year, Long officeId, Long deptId) {
        final int finalYear = (year == 0) ? LocalDate.now().getYear() : year;
        Map<String, Object> data = new HashMap<>();

        data.put("monthlyCustomers", monthlyCustomerCounts(finalYear, officeId, deptId));
        data.put("monthlyFiles", monthlyFileCounts(finalYear));
        data.put("officeComparison", buildOfficeComparison(visibleOfficeIdsForCompare()));

        LocalDate today = LocalDate.now();
        LocalDateTime yearStart = LocalDateTime.of(finalYear, 1, 1, 0, 0);
        LocalDateTime yearEnd = LocalDateTime.of(finalYear + 1, 1, 1, 0, 0);
        data.put("totalCustomers", customerMapper.selectCount(scopedCustomerQuery(officeId, deptId)));
        data.put("inProtection", customerMapper.selectCount(
                scopedCustomerQuery(officeId, deptId).eq(BizCustomer::getProtectionStatus, "active")));
        data.put(LIT_EXPIRING, customerMapper.selectCount(
                scopedCustomerQuery(officeId, deptId).eq(BizCustomer::getProtectionStatus, LIT_EXPIRING)));
        data.put("expired", customerMapper.selectCount(
                scopedCustomerQuery(officeId, deptId).lt(BizCustomer::getExpireDate, today)));
        long todayExpiring = customerMapper.selectCount(
                scopedCustomerQuery(officeId, deptId).eq(BizCustomer::getExpireDate, today));
        data.put("todayExpiring", todayExpiring);
        data.put("yearNew", customerMapper.selectCount(
                scopedCustomerQuery(officeId, deptId)
                        .ge(BizCustomer::getCreatedAt, yearStart).lt(BizCustomer::getCreatedAt, yearEnd)));
        data.put("industryStats", industryStats(officeId, deptId));
        return data;
    }

    /** 【B5 2026-09-22】统计统一口径基座：deleted=0 且排除 sea/archived。 */
    private LambdaQueryWrapper<BizCustomer> baseCustomerQuery() {
        return new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0)
                .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
    }

    /** 办事处/部门筛选：deptId 优先展开子树，其次 officeId 等值。 */
    private void applyOfficeScope(LambdaQueryWrapper<BizCustomer> w, Long officeId, Long deptId) {
        if (deptId != null) {
            w.in(BizCustomer::getOfficeId, com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        } else if (officeId != null) {
            w.eq(BizCustomer::getOfficeId, officeId);
        }
    }

    private LambdaQueryWrapper<BizCustomer> scopedCustomerQuery(Long officeId, Long deptId) {
        LambdaQueryWrapper<BizCustomer> w = baseCustomerQuery();
        applyOfficeScope(w, officeId, deptId);
        applyCustomerScope(w);
        return w;
    }

    private int[] monthlyCustomerCounts(int year, Long officeId, Long deptId) {
        int[] monthlyCustomers = new int[12];
        for (int m = 1; m <= 12; m++) {
            LocalDateTime from = LocalDateTime.of(year, m, 1, 0, 0);
            LocalDateTime to = (m < 12) ? LocalDateTime.of(year, m + 1, 1, 0, 0) : LocalDateTime.of(year + 1, 1, 1, 0, 0);
            LambdaQueryWrapper<BizCustomer> cw = baseCustomerQuery()
                    .ge(BizCustomer::getCreatedAt, from).lt(BizCustomer::getCreatedAt, to);
            applyOfficeScope(cw, officeId, deptId);
            applyCustomerScope(cw);
            monthlyCustomers[m - 1] = customerMapper.selectCount(cw).intValue();
        }
        return monthlyCustomers;
    }

    private int[] monthlyFileCounts(int year) {
        int[] monthlyFiles = new int[12];
        for (int m = 1; m <= 12; m++) {
            LocalDateTime from = LocalDateTime.of(year, m, 1, 0, 0);
            LocalDateTime to = (m < 12) ? LocalDateTime.of(year, m + 1, 1, 0, 0) : LocalDateTime.of(year + 1, 1, 1, 0, 0);
            LambdaQueryWrapper<BizFile> fw = new LambdaQueryWrapper<>();
            fw.ge(BizFile::getCreatedAt, from).lt(BizFile::getCreatedAt, to);
            applyFileScope(fw);
            monthlyFiles[m - 1] = fileMapper.selectCount(fw).intValue();
        }
        return monthlyFiles;
    }

    /** 【WP1.5 2026-11-13】对比列表可见办事处集；null=全量（范围外办事处靠 total==0 跳过）。 */
    private java.util.Set<Long> visibleOfficeIdsForCompare() {
        List<Long> visOwners = customerVisibilityService.visibleOwnerIds();
        if (visOwners == null) return null;
        java.util.Set<Long> visibleOfficeIds = new java.util.HashSet<>();
        if (visOwners.isEmpty()) return visibleOfficeIds;
        LambdaQueryWrapper<BizCustomer> vwc = new LambdaQueryWrapper<>();
        vwc.eq(BizCustomer::getDeleted, 0)
           .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED)
           .isNotNull(BizCustomer::getOfficeId)
           .in(BizCustomer::getOwnerId, visOwners);
        for (BizCustomer vc : customerMapper.selectList(vwc)) {
            visibleOfficeIds.add(vc.getOfficeId());
        }
        return visibleOfficeIds;
    }

    private List<Map<String, Object>> buildOfficeComparison(java.util.Set<Long> visibleOfficeIds) {
        var allDepts = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getDeptType, 3));
        // 【WP1.5 2026-11-13】办事处列表先按可见范围过滤(customer:view): 范围外办事处不再进入对比循环
        // (此前靠 total==0 跳过, 范围外每处白跑 3 条 count; 输出口径不变, 只列有可见客户的办事处)
        List<Map<String, Object>> officeComparison = new ArrayList<>();
        for (var dept : allDepts) {
            if (visibleOfficeIds == null || visibleOfficeIds.contains(dept.getId())) {
                Map<String, Object> row = buildOfficeRow(dept.getId(), dept.getDeptName());
                if (row != null) officeComparison.add(row);
            }
        }
        officeComparison.sort((a, b) -> Long.compare((long) b.get(LIT_CUSTOMERCOUNT), (long) a.get(LIT_CUSTOMERCOUNT)));
        return officeComparison;
    }

    private Map<String, Object> buildOfficeRow(Long deptId, String deptName) {
        LambdaQueryWrapper<BizCustomer> w = baseCustomerQuery().eq(BizCustomer::getOfficeId, deptId);
        applyCustomerScope(w);
        long total = customerMapper.selectCount(w);
        if (total == 0) return null;

        long active = countActive(deptId);
        long monthNew = countMonthNew(deptId);
        int[] recent5 = recent5Counts(deptId);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("officeName", deptName);
        row.put(LIT_CUSTOMERCOUNT, total);
        row.put("monthNew", monthNew);
        row.put("activeRate", Math.round(active * 1000.0 / total) / 10.0);
        row.put("recent5", recent5);
        return row;
    }

    private long countActive(Long deptId) {
        // 【体检 P1 2026-10-12】活跃率分子补 notIn(sea/archived)与分母/同方法 inProtection 对齐:
        // 归档不清 protectionStatus, 不排时分子可含归档客户(虚高, 极端时 >100%)
        LambdaQueryWrapper<BizCustomer> aw = baseCustomerQuery()
                .eq(BizCustomer::getProtectionStatus, "active")
                .eq(BizCustomer::getOfficeId, deptId);
        applyCustomerScope(aw);
        return customerMapper.selectCount(aw);
    }

    private long countMonthNew(Long deptId) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LambdaQueryWrapper<BizCustomer> nw = baseCustomerQuery()
                .eq(BizCustomer::getOfficeId, deptId)
                .ge(BizCustomer::getCreatedAt, monthStart);
        applyCustomerScope(nw);
        return customerMapper.selectCount(nw);
    }

    private int[] recent5Counts(Long deptId) {
        int[] recent5 = new int[5];
        for (int i = 4; i >= 0; i--) {
            LocalDate md = LocalDate.now().minusMonths(i).withDayOfMonth(1);
            LambdaQueryWrapper<BizCustomer> rw = baseCustomerQuery()
                    .eq(BizCustomer::getOfficeId, deptId)
                    .ge(BizCustomer::getCreatedAt, md.atStartOfDay())
                    .lt(BizCustomer::getCreatedAt, md.plusMonths(1).atStartOfDay());
            applyCustomerScope(rw);
            recent5[4 - i] = customerMapper.selectCount(rw).intValue();
        }
        return recent5;
    }

    private Map<String, Long> industryStats(Long officeId, Long deptId) {
        LambdaQueryWrapper<BizCustomer> indW = scopedCustomerQuery(officeId, deptId)
                .isNotNull(BizCustomer::getIndustry);
        Map<String, Long> industryStats = new LinkedHashMap<>();
        for (BizCustomer c : customerMapper.selectList(indW)) {
            // 行业为空不参与分布统计(待用户补全后自动计入), 图表不显示"未填写"
            if (c.getIndustry() != null && !c.getIndustry().isBlank()) {
                industryStats.merge(c.getIndustry().trim(), 1L, Long::sum);
            }
        }
        return industryStats;
    }

    /**
     * 团队动态
     * 【需求】首页团队动态流：跟进/激励/文件/审批/新增客户/系统日志，取最近 15 条
     * 【方法】六个来源各自 LIMIT 查询后按时间倒序合并
     */
    public List<Map<String, Object>> teamActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        // 【2026-08-08 设计规则（最终）：统计按账号数据范围】visible=null 表示全量不过滤。
        // 【WP1.5 2026-11-13】口径切换 customer:view(可见人员=客户可见负责人集合)
        List<Long> visibleUserIds = customerVisibilityService.visibleOwnerIds();
        // 【A6 2026-10-20 审计】空集守卫: in(空集合) 生成 IN () 语法错误(500)——六路恒空, 直接空列表
        if (visibleUserIds != null && visibleUserIds.isEmpty()) {
            return activities;
        }
        boolean scopeAll = visibleUserIds == null;
        appendCustomerLogActivities(activities, visibleUserIds, scopeAll);
        appendIncentiveActivities(activities, visibleUserIds, scopeAll);
        appendFileActivities(activities, visibleUserIds, scopeAll);
        appendApprovalActivities(activities, visibleUserIds, scopeAll);
        appendNewCustomerActivities(activities, visibleUserIds, scopeAll);
        appendSysLogActivities(activities, visibleUserIds, scopeAll);
        return sortAndTrimActivities(activities);
    }

    private void appendCustomerLogActivities(List<Map<String, Object>> activities, List<Long> visibleUserIds,
                                             boolean scopeAll) {
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
    }

    private void appendIncentiveActivities(List<Map<String, Object>> activities, List<Long> visibleUserIds,
                                           boolean scopeAll) {
        LambdaQueryWrapper<BizIncentiveRecord> incWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) incWrapper.in(BizIncentiveRecord::getUserId, visibleUserIds);
        incWrapper.orderByDesc(BizIncentiveRecord::getCreatedAt).last(LIT_LIMIT_5);
        // 循环变量避开受限标识符 record（SonarLint S6213）
        for (BizIncentiveRecord incRecord : incentiveRecordMapper.selectList(incWrapper)) {
            SysUser user = userMapper.selectById(incRecord.getUserId());
            BizIncentiveCategory cat = incentiveCategoryMapper.selectById(incRecord.getCategoryId());
            activities.add(buildActivity(
                    "激励上传 · " + (cat != null ? cat.getCategoryName() : ""),
                    user, incRecord.getCreatedAt()));
        }
    }

    private void appendFileActivities(List<Map<String, Object>> activities, List<Long> visibleUserIds,
                                      boolean scopeAll) {
        LambdaQueryWrapper<BizFile> fileWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) fileWrapper.in(BizFile::getUploaderId, visibleUserIds);
        fileWrapper.orderByDesc(BizFile::getCreatedAt).last(LIT_LIMIT_5);
        for (BizFile file : fileMapper.selectList(fileWrapper)) {
            SysUser user = file.getUploaderId() != null ? userMapper.selectById(file.getUploaderId()) : null;
            activities.add(buildActivity("上传文件 · " + file.getFileName(), user, file.getCreatedAt()));
        }
    }

    private void appendApprovalActivities(List<Map<String, Object>> activities, List<Long> visibleUserIds,
                                          boolean scopeAll) {
        LambdaQueryWrapper<BizApproval> apprWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) apprWrapper.in(BizApproval::getApplicantId, visibleUserIds);
        apprWrapper.orderByDesc(BizApproval::getCreatedAt).last(LIT_LIMIT_5);
        for (BizApproval appr : approvalMapper.selectList(apprWrapper)) {
            SysUser user = appr.getApplicantId() != null ? userMapper.selectById(appr.getApplicantId()) : null;
            BizCustomer customer = appr.getCustomerId() != null ? customerMapper.selectById(appr.getCustomerId()) : null;
            activities.add(buildActivity(
                    approvalTypeText(appr.getApprovalType()) + " · " + (customer != null ? customer.getCustomerName() : ""),
                    user, appr.getCreatedAt()));
        }
    }

    private String approvalTypeText(String approvalType) {
        return switch (approvalType) {
            case "claim" -> "捞取申请";
            case "transfer" -> "转让申请";
            case "extend" -> "延期申请";
            case "storage" -> "入库申请";
            default -> "审批";
        };
    }

    private void appendNewCustomerActivities(List<Map<String, Object>> activities, List<Long> visibleUserIds,
                                             boolean scopeAll) {
        LambdaQueryWrapper<BizCustomer> custWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) custWrapper.in(BizCustomer::getOwnerId, visibleUserIds);
        custWrapper.orderByDesc(BizCustomer::getCreatedAt).last(LIT_LIMIT_5);
        for (BizCustomer cust : customerMapper.selectList(custWrapper)) {
            SysUser user = cust.getOwnerId() != null ? userMapper.selectById(cust.getOwnerId()) : null;
            activities.add(buildActivity("新增客户 · " + cust.getCustomerName(), user, cust.getCreatedAt()));
        }
    }

    private void appendSysLogActivities(List<Map<String, Object>> activities, List<Long> visibleUserIds,
                                        boolean scopeAll) {
        LambdaQueryWrapper<SysLog> sysWrapper = new LambdaQueryWrapper<>();
        if (!scopeAll) sysWrapper.in(SysLog::getUserId, visibleUserIds);
        sysWrapper.orderByDesc(SysLog::getCreatedAt).last(LIT_LIMIT_5);
        for (SysLog log : sysLogMapper.selectList(sysWrapper)) {
            SysUser user = log.getUserId() != null ? userMapper.selectById(log.getUserId()) : null;
            if (user == null && log.getUsername() != null) {
                activities.add(buildActivitySimple(
                        log.getModule() + " · " + log.getAction(),
                        log.getUsername(), "", log.getCreatedAt()));
            } else {
                activities.add(buildActivity(
                        log.getModule() + " · " + log.getAction(),
                        user, log.getCreatedAt()));
            }
        }
    }

    private List<Map<String, Object>> sortAndTrimActivities(List<Map<String, Object>> activities) {
        activities.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("time");
            LocalDateTime tb = (LocalDateTime) b.get("time");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
        return activities.size() > 15 ? activities.subList(0, 15) : activities;
    }

    /**
     * 【2026-08-08 设计规则（最终）：统计按账号数据范围】统计模块数据按当前账号数据范围过滤
     * （管理员全量 / 负责人本组织 / 销售本人，含系统级开关）。覆盖 dashboard/经营分析/日报周报等查询。
     * 【注释修正 2026-11-08】核心客户指标(indicatorStats)自 G2 起同受统计可见范围过滤
     * （visibleCustomers 按客户可见范围过滤, 见该方法内注释）, 原"六项指标全员可见不过滤"例外表述作废。
     * 【WP1.5 2026-11-13】可见负责人统一走 CustomerVisibilityService.visibleOwnerIds
     * ((customer,view) 单一源, 锁定决策#7), null=全量不过滤。
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
        // 【WP4 2026-11-13 §4.1】文件统计来源动作=(file, view): 经营分析"本月文件上传"按文件可见负责人
        // 过滤(不再借道 customer:view 停止血——客户域与文件域各自独立来源动作)
        var user = com.aigicrm.security.SecurityUtils.getCurrentUser();
        if (user == null) {
            wrapper.apply("1=0");
            return;
        }
        List<Long> visible = orgScopeResolver.resolveModuleUsers(user, "file", "view");
        if (visible == null) return; // all: 不过滤
        if (visible.isEmpty()) {
            wrapper.apply("1=0"); // 空集显式恒空(A6 防线)
            return;
        }
        wrapper.in(BizFile::getUploaderId, visible);
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
