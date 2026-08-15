package com.ycmn.module.incentive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
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
    private final SysDeptMapper deptMapper;
    private final com.ycmn.common.scope.OrgScopeResolver scopeResolver;

    /**
     * 【2026-08-14 RBAC 收敛】独立"看全部"资格：admin ∨ incentive:view-all 权限码
     * （M1 迁移将 incentive:view-all 绑定 admin+销售总监角色，替代 biz_incentive_permission.can_view_all 直查）。
     * 可看全部激励(含未分享)，但不含编辑/删除他人。
     */
    public boolean canViewAll(Long userId) {
        return SecurityUtils.isAdmin() || SecurityUtils.hasAuthority("incentive:view-all");
    }

    /**
     * 【2026-08-14 RBAC 收敛】查看已分享记录资格：incentive:view-shared 权限码
     * （「激励-分享可见」角色由 M1 迁移全员绑定，等价原"无面板行默认可见"的语义）。
     */
    public boolean canViewShared(Long userId) {
        return SecurityUtils.hasAuthority("incentive:view-shared");
    }

    /** 【2026-08-14 RBAC 收敛】编辑他人激励记录资格：admin ∨ incentive:manage（M1 迁移绑定 admin+销售总监）。本人校验在 Controller */
    public boolean canEdit() {
        return SecurityUtils.isAdmin() || SecurityUtils.hasAuthority("incentive:manage");
    }

    /** 人员列表（公开）：激励页下拉框用 */
    public List<Map<String, Object>> getUserList() {
        List<Map<String, Object>> list = new ArrayList<>();
        // 【修复 2026-08-08 全量用户下拉】不再返回全部用户，按数据范围(含系统级开关)过滤
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, 1);
        List<Long> visible = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visible != null) wrapper.in(SysUser::getId, visible);
        List<SysUser> users = userMapper.selectList(wrapper);
        for (SysUser u : users) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("realName", u.getRealName());
            list.add(m);
        }
        list.sort(Comparator.comparing(m -> (String) m.getOrDefault("realName", "")));
        return list;
    }

    /** 【2026-08-14 RBAC 收敛】删除他人激励记录资格：admin ∨ incentive:manage（M1 迁移绑定 admin+销售总监）。本人校验在 Controller */
    public boolean canDelete() {
        return SecurityUtils.isAdmin() || SecurityUtils.hasAuthority("incentive:manage");
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

    /**
     * 【2026-08-14 RBAC 收敛】看全部资格：系统管理员 ∨ incentive:view-all ∨ incentive:manage。
     * 激励模块可见性统一为分享制，不再使用 data_scope/上级可见下级/负责人可见本组织/客户分享；
     * 替代原 biz_incentive_permission 直查与销售总监角色硬编码。
     */
    private boolean canSeeAllIncentive() {
        return SecurityUtils.isAdmin()
                || SecurityUtils.hasAuthority("incentive:view-all")
                || SecurityUtils.hasAuthority("incentive:manage");
    }

    /**
     * 【2026-08-08 分享制+双勾选】记录列表可见性统一：canSeeAllIncentive → 全部；
     * 否则"本人 ∨ (已分享 AND canViewShared)"——面板取消"查看已分享"的用户不可见他人分享记录。
     */
    private void applyShareVisibility(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        if (canSeeAllIncentive()) return;
        Long userId = SecurityUtils.getCurrentUserId();
        boolean canShared = canViewShared(userId);
        wrapper.and(w -> {
            w.eq(BizIncentiveRecord::getUserId, userId);
            if (canShared) w.or().eq(BizIncentiveRecord::getShared, 1);
        });
    }

    /**
     * 分类角标统计：按当前账号权限可见范围（admin/有激励编辑删除授权 → 全量；
     * 否则 resolver 可见集合 U），每个分类含子分类聚合。
     */
    private void fillRecordCounts(List<BizIncentiveCategory> tree, List<BizIncentiveCategory> all) {
        com.ycmn.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null || all.isEmpty()) return;

        // 【2026-08-08 分享制】分类角标按分享制计数：admin/专项授权→全量，否则本人+已分享
        LambdaQueryWrapper<BizIncentiveRecord> q = new LambdaQueryWrapper<BizIncentiveRecord>()
                .select(BizIncentiveRecord::getCategoryId);
        applyShareVisibility(q);

        java.util.Map<Long, Long> countByCat = recordMapper.selectList(q)
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
        return getRecords(userId, period, parentCategoryId, null);
    }

    /** 按客户查询激励记录（客户详情 Tab 用，customerId 可选过滤） */
    public List<BizIncentiveRecord> getRecords(Long userId, String period, Long parentCategoryId, Long customerId) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            // 【2026-08-08 分享制】显式指定查看某人：admin/专项授权→全部；本人→自己全部；他人→仅其已分享记录。
            if (!canViewIncentiveUser(userId)) return List.of();
            wrapper.eq(BizIncentiveRecord::getUserId, userId);
            Long self = SecurityUtils.getCurrentUserId();
            if (!canSeeAllIncentive() && (self == null || !self.equals(userId))) {
                wrapper.eq(BizIncentiveRecord::getShared, 1);
            }
        } else {
            // 【2026-08-08 分享制】未指定人时按分享制过滤：本人 + shared=1（数据范围/上级可见不再作用）。
            applyShareVisibility(wrapper);
        }
        if (period != null) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (customerId != null) wrapper.eq(BizIncentiveRecord::getCustomerId, customerId);
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
        uidSet.addAll(records.stream().map(BizIncentiveRecord::getUpdatedBy).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        java.util.Set<Long> custIdSet = records.stream().map(BizIncentiveRecord::getCustomerId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, BizIncentiveCategory> catMap = catIds.isEmpty() ? java.util.Map.of()
                : categoryMapper.selectByIds(catIds).stream().collect(java.util.stream.Collectors.toMap(BizIncentiveCategory::getId, c -> c));
        java.util.Map<Long, SysUser> userMap = uidSet.isEmpty() ? java.util.Map.of()
                : userMapper.selectByIds(uidSet).stream().collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));
        java.util.Map<Long, BizCustomer> custMap = custIdSet.isEmpty() ? java.util.Map.of()
                : customerMapper.selectByIds(custIdSet).stream().collect(java.util.stream.Collectors.toMap(BizCustomer::getId, c -> c));
        for (BizIncentiveRecord r : records) {
            if (r.getCategoryId() != null) {
                BizIncentiveCategory cat = catMap.get(r.getCategoryId());
                if (cat != null) r.setCategoryName(cat.getCategoryName());
            }
            if (r.getUserId() != null) {
                SysUser user = userMap.get(r.getUserId());
                if (user != null) r.setUserName(user.getRealName());
            }
            if (r.getUpdatedBy() != null) {
                SysUser updater = userMap.get(r.getUpdatedBy());
                if (updater != null) r.setUpdatedByName(updater.getRealName());
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

        // 合并可编辑业务字段（全部 != null 才合并；user_id/created_at/period/category_id 保持不动——
        // 初始上传人(userId) 在编辑时不可被改变，上传时间(createdAt) 不被重置）
        if (update.getCustomerId() != null) existing.setCustomerId(update.getCustomerId());
        if (update.getIndustry() != null) existing.setIndustry(update.getIndustry());
        if (update.getRelatedProducts() != null) existing.setRelatedProducts(update.getRelatedProducts());
        if (update.getSpecificProduct() != null) existing.setSpecificProduct(update.getSpecificProduct());
        if (update.getPreInstallQuestion() != null) existing.setPreInstallQuestion(update.getPreInstallQuestion());
        // 文件字段 + 分享开关（【2026-08-08 分享制】本人随时可开关分享）
        if (update.getFilePreId() != null) existing.setFilePreId(update.getFilePreId());
        if (update.getFileMidId() != null) existing.setFileMidId(update.getFileMidId());
        if (update.getFilePostId() != null) existing.setFilePostId(update.getFilePostId());
        if (update.getFileId() != null) existing.setFileId(update.getFileId());
        if (update.getFileTrackId() != null) existing.setFileTrackId(update.getFileTrackId());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        // 【2026-08-12】分享开关仅本人可改：admin/can_edit 编辑他人记录时忽略 shared，保持原值
        if (update.getShared() != null) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (currentUserId != null && currentUserId.equals(existing.getUserId())) {
                existing.setShared(update.getShared());
            }
        }

        // 审计：记录最后修改人（updated_at 由 DB ON UPDATE 自动维护）
        existing.setUpdatedBy(SecurityUtils.getCurrentUserId());

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
    public void addRecord(BizIncentiveRecord incRecord) {
        BizIncentiveCategory category = categoryMapper.selectById(incRecord.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        incRecord.setScore(calcScore(incRecord, category, base));
        incRecord.setUserId(SecurityUtils.getCurrentUserId());
        recordMapper.insert(incRecord);
    }

    /**
     * 积分规则（2026-08-01 单槽化改造后；2026-08-10 plan 规则修正）：
     * 1. 两谈两做方案(plan)：方案文件任一存在即 +base，不论文件数量（多文件也只积一次）
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
            // 两谈两做方案(plan): 方案文件任一存在即 +base, 不论文件数量(上限5份)——多文件也只积一次(2026-08-10 用户确认)
            return (hasPre || hasMid || hasPost) ? base : 0;
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

    /** 激励统计表(全员口径): 按子类聚合文件数/总学分, 并给出周期总文件数/总学分 */
    public Map<String, Object> categoryStats(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        // 【2026-08-08 分享制】激励统计表=公司级展示，不做可见性过滤，全员看到全公司聚合（含未分享）
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
        // 【2026-08-08 分享制】个人排行=公司级展示，不做可见性过滤，全员看到全公司排行（含未分享）
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);

        Map<Long, int[]> userAgg = new LinkedHashMap<>(); // userId -> [score, recordCount, pre, mid, post]
        Map<Long, java.util.Map<Long, int[]>> userByCat = new HashMap<>(); // userId -> categoryId -> [记录数, 分值]
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
                int[] cagg = userByCat.computeIfAbsent(r.getUserId(), k -> new HashMap<>())
                        .computeIfAbsent(r.getCategoryId(), k -> new int[2]);
                cagg[0] += 1;  // 记录数
                cagg[1] += sc; // 分值
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
        Map<String, java.util.Map<Long, int[]>> deptByCat = new HashMap<>();
        for (Map<String, Object> p : personal) {
            String dept = (String) p.getOrDefault("officeName", "未知");
            int[] agg = deptAgg.computeIfAbsent(dept, k -> new int[]{0, 0});
            agg[0] += ((Number) p.getOrDefault("totalScore", 0)).intValue();
            agg[1] += 1;
            // 部门品类聚合（个人 byCategory 求和: [记录数, 分值]）
            @SuppressWarnings("unchecked")
            java.util.Map<Long, int[]> pbc = (java.util.Map<Long, int[]>) p.getOrDefault("byCategory", java.util.Map.of());
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

    /**
     * 获取客户关联的激励积分总分
     */
    public int getScoreByCustomerId(Long customerId) {
        // 【2026-08-08 分享制 防越权】积分只按当前用户可见记录计算（admin/专项授权→全部，否则本人+已分享）
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizIncentiveRecord::getCustomerId, customerId);
        applyShareVisibility(wrapper);
        List<BizIncentiveRecord> records = recordMapper.selectList(wrapper);
        return records.stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
    }

    // ======================== 分享制可见性（2026-08-08） ========================

    /**
     * 【2026-08-08 分享制】显式指定查看某人是否可见：admin/专项授权→全部；本人→是；
     * 他人→仅当其存在已分享(shared=1)记录时可见（非授权视角只能看该用户已分享内容）。
     */
    private boolean canViewIncentiveUser(Long targetUserId) {
        if (canSeeAllIncentive()) return true;
        Long self = SecurityUtils.getCurrentUserId();
        if (targetUserId != null && targetUserId.equals(self)) return true;
        // 【2026-08-08 双勾选】被取消"查看已分享"的用户无法查看他人的分享记录
        if (!canViewShared(self)) return false;
        return recordMapper.selectCount(new LambdaQueryWrapper<BizIncentiveRecord>()
                .eq(BizIncentiveRecord::getUserId, targetUserId)
                .eq(BizIncentiveRecord::getShared, 1)) > 0;
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
    public List<Map<String, Object>> getCustomerRecords(String industry, Long mainCategoryId,
            String customerName, String salesperson, String startDate, String endDate, String orgIds, String devType,
            String product, String keyword) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BizIncentiveRecord::getCustomerId);
        // 【2026-08-08 分享制】激励查询明细按分享制可见：admin/专项授权→全部，否则本人+已分享（未分享对他人不可见）
        applyShareVisibility(wrapper);
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
            List<BizCustomer> custs = customerMapper.selectByIds(cids);
            for (BizCustomer c : custs) custMap.put(c.getId(), c);
        }
        // 批量查分类
        Set<Long> catIds = records.stream().map(BizIncentiveRecord::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizIncentiveCategory> catMap2 = new HashMap<>();
        if (!catIds.isEmpty()) {
            List<BizIncentiveCategory> cats = categoryMapper.selectByIds(catIds);
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
            if (r.getUpdatedBy() != null) { SysUser up = userMapper.selectById(r.getUpdatedBy()); if (up != null) r.setUpdatedByName(up.getRealName()); }
            if (r.getCustomerId() != null) {
                BizCustomer c = custMap.get(r.getCustomerId());
                if (c != null) r.setCustomerName(c.getCustomerName());
            }
        }

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
