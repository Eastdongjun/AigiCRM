package com.aigicrm.module.incentive.service;

import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.service.CustomerVisibilityService;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 激励授权与可见性：四项授权开关、分享制可见性、组织范围客户过滤、人员下拉、单用户可见判定。
 *
 * <p>S5.3 从 {@link IncentiveService} 抽出；授权仍是 biz_incentive_grant 单一源。</p>
 */
@Service
@RequiredArgsConstructor
public class IncentiveAccessService {

    private static final String LIT_REALNAME = "realName";

    private final BizIncentiveRecordMapper recordMapper;
    private final SysUserMapper userMapper;
    private final BizCustomerMapper customerMapper;
    private final CustomerVisibilityService customerVisibilityService;
    private final JdbcTemplate jdbc;

    public boolean canViewAll() {
        // 【产品口径 2026-11-15】admin 默认拥有全部权限
        if (SecurityUtils.isAdmin()) return true;
        return grantFlag(SecurityUtils.getCurrentUserId(), "can_view_all", false);
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
    public boolean canViewShared() {
        // 【产品口径 2026-11-15】admin 默认拥有全部权限
        if (SecurityUtils.isAdmin()) return true;
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
        // 【产品口径 2026-11-15】admin 默认拥有全部权限
        if (SecurityUtils.isAdmin()) return true;
        return grantFlag(SecurityUtils.getCurrentUserId(), "can_edit", false);
    }

    /** 人员列表（公开）：激励页下拉框用 */
    public List<Map<String, Object>> getUserList() {
        List<Map<String, Object>> list = new ArrayList<>();
        // 【2026-09-23 选人边界修正】人员筛选下拉必须与查看语义(canViewIncentiveUser)同口径:
        // 原按数据范围(scopeResolver.resolve)过滤,跨团队同事不出现在下拉里——但后端允许查看
        // 任何"有已分享记录"的人,分享制是全公司口径 → 列表看得到他人 shared 记录却选不到人,
        // 与客户转移选人同族缺陷。口径为本人加有已分享记录的作者（被取消查看已分享者仅本人）；
        // 管理员或专项授权为全量（覆盖上传弹窗代录选人场景）。
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, 1);
        if (!canSeeAllIncentive()) {
            Long self = SecurityUtils.getCurrentUserId();
            java.util.Set<Long> selectable = new java.util.HashSet<>();
            if (self != null) selectable.add(self);
            if (canViewShared()) {
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
            m.put(LIT_REALNAME, u.getRealName());
            list.add(m);
        }
        list.sort(Comparator.comparing(m -> (String) m.getOrDefault(LIT_REALNAME, "")));
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
        // 【产品口径 2026-11-15】admin 默认拥有全部权限
        if (SecurityUtils.isAdmin()) return true;
        return grantFlag(SecurityUtils.getCurrentUserId(), "can_delete", false);
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
        if (userId == null) return Map.of();
        try {
            return jdbc.queryForMap(
                    "SELECT can_view_shared, can_view_all, can_edit, can_delete " +
                    "FROM biz_incentive_grant WHERE user_id = ?", userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of();
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
        if (row.isEmpty()) return defaultValue;
        Object v = row.get(column);
        if (v == null) return defaultValue;
        return Integer.valueOf(1).equals(((Number) v).intValue());
    }

    boolean canSeeAllIncentive() {
        // 【产品口径 2026-11-15】admin 默认拥有全部权限
        if (SecurityUtils.isAdmin()) return true;
        return grantFlag(SecurityUtils.getCurrentUserId(), "can_view_all", false)
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
        boolean canShared = canViewShared();
        wrapper.and(w -> {
            w.eq(BizIncentiveRecord::getUserId, userId);
            if (canShared) w.or().eq(BizIncentiveRecord::getShared, 1);
        });
    }

    // ========== 【2026-11-05 用户拍板 B"激励查询改走组织范围档位"(三面全改)】==========
    // 激励查询/案例库/MP 查询共用的 customer-records 分组面, 可见性从"全员可见(无过滤)"
    // 改为组织范围档位: 客户属当前账号 (customer,view) 数据范围 ∨ 本人上传恒可见。
    // 管理员恒全量；专项授权保持全量旁路；分享开关在本面退役（仍作用于记录列表面）；
    // 未分配负责人的客户保持全员可见（镜像统计家族口径）。
    private Optional<java.util.Set<Long>> orgScopeVisibleCustomerIds() {
        com.aigicrm.security.SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Optional.empty(); // empty=不过滤(全量)
        // 【WP1.5 2026-11-13】单一源化: 可见负责人集合改走 CustomerVisibilityService((customer,view)),
        // 客户过滤口径不变(未分配负责人的客户保持全员可见)
        java.util.List<Long> visibleUsers = customerVisibilityService.visibleOwnerIds();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (BizCustomer c : customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getDeleted, 0))) {
            if (c.getOwnerId() == null || (visibleUsers != null && visibleUsers.contains(c.getOwnerId()))) {
                ids.add(c.getId());
            }
        }
        return Optional.of(ids);
    }

    void applyOrgQueryVisibility(LambdaQueryWrapper<BizIncentiveRecord> wrapper) {
        if (canSeeAllIncentive()) return; // 专项授权"查看全部"=全量旁路(授权面板语义保持)
        Optional<java.util.Set<Long>> idsOpt = orgScopeVisibleCustomerIds();
        Long me = SecurityUtils.getCurrentUserId();
        if (idsOpt.isEmpty()) return; // 全量
        java.util.Set<Long> ids = idsOpt.get();
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

    boolean canViewIncentiveUser(Long targetUserId) {
        if (canSeeAllIncentive()) return true;
        Long self = SecurityUtils.getCurrentUserId();
        if (targetUserId != null && targetUserId.equals(self)) return true;
        // 【2026-08-08 双勾选】被取消"查看已分享"的用户无法查看他人的分享记录
        if (!canViewShared()) return false;
        return recordMapper.selectCount(new LambdaQueryWrapper<BizIncentiveRecord>()
                .eq(BizIncentiveRecord::getUserId, targetUserId)
                .eq(BizIncentiveRecord::getShared, 1)) > 0;
    }

}
