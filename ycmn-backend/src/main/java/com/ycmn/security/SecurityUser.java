package com.ycmn.security;

import com.ycmn.common.engine.PermissionRuleEngine.RuleResult;
import com.ycmn.common.enums.DataScopeType;
import com.ycmn.module.system.entity.SysPermissionRule;
import com.ycmn.module.system.entity.SysPosition;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class SecurityUser implements UserDetails {
    private Long userId;
    private String username;
    private String password;
    private String realName;
    private Long deptId;
    private Long officeId;
    private Long orgId;

    // 多角色 + 多岗位支持
    private List<String> roleCodes = new ArrayList<>();
    private List<SysPosition> positions = new ArrayList<>();        // 用户所有岗位
    private String dataScope = DataScopeType.SELF.getCode();
    private List<Long> managedOrgIds = new ArrayList<>();

    // 集团制扩展：从岗位关联的组织推导出的集团和法人公司归属
    private List<Long> groupIds = new ArrayList<>();                // 用户所属的所有集团ID
    private List<Long> legalEntityIds = new ArrayList<>();          // 用户所属的所有法人公司ID

    // 权限规则引擎
    private List<SysPermissionRule> permissionRules = new ArrayList<>(); // 生效的权限规则
    private RuleResult ruleResult;                                      // 规则求值缓存

    private Set<String> permissions;
    private Collection<? extends GrantedAuthority> authorities;

    /** 获取最高数据范围（多角色取最宽松） */
    public String getEffectiveDataScope() {
        if (dataScope == null) return DataScopeType.SELF.getCode();
        return dataScope;
    }

    /** 是否有某个角色 */
    public boolean hasRole(String roleCode) {
        return roleCodes != null && roleCodes.contains(roleCode);
    }

    /** 是否组织管理员 */
    public boolean isOrgAdmin() {
        return managedOrgIds != null && !managedOrgIds.isEmpty();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
