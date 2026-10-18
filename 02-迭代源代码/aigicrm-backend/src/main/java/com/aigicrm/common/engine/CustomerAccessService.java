package com.aigicrm.common.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerShare;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户统一访问控制服务
 *
 * 解决审计报告的"ID 越权"问题：
 *   - 列表过滤了，但 getById / 子资源没做归属校验
 *   - 所有涉及 customerId 的操作必须先过本服务
 *
 * 权限判断优先级：
 *   1. 管理员 → 全通过
 *   2. 负责人 (ownerId) → 通过
 *   3. 共享关系 → 可读通过
 *   4. 数据范围 → 按 DataScopeType 判断
 *   5. 否则 → 拒绝
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAccessService {

    private final BizCustomerMapper customerMapper;
    private final BizCustomerShareMapper shareMapper;
    private final OrgScopeResolver scopeResolver;
    private final com.aigicrm.module.sea.mapper.BizSeaPoolMapper seaPoolMapper;
    private final com.aigicrm.module.sea.service.SeaConfigService seaConfigService;
    private final com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService;

    // ==================== 判断方法 ====================

    /** 是否有权限查看该客户 */
    public boolean canView(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        Long userId = SecurityUtils.getCurrentUserId();

        // 1. 是负责人
        if (userId.equals(customer.getOwnerId())) return true;

        // 2. 被共享(共享制专属授权模型, 不并入统一层)
        if (isSharedWith(customerId, userId)) return true;

        // 3. 【批② 统一数据访问层】数据范围判定(OWNER/ORG 双锚点, 与列表同一链路)
        if (canAccessCustomer(SecurityUtils.getCurrentUser(), "view", customer)) return true;

        // 【2026-08-10 池级权限】客户在公海池内(status!=claimed)时, 池"可见"即可读详情——
        // 与列表可见范围一致(列表按 PERM_VIEW 过滤且客户名可点); 编辑/捞取/删除者自然可读。
        com.aigicrm.module.sea.entity.BizSeaPool pool = findActivePool(customerId);
        if (pool != null && pool.getPoolId() != null) {
            var user = SecurityUtils.getCurrentUser();
            return seaConfigService.hasPoolPerm(pool.getPoolId(), com.aigicrm.module.sea.service.SeaConfigService.PERM_VIEW, user)
                    || seaConfigService.hasPoolPerm(pool.getPoolId(), com.aigicrm.module.sea.service.SeaConfigService.PERM_EDIT, user)
                    || seaConfigService.hasPoolPerm(pool.getPoolId(), com.aigicrm.module.sea.service.SeaConfigService.PERM_CLAIM, user)
                    || seaConfigService.hasPoolPerm(pool.getPoolId(), com.aigicrm.module.sea.service.SeaConfigService.PERM_DELETE, user);
        }

        return false;
    }

    /** 是否有权限编辑该客户 */
    public boolean canEdit(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        // 【2026-08-10 池级权限】客户在公海池内(status!=claimed)时, 编辑按池的编辑权限判定;
        // 【2026-08-14 功能码收紧】池内编辑 = customer:edit 功能码 AND 池 PERM_EDIT 对象权限
        // (此前仅判池权限, 缺 customer:edit 的用户也可编辑池内客户, 已与生产角色绑定对齐: 4 角色均有该码, 零影响)
        com.aigicrm.module.sea.entity.BizSeaPool pool = findActivePool(customerId);
        if (pool != null && pool.getPoolId() != null) {
            if (!SecurityUtils.hasAuthority("customer:edit")) return false;
            return seaConfigService.hasPoolPerm(pool.getPoolId(), com.aigicrm.module.sea.service.SeaConfigService.PERM_EDIT,
                    SecurityUtils.getCurrentUser());
        }
        // 非池客户: 需具备 customer:edit 能力 且 目标在"编辑范围"内(统一层判定, 与列表同源)
        if (!SecurityUtils.hasAuthority("customer:edit")) return false;
        return canAccessCustomer(SecurityUtils.getCurrentUser(), "edit", customer);
    }

    /** 客户是否在公海池内(存在未捞取的池记录) */
    public boolean isInSeaPool(Long customerId) {
        return findActivePool(customerId) != null;
    }

    /** 查找客户当前未捞取的公海池记录(按入池时间倒序取最近一条) */
    private com.aigicrm.module.sea.entity.BizSeaPool findActivePool(Long customerId) {
        return seaPoolMapper.selectOne(
                new LambdaQueryWrapper<com.aigicrm.module.sea.entity.BizSeaPool>()
                        .eq(com.aigicrm.module.sea.entity.BizSeaPool::getCustomerId, customerId)
                        .ne(com.aigicrm.module.sea.entity.BizSeaPool::getStatus, "claimed")
                        .orderByDesc(com.aigicrm.module.sea.entity.BizSeaPool::getEnterDate)
                        .last("LIMIT 1"));
    }

    /** 是否有权限删除该客户 */
    public boolean canDelete(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        // 【2026-08-10 池级权限】客户在公海池内时, 删除按池的删除权限判定(与 SeaController.deletePool/PERM_DELETE 一致);
        // 否则回退统一层判定——防止原负责人(释放未清 ownerId)经客户模块 DELETE 绕过公海删除治理
        com.aigicrm.module.sea.entity.BizSeaPool pool = findActivePool(customerId);
        if (pool != null && pool.getPoolId() != null) {
            return seaConfigService.hasPoolPerm(pool.getPoolId(), com.aigicrm.module.sea.service.SeaConfigService.PERM_DELETE,
                    SecurityUtils.getCurrentUser());
        }
        // 非池客户: 目标在"删除范围"内(统一层判定)
        return canAccessCustomer(SecurityUtils.getCurrentUser(), "delete", customer);
    }

    /** 【批② 统一数据访问层】单条客户访问判定: 与列表 applyDataScope 共用 OrgScopeResolver 同一链路 */
    private boolean canAccessCustomer(com.aigicrm.security.SecurityUser user, String action, BizCustomer customer) {
        if (user == null) return false;
        com.aigicrm.common.scope.ScopeRequest req = com.aigicrm.common.scope.ScopeRequest.builder()
                .user(user)
                .module("customer")
                .action(action)
                .anchor(rolePermScopeService.anchorOf(user, "customer", action))
                .targetOwnerId(customer.getOwnerId())
                .targetDeptId(customer.getDeptId())
                .build();
        return scopeResolver.canAccess(req);
    }

    /** 是否有权限导出该客户（敏感字段如营收等） */
    public boolean canExport(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        Long userId = SecurityUtils.getCurrentUserId();

        if (userId.equals(customer.getOwnerId())) return true;
        if (isSharedWith(customerId, userId)) return true;

        return false;
    }

    // ==================== 断言方法（抛出异常） ====================

    public void assertCanView(Long customerId) {
        if (!canView(customerId)) {
            log.warn("用户 {} 尝试越权查看客户 {}", SecurityUtils.getCurrentUserId(), customerId);
            throw new BusinessException("无权查看该客户");
        }
    }

    public void assertCanEdit(Long customerId) {
        if (!canEdit(customerId)) {
            log.warn("用户 {} 尝试越权编辑客户 {}", SecurityUtils.getCurrentUserId(), customerId);
            throw new BusinessException("无权编辑该客户");
        }
    }

    public void assertCanDelete(Long customerId) {
        if (!canDelete(customerId)) {
            log.warn("用户 {} 尝试越权删除客户 {}", SecurityUtils.getCurrentUserId(), customerId);
            throw new BusinessException("无权删除该客户");
        }
    }

    /**
     * 【决策6 2026-10-18 用户裁决·财务代录】财务记录(发票/回款)写路径的对象校验:
     * 持 finance:manage 的财务专责可对任意客户录财务(录财务 ≠ 编辑客户资料,不要求是负责人);
     * 未持该码者维持原口径=assertCanEdit(负责人/编辑范围)。@PreAuthorize 已在控制器层
     * 要求 finance:manage 或 (customer:edit+finance:view),这里只放宽对象归属维度。
     * 【审查 backend-4 2026-10-18】权限码放行前必须先过客户存在/未删/同集团校验——
     * 否则代录可对任意 id（含已删/他集团）落发票回款，并把孤儿数据写进台账。
     */
    public void assertCanManageFinance(Long customerId) {
        assertFinanceCustomerWritable(customerId);
        if (SecurityUtils.hasAuthority("finance:manage")) {
            return;
        }
        assertCanEdit(customerId);
    }

    /**
     * 【审查 backend-4 2026-10-18】财务写路径的客户对象校验（存在、未逻辑删除、集团边界）。
     * 非 admin 用 GroupScopeUtil.canAccessGroup 校验客户 tenant_id（显式绕过租户拦截器取真实值，
     * 否则跨集团客户会被拦截器改写成"不存在"，无法给出明确 403）。admin 可跨集团。
     * 供 assertCanManageFinance 与 CustomerService.addInvoice/addPayment 共用。
     */
    public void assertFinanceCustomerWritable(Long customerId) {
        if (customerId == null) {
            throw new BusinessException("客户不存在或已删除，无法录入财务记录");
        }
        if (!SecurityUtils.isAdmin()) {
            Long tenantId = customerMapper.selectTenantIdById(customerId);
            if (tenantId != null && !com.aigicrm.common.scope.GroupScopeUtil.canAccessGroup(tenantId)) {
                throw new BusinessException(403, "无权操作其他集团的客户财务记录");
            }
        }
        if (customerMapper.selectById(customerId) == null) {
            throw new BusinessException("客户不存在或已删除，无法录入财务记录");
        }
    }

    // ==================== 批量方法 ====================

    /** 获取当前用户可查看的客户 ID 集合 */
    public Set<Long> getAccessibleCustomerIds() {
        if (SecurityUtils.isAdmin()) return Set.of(); // 空集合代表全部

        Long userId = SecurityUtils.getCurrentUserId();
        Set<Long> ids = new HashSet<>();

        // 自己负责的客户
        List<BizCustomer> owned = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>().eq(BizCustomer::getOwnerId, userId));
        owned.forEach(c -> ids.add(c.getId()));

        // 被共享的客户
        List<BizCustomerShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<BizCustomerShare>().eq(BizCustomerShare::getSharedToUserId, userId));
        shares.forEach(s -> ids.add(s.getCustomerId()));

        // 数据范围内的客户（统一走 OrgScopeResolver，含系统级开关并集；null=all 不过滤）
        // 【2026-08-07】由"仅 ORG/ORG_AND_CHILDREN"扩展为全范围，team/office/region/self+开关 一并生效
        List<Long> visible = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visible != null && !visible.isEmpty()) {
            List<BizCustomer> scopedCustomers = customerMapper.selectList(
                    new LambdaQueryWrapper<BizCustomer>().in(BizCustomer::getOwnerId, visible));
            scopedCustomers.forEach(c -> ids.add(c.getId()));
        }

        return ids;
    }

    // ==================== 辅助方法 ====================

    /** 检查客户是否被共享给指定用户 */
    private boolean isSharedWith(Long customerId, Long userId) {
        return shareMapper.selectCount(new LambdaQueryWrapper<BizCustomerShare>()
                .eq(BizCustomerShare::getCustomerId, customerId)
                .eq(BizCustomerShare::getSharedToUserId, userId)) > 0;
    }

    /** 【批② 2026-08-10】废弃方法已删除: isInGroupScope(基于从未回填的 group_id, 由租户插件物理隔离取代)、
     *  isInOrgDataScope/isOwnerInScope(由统一层 canAccess 取代, 与列表同源)、
     *  getOwnerDeptId/getDeptUserIds(未被统一层使用, 语义由 OrgScopeResolver.resolveDeptScope 覆盖) */
}
