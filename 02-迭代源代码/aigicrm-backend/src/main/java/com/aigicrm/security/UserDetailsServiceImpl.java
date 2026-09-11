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
        SysUser user;
        try {
            Long userId = Long.parseLong(usernameOrId);
            user = userMapper.selectById(userId);
        } catch (NumberFormatException e) {
            user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, usernameOrId)
                            .eq(SysUser::getDeleted, 0)
            );
        }

        if (user == null || user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户不存在或已禁用");
        }

        // 【2026-08-09 Redis 权限缓存】命中缓存直接从 PermissionContext 重组返回（构建需 10+ 条 SQL）
        // 【2026-08-14 缓存自愈】命中时比对权限版本号：evict 漏网/写失败时，版本不一致即重建，旧权限最长残留由 TTL 降到 1 个请求
        PermissionContext cached = permissionCacheService.get(user.getId());
        if (cached != null && cached.getPermVersion() != null
                && cached.getPermVersion().equals(user.getPermVersion())) {
            SecurityUser su = rebuildFromContext(cached);
            // pwdChangedAt/pwdResetRequired 每请求从 DB 行读取（不入缓存）：改密撤销与首登强制改密不依赖 Redis
            su.setPwdChangedAt(user.getPwdChangedAt());
            su.setPwdResetRequired(user.getPwdResetRequired());
            return su;
        }

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

        Long uid = user.getId();

        // ======================== 1. 加载角色 ========================
        List<SysRole> roles = roleMapper.selectRolesByUserId(uid);
        if (!roles.isEmpty()) {
            List<String> roleCodes = roles.stream()
                    .map(SysRole::getRoleCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            securityUser.setRoleCodes(roleCodes);

            // 多角色并集基础（设计 §4.2）：收集所有角色各自的 dataScope code，
            // OrgScopeResolver 对 size>1 逐 code 解析后并集合并；dataScope 单值（min-rank）保留向后兼容
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
                List<Long> customOrgIds = new ArrayList<>();
                List<SysRoleOrg> roleOrgs = roleOrgMapper.selectByUserId(uid);
                for (SysRoleOrg ro : roleOrgs) {
                    if (ro.getIncludeChildren() != null && ro.getIncludeChildren() == 1) {
                        customOrgIds.addAll(deptMapper.selectDescendantIds(ro.getOrgId()));
                    } else {
                        customOrgIds.add(ro.getOrgId());
                    }
                }
                securityUser.setManagedOrgIds(customOrgIds);
            }
        }

        // ======================== 1b. 用户级数据范围覆盖（层2：用户级 > 角色级） ========================
        // 仅覆盖 dataScope 字符串，不影响角色聚合的其他产物；空/未配置则跟随角色默认
        // 覆盖存在时清空 dataScopes → OrgScopeResolver 走单值路径（用户级覆盖整体取代角色并集）
        String override = user.getDataScopeOverride();
        if (override != null && !override.isBlank()) {
            securityUser.setDataScope(override);
            securityUser.setDataScopes(new ArrayList<>());
        }
        // 【2026-08-08 用户级自定义授权组织】override=custom 时加载个人授权组织（sys_user_org），
        // 优先于角色级 sys_role_org（多个行政各配不同部门）。无个人授权则回退角色级。
        if ("custom".equals(override)) {
            List<com.aigicrm.module.system.entity.SysUserOrg> userOrgs = userOrgMapper.selectByUserId(uid);
            if (userOrgs != null && !userOrgs.isEmpty()) {
                List<Long> customOrgIds = new ArrayList<>();
                for (com.aigicrm.module.system.entity.SysUserOrg uo : userOrgs) {
                    if (uo.getOrgId() == null) continue;
                    if (uo.getIncludeChildren() != null && uo.getIncludeChildren() == 1) {
                        customOrgIds.addAll(deptMapper.selectDescendantIds(uo.getOrgId()));
                    } else {
                        customOrgIds.add(uo.getOrgId());
                    }
                }
                securityUser.setManagedOrgIds(customOrgIds);
            }
        }

        // ======================== 2. 加载岗位（一人多岗） ========================
        List<SysPosition> positions = positionMapper.selectByUserId(uid);
        securityUser.setPositions(positions);

        // ======================== 2c. 【批③ 正交配置层】装载数据范围配置(USER>POSITION>ROLE 三级) ========================
        java.util.List<Long> positionIds = positions.stream().map(SysPosition::getId).toList();
        securityUser.setScopeConfigs(rolePermScopeService.loadScopeConfigs(uid,
                securityUser.getRoleCodes(), positionIds));

        // ======================== 2b. 从岗位推导集团和法人公司归属 ========================
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

        // ======================== 3. 聚合所有权限规则 ========================
        List<SysPermissionRule> allRules = new ArrayList<>();

        // 3a. 从岗位关联的规则
        for (SysPosition pos : positions) {
            List<SysPermissionRule> posRules = permissionRuleMapper.selectByPositionId(pos.getId());
            allRules.addAll(posRules);
        }

        // 3b. 从角色关联的规则
        for (SysRole role : roles) {
            List<SysPermissionRule> roleRules = permissionRuleMapper.selectByRoleId(role.getId());
            allRules.addAll(roleRules);
        }

        // 去重 + 按优先级排序
        allRules = allRules.stream()
                .collect(Collectors.toMap(SysPermissionRule::getId, r -> r, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : DEFAULT_RULE_PRIORITY))
                .toList();
        securityUser.setPermissionRules(allRules);

        // ======================== 4. 预求值规则 ========================
        RuleResult ruleResult = PermissionRuleEngine.evaluate(allRules, securityUser);
        securityUser.setRuleResult(ruleResult);

        // ======================== 5. 加载组织管理员 ========================
        try {
            List<Long> managedOrgIds = orgAdminMapper.selectManagedOrgIds(uid);
            if (!managedOrgIds.isEmpty()) {
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
            }
        } catch (Exception e) {
            // S4: 组织管理员 dataScope 提升查询失败不得静默——记录日志（当前未提升，保守 fail-closed）
            log.error("查询组织管理员 dataScope 失败 userId={}", securityUser.getUserId(), e);
        }

        permissionCacheService.put(user.getId(), toContext(securityUser));
        return securityUser;
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
