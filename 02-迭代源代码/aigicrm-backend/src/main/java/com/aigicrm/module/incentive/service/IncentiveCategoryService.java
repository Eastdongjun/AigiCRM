package com.aigicrm.module.incentive.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 激励分类树与分类 CRUD（含角标计数、层级校验）。
 *
 * <p>S5.3 从 {@link IncentiveService} 抽出；可见性复用 IncentiveAccessService。</p>
 */
@Service
@RequiredArgsConstructor
public class IncentiveCategoryService {

    private final BizIncentiveCategoryMapper categoryMapper;
    private final BizIncentiveRecordMapper recordMapper;
    private final IncentiveAccessService incentiveAccessService;

    public List<BizIncentiveCategory> getCategoryTree() {
        List<BizIncentiveCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<BizIncentiveCategory>()
                        .eq(BizIncentiveCategory::getStatus, 1)
                        .orderByAsc(BizIncentiveCategory::getSortOrder)
        );
        List<BizIncentiveCategory> tree = buildTree(all, 0L);
        fillRecordCounts(all);
        return tree;
    }

    /**
     * 分类角标统计：按当前账号权限可见范围（admin/有激励编辑删除授权 → 全量；
     * 否则 resolver 可见集合 U），每个分类含子分类聚合。
     */
    private void fillRecordCounts(List<BizIncentiveCategory> all) {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null || all.isEmpty()) return;

        // 【2026-08-08 分享制】分类角标按分享制计数：admin/专项授权→全量，否则本人+已分享
        LambdaQueryWrapper<BizIncentiveRecord> q = new LambdaQueryWrapper<BizIncentiveRecord>()
                .select(BizIncentiveRecord::getCategoryId);
        incentiveAccessService.applyShareVisibility(q);

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
        // 【2026-11-01 审计修复】放行三级（第三级为产品小类，归树批后的既定结构，唯一维护入口须可用）；
        // 仍拒绝四级：父必须挂在顶级或二级。
        BizIncentiveCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("上级类别不存在");
        }
        Long grandId = parent.getParentId();
        if (grandId == null || grandId == 0L) {
            return; // 父=顶级 → 本节点为二级
        }
        BizIncentiveCategory grand = categoryMapper.selectById(grandId);
        if (grand == null || grand.getParentId() == null || grand.getParentId() != 0L) {
            throw new BusinessException("最多支持三级（第三级为产品小类），不允许四级嵌套");
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
        // 【七轮复检修复】记录引用守卫: 悬空 category_id 的存量记录被编辑/移除时重算学分
        // 会以 category=null 得 base=0 把 score 静默落 0 分(无外键约束兜底, 须应用层拦截)
        Long usedCount = recordMapper.selectCount(
                new LambdaQueryWrapper<BizIncentiveRecord>().eq(BizIncentiveRecord::getCategoryId, id));
        if (usedCount != null && usedCount > 0) {
            throw new BusinessException("该分类下仍有 " + usedCount + " 条激励记录，请先处理记录后再删除分类");
        }
        categoryMapper.deleteById(id);
    }

    private List<BizIncentiveCategory> buildTree(List<BizIncentiveCategory> list, Long parentId) {
        List<BizIncentiveCategory> children = new ArrayList<>();
        for (BizIncentiveCategory c : list) {
            if (Objects.equals(c.getParentId(), parentId)) {
                c.setChildren(buildTree(list, c.getId()));
                children.add(c);
            }
        }
        return children;
    }
}
