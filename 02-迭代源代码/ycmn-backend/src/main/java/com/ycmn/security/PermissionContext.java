package com.ycmn.security;

import com.ycmn.common.engine.PermissionRuleEngine.RuleResult;
import com.ycmn.module.system.entity.SysPermissionRule;
import com.ycmn.module.system.entity.SysPosition;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限上下文缓存 DTO（Redis 可序列化）。
 *
 * 【2026-08-09 Redis 权限缓存】SecurityUser 挂在每次请求的 SecurityContext 且含
 * SimpleGrantedAuthority(不在 RedisConfig 多态白名单) / password / 规则求值等，
 * 直接塞 Redis 会 InvalidTypeIdException。此 DTO 只保存重组 SecurityUser 所需的
 * 精简字段，authorities 由 permissions 重建、password 由登录路径单独查库(不走缓存)。
 *
 * 字段与 UserDetailsServiceImpl.loadUserByUsername 的构建产物一一对应，
 * 任何新增的 SecurityUser 派生字段必须同步到此 DTO，否则缓存重组会丢字段。
 */
@Data
public class PermissionContext {

    private Long userId;
    private String username;
    private String realName;
    private Long deptId;
    private Long officeId;
    private Long orgId;

    private List<String> roleCodes = new ArrayList<>();
    private String dataScope = "self";
    private List<String> dataScopes = new ArrayList<>();
    private List<Long> managedOrgIds = new ArrayList<>();
    private List<Long> groupIds = new ArrayList<>();
    private List<Long> legalEntityIds = new ArrayList<>();

    private List<SysPosition> positions = new ArrayList<>();
    private List<SysPermissionRule> permissionRules = new ArrayList<>();
    private RuleResult ruleResult;

    private Set<String> permissions = new HashSet<>();

    /** 【2026-08-14 缓存自愈】构建时的权限版本号：命中时与 sys_user.perm_version 比对，不一致即重建 */
    private Integer permVersion;

    /** 【批③ 2026-08-10】数据范围配置(随权限上下文缓存, 任何新增派生字段必须同步到此 DTO) */
    private List<com.ycmn.common.scope.ScopeConfig> scopeConfigs = new ArrayList<>();
}
