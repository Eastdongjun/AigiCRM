package com.aigicrm.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.engine.PermissionRuleEngine;
import com.aigicrm.common.engine.PermissionRuleEngine.RuleResult;
import com.aigicrm.common.enums.DataScopeType;
import com.aigicrm.module.system.entity.*;
import com.aigicrm.module.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    /** 未配置优先级的权限规则按最低优先级处理(排最后),999 语义=兜底排序权重 */
    private static final int DEFAULT_RULE_PRIORITY = 999;


    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysOrgAdminMapper orgAdminMapper;
    private final SysRoleOrgMapper roleOrgMapper;
    private final com.aigicrm.module.system.mapper.SysUserOrgMapper userOrgMapper;
    private final SysPositionMapper positionMapper;
    private final SysPermissionRuleMapper permissionRuleMapper;
    private final PermissionCacheService permissionCacheService;
    private final com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService;
    /** 【WP3】任职组织装载(内核表 sys_employment 经 JDBC 直查, 避免为内核新增 Mapper 树) */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    /**
     * @SuppressWarnings("deprecation") 有意保留 ORG_AND_CHILDREN:
     * 组织管理员 dataScope 升级路径的语义是"本人部门 ∪ 托管组织子树"(OrgScopeResolver.resolveOrgAndChildren),
     * 与 DEPT(仅本人部门树)【不等价】——迁移到 DEPT 会收窄组织管理员可见范围, 属行为变更, 不可机械替换。
     * DataScopeType.ORG_AND_CHILDREN 由 DEPT 兼容替代的注释针对"旧 org 体系统一", 此处是显式例外。
     */
    @SuppressWarnings("deprecation")
    @Override
    public UserDetails loadUserByUsername(String usernameOrId) throws UsernameNotFoundException {
        SysUser user = findUser(usernameOrId);
        if (user == null || user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户不存在或已禁用");
        }

        // 【2026-08-09 Redis 权限缓存】命中缓存直接从 PermissionContext 重组返回（构建需 10+ 条 SQL）
        // 【2026-08-14 缓存自愈】版本不一致即重建，旧权限最长残留由 TTL 降到 1 个请求
        PermissionContext cached = permissionCacheService.get(user.getId());
        if (isCacheFresh(cached, user)) {
            SecurityUser su = rebuildFromContext(cached);
            // pwdChangedAt/pwdResetRequired 每请求从 DB 行读取（不入缓存）：改密撤销与首登强制改密不依赖 Redis
            su.setPwdChangedAt(user.getPwdChangedAt());
            su.setPwdResetRequired(user.getPwdResetRequired());
            return su;
        }

        SecurityUser securityUser = buildSecurityUser(user);
        Long uid = user.getId();

        // ======================== 1. 加载角色 ========================
        List<SysRole> roles = roleMapper.selectRolesByUserId(uid);
        applyRoles(securityUser, roles, uid);

        // ======================== 1b. 用户级数据范围覆盖（层2：用户级 > 角色级） ========================
        applyUserScopeOverride(securityUser, user, uid);

        // ======================== 2. 加载岗位（一人多岗） ========================
        List<SysPosition> positions = positionMapper.selectByUserId(uid);
        securityUser.setPositions(positions);

        // ======================== 2d.【WP3 2026-11-13】装载任职组织(sys_employment) ========================
        loadEmploymentOrgs(securityUser, uid);

        // ======================== 2c. 【批③ 正交配置层】装载数据范围配置(USER>POSITION>ROLE 三级) ========================
        java.util.List<Long> positionIds = positions.stream().map(SysPosition::getId).toList();
        securityUser.setScopeConfigs(rolePermScopeService.loadScopeConfigs(uid,
                securityUser.getRoleCodes(), positionIds));

        // ======================== 2b. 从岗位推导集团和法人公司归属 ========================
        applyGroupAndLegalEntity(securityUser, positions, user);

        // ======================== 3. 聚合所有权限规则 ========================
        applyPermissionRules(securityUser, positions, roles);

        // ======================== 4. 预求值规则 ========================
        RuleResult ruleResult = PermissionRuleEngine.evaluate(securityUser.getPermissionRules(), securityUser);
        securityUser.setRuleResult(ruleResult);

        // ======================== 5. 加载组织管理员 ========================
        applyOrgAdminUpgrade(securityUser, uid);

        permissionCacheService.put(user.getId(), toContext(securityUser));
        return securityUser;
    }

    private SysUser findUser(String usernameOrId) {
        try {
            Long userId = Long.parseLong(usernameOrId);
            return userMapper.selectById(userId);
        } catch (NumberFormatException e) {
            return userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, usernameOrId)
                            .eq(SysUser::getDeleted, 0)
            );
        }
    }

    private boolean isCacheFresh(PermissionContext cached, SysUser user) {
        return cached != null && cached.getPermVersion() != null
                && cached.getPermVersion().equals(user.getPermVersion());
    }

    private SecurityUser buildSecurityUser(SysUser user) {
        Set<String> permissions = menuMapper.selectPermissionsByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        SecurityUser securityUser = new SecurityUser();
        securityUser.setUserId(user.getId());
        securityUser.setUsername(user.getUsername());
        securityUser.setPassword(user.getPassword());
        securityUser.setRealName(user.getRealName());
        securityUser.setDeptId(user.getDeptId());
        securityUser.setOfficeId(user.getOfficeId());
        securityUser.setOrgId(user.getOrgId());
        securityUser.setPermissions(permissions);
        securityUser.setAuthorities(authorities);
        securityUser.setPermVersion(user.getPermVersion());
        securityUser.setPwdChangedAt(user.getPwdChangedAt());
        securityUser.setPwdResetRequired(user.getPwdResetRequired());
        return securityUser;
    }

    /** 角色并集基础（设计 §4.2）：dataScopes 收集全部角色 code；dataScope 取最宽(min-rank)向后兼容。 */
    private void applyRoles(SecurityUser securityUser, List<SysRole> roles, Long uid) {
        if (roles.isEmpty()) return;
        List<String> roleCodes = roles.stream()
                .map(SysRole::getRoleCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        securityUser.setRoleCodes(roleCodes);

        List<String> scopeCodes = roles.stream()
                .map(SysRole::getDataScope)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        securityUser.setDataScopes(scopeCodes);

        DataScopeType bestScope = roles.stream()
                .map(r -> DataScopeType.fromCode(r.getDataScope()))
                .min(Comparator.comparingInt(DataScopeType::rank))
                .orElse(DataScopeType.SELF);
        securityUser.setDataScope(bestScope.getCode());

        if (bestScope == DataScopeType.CUSTOM) {
            // 【WP1.2 2026-11-13】可见性解析改走原始行 refs(不预展开, 展开时机在 OrgScopeResolver);
            // managedOrgIds 保持旧计算不动——它同时是 isOrgAdmin 管理通道
            securityUser.setManagedOrgIds(loadLegacyCustomOrgIds(uid));
            securityUser.setManagedOrgRefs(loadCustomOrgRefs(uid));
        }
    }

    /** 【WP1.1 2026-11-13 并集语义】用户级 override 改为追加（"额外范围只能加"）。 */
    private void applyUserScopeOverride(SecurityUser securityUser, SysUser user, Long uid) {
        String override = user.getDataScopeOverride();
        if (override != null && !override.isBlank()) {
            List<String> scopes = new ArrayList<>(securityUser.getDataScopes());
            if (!scopes.contains(override)) scopes.add(override);
            securityUser.setDataScopes(scopes);
            securityUser.setDataScope(securityUser.getWidestDataScope());
        }
        // 【2026-08-08 用户级自定义授权组织】override=custom 时加载个人授权组织原始行（sys_user_org）
        if ("custom".equals(override)) {
            applyCustomUserOrgs(securityUser, uid);
        }
    }

    private void applyCustomUserOrgs(SecurityUser securityUser, Long uid) {
        List<com.aigicrm.module.system.entity.SysUserOrg> userOrgs = userOrgMapper.selectByUserId(uid);
        if (userOrgs == null || userOrgs.isEmpty()) return;
        java.util.List<ManagedOrgRef> refs = new ArrayList<>(securityUser.getManagedOrgRefs());
        List<Long> legacyIds = new ArrayList<>();
        for (com.aigicrm.module.system.entity.SysUserOrg uo : userOrgs) {
            if (uo.getOrgId() != null) {
                refs.add(new ManagedOrgRef(uo.getOrgId(), uo.getIncludeChildren()));
                if (uo.getIncludeChildren() != null && uo.getIncludeChildren() == 1) {
                    legacyIds.addAll(deptMapper.selectDescendantIds(uo.getOrgId()));
                } else {
                    legacyIds.add(uo.getOrgId());
                }
            }
        }
        securityUser.setManagedOrgRefs(refs);
        securityUser.setManagedOrgIds(legacyIds);
    }

    /** 【WP3/P1-7 2026-11-13】任职组织装载失败 fail-closed 为空集，不阻断登录。 */
    private void loadEmploymentOrgs(SecurityUser securityUser, Long uid) {
        try {
            // 【P1 修复 2026-11-17】派生表去重并携带 MAX(is_primary)，外层排序（MySQL 3065 修复）
            java.util.List<Long> empOrgs = jdbc.queryForList(
                    "SELECT t.org_id FROM (" +
                    "SELECT e.org_id, MAX(e.is_primary) AS is_primary FROM sys_employment e " +
                    "JOIN sys_dept d ON d.id = e.org_id AND d.status = 1 " +
                    "WHERE e.user_id = ? AND e.status = 1 " +
                    "AND (e.valid_from IS NULL OR e.valid_from <= CURDATE()) " +
                    "AND (e.valid_to IS NULL OR e.valid_to >= CURDATE()) " +
                    "GROUP BY e.org_id" +
                    ") t ORDER BY t.is_primary DESC, t.org_id", Long.class, uid);
            securityUser.setEmploymentOrgIds(empOrgs);
            List<Long> primaries = jdbc.queryForList(
                    "SELECT e.org_id FROM sys_employment e WHERE e.user_id = ? AND e.is_primary = 1 AND e.status = 1 " +
                    "ORDER BY e.id LIMIT 1", Long.class, uid);
            securityUser.setPrimaryEmploymentOrgId(primaries.isEmpty() ? null : primaries.get(0));
        } catch (Exception e) {
            // 表尚未就绪(新内核表在灰度前可能缺失)→ 空集 fail-closed, 不阻断登录装载
            log.warn("装载任职组织失败(内核表未就绪?) userId={}", uid, e);
            securityUser.setEmploymentOrgIds(new ArrayList<>());
        }
    }

    private void applyGroupAndLegalEntity(SecurityUser securityUser, List<SysPosition> positions, SysUser user) {
        Set<Long> allGroupIds = new HashSet<>();
        Set<Long> allLegalEntityIds = new HashSet<>();
        for (SysPosition pos : positions) {
            if (pos.getOrgId() != null) {
                SysDept posDept = deptMapper.selectById(pos.getOrgId());
                if (posDept != null) {
                    if (posDept.getGroupId() != null) allGroupIds.add(posDept.getGroupId());
                    if (posDept.getLegalEntityId() != null) allLegalEntityIds.add(posDept.getLegalEntityId());
                }
            }
        }
        // 如果岗位没有组织，用用户默认组织
        if (allGroupIds.isEmpty() && user.getOrgId() != null) {
            SysDept userDept = deptMapper.selectById(user.getOrgId());
            if (userDept != null && userDept.getGroupId() != null) allGroupIds.add(userDept.getGroupId());
            if (userDept != null && userDept.getLegalEntityId() != null) allLegalEntityIds.add(userDept.getLegalEntityId());
        }
        securityUser.setGroupIds(new ArrayList<>(allGroupIds));
        securityUser.setLegalEntityIds(new ArrayList<>(allLegalEntityIds));
    }

    private void applyPermissionRules(SecurityUser securityUser, List<SysPosition> positions, List<SysRole> roles) {
        // 3a. 从岗位关联的规则
        List<SysPermissionRule> allRules = new ArrayList<>();
        for (SysPosition pos : positions) {
            allRules.addAll(permissionRuleMapper.selectByPositionId(pos.getId()));
        }
        // 3b. 从角色关联的规则
        for (SysRole role : roles) {
            allRules.addAll(permissionRuleMapper.selectByRoleId(role.getId()));
        }
        // 去重 + 按优先级排序
        List<SysPermissionRule> deduped = allRules.stream()
                .collect(Collectors.toMap(SysPermissionRule::getId, r -> r, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : DEFAULT_RULE_PRIORITY))
                .toList();
        securityUser.setPermissionRules(deduped);
    }

    /** 组织管理员管理通道：托管组织并入 + dataScope 提升到 ORG_AND_CHILDREN（含并集列表同步）。 */
    private void applyOrgAdminUpgrade(SecurityUser securityUser, Long uid) {
        try {
            List<Long> managedOrgIds = orgAdminMapper.selectManagedOrgIds(uid);
            if (managedOrgIds.isEmpty()) return;
            Set<Long> allManaged = new HashSet<>(securityUser.getManagedOrgIds());
            allManaged.addAll(managedOrgIds);
            securityUser.setManagedOrgIds(new ArrayList<>(allManaged));

            DataScopeType currentScope = DataScopeType.fromCode(securityUser.getDataScope());
            if (currentScope.rank() > DataScopeType.ORG_AND_CHILDREN.rank()) {
                securityUser.setDataScope(DataScopeType.ORG_AND_CHILDREN.getCode());
                // 多角色并集路径同样生效：把升级后的 org_and_children 并入并集列表
                List<String> scopes = new ArrayList<>(securityUser.getDataScopes());
                if (!scopes.contains(DataScopeType.ORG_AND_CHILDREN.getCode())) {
                    scopes.add(DataScopeType.ORG_AND_CHILDREN.getCode());
                }
                securityUser.setDataScopes(scopes);
            }
        } catch (Exception e) {
            // S4: 组织管理员 dataScope 提升查询失败不得静默——记录日志（当前未提升，保守 fail-closed）
            log.error("查询组织管理员 dataScope 失败 userId={}", securityUser.getUserId(), e);
        }
    }

    /** 【WP1.2 2026-11-13】角色级自定义授权组织原始行(sys_role_org, 含 includeChildren, 不预展开) */
    private java.util.List<ManagedOrgRef> loadCustomOrgRefs(Long uid) {
        java.util.List<ManagedOrgRef> refs = new ArrayList<>();
        for (SysRoleOrg ro : roleOrgMapper.selectByUserId(uid)) {
            if (ro.getOrgId() == null) continue;
            refs.add(new ManagedOrgRef(ro.getOrgId(), ro.getIncludeChildren()));
        }
        return refs;
    }

    /**
     * 【WP1.2 2026-11-13】managedOrgIds 旧口径计算(预展开, 与重构前逐字节一致)——
     * 仅供 isOrgAdmin 管理通道与 org_and_children 旧分支消费; 可见性解析已改走 refs。
     */
    private List<Long> loadLegacyCustomOrgIds(Long uid) {
        List<Long> customOrgIds = new ArrayList<>();
        for (SysRoleOrg ro : roleOrgMapper.selectByUserId(uid)) {
            if (ro.getOrgId() == null) continue;
            if (ro.getIncludeChildren() != null && ro.getIncludeChildren() == 1) {
                customOrgIds.addAll(deptMapper.selectDescendantIds(ro.getOrgId()));
            } else {
                customOrgIds.add(ro.getOrgId());
            }
        }
        return customOrgIds;
    }

    /** 从 SecurityUser 提取可序列化的 PermissionContext（不含 password/authorities——由 permissions 重建） */
    private PermissionContext toContext(SecurityUser su) {
        PermissionContext ctx = new PermissionContext();
        ctx.setUserId(su.getUserId());
        ctx.setUsername(su.getUsername());
        ctx.setRealName(su.getRealName());
        ctx.setDeptId(su.getDeptId());
        ctx.setOfficeId(su.getOfficeId());
        ctx.setOrgId(su.getOrgId());
        ctx.setRoleCodes(su.getRoleCodes() != null ? su.getRoleCodes() : new ArrayList<>());
        ctx.setDataScope(su.getDataScope());
        ctx.setDataScopes(su.getDataScopes() != null ? su.getDataScopes() : new ArrayList<>());
        ctx.setManagedOrgIds(su.getManagedOrgIds() != null ? su.getManagedOrgIds() : new ArrayList<>());
        ctx.setManagedOrgRefs(su.getManagedOrgRefs() != null ? su.getManagedOrgRefs() : new ArrayList<>());
        ctx.setEmploymentOrgIds(su.getEmploymentOrgIds() != null ? su.getEmploymentOrgIds() : new ArrayList<>());
        ctx.setPrimaryEmploymentOrgId(su.getPrimaryEmploymentOrgId());
        ctx.setGroupIds(su.getGroupIds() != null ? su.getGroupIds() : new ArrayList<>());
        ctx.setLegalEntityIds(su.getLegalEntityIds() != null ? su.getLegalEntityIds() : new ArrayList<>());
        ctx.setPositions(su.getPositions() != null ? su.getPositions() : new ArrayList<>());
        ctx.setPermissionRules(su.getPermissionRules() != null ? su.getPermissionRules() : new ArrayList<>());
        ctx.setRuleResult(su.getRuleResult());
        ctx.setPermissions(su.getPermissions() != null ? su.getPermissions() : new java.util.HashSet<>());
        ctx.setPermVersion(su.getPermVersion());
        ctx.setScopeConfigs(su.getScopeConfigs() != null ? su.getScopeConfigs() : new java.util.ArrayList<>());
        return ctx;
    }

    /** 从缓存 PermissionContext 重组 SecurityUser（authorities 由 permissions 重建；password 登录路径单独查库，此处置 null 安全） */
    private SecurityUser rebuildFromContext(PermissionContext ctx) {
        SecurityUser su = new SecurityUser();
        su.setUserId(ctx.getUserId());
        su.setUsername(ctx.getUsername());
        su.setPassword(null); // 登录验证不走 loadUserByUsername（AuthService 直接查库）
        su.setRealName(ctx.getRealName());
        su.setDeptId(ctx.getDeptId());
        su.setOfficeId(ctx.getOfficeId());
        su.setOrgId(ctx.getOrgId());
        su.setRoleCodes(ctx.getRoleCodes());
        su.setDataScope(ctx.getDataScope());
        su.setDataScopes(ctx.getDataScopes());
        su.setManagedOrgIds(ctx.getManagedOrgIds());
        su.setManagedOrgRefs(ctx.getManagedOrgRefs() != null ? ctx.getManagedOrgRefs() : new java.util.ArrayList<>());
        su.setEmploymentOrgIds(ctx.getEmploymentOrgIds() != null ? ctx.getEmploymentOrgIds() : new java.util.ArrayList<>());
        su.setPrimaryEmploymentOrgId(ctx.getPrimaryEmploymentOrgId());
        su.setPermVersion(ctx.getPermVersion());
        su.setGroupIds(ctx.getGroupIds());
        su.setLegalEntityIds(ctx.getLegalEntityIds());
        su.setPositions(ctx.getPositions());
        su.setPermissionRules(ctx.getPermissionRules());
        su.setRuleResult(ctx.getRuleResult());
        su.setPermissions(ctx.getPermissions());
        su.setScopeConfigs(ctx.getScopeConfigs() != null ? ctx.getScopeConfigs() : new java.util.ArrayList<>());
        su.setAuthorities(ctx.getPermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList()));
        return su;
    }
}
