package com.ycmn.common.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.common.enums.DataScopeType;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.entity.BizCustomerShare;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
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
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final OrgScopeResolver scopeResolver;
    private final com.ycmn.module.sea.mapper.BizSeaPoolMapper seaPoolMapper;
    private final com.ycmn.module.sea.service.SeaConfigService seaConfigService;

    // ==================== 判断方法 ====================

    /** 是否有权限查看该客户 */
    public boolean canView(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        Long userId = SecurityUtils.getCurrentUserId();

        // 1. 是负责人
        if (userId.equals(customer.getOwnerId())) return true;

        // 2. 被共享
        if (isSharedWith(customerId, userId)) return true;

        // 3. 集团/法人公司数据范围
        if (isInGroupScope(customer)) return true;

        // 4. 组织数据范围
        if (isInOrgDataScope(customer)) return true;

        // 【2026-08-10 池级权限】客户在公海池内(status!=claimed)时, 池"可见"即可读详情——
        // 与列表可见范围一致(列表按 PERM_VIEW 过滤且客户名可点); 编辑/捞取/删除者自然可读。
        com.ycmn.module.sea.entity.BizSeaPool pool = findActivePool(customerId);
        if (pool != null && pool.getPoolId() != null) {
            var user = SecurityUtils.getCurrentUser();
            return seaConfigService.hasPoolPerm(pool.getPoolId(), com.ycmn.module.sea.service.SeaConfigService.PERM_VIEW, user)
                    || seaConfigService.hasPoolPerm(pool.getPoolId(), com.ycmn.module.sea.service.SeaConfigService.PERM_EDIT, user)
                    || seaConfigService.hasPoolPerm(pool.getPoolId(), com.ycmn.module.sea.service.SeaConfigService.PERM_CLAIM, user)
                    || seaConfigService.hasPoolPerm(pool.getPoolId(), com.ycmn.module.sea.service.SeaConfigService.PERM_DELETE, user);
        }

        return false;
    }

    /** 是否有权限编辑该客户 */
    public boolean canEdit(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        Long userId = SecurityUtils.getCurrentUserId();

        // 【2026-08-10 池级权限】客户在公海池内(status!=claimed)时, 编辑按池的编辑权限判定;
        // 否则回退"负责人才能编辑"(owner-only)
        com.ycmn.module.sea.entity.BizSeaPool pool = findActivePool(customerId);
        if (pool != null && pool.getPoolId() != null) {
            return seaConfigService.hasPoolPerm(pool.getPoolId(), com.ycmn.module.sea.service.SeaConfigService.PERM_EDIT,
                    SecurityUtils.getCurrentUser());
        }
        // 非池客户: 需具备 customer:edit 能力 且 是负责人。
        // 原 @PreAuthorize("hasAuthority('customer:edit')") 移到方法内(SecurityUtils.hasAuthority 等价),
        // 使"池内客户"可绕过角色码、按池级编辑权限判定(池编辑者缺 customer:edit 角色时仍可保存)
        return SecurityUtils.hasAuthority("customer:edit") && userId.equals(customer.getOwnerId());
    }

    /** 客户是否在公海池内(存在未捞取的池记录) */
    public boolean isInSeaPool(Long customerId) {
        return findActivePool(customerId) != null;
    }

    /** 查找客户当前未捞取的公海池记录(按入池时间倒序取最近一条) */
    private com.ycmn.module.sea.entity.BizSeaPool findActivePool(Long customerId) {
        return seaPoolMapper.selectOne(
                new LambdaQueryWrapper<com.ycmn.module.sea.entity.BizSeaPool>()
                        .eq(com.ycmn.module.sea.entity.BizSeaPool::getCustomerId, customerId)
                        .ne(com.ycmn.module.sea.entity.BizSeaPool::getStatus, "claimed")
                        .orderByDesc(com.ycmn.module.sea.entity.BizSeaPool::getEnterDate)
                        .last("LIMIT 1"));
    }

    /** 是否有权限删除该客户 */
    public boolean canDelete(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        Long userId = SecurityUtils.getCurrentUserId();

        // 【2026-08-10 池级权限】客户在公海池内时, 删除按池的删除权限判定(与 SeaController.deletePool/PERM_DELETE 一致);
        // 否则回退"负责人才能删除"(owner-only)——防止原负责人(释放未清 ownerId)经客户模块 DELETE 绕过公海删除治理
        com.ycmn.module.sea.entity.BizSeaPool pool = findActivePool(customerId);
        if (pool != null && pool.getPoolId() != null) {
            return seaConfigService.hasPoolPerm(pool.getPoolId(), com.ycmn.module.sea.service.SeaConfigService.PERM_DELETE,
                    SecurityUtils.getCurrentUser());
        }
        // 只有负责人能删除
        return userId.equals(customer.getOwnerId());
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

    /** 集团/法人公司数据范围检查
     *  如果用户有岗位绑定到某集团/法人公司，则只允许访问同集团/法人公司的客户 */
    private boolean isInGroupScope(BizCustomer customer) {
        List<Long> userGroupIds = SecurityUtils.getCurrentGroupIds();
        List<Long> userLegalEntityIds = SecurityUtils.getCurrentLegalEntityIds();

        // 如果没有配置集团/法人公司归属 → 不限制（向下兼容）
        boolean hasNewScope = !userGroupIds.isEmpty() || !userLegalEntityIds.isEmpty();
        if (!hasNewScope) return false;

        boolean passGroup = true;
        boolean passEntity = true;

        // 检查集团
        if (!userGroupIds.isEmpty() && customer.getGroupId() != null) {
            passGroup = userGroupIds.contains(customer.getGroupId());
        }

        // 检查法人公司
        if (!userLegalEntityIds.isEmpty() && customer.getLegalEntityId() != null) {
            passEntity = userLegalEntityIds.contains(customer.getLegalEntityId());
        }

        return passGroup && passEntity;
    }

    /** 组织数据范围检查：客户的负责人是否在用户可见范围内（统一走 OrgScopeResolver，含系统级开关） */
    private boolean isInOrgDataScope(BizCustomer customer) {
        Long ownerId = customer.getOwnerId();
        if (ownerId == null) return false;
        Long me = SecurityUtils.getCurrentUserId();
        if (me != null && me.equals(ownerId)) return true;

        List<Long> visible = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visible == null) return true; // all：不过滤
        return visible.contains(ownerId);
    }

    /** 获取负责人所属部门 ID */
    private Long getOwnerDeptId(Long ownerId) {
        if (ownerId == null) return null;
        var user = userMapper.selectById(ownerId);
        return user != null ? user.getDeptId() : null;
    }

    /** 获取范围内用户 ID 列表 */
    private List<Long> getDeptUserIds(DataScopeType scopeType) {
        Long deptId = SecurityUtils.getCurrentDeptId();
        if (deptId == null) return List.of();

        List<Long> deptIds;
        if (scopeType == DataScopeType.ORG_AND_CHILDREN) {
            deptIds = deptMapper.selectDescendantIds(deptId);
        } else {
            deptIds = List.of(deptId);
        }

        return userMapper.selectList(
                new LambdaQueryWrapper<com.ycmn.module.system.entity.SysUser>()
                        .in(com.ycmn.module.system.entity.SysUser::getDeptId, deptIds))
                .stream()
                .map(com.ycmn.module.system.entity.SysUser::getId)
                .toList();
    }
}
