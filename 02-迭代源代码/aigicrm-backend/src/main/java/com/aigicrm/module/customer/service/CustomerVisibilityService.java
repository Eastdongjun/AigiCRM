package com.aigicrm.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 【WP1.5 统计口径统一 2026-11-13】客户可见性单一源。
 *
 * 锁定决策#7: statistics:view 只做入口码, (statistics, view) 不再参与数据范围;
 * 每个统计项按**来源动作**的范围过滤——客户域统计/排行/团队动态的来源动作统一为
 * (customer, view)。本服务即"可见客户/可见负责人"的唯一产出点:
 * <ul>
 *   <li>{@link #visibleOwnerIds()} → 可见负责人 userIds(null=全量不过滤)</li>
 *   <li>{@link #filterVisible(List)} → 可见客户子集(未分配负责人的客户保持全员可见, 镜像旧口径)</li>
 * </ul>
 * 验收红线: 首页"客户总数" = 统计页"客户总数" = 客户列表 total(同一来源动作同一解析链)。
 */
@Service
@RequiredArgsConstructor
public class CustomerVisibilityService {

    private final OrgScopeResolver scopeResolver;
    private final RolePermScopeService rolePermScopeService;
    @SuppressWarnings("unused")
    private final SysUserMapper userMapper; // 预留: WP4 可见负责人白名单扩展

    /**
     * 可见负责人集合(按 (customer, view) 数据范围解析, 锚点读 role_perm_scope 配置)。
     *
     * @return null=全量不过滤(admin/all); 非空=可见负责人 userIds(恒含本人; 空集=范围内无人,
     *         调用方按 1=0/恒空 处理, 不得回退全量)
     */
    public List<Long> visibleOwnerIds() {
        var user = SecurityUtils.getCurrentUser();
        if (user == null) return List.of();
        if (SecurityUtils.isAdmin()) return null; // null=全部
        ScopeRequest req = ScopeRequest.builder()
                .user(user)
                .module("customer")
                .action("view")
                .anchor(rolePermScopeService.anchorOf(user, "customer", "view"))
                .build();
        return scopeResolver.resolveScope(req).getVisibleUserIds();
    }

    /**
     * 按可见负责人过滤客户集合。
     * 口径与全系统一致: admin/全量 → 原样返回; 未分配负责人(ownerId=null)的客户保持全员可见
     * (否则任何范围下都不可见, 与 G2 既有裁决一致); 其余按 ownerId ∈ 可见负责人集过滤。
     */
    public List<com.aigicrm.module.customer.entity.BizCustomer> filterVisible(List<com.aigicrm.module.customer.entity.BizCustomer> all) {
        if (all == null || all.isEmpty()) return all;
        var user = SecurityUtils.getCurrentUser();
        if (user == null || SecurityUtils.isAdmin()) return all;
        List<Long> visible = visibleOwnerIds();
        if (visible == null) return all;
        Set<Long> ids = new HashSet<>(visible);
        List<com.aigicrm.module.customer.entity.BizCustomer> out = new ArrayList<>(all.size());
        for (com.aigicrm.module.customer.entity.BizCustomer c : all) {
            if (c.getOwnerId() == null || ids.contains(c.getOwnerId())) out.add(c);
        }
        return out;
    }

    /** 是否全量口径(admin 或 all 档) — 供统计路径免过滤判断 */
    public boolean isUnfiltered() {
        return visibleOwnerIds() == null;
    }

    /** 空集守卫口径: 给 LambdaQueryWrapper 生成"恒空"条件(镜像统计家族 IN () 防线) */
    public <T> boolean applyOwnerScope(LambdaQueryWrapper<T> wrapper,
                                       com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> ownerColumn) {
        List<Long> userIds = visibleOwnerIds();
        if (userIds == null) return false; // all: 未加条件
        if (userIds.isEmpty()) {
            wrapper.apply("1=0");
            return true;
        }
        wrapper.in(ownerColumn, userIds);
        return true;
    }
}
