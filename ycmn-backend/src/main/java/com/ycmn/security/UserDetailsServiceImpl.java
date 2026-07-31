package com.ycmn.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.engine.PermissionRuleEngine;
import com.ycmn.common.engine.PermissionRuleEngine.RuleResult;
import com.ycmn.common.enums.DataScopeType;
import com.ycmn.module.system.entity.*;
import com.ycmn.module.system.mapper.*;
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

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysOrgMapper orgMapper;
    private final SysOrgAdminMapper orgAdminMapper;
    private final SysRoleOrgMapper roleOrgMapper;
    private final SysPositionMapper positionMapper;
    private final SysPermissionRuleMapper permissionRuleMapper;

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

        Long uid = user.getId();

        // ======================== 1. 加载角色 ========================
        List<SysRole> roles = roleMapper.selectRolesByUserId(uid);
        if (!roles.isEmpty()) {
            List<String> roleCodes = roles.stream()
                    .map(SysRole::getRoleCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            securityUser.setRoleCodes(roleCodes);

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
                        customOrgIds.addAll(orgMapper.selectDescendantIds(ro.getOrgId()));
                    } else {
                        customOrgIds.add(ro.getOrgId());
                    }
                }
                securityUser.setManagedOrgIds(customOrgIds);
            }
        }

        // ======================== 2. 加载岗位（一人多岗） ========================
        List<SysPosition> positions = positionMapper.selectByUserId(uid);
        securityUser.setPositions(positions);

        // ======================== 2b. 从岗位推导集团和法人公司归属 ========================
        Set<Long> allGroupIds = new HashSet<>();
        Set<Long> allLegalEntityIds = new HashSet<>();
        for (SysPosition pos : positions) {
            if (pos.getOrgId() != null) {
                SysOrg posOrg = orgMapper.selectById(pos.getOrgId());
                if (posOrg != null) {
                    if (posOrg.getGroupId() != null) allGroupIds.add(posOrg.getGroupId());
                    if (posOrg.getLegalEntityId() != null) allLegalEntityIds.add(posOrg.getLegalEntityId());
                }
            }
        }
        // 如果岗位没有组织，用用户默认组织
        if (allGroupIds.isEmpty() && user.getOrgId() != null) {
            SysOrg userOrg = orgMapper.selectById(user.getOrgId());
            if (userOrg != null && userOrg.getGroupId() != null) allGroupIds.add(userOrg.getGroupId());
            if (userOrg != null && userOrg.getLegalEntityId() != null) allLegalEntityIds.add(userOrg.getLegalEntityId());
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
                .sorted(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : 999))
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
                }
            }
        } catch (Exception ignored) {}

        return securityUser;
    }
}
