package com.ycmn.security;

import com.ycmn.common.engine.PermissionRuleEngine;
import com.ycmn.common.enums.DataScopeType;
import com.ycmn.module.system.entity.SysPosition;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

public class SecurityUtils {

    public static SecurityUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            return (SecurityUser) authentication.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentUserId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public static String getCurrentUsername() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public static String getCurrentRealName() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getRealName() : null;
    }

    public static Long getCurrentDeptId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getDeptId() : null;
    }

    public static Long getCurrentOfficeId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getOfficeId() : null;
    }

    /** 用户所属组织ID */
    public static Long getCurrentOrgId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getOrgId() : null;
    }

    /** 获取所有角色编码（权限判断请用 isAdmin()/isOrgAdmin()，勿按首角色判定） */
    public static List<String> getCurrentRoleCodes() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getRoleCodes() : Collections.emptyList();
    }

    /** 获取数据范围枚举 */
    public static DataScopeType getCurrentDataScopeType() {
        SecurityUser user = getCurrentUser();
        return user != null ? DataScopeType.fromCode(user.getDataScope()) : DataScopeType.SELF;
    }

    /** 获取数据范围（多角色最高权限） */
    public static String getCurrentDataScope() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getEffectiveDataScope() : DataScopeType.SELF.getCode();
    }

    /** 获取管理的组织ID列表（管理员才有值） */
    public static List<Long> getCurrentManagedOrgIds() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getManagedOrgIds() : Collections.emptyList();
    }

    /** 获取用户所属的所有集团ID（从岗位关联组织推导） */
    public static List<Long> getCurrentGroupIds() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getGroupIds() : Collections.emptyList();
    }

    /** 获取用户所属的所有法人公司ID（从岗位关联组织推导） */
    public static List<Long> getCurrentLegalEntityIds() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getLegalEntityIds() : Collections.emptyList();
    }

    /** 获取当前用户的权限规则求值结果（缓存） */
    public static PermissionRuleEngine.RuleResult getCurrentRuleResult() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getRuleResult() : null;
    }

    /** 获取当前用户的所有岗位 */
    public static List<SysPosition> getCurrentPositions() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getPositions() : Collections.emptyList();
    }

    /** 是否组织管理员 */
    public static boolean isOrgAdmin() {
        SecurityUser user = getCurrentUser();
        return user != null && user.isOrgAdmin();
    }

    /** 是否系统管理员（admin角色） */
    public static boolean isAdmin() {
        SecurityUser user = getCurrentUser();
        return user != null && user.hasRole("admin");
    }

    /** 【2026-08-14 RBAC 收敛】isSalesDirector 已删除——角色码硬编码替代权限点的偏离，
     *  统一改判权限码 hasAuthority("incentive:manage")（M1 绑定给 admin+销售总监） */

    /** 是否持有指定权限码（方法内校验用，等价 @PreAuthorize hasAuthority） */
    public static boolean hasAuthority(String authority) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }
}
