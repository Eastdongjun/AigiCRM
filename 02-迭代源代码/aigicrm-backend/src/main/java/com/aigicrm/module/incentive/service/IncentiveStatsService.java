package com.aigicrm.module.incentive.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 激励统计与已读：分类统计、积分榜、部门排行、客户记录查询、已读统计/排行、单条查看判定。
 *
 * <p>S5.3 从 {@link IncentiveService} 抽出；授权与可见性复用 IncentiveAccessService。</p>
 */
@Service
@RequiredArgsConstructor
public class IncentiveStatsService {

    private static final String LIT_BYCATEGORY = "byCategory";
    private static final String LIT_TOTALSCORE = "totalScore";
    private static final String LIT_REALNAME = "realName";
    private static final String LIT_USERID = "userId";
    private static final String LIT_DEPTNAME = "deptName";
    private static final String LIT_PUBLISHEDCOUNT = "publishedCount";
    private static final String LIT_READCOUNT = "readCount";
    private static final String LIT_UNREADCOUNT = "unreadCount";
    private final BizIncentiveCategoryMapper categoryMapper;
    private final BizIncentiveRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final BizCustomerMapper customerMapper;
    private final BizFileMapper fileMapper;
    private final SysDeptMapper deptMapper;
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;
    private final JdbcTemplate jdbc;
    private final IncentiveAccessService incentiveAccessService;

    /** 单条记录是否计分(任意文件槽位有文件即算; 兼容旧版 fileId) */
    private int scoreOf(BizIncentiveRecord r) {
        return r.getScore() != null ? r.getScore() : 0;
    }

    private int fileCountOf(BizIncentiveRecord r) {
        if (r.hasAnyFile()) return 1;
        return 0;
    }

    private Optional<java.util.Set<Long>> statsVisibleUserIds() {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Optional.empty();
        // 【2026-11-05 用户拍板 B"四项激励独立数据范围"】改读独立档位 (incentive, stats_view),
        // 不再跟随 (statistics, view); 存量角色由 V2026.11.05.7 回填拷贝统计分析档位, 行为不变。
        java.util.List<Long> visible = scopeResolver.resolveModuleUsers(user, "incentive", "stats_view");
        return visible == null ? Optional.empty() : Optional.of(new java.util.HashSet<>(visible));
    }

    /** 客户维度基线: active 非 sea/archived 且(未分配负责人保持全员可见 或 负责人在可见人员集内) */
    private Optional<java.util.Set<Long>> statsVisibleCustomerIds() {
        Optional<java.util.Set<Long>> usersOpt = statsVisibleUserIds();
        if (usersOpt.isEmpty()) return Optional.empty();
        java.util.Set<Long> users = usersOpt.get();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (BizCustomer c : customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0)
                .notIn(BizCustomer::getStatus, "sea", "archived"))) {
            if (c.getOwnerId() == null || users.contains(c.getOwnerId())) ids.add(c.getId());
        }
        return Optional.of(ids);
    }

    private List<BizIncentiveRecord> filterRecordsByVisibleCustomers(List<BizIncentiveRecord> records) {
        Optional<java.util.Set<Long>> idsOpt = statsVisibleCustomerIds();
        if (idsOpt.isEmpty()) return records;
        java.util.Set<Long> ids = idsOpt.get();
        return records.stream()
                .filter(r -> r.getCustomerId() != null && ids.contains(r.getCustomerId()))
                .toList();
    }

    private List<BizIncentiveRecord> filterRecordsByVisibleUsers(List<BizIncentiveRecord> records) {
        Optional<java.util.Set<Long>> usersOpt = statsVisibleUserIds();
        if (usersOpt.isEmpty()) return records;
        java.util.Set<Long> users = usersOpt.get();
        return records.stream()
                .filter(r -> r.getUserId() != null && users.contains(r.getUserId()))
                .toList();
    }

    /** 激励统计表: 按子类聚合文件数/总学分, 并给出周期总文件数/总学分(数据按当前权限账号范围收口) */
    public Map<String, Object> categoryStats(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        // 【2026-11-04 用户令"四项服务/四项激励也设置上对应的数据的权限"】统计表按当前权限账号
        // 数据范围收口(可见客户基线), 取代 2026-08-08 全员口径(公司级→范围内, 与客户排行/PC 同基线)
        List<BizIncentiveRecord> records = filterRecordsByVisibleCustomers(recordMapper.selectList(wrapper));

        Map<Long, int[]> byCat = new LinkedHashMap<>(); // categoryId -> [fileCount, totalScore, pre, mid, post]
        int totalFiles = 0;
        int totalScore = 0;
        int totalPre = 0;
        int totalMid = 0;
        int totalPost = 0;
        for (BizIncentiveRecord r : records) {
            int fc = fileCountOf(r);
            int sc = fc > 0 ? scoreOf(r) : 0;
            int pre = r.hasFiles(r.getFilePreId()) ? 1 : 0;
            int mid = r.hasFiles(r.getFileMidId()) ? 1 : 0;
            int post = r.hasFiles(r.getFilePostId()) ? 1 : 0;
            int[] agg = byCat.computeIfAbsent(r.getCategoryId(), k -> new int[5]);
            agg[0] += fc;
            agg[1] += sc;
            agg[2] += pre;
            agg[3] += mid;
            agg[4] += post;
            totalFiles += fc;
            totalScore += sc;
            totalPre += pre;
            totalMid += mid;
            totalPost += post;
        }
        List<Map<String, Object>> byCategory = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : byCat.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("categoryId", e.getKey());
            m.put("fileCount", e.getValue()[0]);
            m.put(LIT_TOTALSCORE, e.getValue()[1]);
            m.put("preCount", e.getValue()[2]);
            m.put("midCount", e.getValue()[3]);
            m.put("postCount", e.getValue()[4]);
            byCategory.add(m);
        }
        Map<String, Object> result = new HashMap<>();
        result.put(LIT_BYCATEGORY, byCategory);
        result.put("totalFiles", totalFiles);
        result.put(LIT_TOTALSCORE, totalScore);
        result.put("totalPre", totalPre);
        result.put("totalMid", totalMid);
        result.put("totalPost", totalPost);
        return result;
    }

    // 个人学分榜
    // 【容量加固 2026-10-06】30s TTL 缓存(公司级,按 period 键,照 RolePermScopeService 同款模式)：
    // 原实现每次请求全表 selectList + JVM 聚合,而结果是公司级全员相同的(并发仿真 600 用户时 p50 4.6s)；
    // 全员共享同缓存命中率极高；激励记录写入后最迟 30s 反映(排行页天然容忍)。
    private static final long SCOREBOARD_TTL_MS = 30_000L;
    private record ScoreboardCacheEntry(List<Map<String, Object>> value, long expireAt) {}
    private final java.util.concurrent.ConcurrentHashMap<String, ScoreboardCacheEntry> scoreboardCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 【2026-11-01 用户令: 上传了就更新】激励排行缓存写穿失效: 记录 增/改/删/移文件/全量重算
     * 五个写口的瞬间清空排行缓存, 下一次查询现算——读侧重负载仍有 30s 缓存兜底, 但写后必即时
     * (清的是公司级少量键, 开销可忽略)。前端写后强制刷新机制(小程序 forceRefresh.incentive /
     * PC 保存后重进页)配合此失效, 构成"上传完立刻见新排行"的完整链路。
     */
    /** 回收站恢复/彻底删除后清排行缓存（管理面单入口，避免把 private 实现暴露给业务调用方）。 */
    public void evictScoreboardCacheAfterRecycle() {
        evictScoreboardCache();
    }

    private void evictScoreboardCache() {
        scoreboardCache.clear();
    }

    public List<Map<String, Object>> scoreboard(String period) {
        // 【C3 2026-10-08】缓存键补集团+身份两维:原键只有 period,而 biz_incentive_record 在租户白名单内——
        // admin 被 ignoreTable 放行=全集团合并榜, 非 admin 被改写 tenant_id=当前集团=本集团榜,
        // 同 period 键会让两类榜互染。集团前缀与租户拦截器同口径(GroupScopeUtil.tenantCachePrefix,
        // 取不到集团=fail-closed 固定前缀),admin 用 "adm:" / 非 admin 用 "usr:" 区分。
        // 第二集团前置条件已补:第二集团上线即天然隔离,无需再动此键。
        String periodKey = (period == null || period.isBlank()) ? "" : period;
        // 【2026-11-04 数据范围收口】非 admin 榜单按调用人分键(各账号可见范围不同, 不得互染)
        com.aigicrm.security.SecurityUser caller = SecurityUtils.getCurrentUser();
        String callerPart;
        if (caller == null) {
            callerPart = "usr:sys:";
        } else if (SecurityUtils.isAdmin()) {
            callerPart = "adm:";
        } else {
            callerPart = "usr:" + caller.getUserId() + ":";
        }
        String key = com.aigicrm.common.scope.GroupScopeUtil.tenantCachePrefix() + callerPart + periodKey;
        ScoreboardCacheEntry hit = scoreboardCache.get(key);
        if (hit != null && System.currentTimeMillis() < hit.expireAt()) {
            return hit.value();
        }
        List<Map<String, Object>> result = computeScoreboard(period);
        // 【防过期风暴】TTL 加 0~50% 随机抖动，把集中过期重算摊开（同 StatisticsQueryService.dashboard）
        long ttl = ThreadLocalRandom.current().nextLong(SCOREBOARD_TTL_MS / 2, SCOREBOARD_TTL_MS);
        scoreboardCache.put(key, new ScoreboardCacheEntry(result, System.currentTimeMillis() + ttl));
        return result;
    }

    private record ScoreAggregation(Map<Long, int[]> userAgg, Map<Long, Map<Long, int[]>> userByCat) {}

    private List<Map<String, Object>> computeScoreboard(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        List<BizIncentiveRecord> records = filterRecordsByVisibleUsers(recordMapper.selectList(wrapper));
        ScoreAggregation agg = aggregateRecords(records);
        List<Map.Entry<Long, int[]>> sorted = new ArrayList<>(agg.userAgg().entrySet());
        sorted.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<Long, int[]> e : sorted) {
            result.add(scoreboardItem(e.getKey(), e.getValue(), ++rank, agg.userByCat()));
        }
        return result;
    }

    private ScoreAggregation aggregateRecords(List<BizIncentiveRecord> records) {
        Map<Long, int[]> userAgg = new LinkedHashMap<>();
        Map<Long, Map<Long, int[]>> userByCat = new HashMap<>();
        for (BizIncentiveRecord r : records) {
            int sc = fileCountOf(r) > 0 ? scoreOf(r) : 0;
            int[] agg = userAgg.computeIfAbsent(r.getUserId(), k -> new int[5]);
            agg[0] += sc;
            agg[1] += 1;
            agg[2] += r.hasFiles(r.getFilePreId()) ? 1 : 0;
            agg[3] += r.hasFiles(r.getFileMidId()) ? 1 : 0;
            agg[4] += r.hasFiles(r.getFilePostId()) ? 1 : 0;
            if (r.getCategoryId() != null) {
                int[] cagg = userByCat.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                        .computeIfAbsent(r.getCategoryId(), k -> new int[2]);
                cagg[0] += 1;  // 记录数
                cagg[1] += sc; // 分值
            }
        }
        return new ScoreAggregation(userAgg, userByCat);
    }

    private Map<String, Object> scoreboardItem(Long userId, int[] agg, int rank, Map<Long, Map<Long, int[]>> userByCat) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("rank", rank);
        item.put(LIT_USERID, userId);
        item.put(LIT_TOTALSCORE, agg[0]);
        item.put("recordCount", agg[1]);
        item.put("preCount", agg[2]);
        item.put("midCount", agg[3]);
        item.put("postCount", agg[4]);
        item.put(LIT_BYCATEGORY, userByCat.getOrDefault(userId, java.util.Map.of()));
        SysUser user = userMapper.selectById(userId);
        if (user != null) {
            item.put(LIT_REALNAME, user.getRealName());
            item.put("deptId", user.getDeptId());
            item.put("officeName", officeNameOf(user));
        }
        return item;
    }

    private String officeNameOf(SysUser user) {
        if (user.getOfficeId() == null) return "未知";
        SysDept office = deptMapper.selectById(user.getOfficeId());
        return office == null ? "未知" : office.getDeptName();
    }

    // 部门排行
    public List<Map<String, Object>> deptRanking(String period) {
        List<Map<String, Object>> personal = scoreboard(period);
        Map<String, int[]> deptAgg = new LinkedHashMap<>();
        Map<String, java.util.Map<Long, int[]>> deptByCat = new HashMap<>();
        for (Map<String, Object> p : personal) {
            String dept = (String) p.getOrDefault("officeName", "未知");
            int[] agg = deptAgg.computeIfAbsent(dept, k -> new int[]{0, 0});
            agg[0] += ((Number) p.getOrDefault(LIT_TOTALSCORE, 0)).intValue();
            agg[1] += 1;
            // 部门品类聚合（个人 byCategory 求和: [记录数, 分值]）
            @SuppressWarnings("unchecked")
            java.util.Map<Long, int[]> pbc = (java.util.Map<Long, int[]>) p.getOrDefault(LIT_BYCATEGORY, java.util.Map.of());
            for (Map.Entry<Long, int[]> en : pbc.entrySet()) {
                int[] dagg = deptByCat.computeIfAbsent(dept, k -> new HashMap<>())
                        .computeIfAbsent(en.getKey(), k -> new int[2]);
                dagg[0] += en.getValue()[0];
                dagg[1] += en.getValue()[1];
            }
        }

        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(deptAgg.entrySet());
        sorted.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, int[]> e : sorted) {
            rank++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank);
            row.put(LIT_DEPTNAME, e.getKey());
            row.put(LIT_TOTALSCORE, e.getValue()[0]);
            row.put("participantCount", e.getValue()[1]);
            row.put(LIT_BYCATEGORY, deptByCat.getOrDefault(e.getKey(), java.util.Map.of()));
            row.put("avgScore", e.getValue()[1] > 0
                    ? Math.round((double) e.getValue()[0] / e.getValue()[1] * 10) / 10.0 : 0);
            result.add(row);
        }
        return result;
    }

    /**
     * 获取客户关联的激励学分总分
     */
    public int getScoreByCustomerId(Long customerId) {
        // 【2026-08-08 分享制 防越权】学分只按当前用户可见记录计算（admin/专项授权→全部，否则本人+已分享）
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizIncentiveRecord::getCustomerId, customerId);
        incentiveAccessService.applyShareVisibility(wrapper);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
        return records.stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
    }

    // ======================== 分享制可见性（2026-08-08） ========================

    /**
     * 【2026-08-08 分享制】显式指定查看某人是否可见：admin/专项授权→全部；本人→是；
     * 他人→仅当其存在已分享(shared=1)记录时可见（非授权视角只能看该用户已分享内容）。
     */
    boolean canViewIncentiveUser(Long targetUserId) {
        return incentiveAccessService.canViewIncentiveUser(targetUserId);
    }

    /** 递归获取所有下级部门ID（激励查询组织筛选用，非权限判定） */
    private List<Long> getDescendantDeptIds(Long parentId) {
        List<Long> result = new ArrayList<>();
        List<SysDept> children = deptMapper.selectList(
            new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, parentId).eq(SysDept::getStatus, 1));
        for (SysDept child : children) {
            result.add(child.getId());
            result.addAll(getDescendantDeptIds(child.getId()));
        }
        return result;
    }

    /** 按客户分组查询激励记录 — 多维度筛选（2026-08-01 新增 product/keyword 搜索与四区排序） */
    public List<Map<String, Object>> getCustomerRecords(IncentiveCustomerRecordQuery q) {
        String industry = q.industry();
        Long mainCategoryId = q.mainCategoryId();
        String customerName = q.customerName();
        String salesperson = q.salesperson();
        String startDate = q.startDate();
        String endDate = q.endDate();
        String period = q.period();
        String orgIds = q.orgIds();
        String devType = q.devType();
        String product = q.product();
        String keyword = q.keyword();
        Long recordId = q.recordId();
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BizIncentiveRecord::getCustomerId);
        // 【2026-11-05 用户拍板 B"激励查询改走组织范围档位"】激励查询/案例库/MP 查询三面统一
        // 改走组织范围: 客户属 (customer,view) 档位 ∨ 本人上传恒可见(原"全员可见"无过滤退役)
        incentiveAccessService.applyOrgQueryVisibility(wrapper);
        if (!applyRecordSeedFilter(wrapper, recordId)) return List.of();
        applyPeriodIndustry(wrapper, period, industry);
        if (!applyCustomerNameFilter(wrapper, customerName, keyword)) return List.of();
        applyProductFilter(wrapper, product);
        applyKeywordFilter(wrapper, keyword);
        if (!applySalespersonFilter(wrapper, salesperson)) return List.of();
        applyCategoryFilter(wrapper, mainCategoryId);
        if (!applyOrgFilter(wrapper, orgIds)) return List.of();
        if (!applyDevTypeFilter(wrapper, devType)) return List.of();
        applyDateFilters(wrapper, startDate, endDate);
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        return assembleCustomerRecords(recordMapper.selectList(wrapper));
    }

    /** recordId 定位：先按可见性取种子记录，命中即收窄到该记录所属客户；不可见→false（空结果）。 */
    private boolean applyRecordSeedFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, Long recordId) {
        // 【性能 2026-10-27】recordId 定位: 先按可见性取种子记录, 命中即把查询收窄到
        // 该记录所属客户(与全量接口的可见性口径一致); 种子不可见/不存在 → 空结果。
        if (recordId == null) return true;
        LambdaQueryWrapper<BizIncentiveRecord> seed = new LambdaQueryWrapper<>();
        seed.eq(BizIncentiveRecord::getId, recordId).isNotNull(BizIncentiveRecord::getCustomerId);
        incentiveAccessService.applyOrgQueryVisibility(seed);
        List<BizIncentiveRecord> seedRows = recordMapper.selectList(seed);
        if (seedRows.isEmpty()) return false;
        wrapper.eq(BizIncentiveRecord::getCustomerId, seedRows.get(0).getCustomerId());
        return true;
    }

    private void applyPeriodIndustry(LambdaQueryWrapper<BizIncentiveRecord> wrapper, String period, String industry) {
        // 【P1-1/M2 2026-10-17】period 过滤补齐：前端统计页一直传 period，此前后端不收导致筛选静默失效；
        // 空白不放行等值（与 getRecords/categoryStats/scoreboard 同守卫——空串当 period='' 匹配必然 0 条）
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (industry != null && !industry.isBlank()) wrapper.eq(BizIncentiveRecord::getIndustry, industry);
    }

    /** 客户名 OR 关键字（相关产品）匹配：搜客户=该客户共享内容；搜关键字=相关案例全出。 */
    private boolean applyCustomerNameFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper,
                                            String customerName, String keyword) {
        if (customerName == null || customerName.isBlank() || (keyword != null && !keyword.isBlank())) return true;
        // 仅客户名匹配：保持原行为
        List<BizCustomer> matched = customerMapper.selectList(
            new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName)
        );
        if (matched.isEmpty()) return false;
        wrapper.in(BizIncentiveRecord::getCustomerId, matched.stream().map(BizCustomer::getId).toList());
        return true;
    }

    private void applyProductFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, String product) {
        if (product != null && !product.isBlank()) {
            wrapper.like(BizIncentiveRecord::getRelatedProducts, product);
        }
    }

    /** 全局关键词六路宽匹配（相关产品/具体产品/客户名/分类名/上传人/文件名）。 */
    private void applyKeywordFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, String keyword) {
        // 【2026-11-09 用户令"全局检索应能检索客户/分类/单一记录/关联记录"】关键词=六路宽匹配:
        // ①关联产品 ②具体产品 ③客户名(customer表LIKE→customerId IN) ④分类名(category表LIKE→categoryId IN)
        // ⑤上传人姓名(user表LIKE→userId IN) ⑥文件名(三槽位文件名LIKE)。
        // 展示层(PC/MP)有关键字时打散为纯记录列表(不按客户分组卡片)——
        // 客户名命中会带出该客户全部记录, 但每条独立成行, 用户逐行看(非旧卡片汇总)。
        // 可见性过滤(applyOrgQueryVisibility)在 wrapper 最前, 深度检索只在有权看的记录内。
        if (keyword == null || keyword.isBlank()) return;
        wrapper.and(w -> applyKeywordBranches(w, keyword));
    }

    private void applyKeywordBranches(LambdaQueryWrapper<BizIncentiveRecord> w, String keyword) {
        w.like(BizIncentiveRecord::getRelatedProducts, keyword)
         .or().like(BizIncentiveRecord::getSpecificProduct, keyword);
        // ③ 客户名
        List<BizCustomer> matchedCustomers = customerMapper.selectList(
            new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, keyword));
        if (!matchedCustomers.isEmpty()) {
            w.or().in(BizIncentiveRecord::getCustomerId,
                matchedCustomers.stream().map(BizCustomer::getId).toList());
        }
        // ④ 分类名
        List<BizIncentiveCategory> matchedCats = categoryMapper.selectList(
            new LambdaQueryWrapper<BizIncentiveCategory>().like(BizIncentiveCategory::getCategoryName, keyword));
        if (!matchedCats.isEmpty()) {
            w.or().in(BizIncentiveRecord::getCategoryId,
                matchedCats.stream().map(BizIncentiveCategory::getId).toList());
        }
        // ⑤ 上传人姓名
        List<SysUser> matchedUsers = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>().like(SysUser::getRealName, keyword));
        if (!matchedUsers.isEmpty()) {
            w.or().in(BizIncentiveRecord::getUserId,
                matchedUsers.stream().map(SysUser::getId).toList());
        }
        // ⑥ 文件名: 从 biz_file 反查文件名含关键字的文件 ID → JSON_OVERLAPS 精确元素匹配
        // (filePreName 等是 @TableField(exist=false) 非表列, 须走 ID 间接匹配; 限 50 防宽词慢查)
        // 【修复 2026-11-09】四槽位列存 JSON 数组(如 [178,179]), 旧 REGEXP 子串匹配把
        // [193]/[1119]/[1903] 误当 19 命中——生产实证: 文件ID=19 误报 35 条真命中 0 条。
        // JSON_OVERLAPS 按数组元素精确比对(需 MySQL>=8.0.17, 生产 8.0.28; 四列已验全合法 JSON),
        // {0} 绑定 '[19,23]' 字符串经 CAST 成 JSON 数组, 保持参数绑定防注入。
        // 【性能 2026-11-11 检索优化】JSON_OVERLAPS 拆出六路 OR 为独立预查询取命中记录 id,
        // 主查询改 IN(ids)——JSON 函数混在 OR 里阻断 MySQL 优化器短路, 是 DB 侧 ~1s 主因
        // (496 行全表×4 列逐行 JSON 解析)。集合恒等: 外条件 AND (五路 OR 全表JSON命中id集)
        // ≡ 原 外条件 AND 六路OR(JSON_OVERLAPS 本身与外条件无关, 全表命中即该路完整结果)。
        List<com.aigicrm.module.file.entity.BizFile> matchedFiles = fileMapper.selectList(
            new LambdaQueryWrapper<com.aigicrm.module.file.entity.BizFile>()
                .like(com.aigicrm.module.file.entity.BizFile::getFileName, keyword)
                .last("LIMIT 50"));
        if (!matchedFiles.isEmpty()) {
            String idJsonArray = matchedFiles.stream()
                .map(f -> String.valueOf(f.getId()))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            List<BizIncentiveRecord> jsonHits = recordMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveRecord>()
                    .select(BizIncentiveRecord::getId)
                    .apply("(JSON_OVERLAPS(file_pre_id, CAST({0} AS JSON)) OR JSON_OVERLAPS(file_mid_id, CAST({0} AS JSON)) OR JSON_OVERLAPS(file_post_id, CAST({0} AS JSON)) OR JSON_OVERLAPS(file_track_id, CAST({0} AS JSON)))",
                        idJsonArray));
            if (!jsonHits.isEmpty()) {
                w.or().in(BizIncentiveRecord::getId,
                    jsonHits.stream().map(BizIncentiveRecord::getId).collect(java.util.stream.Collectors.toSet()));
            }
        }
    }

    private boolean applySalespersonFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, String salesperson) {
        if (salesperson == null || salesperson.isBlank()) return true;
        List<SysUser> users = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>().like(SysUser::getRealName, salesperson)
        );
        if (users.isEmpty()) return false;
        wrapper.in(BizIncentiveRecord::getUserId, users.stream().map(SysUser::getId).toList());
        return true;
    }

    private void applyCategoryFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, Long mainCategoryId) {
        if (mainCategoryId == null) return;
        Set<Long> catIds = new HashSet<>();
        catIds.add(mainCategoryId);
        List<BizIncentiveCategory> children = categoryMapper.selectList(
            new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, mainCategoryId));
        for (BizIncentiveCategory child : children) {
            catIds.add(child.getId());
            List<BizIncentiveCategory> grands = categoryMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, child.getId()));
            for (BizIncentiveCategory g : grands) catIds.add(g.getId());
        }
        wrapper.in(BizIncentiveRecord::getCategoryId, catIds);
    }

    private boolean applyOrgFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, String orgIds) {
        if (orgIds == null || orgIds.isBlank()) return true;
        List<Long> oids = java.util.Arrays.stream(orgIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
        if (oids.isEmpty()) return true;
        // 组织筛选含全部子部门（部门可无限嵌套）：展开所选组织为子树集合
        java.util.Set<Long> orgSet = new java.util.LinkedHashSet<>();
        for (Long oid : oids) {
            orgSet.add(oid);
            orgSet.addAll(getDescendantDeptIds(oid));
        }
        List<SysUser> orgUsers = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>().in(SysUser::getOfficeId, orgSet)
        );
        List<Long> uids = orgUsers.stream().map(SysUser::getId).distinct().toList();
        if (uids.isEmpty()) return false;
        wrapper.in(BizIncentiveRecord::getUserId, uids);
        return true;
    }

    private boolean applyDevTypeFilter(LambdaQueryWrapper<BizIncentiveRecord> wrapper, String devType) {
        if (devType == null || devType.isBlank()) return true;
        List<BizCustomer> devCustomers = customerMapper.selectList(
            new LambdaQueryWrapper<BizCustomer>().eq(BizCustomer::getDevType, devType)
        );
        if (devCustomers.isEmpty()) return false;
        wrapper.in(BizIncentiveRecord::getCustomerId, devCustomers.stream().map(BizCustomer::getId).toList());
        return true;
    }

    private void applyDateFilters(LambdaQueryWrapper<BizIncentiveRecord> wrapper, String startDate, String endDate) {
        if (startDate != null && !startDate.isBlank()) wrapper.ge(BizIncentiveRecord::getCreatedAt, startDate + " 00:00:00");
        if (endDate != null && !endDate.isBlank()) wrapper.le(BizIncentiveRecord::getCreatedAt, endDate + " 23:59:59");
    }

    private List<Map<String, Object>> assembleCustomerRecords(List<BizIncentiveRecord> records) {
        // 批量查客户
        Set<Long> cids = records.stream().map(BizIncentiveRecord::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizCustomer> custMap = loadCustomers(cids);
        // 批量查分类
        Set<Long> catIds = records.stream().map(BizIncentiveRecord::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizIncentiveCategory> catMap2 = loadCategories(catIds);

        // 批量查文件 & 填充文件名
        fillFileNames(records);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Set<Long> readRecordIds = findReadRecordIds(currentUserId, records.stream()
                .map(BizIncentiveRecord::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        decorateRecordNames(records, custMap, catMap2, readRecordIds);
        Map<Long, List<BizIncentiveRecord>> grouped = groupByCustomer(records);
        sortGroupsByCategory(grouped);
        return buildFlatList(grouped, custMap);
    }

    private Map<Long, BizCustomer> loadCustomers(Set<Long> cids) {
        Map<Long, BizCustomer> custMap = new HashMap<>();
        if (cids.isEmpty()) return custMap;
        List<BizCustomer> custs = customerMapper.selectByIds(cids);
        for (BizCustomer c : custs) custMap.put(c.getId(), c);
        return custMap;
    }

    private Map<Long, BizIncentiveCategory> loadCategories(Set<Long> catIds) {
        Map<Long, BizIncentiveCategory> catMap = new HashMap<>();
        if (catIds.isEmpty()) return catMap;
        List<BizIncentiveCategory> cats = categoryMapper.selectByIds(catIds);
        for (BizIncentiveCategory c : cats) catMap.put(c.getId(), c);
        return catMap;
    }

    /** 填充关联信息（分类名/上传人/更新人/客户名/未读）。 */
    private void decorateRecordNames(List<BizIncentiveRecord> records, Map<Long, BizCustomer> custMap,
                                     Map<Long, BizIncentiveCategory> catMap2, Set<Long> readRecordIds) {
        Map<Long, SysUser> userMap = loadRecordUsers(records);
        for (BizIncentiveRecord r : records) {
            r.setUnread(r.getId() != null && !readRecordIds.contains(r.getId()));
            applyCategoryName(r, catMap2);
            applyUserNames(r, userMap);
            applyCustomerName(r, custMap);
        }
    }

    private Map<Long, SysUser> loadRecordUsers(List<BizIncentiveRecord> records) {
        // 【性能 2026-11-11 检索优化】用户名批量化: 原循环内逐条 userMapper.selectById,
        // 300+ 记录=600+ 次 DB 往返(实测组装层 ~2s 主因); 与客户/分类同款批量建 map, 数据源不变
        Set<Long> uidsAll = new LinkedHashSet<>();
        for (BizIncentiveRecord r : records) {
            if (r.getUserId() != null) uidsAll.add(r.getUserId());
            if (r.getUpdatedBy() != null) uidsAll.add(r.getUpdatedBy());
        }
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!uidsAll.isEmpty()) userMapper.selectByIds(uidsAll).forEach(u -> userMap.put(u.getId(), u));
        return userMap;
    }

    private void applyCategoryName(BizIncentiveRecord r, Map<Long, BizIncentiveCategory> catMap) {
        if (r.getCategoryId() == null) return;
        BizIncentiveCategory cat = catMap.get(r.getCategoryId());
        if (cat != null) r.setCategoryName(cat.getCategoryName());
    }

    private void applyUserNames(BizIncentiveRecord r, Map<Long, SysUser> userMap) {
        if (r.getUserId() != null) { SysUser u = userMap.get(r.getUserId()); if (u != null) r.setUserName(u.getRealName()); }
        if (r.getUpdatedBy() != null) { SysUser up = userMap.get(r.getUpdatedBy()); if (up != null) r.setUpdatedByName(up.getRealName()); }
    }

    private void applyCustomerName(BizIncentiveRecord r, Map<Long, BizCustomer> custMap) {
        if (r.getCustomerId() == null) return;
        BizCustomer c = custMap.get(r.getCustomerId());
        if (c != null) r.setCustomerName(c.getCustomerName());
    }

    private Map<Long, List<BizIncentiveRecord>> groupByCustomer(List<BizIncentiveRecord> records) {
        Map<Long, List<BizIncentiveRecord>> grouped = new LinkedHashMap<>();
        for (BizIncentiveRecord r : records) {
            Long cid = r.getCustomerId();
            if (cid != null) grouped.computeIfAbsent(cid, k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    private void sortGroupsByCategory(Map<Long, List<BizIncentiveRecord>> grouped) {
        // 组内四区排序：试点→两谈两做→交流拜访→外出参观，同品类按时间倒序
        // 注意：需全量分类映射（含父级），catMap2 只含记录引用的子类
        Map<Long, BizIncentiveCategory> allCatMap = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> allCatMap.put(c.getId(), c));
        for (List<BizIncentiveRecord> recs : grouped.values()) {
            recs.sort(Comparator
                .comparingInt((BizIncentiveRecord r) -> categoryMainOrder(allCatMap.get(r.getCategoryId()), allCatMap))
                .thenComparing(Comparator.comparing(BizIncentiveRecord::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))));
        }
    }

    private List<Map<String, Object>> buildFlatList(Map<Long, List<BizIncentiveRecord>> grouped,
                                                    Map<Long, BizCustomer> custMap) {
        List<String> industryOrder = List.of("冶金","煤矿","化工","电力","有色金属","机械","工程公司","维修厂","其他终端客户");
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < industryOrder.size(); i++) orderMap.put(industryOrder.get(i), i);
        List<Map<String, Object>> flatList = new ArrayList<>();
        for (Map.Entry<Long, List<BizIncentiveRecord>> e : grouped.entrySet()) {
            flatList.add(buildCustomerItem(e, custMap.get(e.getKey()), orderMap));
        }
        flatList.sort(Comparator
                .comparingInt((Map<String, Object> m) -> -((Number) m.getOrDefault(LIT_TOTALSCORE, 0)).intValue())
                .thenComparingInt(m -> (int) m.get("industryOrder"))
                .thenComparing(m -> String.valueOf(m.getOrDefault("customerName", ""))));
        return flatList;
    }

    private Map<String, Object> buildCustomerItem(Map.Entry<Long, List<BizIncentiveRecord>> e,
                                                  BizCustomer cust, Map<String, Integer> orderMap) {
        // 每个客户一个条目
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("customerId", e.getKey());
        String ind = cust != null ? cust.getIndustry() : "其他";
        item.put("customerName", cust != null ? cust.getCustomerName() : null);
        item.put("ownerName", cust != null ? cust.getOwnerName() : null);
        // 【2026-09-29】客户排行销售视图(06只看自己名下客户,rank保留公司级序号)需要按负责人过滤
        item.put("ownerId", cust != null ? cust.getOwnerId() : null);
        item.put("industry", ind);
        item.put("industryOrder", orderMap.getOrDefault(ind, 99));
        item.put("count", e.getValue().size());
        int totalScore = e.getValue().stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
        item.put(LIT_TOTALSCORE, totalScore);
        item.put("records", e.getValue());
        return item;
    }

    private Set<Long> findReadRecordIds(Long userId, Set<Long> recordIds) {
        if (userId == null || recordIds == null || recordIds.isEmpty()) return Collections.emptySet();
        String placeholders = recordIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(recordIds);
        return new HashSet<>(jdbc.queryForList(
                "SELECT record_id FROM biz_incentive_read WHERE user_id=? AND record_id IN (" + placeholders + ")",
                Long.class,
                args.toArray()));
    }

    @Transactional
    public void markRead(Long recordId) {
        // 【PR-01 P0-04 2026-09-18】写入前复用记录详情一致的可见性判定（含集团边界），
        // 不可见/不存在统一 404（防 IDOR 与跨集团已读污染，不泄露存在性）。
        if (!canViewRecord(recordId)) {
            throw new com.aigicrm.common.BusinessException(404, "记录不存在或无权访问");
        }
        jdbc.update("INSERT INTO biz_incentive_read (user_id, record_id, read_at) VALUES (?, ?, CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE read_at=CURRENT_TIMESTAMP",
                SecurityUtils.getCurrentUserId(), recordId);
    }

    /**
     * 【PR-01 P0-04】单条激励记录可见性判定（与列表分享制同口径 + 集团边界）：
     * 记录不存在 → false；非 admin 且记录 tenant 与当前集团不一致 → false；
     * canSeeAllIncentive → true；本人 → true；已分享且有 canViewShared → true。
     */
    public boolean canViewRecord(Long recordId) {
        if (recordId == null) return false;
        java.util.Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT user_id, shared, tenant_id FROM biz_incentive_record WHERE id=?", recordId);
        } catch (Exception e) {
            return false;
        }
        Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        Object tenant = row.get("tenant_id");
        Long recordTenant = tenant == null ? null : Long.valueOf(String.valueOf(tenant));
        if (g == null || !g.equals(recordTenant)) return false;
        if (incentiveAccessService.canSeeAllIncentive()) return true;
        Long self = SecurityUtils.getCurrentUserId();
        Object owner = row.get("user_id");
        if (self != null && owner != null && self.equals(Long.valueOf(String.valueOf(owner)))) return true;
        Object shared = row.get("shared");
        boolean isShared = (shared instanceof Number num && num.intValue() == 1)
                || Boolean.TRUE.equals(shared)
                || "1".equals(String.valueOf(shared));
        return isShared && incentiveAccessService.canViewShared();
    }

    public List<Map<String, Object>> readStats(String period) {
        return monthlyReadAggregate(normalizeMonth(period));
    }

    /**
     * 本月激励查询阅读排行榜（2026-09-13 新增页面数据源）
     *
     * 【需求】PC 端新增「本月激励查询阅读排行榜」页（入口在系统设置下，默认仅管理员可见，
     *         由管理员在角色管理里勾选后其它角色才可见）
     * 【方法】复用 {@link #monthlyReadAggregate(String)} 的月度聚合，再补部门名、已读率、名次与总体汇总
     * 【业务规则】口径与激励查询页的"已读/未读"完全一致：本月发布的记录为分母，某人对其中
     *         已读的条数为分子；已读率 = 已读 / 本月发布数（分母 0 时记 0）；名次按已读条数降序，
     *         已读相同再看未读更少者优先，保证榜单稳定
     * 【返回】{ period, publishedTotal, readTotal, participantCount, avgReadRate, ranking[] }
     */
    public Map<String, Object> readRanking(String period) {
        String month = normalizeMonth(period);
        List<Map<String, Object>> rows = monthlyReadAggregate(month);

        int publishedTotal = 0;
        int readTotal = 0;
        int participantCount = 0;
        for (Map<String, Object> row : rows) {
            int published = toInt(row.get(LIT_PUBLISHEDCOUNT));
            int read = toInt(row.get(LIT_READCOUNT));
            publishedTotal = Math.max(publishedTotal, published); // 本月发布数对所有人相同，取最大值即真实发布量
            readTotal += read;
            if (read > 0) participantCount++;
        }

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int published = toInt(row.get(LIT_PUBLISHEDCOUNT));
            int read = toInt(row.get(LIT_READCOUNT));
            int unread = Math.max(0, toInt(row.get(LIT_UNREADCOUNT)));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(LIT_USERID, row.get(LIT_USERID));
            item.put(LIT_REALNAME, row.get(LIT_REALNAME));
            item.put(LIT_DEPTNAME, row.get(LIT_DEPTNAME));
            item.put(LIT_PUBLISHEDCOUNT, published);
            item.put(LIT_READCOUNT, read);
            item.put(LIT_UNREADCOUNT, unread);
            item.put("readRate", rate(read, published));
            ranking.add(item);
        }
        ranking.sort((a, b) -> {
            int byRead = Integer.compare(toInt(b.get(LIT_READCOUNT)), toInt(a.get(LIT_READCOUNT)));
            if (byRead != 0) return byRead;
            int byUnread = Integer.compare(toInt(a.get(LIT_UNREADCOUNT)), toInt(b.get(LIT_UNREADCOUNT)));
            if (byUnread != 0) return byUnread;
            return String.valueOf(a.get(LIT_REALNAME)).compareTo(String.valueOf(b.get(LIT_REALNAME)));
        });
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).put("rank", i + 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", month);
        result.put("publishedTotal", publishedTotal);
        result.put("readTotal", readTotal);
        result.put("participantCount", participantCount);
        result.put("userCount", rows.size());
        result.put("avgReadRate", rate(readTotal, (long) publishedTotal * rows.size()));
        result.put("ranking", ranking);
        return result;
    }

    /** 月度已读聚合（readStats 与 readRanking 共用，避免两处维护同一段 SQL）。
     * 【PR-01 P0-03 2026-09-18】原生 SQL 绕过租户拦截器：用户集合限定当前集团成员，
     * 记录限定当月 + 当前集团 tenant；无集团归属直接返回空（fail-closed）。 */
    private List<Map<String, Object>> monthlyReadAggregate(String month) {
        Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        if (g == null) return List.of();
        List<Long> inGroup = com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, g);
        if (inGroup.isEmpty()) return List.of();
        String holders = String.join(",", java.util.Collections.nCopies(inGroup.size(), "?"));
        List<Object> args = new java.util.ArrayList<>();
        args.add(month);
        args.add(g);
        args.addAll(inGroup);
        return jdbc.queryForList(
                "SELECT u.id AS userId, u.real_name AS realName, d.dept_name AS deptName, "
                        + "COUNT(DISTINCT r.id) AS publishedCount, COUNT(DISTINCT br.record_id) AS readCount, "
                        + "COUNT(DISTINCT r.id)-COUNT(DISTINCT br.record_id) AS unreadCount "
                        + "FROM sys_user u "
                        + "LEFT JOIN sys_dept d ON d.id = u.dept_id "
                        + "LEFT JOIN biz_incentive_record r ON r.created_at LIKE CONCAT(?, '%') AND r.tenant_id = ? "
                        + "LEFT JOIN biz_incentive_read br ON br.user_id=u.id AND br.record_id=r.id "
                        + "WHERE u.deleted=0 AND u.id IN (" + holders + ") "
                        + "GROUP BY u.id,u.real_name,d.dept_name "
                        + "ORDER BY readCount DESC, unreadCount DESC LIMIT 100",
                args.toArray());
    }

    /** 周期口径：空/空白 → 当月（YYYY-MM） */
    private String normalizeMonth(String period) {
        if (period == null || period.isBlank()) return java.time.LocalDate.now().toString().substring(0, 7);
        return period.trim();
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** 百分比（保留 1 位小数）；分母 <= 0 时记 0 */
    private double rate(long part, long whole) {
        if (whole <= 0) return 0.0;
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    private record AssociationMaps(Map<Long, BizIncentiveCategory> categories, Map<Long, SysUser> users,
                                   Map<Long, BizCustomer> customers) {}

    void fillRecordAssociations(List<BizIncentiveRecord> records) {
        AssociationMaps maps = loadAssociationMaps(records);
        for (BizIncentiveRecord r : records) {
            applyCategoryName(r, maps.categories());
            applyUserNames(r, maps.users());
            applyCustomerName(r, maps.customers());
        }
    }

    private AssociationMaps loadAssociationMaps(List<BizIncentiveRecord> records) {
        Set<Long> catIds = records.stream().map(BizIncentiveRecord::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> uidSet = records.stream().map(BizIncentiveRecord::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        uidSet.addAll(records.stream().map(BizIncentiveRecord::getUpdatedBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        Set<Long> custIdSet = records.stream().map(BizIncentiveRecord::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizIncentiveCategory> catMap = catIds.isEmpty() ? Map.of()
                : categoryMapper.selectByIds(catIds).stream().collect(Collectors.toMap(BizIncentiveCategory::getId, c -> c));
        Map<Long, SysUser> userMap = uidSet.isEmpty() ? Map.of()
                : userMapper.selectByIds(uidSet).stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, BizCustomer> custMap = custIdSet.isEmpty() ? Map.of()
                : customerMapper.selectByIds(custIdSet).stream().collect(Collectors.toMap(BizCustomer::getId, c -> c));
        return new AssociationMaps(catMap, userMap, custMap);
    }

    /** 批量填充记录的文件名（从 JSON 数组解析并批量查询） */
    void fillFileNames(List<BizIncentiveRecord> records) {
        // 收集所有文件 ID
        Set<Long> fids = new LinkedHashSet<>();
        for (BizIncentiveRecord r : records) {
            fids.addAll(r.getFileIds());
            fids.addAll(r.getFilePreIds());
            fids.addAll(r.getFileMidIds());
            fids.addAll(r.getFilePostIds());
            fids.addAll(r.getFileTrackIds());
        }
        if (fids.isEmpty()) return;
        Map<Long, BizFile> fileMap = new HashMap<>();
        fileMapper.selectByIds(fids).forEach(f -> fileMap.put(f.getId(), f));

        for (BizIncentiveRecord r : records) {
            r.setFileName(joinFileNames(r.getFileIds(), fileMap));
            r.setFilePreName(joinFileNames(r.getFilePreIds(), fileMap));
            r.setFileMidName(joinFileNames(r.getFileMidIds(), fileMap));
            r.setFilePostName(joinFileNames(r.getFilePostIds(), fileMap));
            r.setFileTrackName(joinFileNames(r.getFileTrackIds(), fileMap));
        }
    }

    private String joinFileNames(List<Long> ids, Map<Long, BizFile> fileMap) {
        if (ids.isEmpty()) return null;
        return ids.stream()
            .map(fileMap::get)
            .filter(Objects::nonNull)
            .map(BizFile::getFileName)
            .collect(Collectors.joining(", "));
    }


    /** 四区排序权重：试点(0)→两谈两做(1)→交流拜访(2)→外出参观(3)，未知排最后(4) */
    private int categoryMainOrder(BizIncentiveCategory cat, Map<Long, BizIncentiveCategory> catMap) {
        if (cat == null) return 4;
        int order = mainOrderOfCode(cat.getCategoryCode());
        if (order == 4 && cat.getParentId() != null) {
            BizIncentiveCategory parent = catMap.get(cat.getParentId());
            if (parent != null) order = mainOrderOfCode(parent.getCategoryCode());
        }
        return order;
    }

    private int mainOrderOfCode(String code) {
        if (code == null) return 4;
        return switch (code) {
            case "trial" -> 0;
            case "plan" -> 1;
            case "exchange" -> 2;
            case "visit" -> 3;
            default -> 4;
        };
    }
}
