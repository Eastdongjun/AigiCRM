package com.aigicrm.module.incentive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;
    /** 激励专项授权表直查（2026-08-16 授权表化，替代载体角色+权限码判定） */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * 独立"看全部"资格：admin ∨ 授权表 can_view_all=1
     *
     * 【需求】激励模块看全部(含未分享)记录的白名单判定
     * 【方法】查用户级授权表 biz_incentive_grant（2026-08-16 替代载体角色模式）
     * 【业务规则】admin 恒可；无授权记录=默认不可看全部；can_view_all=1 才放行
     * 【参数】userId 目标用户ID（兼容旧签名，实际按当前登录人判定）
     * 【返回】true=可看全部未分享记录
     * 【注意】只读查表；不再读权限码，旧 incentive:view-all 权限码已废弃
     */
    public boolean canViewAll(Long userId) {
        return SecurityUtils.isAdmin()
                || grantFlag(SecurityUtils.getCurrentUserId(), "can_view_all", false);
    }

    /**
     * 查看已分享记录资格：授权表 can_view_shared（无记录=默认开）
     *
     * 【需求】"分享制"下能否看到他人已分享(shared=1)的记录
     * 【方法】查用户级授权表 biz_incentive_grant；无记录按默认值(开)处理
     * 【业务规则】无记录=开(全员默认，等价旧"载体角色全员绑定"语义)；记录=0 表示被收窄
     * 【参数】userId 目标用户ID（兼容旧签名，实际按当前登录人判定）
     * 【返回】true=可查看他人已分享记录
     * 【注意】只读查表；收窄通过激励授权面板把 can_view_shared 置 0 实现
     */
    public boolean canViewShared(Long userId) {
        return grantFlag(SecurityUtils.getCurrentUserId(), "can_view_shared", true);
    }

    /**
     * 编辑他人激励记录资格：admin ∨ 授权表 can_edit=1
     *
     * 【需求】谁能追加/修改他人上传的激励记录
     * 【方法】查用户级授权表 biz_incentive_grant（2026-08-16 替代 incentive:manage 权限码）
     * 【业务规则】admin 恒可；本人记录编辑由 Controller 归属校验另行放行，不依赖本方法
     * 【参数】无（按当前登录人判定）
     * 【返回】true=可编辑他人记录
     * 【注意】只读查表；与 canDelete 独立勾选
     */
    public boolean canEdit() {
        return SecurityUtils.isAdmin()
                || grantFlag(SecurityUtils.getCurrentUserId(), "can_edit", false);
    }

    /** 人员列表（公开）：激励页下拉框用 */
    public List<Map<String, Object>> getUserList() {
        List<Map<String, Object>> list = new ArrayList<>();
        // 【2026-09-23 选人边界修正】人员筛选下拉必须与查看语义(canViewIncentiveUser)同口径:
        // 原按数据范围(scopeResolver.resolve)过滤,跨团队同事不出现在下拉里——但后端允许查看
        // 任何"有已分享记录"的人,分享制是全公司口径 → 列表看得到他人 shared 记录却选不到人,
        // 与客户转移选人同族缺陷。口径=本人+有 shared 记录的作者(被取消"查看已分享"者仅本人);
        // admin/专项授权(canSeeAllIncentive)→ 全量(覆盖上传弹窗代录选人场景)。
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, 1);
        if (!canSeeAllIncentive()) {
            Long self = SecurityUtils.getCurrentUserId();
            java.util.Set<Long> selectable = new java.util.HashSet<>();
            if (self != null) selectable.add(self);
            if (canViewShared(self)) {
                for (BizIncentiveRecord r : recordMapper.selectList(new LambdaQueryWrapper<BizIncentiveRecord>()
                        .select(BizIncentiveRecord::getUserId)
                        .eq(BizIncentiveRecord::getShared, 1))) {
                    if (r.getUserId() != null) selectable.add(r.getUserId());
                }
            }
            if (selectable.isEmpty()) return List.of();
            wrapper.in(SysUser::getId, selectable);
        }
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

    /**
     * 删除他人激励记录资格：admin ∨ 授权表 can_delete=1
     *
     * 【需求】谁能删除他人上传的激励记录
     * 【方法】查用户级授权表 biz_incentive_grant（2026-08-16 替代 incentive:manage 权限码）
     * 【业务规则】admin 恒可；本人记录删除由 Controller 归属校验另行放行，不依赖本方法
     * 【参数】无（按当前登录人判定）
     * 【返回】true=可删除他人记录
     * 【注意】只读查表；与 canEdit 独立勾选
     */
    public boolean canDelete() {
        return SecurityUtils.isAdmin()
                || grantFlag(SecurityUtils.getCurrentUserId(), "can_delete", false);
    }

    /**
     * 读取当前登录人的激励专项授权行
     *
     * 【需求】canViewAll/canViewShared/canEdit/canDelete 的统一数据源
     * 【方法】按 user_id 主键查 biz_incentive_grant，无记录返回 null（调用方按默认值处理）
     * 【业务规则】无记录=默认（分享可见开、其余关）；表行以面板勾选为准
     * 【参数】userId 用户ID
     * 【返回】授权行 Map；无记录或 userId 为空返回 null
     * 【注意】只读；实时查询不走权限缓存，面板改动即时生效
     */
    // 【2026-11-03 权限中心·生效预览】提 public: 预览接口按任意 userId 读四开关(不绑当前登录人)
    public Map<String, Object> grantRow(Long userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForMap(
                    "SELECT can_view_shared, can_view_all, can_edit, can_delete " +
                    "FROM biz_incentive_grant WHERE user_id = ?", userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 读取授权行中某个布尔开关（无行时返回调用方指定的默认值）
     *
     * 【需求】授权表语义的布尔判定封装
     * 【方法】grantRow 取行 → 取列 → 转为 boolean；行缺失/列缺失按默认值
     * 【业务规则】can_view_shared 默认 true（全员默认开）；can_view_all/can_edit/can_delete 默认 false
     * 【参数】userId 用户ID；column 列名(can_view_shared 等)；defaultValue 无记录时的默认值
     * 【返回】开关值
     * 【注意】只读；列值为 null 时按默认值
     */
    private boolean grantFlag(Long userId, String column, boolean defaultValue) {
        Map<String, Object> row = grantRow(userId);
        if (row == null) return defaultValue;
        Object v = row.get(column);
        if (v == null) return defaultValue;
        return Integer.valueOf(1).equals(((Number) v).intValue());
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
     * 【2026-08-16 授权表化】看全部资格：系统管理员 ∨ can_view_all ∨ can_edit ∨ can_delete
     * （能编辑/删除他人记录隐含可看全部，对齐旧 incentive:manage 语义）。
     * 激励模块可见性统一为分享制，不再使用 data_scope/上级可见下级/负责人可见本组织/客户分享。
     */
    private boolean canSeeAllIncentive() {
        return SecurityUtils.isAdmin()
                || grantFlag(SecurityUtils.getCurrentUserId(), "can_view_all", false)
                || grantFlag(SecurityUtils.getCurrentUserId(), "can_edit", false)
                || grantFlag(SecurityUtils.getCurrentUserId(), "can_delete", false);
    }

    /**
     * 【2026-08-08 分享制+双勾选】记录列表可见性统一：canSeeAllIncentive → 全部；
     * 否则"本人 ∨ (已分享 AND canViewShared)"——面板取消"查看已分享"的用户不可见他人分享记录。
     * 【2026-09-23】公开给统计模块复用：客户排行(/statistics/customer-rankings)的四项服务聚合
     * 与本模块同口径(此前统计侧按客户数据可见性收口,SELF/team 销售只剩名下客户,两套口径打架)。
     */
    public void applyShareVisibility(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        if (canSeeAllIncentive()) return;
        Long userId = SecurityUtils.getCurrentUserId();
        boolean canShared = canViewShared(userId);
        wrapper.and(w -> {
            w.eq(BizIncentiveRecord::getUserId, userId);
            if (canShared) w.or().eq(BizIncentiveRecord::getShared, 1);
        });
    }

    // ========== 【2026-11-05 用户拍板 B"激励查询改走组织范围档位"(三面全改)】==========
    // 激励查询/案例库/MP 查询共用的 customer-records 分组面, 可见性从"全员可见(无过滤)"
    // 改为组织范围档位: 客户属当前账号 (customer,view) 数据范围 ∨ 本人上传恒可见。
    // admin 恒全量; 专项授权 can_view_all=1 保持全量旁路; can_view_shared/shared=1 在本面
    // 退役(仍作用于 /incentive/records 等记录列表面); 未分配负责人的客户保持全员可见
    // (镜像统计家族口径); scoreboard 同款 30s 数据缓存窗内变更延迟生效。
    private java.util.Set<Long> orgScopeVisibleCustomerIds() {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null || SecurityUtils.isAdmin()) return null; // null=不过滤(全量)
        java.util.List<Long> visibleUsers = scopeResolver.resolveModuleUsers(user, "customer");
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (BizCustomer c : customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0))) {
            if (c.getOwnerId() == null || (visibleUsers != null && visibleUsers.contains(c.getOwnerId()))) {
                ids.add(c.getId());
            }
        }
        return ids;
    }

    private void applyOrgQueryVisibility(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        if (canSeeAllIncentive()) return; // 专项授权"查看全部"=全量旁路(授权面板语义保持)
        java.util.Set<Long> ids = orgScopeVisibleCustomerIds();
        Long me = SecurityUtils.getCurrentUserId();
        if (ids == null) return; // 全量
        wrapper.and(w -> {
            if (ids.isEmpty()) {
                if (me != null) w.eq(BizIncentiveRecord::getUserId, me);
                else w.apply("1=0");
            } else {
                w.in(BizIncentiveRecord::getCustomerId, ids);
                if (me != null) w.or().eq(BizIncentiveRecord::getUserId, me);
            }
        });
    }

    /**
     * 分类角标统计：按当前账号权限可见范围（admin/有激励编辑删除授权 → 全量；
     * 否则 resolver 可见集合 U），每个分类含子分类聚合。
     */
    private void fillRecordCounts(List<BizIncentiveCategory> tree, List<BizIncentiveCategory> all) {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
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
        // 【2026-11-01 审计修复】放行三级(第三级=产品小类, 归树批后的既定结构, 唯一维护入口须可用);
        // 仍拒绝四级: 父必须挂在顶级(父=顶级→本节点二级) 或 挂在二级(父=二级→本节点三级)
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
        // 【2026-09-12】空周期不放行等值过滤：与 categoryStats/scoreboard 的守卫口径一致。
        // 原来 `if (period != null)` 会把前端传来的空串当成 period='' 精确匹配 ⇒ 一律 0 条
        // （曾导致激励页打开即"没数据"：默认周期与库内 '2026全年' 口径不一致 + 空值仍过滤）。
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        if (customerId != null) wrapper.eq(BizIncentiveRecord::getCustomerId, customerId);
        if (parentCategoryId != null) {
            List<BizIncentiveCategory> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<BizIncentiveCategory>().eq(BizIncentiveCategory::getParentId, parentCategoryId)
            );
            // 【2026-11-01 审计修复】IN 含父 id 本身(直接挂大类的记录原查不出); 大类无子类时
            // 原来不加任何条件=显式传父类却返回全部, 退化为父 id 等值
            List<Long> ids = new java.util.ArrayList<>();
            ids.add(parentCategoryId);
            ids.addAll(children.stream().map(BizIncentiveCategory::getId).toList());
            wrapper.in(BizIncentiveRecord::getCategoryId, ids);
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

    /**
     * 删除激励记录（admin ∨ can_delete 授权 ∨ 本人，归属校验在 Controller 完成）。
     * 【真机UAT修复 2026-09-22】先清 biz_incentive_read 子表（已读状态外键引用本记录），
     * 裸 deleteById 对已被阅读过的记录触发 fk_incentive_read_record 约束失败（测试环境实证
     * 支持编号 S-20260922235952-46683）；两步删除须原子，失败一起回滚。
     */
    @Transactional
    public void deleteRecord(Long id) {
        jdbc.update("DELETE FROM biz_incentive_read WHERE record_id = ?", id);
        recordMapper.deleteById(id);
        evictScoreboardCache();
    }

    /**
     * 更新激励记录（追加/删除文件、编辑业务字段时调用），自动重算学分
     *
     * 【需求】激励记录追加文件/更正信息时不重置初始上传人(userId)与上传时间(createdAt)
     * 【方法】按 id 查原记录，非 null 字段合并进原记录后按最新学分规则重算再落库
     * 【业务规则】归属校验在 Controller 完成(admin ∨ can_edit 授权 ∨ 本人)；
     *             归属客户 customerId 仅 admin/专项授权可变更，本人编辑保持原值；
     *             分享开关 shared 仅记录本人可改(admin 编辑他人记录时忽略)
     * 【参数】id 记录ID；update 待合并字段(非 null 才合并)
     * 【返回】无；记录不存在时静默返回(兼容并发删除场景)
     * 【注意】调用方须保证已通过 Controller 归属校验；@Transactional 保证重算+落库原子
     */
    @Transactional
    public void updateRecord(Long id, BizIncentiveRecord update) {
        // 【复检修复】行锁读与 removeFile 对称：PUT 无锁快照+全实体覆写会把并发 removeFile 已移除的
        // 槽位引用按旧快照写回(移除被静默回滚)；FOR UPDATE 串行化两接口对同一记录的交错
        BizIncentiveRecord existing = recordMapper.selectByIdForUpdate(id);
        if (existing == null) return;

        // 合并可编辑业务字段（全部 != null 才合并；user_id/created_at/period/category_id 保持不动——
        // 初始上传人(userId) 在编辑时不可被改变，上传时间(createdAt) 不被重置）
        // 【2026-08-16 审查修复】归属客户变更仅 admin/can_edit 授权允许：
        // 本人记录改挂任意客户会绕过"记录-客户"绑定语义；前端编辑回传原值不受影响
        if (update.getCustomerId() != null
                && !Objects.equals(update.getCustomerId(), existing.getCustomerId())
                && !canEdit()) {
            throw new BusinessException(403, "无权修改记录归属客户");
        }
        if (update.getCustomerId() != null) existing.setCustomerId(update.getCustomerId());
        if (update.getIndustry() != null) existing.setIndustry(update.getIndustry());
        if (update.getRelatedProducts() != null) existing.setRelatedProducts(update.getRelatedProducts());
        if (update.getSpecificProduct() != null) existing.setSpecificProduct(update.getSpecificProduct());
        if (update.getPreInstallQuestion() != null) existing.setPreInstallQuestion(update.getPreInstallQuestion());
        // 文件字段 + 分享开关（【2026-08-08 分享制】本人随时可开关分享）
        // 【四轮复检修复】与 mergeSlotJson"空串=未提供"对齐: 只有非空白槽位才算触碰,
        // 否则五槽全空的存量脏数据做纯业务字段编辑(前端回传空串)会被守卫误拒
        boolean fileTouched = isProvidedSlot(update.getFilePreId()) || isProvidedSlot(update.getFileMidId())
                || isProvidedSlot(update.getFilePostId()) || isProvidedSlot(update.getFileId()) || isProvidedSlot(update.getFileTrackId());
        // 【真机UAT修复 2026-09-22】文件归属只校验"本次新引入"的引用:编辑/追加场景合并后的记录
        // 天然携带原上传人的存量文件(admin 或 can_edit 编辑他人记录、追加者≠原作者都属正常业务),
        // 存量引用在原上传时已按 create 同口径校验过。此前对合并后全量校验 uploader==当前编辑人,
        // 会把编辑他人记录整体误拒(实测 record 266: 原作者文件 869, admin 编辑被"无效文件引用"拒绝)。
        Set<Long> beforeFileIds = fileTouched ? new HashSet<>(collectFileIds(existing)) : Collections.emptySet();
        // 【三轮复检修复】槽位只接受合法 JSON 数组: 空白串一律视为"未提供"(清空槽位的唯一
        // 途径=remove-file 专用接口, 统一空槽表示为 NULL), 非法脏串直接拒绝不落库
        mergeSlotJson(update, existing, "filePreId");
        mergeSlotJson(update, existing, "fileMidId");
        mergeSlotJson(update, existing, "filePostId");
        mergeSlotJson(update, existing, "fileId");
        mergeSlotJson(update, existing, "fileTrackId");
        // 【DeepSeek四审 P1 修复 2026-09-22】update 新增文件槽时与 create 同口径核对归属:
        // 旧代码只赋值不校验, 填他人/不存在文件 ID 也能保住上传材料分。核对对象=新引入的文件 ID
        if (fileTouched) {
            List<Long> newFileIds = collectFileIds(existing).stream()
                    .filter(fid -> !beforeFileIds.contains(fid)).collect(Collectors.toList());
            if (!newFileIds.isEmpty()) {
                Long currentUserId = SecurityUtils.getCurrentUserId();
                Long existCount = fileMapper.selectCount(new LambdaQueryWrapper<BizFile>()
                        .in(BizFile::getId, newFileIds)
                        .eq(BizFile::getUploaderId, currentUserId));
                if (existCount == null || existCount != newFileIds.size()) {
                    throw new BusinessException("包含无效文件引用，请重新上传");
                }
            }
        }
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        // 【2026-08-12】分享开关仅本人可改：admin/can_edit 编辑他人记录时忽略 shared，保持原值
        if (update.getShared() != null) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (currentUserId != null && currentUserId.equals(existing.getUserId())) {
                existing.setShared(update.getShared());
            }
        }
        // 【五轮复检对齐】防御性留置：现行语义下 PUT 空白=未提供、合法槽位必含≥1个ID，本守卫在
        // 正常路径不可达；防的是未来 merge 语义变化或存量脏数据(五槽全空记录)被槽位编辑意外固化。
        if (fileTouched && !existing.hasAnyFile()) {
            throw new BusinessException(400, "记录至少须保留一份文件；如需删除整条记录请使用「删除」");
        }

        // 审计：记录最后修改人（updated_at 由 DB ON UPDATE 自动维护）
        existing.setUpdatedBy(SecurityUtils.getCurrentUserId());

        BizIncentiveCategory category = categoryMapper.selectById(existing.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        existing.setScore(calcScore(existing, category, base));

        recordMapper.updateById(existing);
        evictScoreboardCache();
    }

    /** 【六轮复检修复】槽位共享校验: 格式正则 + 长度上限, create/update 同口径 */
    private static void assertValidSlotJson(String v) {
        String t = v.trim();
        if (!SLOT_JSON_RE.matcher(t).matches()) {
            throw new BusinessException(400, "安装文件槽位格式非法（应为文件ID数组）");
        }
        if (t.length() > SLOT_JSON_MAX_LEN) {
            throw new BusinessException(400, "槽位文件数量超出上限");
        }
    }

    /** 槽位值是否为"本次实际提供"(非 null 且非空白)——与 mergeSlotJson 的空串=未提供语义一致 */
    private static boolean isProvidedSlot(String v) {
        return v != null && !v.isBlank();
    }

    /**
     * 槽位 JSON 合法形态: "[1,2]"（至少一个纯数字 ID；空/白串视为未提供）。
     * 【六轮复检修复】ID 限定 1-18 位(18 位以内恒在 Long 范围, 防 20 位数字落库后
     * parseJsonArray 溢出被吞成"看似有值实则恒空"的脏槽位)。
     */
    private static final java.util.regex.Pattern SLOT_JSON_RE = java.util.regex.Pattern.compile("^\\[\\d{1,18}(,\\d{1,18})*\\]$");
    /** 槽位列 DDL 为 varchar(500)，超长数组必须显式拒绝而非落库报 500/非严格模式截断 */
    private static final int SLOT_JSON_MAX_LEN = 500;

    /** 【三轮复检修复】槽位合并统一入口: 空白=未提供跳过; 非法格式 400; 合法则覆盖 */
    private void mergeSlotJson(BizIncentiveRecord update, BizIncentiveRecord existing, String field) {
        String v;
        switch (field) {
            case "filePreId" -> v = update.getFilePreId();
            case "fileMidId" -> v = update.getFileMidId();
            case "filePostId" -> v = update.getFilePostId();
            case "fileId" -> v = update.getFileId();
            case "fileTrackId" -> v = update.getFileTrackId();
            default -> v = null;
        }
        if (v == null || v.isBlank()) return;
        assertValidSlotJson(v);
        switch (field) {
            case "filePreId" -> existing.setFilePreId(v.trim());
            case "fileMidId" -> existing.setFileMidId(v.trim());
            case "filePostId" -> existing.setFilePostId(v.trim());
            case "fileId" -> existing.setFileId(v.trim());
            case "fileTrackId" -> existing.setFileTrackId(v.trim());
            default -> { }
        }
    }

    /**
     * 移除记录某槽位中的单个文件（【用户裁决 2026-10-10】上传错了需要修改）
     *
     * 【方法】解析槽位 JSON 数组 → 剔除该文件 ID → 显式 SET 写回（含清空为 NULL）→ 重算学分 → 盖修改人章
     * 【业务规则】物理文件保留（biz_file 原件不动），只解除记录槽位引用——文件原件留在文件库
     *             可查可重挂；操作级留痕(何时谁移除了哪个文件)为 V2 审计日志事项，当前仅盖
     *             最后修改人/时间章；
     *             全记录五槽合计移除后须 ≥1 份文件（防空壳记录：分数还在证据全无）；
     *             槽位 whitelist：file/pre/mid/post/track
     * 【参数】id 记录ID；slot 槽位；fileId 要移除的文件ID
     * 【返回】无；记录/槽位/文件不存在或不属于该槽位时抛 BusinessException
     * 【注意】归属校验在 Controller 完成（同 updateRecord：admin ∨ canEdit 授权 ∨ 本人）；
     *         显式 LambdaUpdateWrapper 而非 updateById——MP 默认策略跳过 null 字段，
     *         删空槽位（字段须置 NULL）用 updateById 会静默不生效（假成功）
     */
    @Transactional
    public void removeFile(Long id, String slot, Long fileId) {
        // 【工作流复检修复 2026-10-10】锁定读：守卫是 check-then-act，普通快照读下并发两事务
        // 各删不同槽的最后一份文件可双双通过"五槽合计≥1"（产出空壳记录）；FOR UPDATE 串行化同记录并发删除
        BizIncentiveRecord existing = recordMapper.selectByIdForUpdate(id);
        if (existing == null) throw new BusinessException(404, "记录不存在");
        if (fileId == null) throw new BusinessException(400, "缺少文件ID");

        List<Long> ids = switch (slot == null ? "" : slot) {
            case "file" -> existing.getFileIds();
            case "pre" -> existing.getFilePreIds();
            case "mid" -> existing.getFileMidIds();
            case "post" -> existing.getFilePostIds();
            case "track" -> existing.getFileTrackIds();
            default -> null;
        };
        if (ids == null) throw new BusinessException(400, "无效槽位: " + slot);
        if (!ids.contains(fileId)) throw new BusinessException(400, "该文件不在此记录的该槽位中");

        // 全记录守卫：五槽合计移除后至少保留 1 份文件
        int total = existing.getFileIds().size() + existing.getFilePreIds().size()
                + existing.getFileMidIds().size() + existing.getFilePostIds().size()
                + existing.getFileTrackIds().size();
        if (total - 1 < 1) {
            throw new BusinessException(400, "记录至少须保留一份文件；如需删除整条记录请使用「删除」");
        }

        List<Long> next = new ArrayList<>(ids);
        next.remove(fileId);
        String nextJson = next.isEmpty() ? null
                : "[" + next.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";

        // 先在内存实体上应用变更，供 calcScore 按移除后的口径重算
        switch (slot) {
            case "file" -> existing.setFileIds(next);
            case "pre" -> existing.setFilePreIds(next);
            case "mid" -> existing.setFileMidIds(next);
            case "post" -> existing.setFilePostIds(next);
            case "track" -> existing.setFileTrackIds(next);
            default -> { /* unreachable，上方已校验 */ }
        }
        BizIncentiveCategory category = categoryMapper.selectById(existing.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        existing.setScore(calcScore(existing, category, base));

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord>()
                        .eq(BizIncentiveRecord::getId, id)
                        .set(BizIncentiveRecord::getScore, existing.getScore())
                        .set(BizIncentiveRecord::getUpdatedBy, SecurityUtils.getCurrentUserId());
        switch (slot) {
            case "file" -> uw.set(BizIncentiveRecord::getFileId, nextJson);
            case "pre" -> uw.set(BizIncentiveRecord::getFilePreId, nextJson);
            case "mid" -> uw.set(BizIncentiveRecord::getFileMidId, nextJson);
            case "post" -> uw.set(BizIncentiveRecord::getFilePostId, nextJson);
            case "track" -> uw.set(BizIncentiveRecord::getFileTrackId, nextJson);
            default -> { /* unreachable */ }
        }
        recordMapper.update(null, uw);
        evictScoreboardCache();
    }

    /**
     * 【BIZ-007 2026-11-13 外部审计修复】文件中心删除激励佐证后的联动重算。
     *
     * 【背景】FileService.deleteFile(incentive scope) 此前只删 biz_file 行，不触碰激励记录的
     * 五槽引用与学分——出现"佐证已删、分数还在"（证据没了分照拿，违反记录佐证不变量）。
     * 【方法】反查引用该文件的全部记录(JSON_CONTAINS 精确数字匹配, JSON_VALID 防脏格式) →
     *         逐条从对应槽位剔除 → 按移除后口径 calcScore 重算 → 显式 SET 写回（含清空 NULL）。
     * 【不变量】与 removeFile 同款空壳守卫: 剔除后五槽合计 <1 → 抛 400（调用方事务回滚,
     *          文件保留），文案指引先处理激励记录——不引入"空壳记录 0 分"新语义。
     * 【事务】REQUIRED 合并进调用方(FileService.deleteFile)事务; scoreboard 缓存失效一次。
     */
    @Transactional
    public void detachFileFromRecords(Long fileId) {
        if (fileId == null) return;
        List<Long> recordIds = jdbc.queryForList(
                "SELECT id FROM biz_incentive_record WHERE " +
                        "(JSON_VALID(file_id) AND JSON_CONTAINS(file_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_pre_id) AND JSON_CONTAINS(file_pre_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_mid_id) AND JSON_CONTAINS(file_mid_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_post_id) AND JSON_CONTAINS(file_post_id, CAST(? AS CHAR))) OR " +
                        "(JSON_VALID(file_track_id) AND JSON_CONTAINS(file_track_id, CAST(? AS CHAR)))",
                Long.class, fileId, fileId, fileId, fileId, fileId);
        if (recordIds == null || recordIds.isEmpty()) return;

        for (Long recordId : recordIds) {
            BizIncentiveRecord r = recordMapper.selectByIdForUpdate(recordId);
            if (r == null) continue;
            Map<String, List<Long>> slots = new LinkedHashMap<>();
            slots.put("file", r.getFileIds() == null ? new ArrayList<>() : new ArrayList<>(r.getFileIds()));
            slots.put("pre", r.getFilePreIds() == null ? new ArrayList<>() : new ArrayList<>(r.getFilePreIds()));
            slots.put("mid", r.getFileMidIds() == null ? new ArrayList<>() : new ArrayList<>(r.getFileMidIds()));
            slots.put("post", r.getFilePostIds() == null ? new ArrayList<>() : new ArrayList<>(r.getFilePostIds()));
            slots.put("track", r.getFileTrackIds() == null ? new ArrayList<>() : new ArrayList<>(r.getFileTrackIds()));
            boolean touched = false;
            int totalAfter = 0;
            for (List<Long> list : slots.values()) {
                if (list.removeIf(id -> id.equals(fileId))) touched = true;
                totalAfter += list.size();
            }
            if (!touched) continue; // JSON_CONTAINS 命中但解析后无此 id(脏格式兜底)

            if (totalAfter < 1) {
                throw new BusinessException(400, "该文件是激励记录 " + recordId
                        + " 的唯一佐证，无法删除；请先在激励记录中更换佐证或删除该记录");
            }
            // 应用到内存实体供 calcScore 按移除后口径评分
            r.setFileIds(slots.get("file"));
            r.setFilePreIds(slots.get("pre"));
            r.setFileMidIds(slots.get("mid"));
            r.setFilePostIds(slots.get("post"));
            r.setFileTrackIds(slots.get("track"));
            BizIncentiveCategory category = categoryMapper.selectById(r.getCategoryId());
            int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
            r.setScore(calcScore(r, category, base));

            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizIncentiveRecord>()
                            .eq(BizIncentiveRecord::getId, recordId)
                            .set(BizIncentiveRecord::getScore, r.getScore())
                            .set(BizIncentiveRecord::getUpdatedBy, SecurityUtils.getCurrentUserId())
                            .set(BizIncentiveRecord::getFileId, slots.get("file").isEmpty() ? null : toJsonArray(slots.get("file")))
                            .set(BizIncentiveRecord::getFilePreId, slots.get("pre").isEmpty() ? null : toJsonArray(slots.get("pre")))
                            .set(BizIncentiveRecord::getFileMidId, slots.get("mid").isEmpty() ? null : toJsonArray(slots.get("mid")))
                            .set(BizIncentiveRecord::getFilePostId, slots.get("post").isEmpty() ? null : toJsonArray(slots.get("post")))
                            .set(BizIncentiveRecord::getFileTrackId, slots.get("track").isEmpty() ? null : toJsonArray(slots.get("track")));
            recordMapper.update(null, uw);
        }
        evictScoreboardCache();
    }

    private String toJsonArray(List<Long> ids) {
        return "[" + ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    // 【死码清理 2026-11-08】recalculateAllScores 已随 POST /records/recalculate 端点删除：
    // 双端零调用（11-08 审计确证），单记录重算由 updateRecord/removeFile 内联完成。

    /**
     * 新增激励上传记录
     *
     * 【需求】激励上传提交后落库一条记录并按分类规则计分
     * 【方法】校验入参(分类/客户/文件引用) → 查分类分值 → calcScore 计分 → 强制 userId=当前登录人
     * 【业务规则】categoryId/customerId 必填且存在；至少一个文件槽有文件；
     *             引用的文件必须存在且为当前用户上传；计分规则见 calcScore
     * 【参数】incRecord 前端提交的记录(不含 userId，由本方法强制注入)
     * 【返回】无；校验失败抛 BusinessException
     * 【注意】调用前置 Controller 已过 incentive:add 权限；@Transactional 落库原子
     */
    @Transactional
    public void addRecord(BizIncentiveRecord incRecord) {
        validateNewRecord(incRecord);
        BizIncentiveCategory category = categoryMapper.selectById(incRecord.getCategoryId());
        int base = (category != null && category.getScore() != null) ? category.getScore() : 0;
        incRecord.setScore(calcScore(incRecord, category, base));
        incRecord.setUserId(SecurityUtils.getCurrentUserId());
        recordMapper.insert(incRecord);
        evictScoreboardCache();
    }

    /**
     * 校验新增激励记录的入参完整性
     *
     * 【需求】防止脏数据入库：无客户/无分类/无文件/引用失效文件的记录会污染统计与查询
     * 【方法】逐项校验：分类存在 → 客户存在 → 任一文件槽非空 → 文件 ID 批量核对存在性与归属
     * 【业务规则】categoryId/customerId 必填且存在；五个文件槽(file/filePre/fileMid/filePost/fileTrack)
     *             至少一个非空；引用的文件必须存在且 uploader_id=当前用户(激励文件只能引用本人上传的文件)
     * 【参数】r 待入库记录
     * 【返回】无；不满足任一规则抛 BusinessException
     * 【注意】只读校验不落库；先于计分执行，失败不产生任何写入
     */
    private void validateNewRecord(BizIncentiveRecord r) {
        if (r.getCategoryId() == null) {
            throw new BusinessException("请选择激励分类");
        }
        if (categoryMapper.selectById(r.getCategoryId()) == null) {
            throw new BusinessException("激励分类不存在或已删除");
        }
        if (r.getCustomerId() == null) {
            throw new BusinessException("请选择关联客户");
        }
        if (customerMapper.selectById(r.getCustomerId()) == null) {
            throw new BusinessException("关联客户不存在或已删除");
        }
        if (!r.hasAnyFile()) {
            throw new BusinessException("请至少上传一个文件");
        }
        // 【五轮复检修复】create 与 update 同口径: 槽位非空白值必须是合法 JSON 数组
        // (parseJsonArray 会把脏串静默解析为空列表绕过归属核对, 原样落库成脏数据)
        for (String v : new String[]{r.getFilePreId(), r.getFileMidId(), r.getFilePostId(), r.getFileId(), r.getFileTrackId()}) {
            if (v != null && !v.isBlank()) assertValidSlotJson(v);
        }
        // 文件引用核对：全部槽位文件 ID 必须存在且属于当前用户（防伪造引用他人文件）
        List<Long> fileIds = collectFileIds(r);
        if (!fileIds.isEmpty()) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            Long existCount = fileMapper.selectCount(new LambdaQueryWrapper<BizFile>()
                    .in(BizFile::getId, fileIds)
                    .eq(BizFile::getUploaderId, currentUserId));
            if (existCount == null || existCount != fileIds.size()) {
                throw new BusinessException("包含无效文件引用，请重新上传");
            }
        }
    }

    /**
     * 汇总记录全部文件槽的 ID（去重），供存在性/归属核对
     *
     * 【需求】新增记录前核对引用的文件是否存在且属于当前用户
     * 【方法】合并五个文件槽(file/filePre/fileMid/filePost/fileTrack)解析出的 ID 并去重
     * 【业务规则】同一文件出现在多个槽只核对一次；空槽跳过
     * 【参数】r 待核对记录
     * 【返回】去重后的文件 ID 列表（可能为空）
     * 【注意】依赖实体 parseJsonArray 容忍脏数据（解析失败视为空，不计入核对）
     */
    private List<Long> collectFileIds(BizIncentiveRecord r) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(r.getFileIds());
        ids.addAll(r.getFilePreIds());
        ids.addAll(r.getFileMidIds());
        ids.addAll(r.getFilePostIds());
        ids.addAll(r.getFileTrackIds());
        return new ArrayList<>(ids);
    }

    /**
     * 学分规则（2026-08-01 单槽化改造后；2026-08-10 plan 规则修正）：
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

    // ========== 【2026-11-04 用户令"四项服务/四项激励也设置上对应的数据的权限"】统计家族数据范围收口 ==========
    // 与 StatisticsQueryService.customerRankings(visibleCustomers, 2026-11-02 客户可见范围基线)同模式:
    // 按当前账号 数据范围解析 的可见人员集过滤记录; SecurityUser 缺席(系统任务/测试)/admin/解析为 null(=全部)
    // 时不过滤(fail-open 同模式)。统计页签=客户维度收口(可见客户), 排行页签=人员维度收口(可见人员)。
    private java.util.Set<Long> statsVisibleUserIds() {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null || SecurityUtils.isAdmin()) return null;
        // 【2026-11-05 用户拍板 B"四项激励独立数据范围"】改读独立档位 (incentive, stats_view),
        // 不再跟随 (statistics, view); 存量角色由 V2026.11.05.7 回填拷贝统计分析档位, 行为不变。
        java.util.List<Long> visible = scopeResolver.resolveModuleUsers(user, "incentive", "stats_view");
        return visible == null ? null : new java.util.HashSet<>(visible);
    }

    /** 客户维度基线: active 非 sea/archived 且(未分配负责人保持全员可见 或 负责人在可见人员集内) */
    private java.util.Set<Long> statsVisibleCustomerIds() {
        java.util.Set<Long> users = statsVisibleUserIds();
        if (users == null) return null;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (BizCustomer c : customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0)
                .notIn(BizCustomer::getStatus, "sea", "archived"))) {
            if (c.getOwnerId() == null || users.contains(c.getOwnerId())) ids.add(c.getId());
        }
        return ids;
    }

    private List<BizIncentiveRecord> filterRecordsByVisibleCustomers(List<BizIncentiveRecord> records) {
        java.util.Set<Long> ids = statsVisibleCustomerIds();
        if (ids == null) return records;
        return records.stream()
                .filter(r -> r.getCustomerId() != null && ids.contains(r.getCustomerId()))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<BizIncentiveRecord> filterRecordsByVisibleUsers(List<BizIncentiveRecord> records) {
        java.util.Set<Long> users = statsVisibleUserIds();
        if (users == null) return records;
        return records.stream()
                .filter(r -> r.getUserId() != null && users.contains(r.getUserId()))
                .collect(java.util.stream.Collectors.toList());
    }

    /** 激励统计表: 按子类聚合文件数/总学分, 并给出周期总文件数/总学分(数据按当前权限账号范围收口) */
    public Map<String, Object> categoryStats(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        // 【2026-11-04 用户令"四项服务/四项激励也设置上对应的数据的权限"】统计表按当前权限账号
        // 数据范围收口(可见客户基线), 取代 2026-08-08 全员口径(公司级→范围内, 与客户排行/PC 同基线)
        List<BizIncentiveRecord> records = filterRecordsByVisibleCustomers(recordMapper.selectList(wrapper));

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
        long ttl = SCOREBOARD_TTL_MS / 2 + (long) (Math.random() * (SCOREBOARD_TTL_MS / 2));
        scoreboardCache.put(key, new ScoreboardCacheEntry(result, System.currentTimeMillis() + ttl));
        return result;
    }

    private List<Map<String, Object>> computeScoreboard(String period) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
        // 【2026-11-04 用户令"四项服务/四项激励也设置上对应的数据的权限"】个人榜(激励统计页签)按当前
        // 权限账号数据范围收口(可见人员基线, 上传人在范围内的记录才计分), 取代 2026-08-08 公司级口径
        List<BizIncentiveRecord> records = filterRecordsByVisibleUsers(recordMapper.selectList(wrapper));

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
     * 获取客户关联的激励学分总分
     */
    public int getScoreByCustomerId(Long customerId) {
        // 【2026-08-08 分享制 防越权】学分只按当前用户可见记录计算（admin/专项授权→全部，否则本人+已分享）
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
            String customerName, String salesperson, String startDate, String endDate, String period,
            String orgIds, String devType, String product, String keyword, Long recordId) {
        LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BizIncentiveRecord::getCustomerId);
        // 【2026-11-05 用户拍板 B"激励查询改走组织范围档位"】激励查询/案例库/MP 查询三面统一
        // 改走组织范围: 客户属 (customer,view) 档位 ∨ 本人上传恒可见(原"全员可见"无过滤退役)
        applyOrgQueryVisibility(wrapper);
        // 【性能 2026-10-27】recordId 定位: 先按可见性取种子记录, 命中即把查询收窄到
        // 该记录所属客户(与全量接口的可见性口径一致); 种子不可见/不存在 → 空结果。
        if (recordId != null) {
            LambdaQueryWrapper<BizIncentiveRecord> seed = new LambdaQueryWrapper<>();
            seed.eq(BizIncentiveRecord::getId, recordId).isNotNull(BizIncentiveRecord::getCustomerId);
            applyOrgQueryVisibility(seed);
            List<BizIncentiveRecord> seedRows = recordMapper.selectList(seed);
            if (seedRows.isEmpty()) return List.of();
            wrapper.eq(BizIncentiveRecord::getCustomerId, seedRows.get(0).getCustomerId());
        }
        // 【P1-1/M2 2026-10-17】period 过滤补齐：前端统计页一直传 period，此前后端不收导致筛选静默失效；
        // 空白不放行等值（与 getRecords/categoryStats/scoreboard 同守卫——空串当 period='' 匹配必然 0 条）
        if (period != null && !period.isBlank()) wrapper.eq(BizIncentiveRecord::getPeriod, period);
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
        // 【2026-11-09 用户令"全局检索应能检索客户/分类/单一记录/关联记录"】关键词=六路宽匹配:
        // ①关联产品 ②具体产品 ③客户名(customer表LIKE→customerId IN) ④分类名(category表LIKE→categoryId IN)
        // ⑤上传人姓名(user表LIKE→userId IN) ⑥文件名(三槽位文件名LIKE)。
        // 展示层(PC/MP)有关键字时打散为纯记录列表(不按客户分组卡片)——
        // 客户名命中会带出该客户全部记录, 但每条独立成行, 用户逐行看(非旧卡片汇总)。
        // 可见性过滤(applyOrgQueryVisibility)在 wrapper 最前, 深度检索只在有权看的记录内。
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> {
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
            });
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

        Long currentUserId = SecurityUtils.getCurrentUserId();
        Set<Long> readRecordIds = findReadRecordIds(currentUserId, records.stream()
                .map(BizIncentiveRecord::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        // 填充关联信息
        // 【性能 2026-11-11 检索优化】用户名批量化: 原循环内逐条 userMapper.selectById,
        // 300+ 记录=600+ 次 DB 往返(实测组装层 ~2s 主因); 与客户/分类同款批量建 map, 数据源不变
        Set<Long> uidsAll = new LinkedHashSet<>();
        for (BizIncentiveRecord r : records) {
            if (r.getUserId() != null) uidsAll.add(r.getUserId());
            if (r.getUpdatedBy() != null) uidsAll.add(r.getUpdatedBy());
        }
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!uidsAll.isEmpty()) userMapper.selectByIds(uidsAll).forEach(u -> userMap.put(u.getId(), u));
        for (BizIncentiveRecord r : records) {
            r.setUnread(r.getId() != null && !readRecordIds.contains(r.getId()));
            if (r.getCategoryId() != null) {
                BizIncentiveCategory cat = catMap2.get(r.getCategoryId());
                if (cat != null) r.setCategoryName(cat.getCategoryName());
            }
            if (r.getUserId() != null) { SysUser u = userMap.get(r.getUserId()); if (u != null) r.setUserName(u.getRealName()); }
            if (r.getUpdatedBy() != null) { SysUser up = userMap.get(r.getUpdatedBy()); if (up != null) r.setUpdatedByName(up.getRealName()); }
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
            item.put("ownerName", cust != null ? cust.getOwnerName() : null);
            // 【2026-09-29】客户排行销售视图(06只看自己名下客户,rank保留公司级序号)需要按负责人过滤
            item.put("ownerId", cust != null ? cust.getOwnerId() : null);
            item.put("industry", ind);
            item.put("industryOrder", orderMap.getOrDefault(ind, 99));
            item.put("count", e.getValue().size());
            int totalScore = e.getValue().stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).sum();
            item.put("totalScore", totalScore);
            item.put("records", e.getValue());
            flatList.add(item);
        }
        flatList.sort(Comparator
                .comparingInt((Map<String, Object> m) -> -((Number) m.getOrDefault("totalScore", 0)).intValue())
                .thenComparingInt(m -> (int) m.get("industryOrder"))
                .thenComparing(m -> String.valueOf(m.getOrDefault("customerName", ""))));
        return flatList;
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
        if (!SecurityUtils.isAdmin()) {
            Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            Object tenant = row.get("tenant_id");
            Long recordTenant = tenant == null ? null : Long.valueOf(String.valueOf(tenant));
            if (g == null || !g.equals(recordTenant)) return false;
        }
        if (canSeeAllIncentive()) return true;
        Long self = SecurityUtils.getCurrentUserId();
        Object owner = row.get("user_id");
        if (self != null && owner != null && self.equals(Long.valueOf(String.valueOf(owner)))) return true;
        Object shared = row.get("shared");
        boolean isShared = (shared instanceof Number num && num.intValue() == 1)
                || Boolean.TRUE.equals(shared)
                || "1".equals(String.valueOf(shared));
        return isShared && canViewShared(self);
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
            int published = toInt(row.get("publishedCount"));
            int read = toInt(row.get("readCount"));
            publishedTotal = Math.max(publishedTotal, published); // 本月发布数对所有人相同，取最大值即真实发布量
            readTotal += read;
            if (read > 0) participantCount++;
        }

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int published = toInt(row.get("publishedCount"));
            int read = toInt(row.get("readCount"));
            int unread = Math.max(0, toInt(row.get("unreadCount")));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", row.get("userId"));
            item.put("realName", row.get("realName"));
            item.put("deptName", row.get("deptName"));
            item.put("publishedCount", published);
            item.put("readCount", read);
            item.put("unreadCount", unread);
            item.put("readRate", rate(read, published));
            ranking.add(item);
        }
        ranking.sort((a, b) -> {
            int byRead = Integer.compare(toInt(b.get("readCount")), toInt(a.get("readCount")));
            if (byRead != 0) return byRead;
            int byUnread = Integer.compare(toInt(a.get("unreadCount")), toInt(b.get("unreadCount")));
            if (byUnread != 0) return byUnread;
            return String.valueOf(a.get("realName")).compareTo(String.valueOf(b.get("realName")));
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
        if (SecurityUtils.isAdmin()) {
            return jdbc.queryForList(
                    "SELECT u.id AS userId, u.real_name AS realName, d.dept_name AS deptName, "
                            + "COUNT(DISTINCT r.id) AS publishedCount, COUNT(DISTINCT br.record_id) AS readCount, "
                            + "COUNT(DISTINCT r.id)-COUNT(DISTINCT br.record_id) AS unreadCount "
                            + "FROM sys_user u "
                            + "LEFT JOIN sys_dept d ON d.id = u.dept_id "
                            + "LEFT JOIN biz_incentive_record r ON r.created_at LIKE CONCAT(?, '%') "
                            + "LEFT JOIN biz_incentive_read br ON br.user_id=u.id AND br.record_id=r.id "
                            + "WHERE u.deleted=0 GROUP BY u.id,u.real_name,d.dept_name "
                            + "ORDER BY readCount DESC, unreadCount DESC LIMIT 100",
                    month);
        }
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

    private List<BizIncentiveCategory> buildTree(List<BizIncentiveCategory> list, Long parentId) {
        return list.stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .peek(c -> c.setChildren(buildTree(list, c.getId())))
                .collect(Collectors.toList());
    }
}
