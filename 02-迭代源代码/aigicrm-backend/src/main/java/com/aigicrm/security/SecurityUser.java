package com.aigicrm.security;

import com.aigicrm.common.engine.PermissionRuleEngine.RuleResult;
import com.aigicrm.common.enums.DataScopeType;
import com.aigicrm.module.system.entity.SysPermissionRule;
import com.aigicrm.module.system.entity.SysPosition;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

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
    /** 所有角色各自的 dataScope code（多角色并集解析用；单角色/空时走 dataScope 单值路径） */
    private List<String> dataScopes = new ArrayList<>();
    private List<Long> managedOrgIds = new ArrayList<>();
    /** 【WP1.2 2026-11-13】自定义范围授权组织原始行(含 includeChildren 标志, 不预展开)——解析统一走 OrgScopeResolver */
    private java.util.List<ManagedOrgRef> managedOrgRefs = new java.util.ArrayList<>();
    /** 【WP3 2026-11-13】全部有效任职组织(sys_employment, 多任职=并集; 新内核 ScopeSpecResolver 消费) */
    private java.util.List<Long> employmentOrgIds = new java.util.ArrayList<>();
    /** 【WP3】主职任职组织(employment is_primary=1 的 org; MY_PRIMARY_DEPT(_SUB) 档解析用) */
    private Long primaryEmploymentOrgId;

    // 集团制扩展：从岗位关联的组织推导出的集团和法人公司归属
    private List<Long> groupIds = new ArrayList<>();                // 用户所属的所有集团ID
    private List<Long> legalEntityIds = new ArrayList<>();          // 用户所属的所有法人公司ID

    // 权限规则引擎
    private List<SysPermissionRule> permissionRules = new ArrayList<>(); // 生效的权限规则
    private RuleResult ruleResult;                                      // 规则求值缓存

    private Set<String> permissions;
    private Collection<? extends GrantedAuthority> authorities;

    /** 【批③ 2026-08-10 正交配置层】role_perm_scope 全量配置(登录装载, 热路径零 DB) */
    private List<com.aigicrm.common.scope.ScopeConfig> scopeConfigs = new ArrayList<>();

    /** 【批④ 2026-08-10 多集团会话】会话级当前集团(JWT claim 携带, 每个 token 独立; 不在 PermissionContext 缓存) */
    private Long currentGroupId;

    /** 【2026-08-14 缓存自愈】权限版本号（缓存一致性比对用） */
    private Integer permVersion;

    /** 【2026-08-14 安全 P0】改密时刻（JwtAuthFilter 校验：早于该时刻签发的 token 失效；每请求从 DB 读取，不入缓存） */
    private java.time.LocalDateTime pwdChangedAt;

    /** 【2026-08-14 安全 P0】首次登录强制改密标记（JwtAuthFilter 服务端强制，非仅前端提示） */
    private Integer pwdResetRequired;

    /** 获取最高数据范围（多角色取最宽松） */
    public String getEffectiveDataScope() {
        if (dataScope == null) return DataScopeType.SELF.getCode();
        return dataScope;
    }

    /**
     * 【WP1.1 2026-11-13】并集最宽值（仅用于展示/日志；数据范围解析一律走 dataScopes 并集）。
     * 对 dataScopes(角色级∪用户级 override 追加) 取 rank 最小(最宽松)的 code。
     */
    public String getWidestDataScope() {
        DataScopeType widest = DataScopeType.fromCode(dataScope);
        if (dataScopes != null) {
            for (String code : dataScopes) {
                DataScopeType t = DataScopeType.fromCode(code);
                if (t.rank() < widest.rank()) widest = t;
            }
        }
        return widest.getCode();
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
