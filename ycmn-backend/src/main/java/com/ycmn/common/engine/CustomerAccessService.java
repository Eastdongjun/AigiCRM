package com.ycmn.common.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.common.enums.DataScopeType;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.entity.BizCustomerShare;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
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
    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;

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

        return false;
    }

    /** 是否有权限编辑该客户 */
    public boolean canEdit(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        Long userId = SecurityUtils.getCurrentUserId();

        // 只有负责人能编辑
        return userId.equals(customer.getOwnerId());
    }

    /** 是否有权限删除该客户 */
    public boolean canDelete(Long customerId) {
        if (SecurityUtils.isAdmin()) return true;

        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return false;

        Long userId = SecurityUtils.getCurrentUserId();

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

        // 数据范围内的客户（根据部门下用户的客户）
        DataScopeType scopeType = SecurityUtils.getCurrentDataScopeType();
        if (scopeType == DataScopeType.ORG_AND_CHILDREN || scopeType == DataScopeType.ORG) {
            List<Long> deptUserIds = getDeptUserIds(scopeType);
            if (!deptUserIds.isEmpty()) {
                List<BizCustomer> deptCustomers = customerMapper.selectList(
                        new LambdaQueryWrapper<BizCustomer>().in(BizCustomer::getOwnerId, deptUserIds));
                deptCustomers.forEach(c -> ids.add(c.getId()));
            }
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

    /** 组织数据范围检查（向下兼容旧逻辑）：客户的负责人是否在用户的数据范围内 */
    private boolean isInOrgDataScope(BizCustomer customer) {
        DataScopeType scopeType = SecurityUtils.getCurrentDataScopeType();
        if (scopeType == DataScopeType.ALL) return true;
        if (scopeType == DataScopeType.SELF) {
            return SecurityUtils.getCurrentUserId().equals(customer.getOwnerId());
        }

        // 获取客户负责人的部门
        Long ownerDeptId = getOwnerDeptId(customer.getOwnerId());
        if (ownerDeptId == null) return false;

        Long userDeptId = SecurityUtils.getCurrentDeptId();
        if (userDeptId == null) return false;

        if (scopeType == DataScopeType.ORG) {
            return ownerDeptId.equals(userDeptId);
        }

        if (scopeType == DataScopeType.ORG_AND_CHILDREN) {
            List<Long> descendantIds = orgMapper.selectDescendantIds(userDeptId);
            return descendantIds.contains(ownerDeptId);
        }

        if (scopeType == DataScopeType.CUSTOM) {
            List<Long> managedOrgIds = SecurityUtils.getCurrentManagedOrgIds();
            return managedOrgIds.contains(ownerDeptId);
        }

        return false;
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
            deptIds = orgMapper.selectDescendantIds(deptId);
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
