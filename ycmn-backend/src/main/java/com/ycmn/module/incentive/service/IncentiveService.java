package com.ycmn.module.incentive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.entity.BizCustomerShare;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncentiveService {

    private final BizIncentiveCategoryMapper categoryMapper;
    private final BizIncentiveRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final BizFileMapper fileMapper;
    private final BizCustomerMapper customerMapper;
    private final BizCustomerShareMapper customerShareMapper;
    private final SysDeptMapper deptMapper;
    private final SysOrgMapper orgMapper;
    private final JdbcTemplate jdbc;
    private final com.ycmn.common.scope.OrgScopeResolver scopeResolver;

    /** 查询用户是否有编辑权限（admin 始终有权限；多角色任一含 admin 即真） */
    public boolean canEdit(Long userId) {
        if (SecurityUtils.isAdmin()) return true;
        try {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT can_edit FROM biz_incentive_permission WHERE user_id=?", userId);
            return row != null && ((Number) row.get("can_edit")).intValue() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 人员列表（公开）：激励页下拉框用 */
    public List<Map<String, Object>> getUserList() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<SysUser> users = userMapper.selectList(null);
        for (SysUser u : users) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("realName", u.getRealName());
            list.add(m);
        }
        list.sort(Comparator.comparing(m -> (String) m.getOrDefault("realName", "")));
        return list;
    }

    /** 查询用户是否有删除权限（admin 始终有权限；多角色任一含 admin 即真） */
    public boolean canDelete(Long userId) {
        if (SecurityUtils.isAdmin()) return true;
        try {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT can_delete FROM biz_incentive_permission WHERE user_id=?", userId);
            return row != null && ((Number) row.get("can_delete")).intValue() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public List<BizIncentiveCategory> getCategoryTree() {
        List<BizIncentiveCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveCategory>()
                        .eq(BizIncentiveCategory::getStatus, 1)
                        .orderByAsc(BizIncentiveCategory::getSortOrder)
        );
        List<BizIncentiveCategory> tree = buildTree(all, 0L);
        fillRecordCounts(tree, all);
        return tree;
    }

    /** 统计查询统一数据范围过滤：admin/有激励编辑删除授权 → 全部；否则 resolver 可见集合 U */
    private void applyScopeToWrapper(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        com.ycmn.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return;
        boolean canSeeAll = SecurityUtils.isAdmin()
                || canEdit(user.getUserId()) || canDelete(user.getUserId());
        List<Long> visible = canSeeAll ? null : scopeResolver.resolve(user);
        if (visible != null) wrapper.in(BizIncentiveRecord::getUserId, visible);
    }

    /**
     * 分类角标统计：按当前账号权限可见范围（admin/有激励编辑删除授权 → 全量；
     * 否则 resolver 可见集合 U），每个分类含子分类聚合。
     */
    private void fillRecordCounts(List<BizIncentiveCategory> tree, List<BizIncentiveCategory> all) {
        com.ycmn.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null || all.isEmpty()) return;
        boolean canSeeAll = SecurityUtils.isAdmin()
                || canEdit(user.getUserId()) || canDelete(user.getUserId());
        List<Long> visible = canSeeAll ? null : scopeResolver.resolve(user);

        java.util.Map<Long, Long> countByCat = recordMapper.selectList(
                        new LambdaQueryWrapper<BizIncentiveRecord>()
                                .select(BizIncentiveRecord::getCategoryId)
                                .in(visible != null && !visible.isEmpty(), BizIncentiveRecord::getUserId, visible))
                .stream()
                .filter(r -> r.getCategoryId() != null)
                .collect(java.util.stream.Collectors.groupingBy(BizIncentiveRecord::getCategoryId,
                        java.util.stream.Collectors.counting()));

        java.util.Map<Long, BizIncentiveCategory> byId = all.stream()
                .collect(java.util.stream.Collectors.toMap(BizIncentiveCategory::getId, c -> c));
        for (BizIncentiveCategory cat : all) {
            long total = countByCat.getOrDefault(cat.getId(), 0L);
            total += countDescendants(cat.getId(), byId, countByCat);
            cat.setRecordCount((int) total);
        }
    }

    private long countDescendants(Long parentId, java.util.Map<Long, BizIncentiveCategory> byId,
                                  java.util.Map<Long, Long> countByCat) {
        long sum = 0;
        for (BizIncentiveCategory c : byId.values()) {
            if (java.util.Objects.equals(c.getParentId(), parentId)) {
                sum += countByCat.getOrDefault(c.getId(), 0L);
                sum += countDescendants(c.getId(), byId, countByCat);
            }
        }
        return sum;
    }

    /**
     * 校验类别层级：只允许两级（一级 parentId=0，二级 parentId 为现存顶级类别 id）
     */
    private void validateCategoryLevel(BizIncentiveCategory category) {
        Long parentId = category.getParentId();
        if (parentId == null || parentId == 0L) {
            category.setParentId(0L);
            return;
        }
        BizIncentiveCategory parent = categoryMapper.selectById(parentId);
        if (parent == null || (parent.getParentId() != null && parent.getParentId() != 0L)) {
            throw new BusinessException("上级类别不存在或不允许三级嵌套，最多支持一级/二级");
        }
    }

    public void createCategory(BizIncentiveCategory category) {
        validateCategoryLevel(category);
        categoryMapper.insert(category);
    }

    public void updateCategory(BizIncentiveCategory category) {
        validateCategoryLevel(category);
        categoryMapper.updateById(category);
    }

    public void deleteCategory(Long id) {
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该类别下有 " + childCount + " 个子项，请先删除子项");
        }
        categoryMapper.deleteById(id);
    }

    public List<BizIncentiveRecord> getRecords(Long userId, String period, Long parentCategoryId) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(BizIncentiveRecord::getUserId, userId);
        if (period != null) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (parentCategoryId != null) {
            List<BizIncentiveCategory> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, parentCategoryId)
            );
            List<Long> childIds = children.stream().map(BizIncentiveCategory::getId).toList();
            if (!childIds.isEmpty()) {
                wrapper.in(BizIncentiveRecord::getCategoryId, childIds);
            }
        }
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        // 批量填充关联名称(替代逐条 selectById 的 N+1,一次 IN 查询按 id 建 Map)
        java.util.Set<Long> catIds = records.stream().map(BizIncentiveRecord::getCategoryId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> uidSet = records.stream().map(BizIncentiveRecord::getUserId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> custIdSet = records.stream().map(BizIncentiveRecord::getCustomerId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, BizIncentiveCategory> catMap = catIds.isEmpty() ? java.util.Map.of()
                : categoryMapper.selectBatchIds(catIds).stream().collect(java.util.stream.Collectors.toMap(BizIncentiveCategory::getId, c -> c));
        java.util.Map<Long, SysUser> userMap = uidSet.isEmpty() ? java.util.Map.of()
                : userMapper.selectBatchIds(uidSet).stream().collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));
        java.util.Map<Long, BizCustomer> custMap = custIdSet.isEmpty() ? java.util.Map.of()
                : customerMapper.selectBatchIds(custIdSet).stream().collect(java.util.stream.Collectors.toMap(BizCustomer::getId, c -> c));
        for (BizIncentiveRecord r : records) {
            if (r.getCategoryId() != null) {
                BizIncentiveCategory cat = catMap.get(r.getCategoryId());
                if (cat != null) r.setCategoryName(cat.getCategoryName());
            }
            if (r.getUserId() != null) {
                SysUser user = userMap.get(r.getUserId());
                if (user != null) r.setUserName(user.getRealName());
            }
            if (r.getCustomerId() != null) {
                BizCustomer cust = custMap.get(r.getCustomerId());
                if (cust != null) r.setCustomerName(cust.getCustomerName());
            }
        }
        fillFileNames(records);
        return records;
    }

    public BizIncentiveRecord getRecordById(Long id) {
        return recordMapper.selectById(id);
    }

    public void deleteRecord(Long id) {
        recordMapper.deleteById(id);
    }

    /** 更新记录（追加/删除文件时调用），自动重算积分 */
    @Transactional
    public void updateRecord(Long id, BizIncentiveRecord update) {
        BizIncentiveRecord existing = recordMapper.selectById(id);
        if (existing == null) return;

        // 只更新文件字段
        if (update.getFilePreId() != null) existing.setFilePreId(update.getFilePreId());
        if (update.getFileMidId() != null) existing.setFileMidId(update.getFileMidId());
        if (update.getFilePostId() != null) existing.setFilePostId(update.getFilePostId());
        if (update.getFileId() != null) existing.setFileId(update.getFileId());
        if (update.getFileTrackId() != null) existing.setFileTrackId(update.getFileTrackId());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        if (update.getSpecificProduct() != null) existing.setSpecificProduct(update.getSpecificProduct());

        // 重算积分
        BizIncentiveCategory category = categoryMapper.selectById(existing.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        existing.setScore(calcScore(existing, category, base));

        recordMapper.updateById(existing);
    }

    /** 批量重算所有历史记录的积分（按新规则） */
    @Transactional
    public int recalculateAllScores() {
        List<BizIncentiveRecord> all = recordMapper.selectList(null);
        int changed = 0;
        for (BizIncentiveRecord r : all) {
            BizIncentiveCategory category = categoryMapper.selectById(r.getCategoryId());
            int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
            int newScore = calcScore(r, category, base);
            if (!Integer.valueOf(newScore).equals(r.getScore())) {
                r.setScore(newScore);
                recordMapper.updateById(r);
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public void addRecord(BizIncentiveRecord record) {
        BizIncentiveCategory category = categoryMapper.selectById(record.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        record.setScore(calcScore(record, category, base));
        record.setUserId(SecurityUtils.getCurrentUserId());
        recordMapper.insert(record);
    }

    /**
     * 积分规则（2026-08-01 单槽化改造后）：
     * 1. 两谈两做方案(plan)：按文件总数计分，每 1 个文件 +base，上限 5×base
     * 2. 外出参观(visit)/交流拜访(exchange)：安装文件任一槽有文件 → +base
     * 3. 其他（含产品试点 trial）：安装前+安装中+安装后 三文件齐全 → +base
     * 4. 否则 0 分
     */
    private int calcScore(BizIncentiveRecord r, BizIncentiveCategory category, int base) {
        if (category == null || base <= 0) return 0;

        boolean hasPre  = r.hasFiles(r.getFilePreId() != null ? r.getFilePreId() : null);
        boolean hasMid  = r.hasFiles(r.getFileMidId() != null ? r.getFileMidId() : null);
        boolean hasPost = r.hasFiles(r.getFilePostId() != null ? r.getFilePostId() : null);

        if (isPlanCategory(category)) {
            // 两谈两做：文件总数 × base（上限 5×base）
            int fileCount = r.getFilePreIds().size() + r.getFileMidIds().size() + r.getFilePostIds().size();
            return Math.min(fileCount, 5) * base;
        } else if (isVisitExchangeCategory(category)) {
            // 外出参观/交流拜访：任一槽有文件即得分
            return (hasPre || hasMid || hasPost) ? base : 0;
        } else {
            // 其他（含产品试点）：三文件齐全
            return (hasPre && hasMid && hasPost) ? base : 0;
        }
    }

    /** 判断子类是否属于"外出参观/交流拜访"大类（category_code = 'visit'/'exchange'） */
    private boolean isVisitExchangeCategory(BizIncentiveCategory category) {
        if (category == null) return false;
        if ("visit".equals(category.getCategoryCode()) || "exchange".equals(category.getCategoryCode())) return true;
        if (category.getParentId() != null) {
            BizIncentiveCategory parent = categoryMapper.selectById(category.getParentId());
            return parent != null && ("visit".equals(parent.getCategoryCode()) || "exchange".equals(parent.getCategoryCode()));
        }
        return false;
    }

    /** 四区排序权重：试点(0)→两谈两做(1)→交流拜访(2)→外出参观(3)，未知排最后(4) */
    private int categoryMainOrder(BizIncentiveCategory cat, Map<Long, BizIncentiveCategory> catMap) {
        if (cat == null) return 4;
        int order = mainOrderOfCode(cat.getCategoryCode());
        // 子类 code 为 "trial_seal" 等带前缀值：查父级顶层 code
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

    /** 判断子类是否属于"两谈两做方案"大类（category_code = 'plan'） */
    private boolean isPlanCategory(BizIncentiveCategory category) {
        if (category == null) return false;
        if ("plan".equals(category.getCategoryCode())) return true;
        if (category.getParentId() != null) {
            BizIncentiveCategory parent = categoryMapper.selectById(category.getParentId());
            return parent != null && "plan".equals(parent.getCategoryCode());
        }
        return false;
    }

    /** 单条记录是否计分(任意文件槽位有文件即算; 兼容旧版 fileId) */
    private int fileCountOf(BizIncentiveRecord r) {
        if (r.hasAnyFile()) return 1;
        return 0;
    }

    /** 批量填充记录的文件名（从 JSON 数组解析并批量查询） */
    private void fillFileNames(List<BizIncentiveRecord> records) {
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
        fileMapper.selectBatchIds(fids).forEach(f -> fileMap.put(f.getId(), f));

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

    /** 激励统计表(全员口径): 按子类聚合文件数/总学分, 并给出周期总文件数/总学分 */
    public Map<String, Object> categoryStats(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        applyScopeToWrapper(wrapper); // 统计按数据范围整体（设计定稿）
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        Map<Long, int[]> byCat = new LinkedHashMap<>(); // categoryId -> [fileCount, totalScore, pre, mid, post]
        int totalFiles = 0, totalScore = 0;
        int totalPre = 0, totalMid = 0, totalPost = 0;
        for (BizIncentiveRecord r : records) {
            int fc = fileCountOf(r);
            int sc = fc > 0 ? (r.getScore() != null ? r.getScore() : 0) : 0;
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
            m.put("totalScore", e.getValue()[1]);
            m.put("preCount", e.getValue()[2]);
            m.put("midCount", e.getValue()[3]);
            m.put("postCount", e.getValue()[4]);
            byCategory.add(m);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("byCategory", byCategory);
        result.put("totalFiles", totalFiles);
        result.put("totalScore", totalScore);
        result.put("totalPre", totalPre);
        result.put("totalMid", totalMid);
        result.put("totalPost", totalPost);
        return result;
    }

    // 个人积分榜
    public List<Map<String, Object>> scoreboard(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        applyScopeToWrapper(wrapper); // 统计按数据范围整体
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        Map<Long, int[]> userAgg = new LinkedHashMap<>(); // userId -> [score, recordCount, pre, mid, post]
        Map<Long, java.util.Map<Long, Integer>> userByCat = new HashMap<>(); // userId -> categoryId -> 记录数
        for (BizIncentiveRecord r : records) {
            int sc = fileCountOf(r) > 0 ? (r.getScore() != null ? r.getScore() : 0) : 0;
            int pre = r.hasFiles(r.getFilePreId()) ? 1 : 0;
            int mid = r.hasFiles(r.getFileMidId()) ? 1 : 0;
            int post = r.hasFiles(r.getFilePostId()) ? 1 : 0;
            int[] agg = userAgg.computeIfAbsent(r.getUserId(), k -> new int[5]);
            agg[0] += sc;
            agg[1] += 1;
            agg[2] += pre;
            agg[3] += mid;
            agg[4] += post;
            if (r.getCategoryId() != null) {
                userByCat.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                        .merge(r.getCategoryId(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        List<Map.Entry<Long, int[]>> sorted = new ArrayList<>(userAgg.entrySet());
        sorted.sort((a, b) -> b.getValue()[0] - a.getValue()[0]);

        for (Map.Entry<Long, int[]> e : sorted) {
            rank++;
            int[] agg = e.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank);
            item.put("userId", e.getKey());
            item.put("totalScore", agg[0]);
            item.put("recordCount", agg[1]);
            item.put("preCount", agg[2]);
            item.put("midCount", agg[3]);
            item.put("postCount", agg[4]);
            item.put("byCategory", userByCat.getOrDefault(e.getKey(), java.util.Map.of()));
            SysUser user = userMapper.selectById(e.getKey());
            if (user != null) {
                item.put("realName", user.getRealName());
                item.put("deptId", user.getDeptId());
                String officeName = "未知";
                if (user.getOfficeId() != null) {
                    SysDept office = deptMapper.selectById(user.getOfficeId());
                    if (office != null) officeName = office.getDeptName();
                }
                item.put("officeName", officeName);
            }
            result.add(item);
        }
        return result;
    }

    // 部门排行
    public List<Map<String, Object>> deptRanking(String period) {
        List<Map<String, Object>> personal = scoreboard(period);
        Map<String, int[]> deptAgg = new LinkedHashMap<>();
        Map<String, java.util.Map<Long, Integer>> deptByCat = new HashMap<>();
        for (Map<String, Object> p : personal) {
            String dept = (String) p.getOrDefault("officeName", "未知");
            int[] agg = deptAgg.computeIfAbsent(dept, k -> new int[]{0, 0});
            agg[0] += ((Number) p.getOrDefault("totalScore", 0)).intValue();
            agg[1] += 1;
            // 部门品类聚合（个人 byCategory 求和）
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Integer> pbc = (java.util.Map<Long, Integer>) p.getOrDefault("byCategory", java.util.Map.of());
            for (Map.Entry<Long, Integer> en : pbc.entrySet()) {
                deptByCat.computeIfAbsent(dept, k -> new HashMap<>()).merge(en.getKey(), en.getValue(), Integer::sum);
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
            row.put("deptName", e.getKey());
            row.put("totalScore", e.getValue()[0]);
            row.put("participantCount", e.getValue()[1]);
            row.put("byCategory", deptByCat.getOrDefault(e.getKey(), java.util.Map.of()));
            row.put("avgScore", e.getValue()[1] > 0
                    ? Math.round((double) e.getValue()[0] / e.getValue()[1] * 10) / 10.0 : 0);
            result.add(row);
        }
        return result;
    }

    // 排行统计概览
    public Map<String, Object> rankingStats(String period) {
        List<Map<String, Object>> personal = scoreboard(period);
        Map<String, Object> stats = new HashMap<>();
        stats.put("participantCount", personal.size());

        Set<String> depts = new HashSet<>();
        int topScore = 0;
        String topName = "";
        int topRecordCount = 0;
        int totalRecordCount = 0;

        for (Map<String, Object> p : personal) {
            depts.add((String) p.getOrDefault("officeName", "未知"));
            int score = ((Number) p.getOrDefault("totalScore", 0)).intValue();
            int rc = ((Number) p.getOrDefault("recordCount", 0)).intValue();
            totalRecordCount += rc;
            if (score > topScore) {
                topScore = score;
                topName = (String) p.getOrDefault("realName", "");
                topRecordCount = rc;
            }
        }
        stats.put("deptCount", depts.size());
        stats.put("topScore", topScore);
        stats.put("topScoreName", topName);
        stats.put("topRecordCount", topRecordCount);
        stats.put("totalRecordCount", totalRecordCount);
        return stats;
    }

    /**
     * 获取客户关联的激励积分总分
     */
    public int getScoreByCustomerId(Long customerId) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizIncentiveRecord::getCustomerId, customerId);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
        return records.stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
    }

    /** 激励统计查询（全员可见）：支持大类/子类/行业/产品/周期筛选，返回完整明细 */
    public List<BizIncentiveRecord> queryRecords(Long mainCategoryId, Long subCategoryId,
                                                  String industry, String product, String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (industry != null && !industry.isBlank()) wrapper.eq(BizIncentiveRecord::getIndustry, industry);
        if (product != null && !product.isBlank()) wrapper.like(BizIncentiveRecord::getRelatedProducts, product);
        if (subCategoryId != null) {
            wrapper.eq(BizIncentiveRecord::getCategoryId, subCategoryId);
        } else if (mainCategoryId != null) {
            List<BizIncentiveCategory> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, mainCategoryId)
            );
            List<Long> childIds = new ArrayList<>();
            for (BizIncentiveCategory child : children) {
                childIds.add(child.getId());
                // 三级结构：再查孙级
                List<BizIncentiveCategory> grands = categoryMapper.selectList(
                        new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, child.getId())
                );
                for (BizIncentiveCategory g : grands) childIds.add(g.getId());
            }
            if (!childIds.isEmpty()) wrapper.in(BizIncentiveRecord::getCategoryId, childIds);
        }
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
        for (BizIncentiveRecord r : records) {
            if (r.getCategoryId() != null) {
                BizIncentiveCategory cat = categoryMapper.selectById(r.getCategoryId());
                if (cat != null) r.setCategoryName(cat.getCategoryName());
            }
            if (r.getUserId() != null) {
                SysUser user = userMapper.selectById(r.getUserId());
                if (user != null) r.setUserName(user.getRealName());
            }
            if (r.getCustomerId() != null) {
                BizCustomer cust = customerMapper.selectById(r.getCustomerId());
                if (cust != null) {
                    r.setCustomerName(cust.getCustomerName());
                    r.setLastInvoiceDate(cust.getLastInvoiceDate());
                    if (cust.getLastInvoiceDate() != null) {
                        r.setOverdueDays(java.time.LocalDate.now().toEpochDay() - cust.getLastInvoiceDate().toEpochDay());
                    }
                }
            }
        }
        fillFileNames(records);
        return records;
    }

    // ======================== 数据权限过滤 ========================

    /**
     * 获取当前用户可见的用户ID列表（数据权限范围）
     * 同时包含通过客户分享可见的客户ID集合
     */
    private Map<String, Object> computeVisibility() {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        // admin、有激励编辑/删除权限的人：看全部
        if (isAdmin || canEdit(userId) || canDelete(userId)) {
            return Map.of("type", "all");
        }

        // 获取数据范围内的用户ID（null=范围覆盖全集团，无限制）
        Set<Long> visibleUserIds = getDataScopeUserIds();
        if (visibleUserIds == null) {
            // all 级范围（非 admin/无激励授权）：本集团全部数据
            return Map.of("type", "all");
        }

        // 获取分享给我的客户ID
        Set<Long> sharedCustomerIds = new HashSet<>();
        List<BizCustomerShare> shares = customerShareMapper.selectList(
            new LambdaQueryWrapper<BizCustomerShare>().eq(BizCustomerShare::getSharedToUserId, userId));
        for (BizCustomerShare s : shares) {
            sharedCustomerIds.add(s.getCustomerId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", "limited");
        result.put("userIds", visibleUserIds);
        result.put("sharedCustomerIds", sharedCustomerIds);
        return result;
    }

    /** 根据数据权限获取可见的用户ID列表 */
    private Set<Long> getDataScopeUserIds() {
        String dataScope = SecurityUtils.getCurrentDataScope();
        Long userId = SecurityUtils.getCurrentUserId();
        Long deptId = SecurityUtils.getCurrentDeptId();
        List<Long> managedOrgIds = SecurityUtils.getCurrentManagedOrgIds();

        Set<Long> userIds = new HashSet<>();
        userIds.add(userId); // 始终包含自己

        if (dataScope == null || "self".equals(dataScope)) {
            return userIds;
        }

        switch (dataScope) {
            case "custom":
                // 【修复 FEAT-20260804-ORG-DEPT-UNIFY】此前 switch 没有 custom 分支，静默降级为
                // "仅本人"。custom = 角色组织矩阵(sys_role_org)勾选的组织，与 CustomerService 保持一致。
                userIds.addAll(getUserIdsByDeptIds(managedOrgIds));
                break;
            case "org_and_children":
                // 【修复 FEAT-20260804-ORG-DEPT-UNIFY】org 体系统一改用部门体系解析（sys_user.org_id
                // 从未被写入，此前恒定降级为仅本人）；org/dept 同一 ID 空间，并入组织管理员额外授权组织。
                List<Long> orgAndChildIds = deptId != null ? getDescendantDeptIds(deptId) : new ArrayList<>();
                if (deptId != null) orgAndChildIds.add(deptId);
                orgAndChildIds.addAll(managedOrgIds);
                userIds.addAll(getUserIdsByDeptIds(orgAndChildIds));
                break;
            case "org":
                List<Long> orgIds = new ArrayList<>();
                if (deptId != null) orgIds.add(deptId);
                orgIds.addAll(managedOrgIds);
                userIds.addAll(getUserIdsByDeptIds(orgIds));
                break;
            case "dept_and_children":
                List<Long> deptIds = getDescendantDeptIds(deptId);
                deptIds.add(deptId);
                userIds.addAll(getUserIdsByDeptIds(deptIds));
                break;
            // 新六级码（team/office/region/dept/all）：统一路由 OrgScopeResolver
            // （设计 §4.2；dept=本部门含子部门；多角色并集在 resolver 内生效）；
            // null=all → 返回 null 表示全集团无限制；custom 由上方既有 case 处理
            case "team":
            case "office":
            case "region":
            case "dept":
            case "all": {
                List<Long> visible = scopeResolver.resolve(SecurityUtils.getCurrentUser());
                if (visible == null) return null;
                userIds.addAll(visible);
                break;
            }
        }
        return userIds;
    }

    /** 递归获取所有下级部门ID */
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

    /** 获取指定部门列表中所有活跃用户的ID */
    private Set<Long> getUserIdsByDeptIds(List<Long> deptIds) {
        if (deptIds.isEmpty()) return new HashSet<>();
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getDeptId, deptIds)
                .eq(SysUser::getStatus, 1)
                .eq(SysUser::getDeleted, 0))
            .stream().map(SysUser::getId).collect(Collectors.toSet());
    }

    /**
     * 为激励记录查询添加数据权限过滤
     * 返回 true 表示无限制（看全部），false 表示已应用限制
     */
    private boolean applyIncentiveDataScope(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        Map<String, Object> visibility = computeVisibility();
        if ("all".equals(visibility.get("type"))) {
            return true; // 无限制
        }
        @SuppressWarnings("unchecked")
        Set<Long> userIds = (Set<Long>) visibility.get("userIds");
        @SuppressWarnings("unchecked")
        Set<Long> sharedCustomerIds = (Set<Long>) visibility.get("sharedCustomerIds");

        if (sharedCustomerIds.isEmpty()) {
            wrapper.in(BizIncentiveRecord::getUserId, userIds);
        } else {
            wrapper.and(w -> w.in(BizIncentiveRecord::getUserId, userIds)
                .or().in(BizIncentiveRecord::getCustomerId, sharedCustomerIds));
        }
        return false;
    }

    /** 时间线查询：按上传时间排序的扁平列表 */
    public List<Map<String, Object>> timeline(String customerName, String salesperson,
            String industry, Long mainCategoryId, String startDate, String endDate) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        applyIncentiveDataScope(wrapper);
        if (customerName != null && !customerName.isBlank()) {
            List<BizCustomer> m = customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName));
            if (!m.isEmpty()) wrapper.in(BizIncentiveRecord::getCustomerId, m.stream().map(BizCustomer::getId).toList());
            else return List.of();
        }
        if (salesperson != null && !salesperson.isBlank()) {
            List<SysUser> u = userMapper.selectList(new LambdaQueryWrapper<SysUser>().like(SysUser::getRealName, salesperson));
            if (!u.isEmpty()) wrapper.in(BizIncentiveRecord::getUserId, u.stream().map(SysUser::getId).toList());
            else return List.of();
        }
        if (industry != null && !industry.isBlank()) wrapper.eq(BizIncentiveRecord::getIndustry, industry);
        if (mainCategoryId != null) {
            Set<Long> ids = new HashSet<>(); ids.add(mainCategoryId);
            categoryMapper.selectList(new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, mainCategoryId))
                .forEach(c -> { ids.add(c.getId()); categoryMapper.selectList(new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, c.getId())).forEach(g -> ids.add(g.getId())); });
            wrapper.in(BizIncentiveRecord::getCategoryId, ids);
        }
        if (startDate != null && !startDate.isBlank()) wrapper.ge(BizIncentiveRecord::getCreatedAt, startDate + " 00:00:00");
        if (endDate != null && !endDate.isBlank()) wrapper.le(BizIncentiveRecord::getCreatedAt, endDate + " 23:59:59");
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
        // 填充
        Set<Long> cids = records.stream().map(BizIncentiveRecord::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizCustomer> cmap = new HashMap<>();
        if (!cids.isEmpty()) customerMapper.selectBatchIds(cids).forEach(c -> cmap.put(c.getId(), c));
        Map<Long, BizIncentiveCategory> catmap = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> catmap.put(c.getId(), c));
        for (BizIncentiveRecord r : records) {
            BizCustomer c = cmap.get(r.getCustomerId()); if (c != null) r.setCustomerName(c.getCustomerName());
            BizIncentiveCategory cat = catmap.get(r.getCategoryId()); if (cat != null) r.setCategoryName(cat.getCategoryName());
            if (r.getUserId() != null) { SysUser u = userMapper.selectById(r.getUserId()); if (u != null) r.setUserName(u.getRealName()); }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (BizIncentiveRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId()); m.put("customerId", r.getCustomerId()); m.put("customerName", r.getCustomerName());
            m.put("categoryId", r.getCategoryId()); m.put("categoryName", r.getCategoryName());
            m.put("period", r.getPeriod()); m.put("industry", r.getIndustry()); m.put("relatedProducts", r.getRelatedProducts());
            m.put("specificProduct", r.getSpecificProduct());
            m.put("score", r.getScore()); m.put("userName", r.getUserName());
            m.put("fileId", r.getFileId()); m.put("filePreId", r.getFilePreId()); m.put("fileMidId", r.getFileMidId()); m.put("filePostId", r.getFilePostId()); m.put("fileTrackId", r.getFileTrackId());
            m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            result.add(m);
        }
        return result;
    }

    /** 按客户分组查询激励记录 — 多维度筛选（2026-08-01 新增 product/keyword 搜索与四区排序） */
    public List<Map<String, Object>> getCustomerRecords(String industry, Long mainCategoryId,
            String customerName, String salesperson, String startDate, String endDate, String orgIds, String devType,
            String product, String keyword) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BizIncentiveRecord::getCustomerId);
        applyIncentiveDataScope(wrapper);
        if (industry != null && !industry.isBlank()) wrapper.eq(BizIncentiveRecord::getIndustry, industry);
        // 客户名 OR 关键字（相关产品）匹配：搜客户=该客户共享内容；搜关键字=相关案例全出
        if (customerName != null && !customerName.isBlank()
                && (keyword == null || keyword.isBlank())) {
            // 仅客户名匹配：保持原行为
            List<BizCustomer> matched = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName)
            );
            if (!matched.isEmpty()) {
                wrapper.in(BizIncentiveRecord::getCustomerId, matched.stream().map(BizCustomer::getId).toList());
            } else { return List.of(); }
        }
        if (product != null && !product.isBlank()) {
            wrapper.like(BizIncentiveRecord::getRelatedProducts, product);
        }
        if (keyword != null && !keyword.isBlank()) {
            if (customerName != null && !customerName.isBlank()) {
                List<BizCustomer> matched = customerMapper.selectList(
                    new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName)
                );
                if (matched.isEmpty()) {
                    wrapper.and(w -> w.like(BizIncentiveRecord::getRelatedProducts, keyword)
                            .or().like(BizIncentiveRecord::getSpecificProduct, keyword));
                } else {
                    List<Long> cids = matched.stream().map(BizCustomer::getId).toList();
                    wrapper.and(w -> w.in(BizIncentiveRecord::getCustomerId, cids)
                        .or().like(BizIncentiveRecord::getRelatedProducts, keyword)
                        .or().like(BizIncentiveRecord::getSpecificProduct, keyword));
                }
            } else {
                wrapper.and(w -> w.like(BizIncentiveRecord::getRelatedProducts, keyword)
                        .or().like(BizIncentiveRecord::getSpecificProduct, keyword));
            }
        }
        if (salesperson != null && !salesperson.isBlank()) {
            List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().like(SysUser::getRealName, salesperson)
            );
            if (!users.isEmpty()) {
                wrapper.in(BizIncentiveRecord::getUserId, users.stream().map(SysUser::getId).toList());
            } else { return List.of(); }
        }
        if (mainCategoryId != null) {
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
        if (orgIds != null && !orgIds.isBlank()) {
            List<Long> oids = java.util.Arrays.stream(orgIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
            if (!oids.isEmpty()) {
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
                if (!uids.isEmpty()) wrapper.in(BizIncentiveRecord::getUserId, uids);
                else return List.of();
            }
        }
        if (devType != null && !devType.isBlank()) {
            List<BizCustomer> devCustomers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>().eq(BizCustomer::getDevType, devType)
            );
            if (!devCustomers.isEmpty()) {
                wrapper.in(BizIncentiveRecord::getCustomerId, devCustomers.stream().map(BizCustomer::getId).toList());
            } else { return List.of(); }
        }
        if (startDate != null && !startDate.isBlank()) wrapper.ge(BizIncentiveRecord::getCreatedAt, startDate + " 00:00:00");
        if (endDate != null && !endDate.isBlank()) wrapper.le(BizIncentiveRecord::getCreatedAt, endDate + " 23:59:59");
        wrapper.orderByDesc(BizIncentiveRecord::getCreatedAt);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        // 批量查客户
        Set<Long> cids = records.stream().map(BizIncentiveRecord::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizCustomer> custMap = new HashMap<>();
        if (!cids.isEmpty()) {
            List<BizCustomer> custs = customerMapper.selectBatchIds(cids);
            for (BizCustomer c : custs) custMap.put(c.getId(), c);
        }
        // 批量查分类
        Set<Long> catIds = records.stream().map(BizIncentiveRecord::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizIncentiveCategory> catMap2 = new HashMap<>();
        if (!catIds.isEmpty()) {
            List<BizIncentiveCategory> cats = categoryMapper.selectBatchIds(catIds);
            for (BizIncentiveCategory c : cats) catMap2.put(c.getId(), c);
        }

        // 批量查文件 & 填充文件名
        fillFileNames(records);

        // 填充关联信息
        for (BizIncentiveRecord r : records) {
            if (r.getCategoryId() != null) {
                BizIncentiveCategory cat = catMap2.get(r.getCategoryId());
                if (cat != null) r.setCategoryName(cat.getCategoryName());
            }
            if (r.getUserId() != null) { SysUser u = userMapper.selectById(r.getUserId()); if (u != null) r.setUserName(u.getRealName()); }
            if (r.getCustomerId() != null) {
                BizCustomer c = custMap.get(r.getCustomerId());
                if (c != null) r.setCustomerName(c.getCustomerName());
            }
        }

        // 按客户分组
        Map<Long, List<BizIncentiveRecord>> grouped = new LinkedHashMap<>();
        for (BizIncentiveRecord r : records) {
            Long cid = r.getCustomerId();
            if (cid == null) continue;
            grouped.computeIfAbsent(cid, k -> new ArrayList<>()).add(r);
        }

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

        // 行业排序
        List<String> industryOrder = List.of("冶金","煤矿","化工","电力","有色金属","机械","工程公司","维修厂","其他终端客户");
        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < industryOrder.size(); i++) orderMap.put(industryOrder.get(i), i);

        // 每个客户一个条目
        List<Map<String, Object>> flatList = new ArrayList<>();
        for (Map.Entry<Long, List<BizIncentiveRecord>> e : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerId", e.getKey());
            BizCustomer cust = custMap.get(e.getKey());
            String ind = cust != null ? cust.getIndustry() : "其他";
            item.put("customerName", cust != null ? cust.getCustomerName() : null);
            item.put("industry", ind);
            item.put("industryOrder", orderMap.getOrDefault(ind, 99));
            item.put("count", e.getValue().size());
            int totalScore = e.getValue().stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
            item.put("totalScore", totalScore);
            item.put("records", e.getValue());
            flatList.add(item);
        }
        flatList.sort(Comparator.comparingInt(m -> (int) m.get("industryOrder")));
        return flatList;
    }

    private List<BizIncentiveCategory> buildTree(List<BizIncentiveCategory> list, Long parentId) {
        return list.stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .peek(c -> c.setChildren(buildTree(list, c.getId())))
                .collect(Collectors.toList());
    }
}
