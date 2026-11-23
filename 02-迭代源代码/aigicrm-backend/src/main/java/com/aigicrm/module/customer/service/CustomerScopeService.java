package com.aigicrm.module.customer.service;

import com.aigicrm.common.engine.PermissionRuleEngine;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 客户数据范围与自动共享规则。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出。数据范围仍以 OrgScopeResolver +
 * CustomerAccessService 的口径为单一来源；本类只负责把范围/auto_share 拼成
 * LambdaQueryWrapper 条件与可见用户集合。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerScopeService {

    private static final String LIT_CUSTOMER = "customer";

    private final SysConfigService configService;
    private final RolePermScopeService rolePermScopeService;
    private final OrgScopeResolver scopeResolver;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final CustomerVisibilityService customerVisibilityService;

    // ====== 自动共享规则 ======

    /** 读取自动共享配置 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAutoShareConfig() {
        try {
            String json = configService.getValue("customer.auto_share");
            if (json == null || json.isBlank()) return java.util.Collections.emptyMap();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    /** 根据自动共享规则扩展可见的 owner ID（当前登录人；与测试/既有调用同一静态入口） */
    private List<Long> getAutoShareOwnerIds() {
        return getAutoShareOwnerIdsCore(SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentRoleCodes(), SecurityUtils.getCurrentOrgId());
    }

    /** 【D2 修复】auto_share 口径按"指定用户"计算，供预览复用执行侧同链路。 */
    private List<Long> getAutoShareOwnerIdsFor(com.aigicrm.security.SecurityUser scopeUser) {
        return scopeUser == null ? java.util.Collections.emptyList()
                : getAutoShareOwnerIdsCore(scopeUser.getUserId(), scopeUser.getRoleCodes(), scopeUser.getOrgId());
    }

    /** auto_share 核心：显式入参（本人/角色/组织），当前用户与预览用户共用。 */
    private List<Long> getAutoShareOwnerIdsCore(Long currentUserId, java.util.List<String> currentRoles, Long orgId) {
        Map<String, Object> cfg = getAutoShareConfig();
        if (cfg.isEmpty() || currentUserId == null) return java.util.Collections.emptyList();
        java.util.Set<Long> ownerIds = new java.util.LinkedHashSet<>();
        ownerIds.add(currentUserId);

        // 规则1: 直接上级 → 看到直属下级的客户
        if (Boolean.TRUE.equals(cfg.get("superior"))) {
            addAutoShareSubordinates(ownerIds, currentUserId);
        }
        // 规则3: 指定角色 → 看到同组织内所有用户
        addAutoShareRoleOrgUsers(ownerIds, cfg, currentRoles, orgId);
        // 规则4: 指定人员 → 不加过滤
        if (isAutoShareExempt(cfg, currentUserId)) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(ownerIds);
    }

    private void addAutoShareSubordinates(java.util.Set<Long> ownerIds, Long currentUserId) {
        java.util.List<SysUser> subordinates = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getManagerId, currentUserId)
                .eq(SysUser::getStatus, 1)
                .eq(SysUser::getDeleted, 0));
        for (SysUser sub : subordinates) {
            ownerIds.add(sub.getId());
        }
    }

    private void addAutoShareRoleOrgUsers(java.util.Set<Long> ownerIds, Map<String, Object> cfg,
                                          java.util.List<String> currentRoles, Long orgId) {
        Object rolesObj = cfg.get("roles");
        if (!(rolesObj instanceof java.util.List<?> roles) || roles.isEmpty()) return;
        for (Object r : roles) {
            String roleId = String.valueOf(r);
            if (currentRoles != null && currentRoles.contains(roleId)) {
                if (orgId != null) {
                    java.util.List<SysUser> orgUsers = userMapper.selectList(
                        new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getOrgId, orgId)
                            .eq(SysUser::getStatus, 1)
                            .eq(SysUser::getDeleted, 0));
                    for (SysUser u : orgUsers) ownerIds.add(u.getId());
                }
                return; // 命中一个指定角色即止（与原 break 同义）
            }
        }
    }

    private boolean isAutoShareExempt(Map<String, Object> cfg, Long currentUserId) {
        // 【测试H1修复 2026-10-13】users 元素类型归一: 配置可经界面/JSON写入口混入字符串数字("136")或非法值,
        // 旧实现仅容 Integer/Long, 字符串直接 ClassCastException 令全体非admin的列表/页签计数500。
        // 归一为 Long, 非法元素忽略并告警(不阻断列表), 用户命中任一合法豁免id即不加过滤。
        Object usersObj = cfg.get("users");
        if (!(usersObj instanceof java.util.List<?> users) || users.isEmpty()) return false;
        for (Object uid : users) {
            Long exemptId = toLongOrNull(uid);
            if (exemptId == null) {
                org.slf4j.LoggerFactory.getLogger(CustomerScopeService.class)
                        .warn("customer.auto_share users 含非法元素({}), 已忽略——请到系统设置核对配置", uid);
                continue;
            }
            if (currentUserId.equals(exemptId)) return true;
        }
        return false;
    }

    /** auto_share users 元素类型归一: Number→longValue; 纯数字字符串→解析; 其余返回 null(非法) */
    private static Long toLongOrNull(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String s) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** B1-A 2026-11-18：auto_share 只扩不缩——office 档（全办事处）不再被 superior/roles 收窄。 */
    private boolean isOfficeScopeFor(com.aigicrm.security.SecurityUser scopeUser) {
        return scopeUser != null
                && "office".equals(rolePermScopeService.effectiveScopeCode(scopeUser, LIT_CUSTOMER, "view"));
    }

    /** B1-A 2026-11-18：数据范围 + auto_share 合并为一组 (base OR share)，auto_share 只扩不缩。 */
    void applyDataScopeAndAutoShare(LambdaQueryWrapper<BizCustomer> wrapper) {
        applyDataScopeAndAutoShareFor(wrapper, SecurityUtils.getCurrentUser(), getAutoShareOwnerIds());
    }

    /** 【D2 修复】按指定用户解析，预览与列表同口径。 */
    void applyDataScopeAndAutoShareFor(LambdaQueryWrapper<BizCustomer> wrapper,
                                               com.aigicrm.security.SecurityUser scopeUser) {
        applyDataScopeAndAutoShareFor(wrapper, scopeUser, getAutoShareOwnerIdsFor(scopeUser));
    }

    void applyDataScopeAndAutoShareFor(LambdaQueryWrapper<BizCustomer> wrapper,
                                               com.aigicrm.security.SecurityUser scopeUser,
                                               java.util.List<Long> shareOwnerIds) {
        if (scopeUser == null) {
            wrapper.eq(BizCustomer::getOwnerId, -1L);
            return;
        }
        com.aigicrm.common.scope.ScopeRequest req = buildCustomerViewScopeRequest(scopeUser);
        com.aigicrm.common.scope.ScopeDecision d = scopeResolver.resolveScope(req);
        if (d == null) {
            wrapper.eq(BizCustomer::getOwnerId, -1L);
            return;
        }
        java.util.List<Long> extra = applicableAutoShareOwnerIds(scopeUser, d, shareOwnerIds);
        if (!d.isAll()) {
            if (req.getAnchor() == com.aigicrm.common.scope.ScopeRequest.AnchorType.ORG) {
                applyOrgScopeWithShare(wrapper, d, extra);
            } else {
                applyOwnerScopeWithShare(wrapper, d, extra);
            }
        }
        // 规则引擎：追加运行时 WHERE 条件（与统一层 AND 合并，追加条件层）
        applyRuleEngineFilter(wrapper, scopeUser);
    }

    /** ORG 锚点：部门基础范围 OR auto_share 扩展 owner（只扩不缩）。 */
    private void applyOrgScopeWithShare(LambdaQueryWrapper<BizCustomer> wrapper,
                                        com.aigicrm.common.scope.ScopeDecision d,
                                        java.util.List<Long> extra) {
        java.util.List<Long> deptIds = d.getVisibleDeptIds();
        boolean hasBase = deptIds != null && !deptIds.isEmpty();
        if (hasBase && !extra.isEmpty()) {
            wrapper.and(w -> w.in(BizCustomer::getDeptId, deptIds).or().in(BizCustomer::getOwnerId, extra));
        } else if (hasBase) {
            wrapper.in(BizCustomer::getDeptId, deptIds);
        } else if (!extra.isEmpty()) {
            wrapper.in(BizCustomer::getOwnerId, extra);
        } else {
            wrapper.eq(BizCustomer::getDeptId, -1L); // 空集合: 不可见
        }
    }

    /** OWNER 锚点：base∪share 合并为单个 IN（只扩不缩）。 */
    private void applyOwnerScopeWithShare(LambdaQueryWrapper<BizCustomer> wrapper,
                                          com.aigicrm.common.scope.ScopeDecision d,
                                          java.util.List<Long> extra) {
        java.util.LinkedHashSet<Long> union = new java.util.LinkedHashSet<>();
        if (d.getVisibleUserIds() != null) union.addAll(d.getVisibleUserIds());
        union.addAll(extra);
        if (!union.isEmpty()) wrapper.in(BizCustomer::getOwnerId, union);
        else wrapper.eq(BizCustomer::getOwnerId, -1L); // 空集合: 不可见
    }

    /** auto_share 的"扩展部分"：已在基础范围内的 owner 不重复加入；office 档不扩不缩。 */
    private java.util.List<Long> applicableAutoShareOwnerIds(com.aigicrm.security.SecurityUser scopeUser,
                                                             com.aigicrm.common.scope.ScopeDecision d,
                                                             java.util.List<Long> shareOwnerIds) {
        if (d == null || d.isAll()) return java.util.Collections.emptyList();
        if (getAutoShareConfig().isEmpty()) return java.util.Collections.emptyList();
        if (isOfficeScopeFor(scopeUser)) return java.util.Collections.emptyList();
        if (shareOwnerIds == null || shareOwnerIds.size() <= 1) return java.util.Collections.emptyList();
        java.util.List<Long> visible = d.getVisibleUserIds();
        if (visible == null) return java.util.Collections.emptyList();
        java.util.List<Long> extra = new java.util.ArrayList<>();
        for (Long id : shareOwnerIds) {
            if (!visible.contains(id)) extra.add(id);
        }
        return extra;
    }

    /** 【D2 修复 2026-11-16】客户查看范围统一解析请求（列表/预览/auto_share 共用，不再各写一套）。 */
    private com.aigicrm.common.scope.ScopeRequest buildCustomerViewScopeRequest(com.aigicrm.security.SecurityUser scopeUser) {
        if (scopeUser == null) return null;
        return com.aigicrm.common.scope.ScopeRequest.builder()
                .user(scopeUser)
                .module(LIT_CUSTOMER)
                .action("view")
                .anchor(rolePermScopeService.anchorOf(scopeUser, LIT_CUSTOMER, "view"))
                .build();
    }


    // ====== 数据范围核心方法（升级版：支持组织层级） ======

    void applyDataScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        applyDataScopeFor(wrapper, SecurityUtils.getCurrentUser());
    }

    /** 【D2 修复】数据范围按指定用户解析，预览计数与列表共用本方法。 */
    void applyDataScopeFor(LambdaQueryWrapper<BizCustomer> wrapper, com.aigicrm.security.SecurityUser scopeUser) {
        // 【批② 2026-08-10 统一数据访问层】列表/统计/导出统一走 OrgScopeResolver.resolveScope(ScopeRequest),
        // 与详情/编辑/删除判定(CustomerAccessService.canAccess)共用同一链路, 根治 P1-05 口径分裂。
        // 旧的分支式数据范围实现已整体删除，
        // 全部由统一层的 OWNER/ORG 双锚点解析取代；未配置 role_perm_scope 时回退默认语义。
        if (scopeUser == null) {
            wrapper.eq(BizCustomer::getOwnerId, -1L);
            return;
        }
        com.aigicrm.common.scope.ScopeRequest req = com.aigicrm.common.scope.ScopeRequest.builder()
                .user(scopeUser)
                .module(LIT_CUSTOMER)
                .action("view")
                .anchor(rolePermScopeService.anchorOf(scopeUser, LIT_CUSTOMER, "view"))
                .build();
        com.aigicrm.common.scope.ScopeDecision d = scopeResolver.resolveScope(req);
        if (d.isAll()) {
            // all: 本集团不过滤
        } else if (req.getAnchor() == com.aigicrm.common.scope.ScopeRequest.AnchorType.ORG) {
            java.util.List<Long> deptIds = d.getVisibleDeptIds();
            if (deptIds != null && !deptIds.isEmpty()) wrapper.in(BizCustomer::getDeptId, deptIds);
            else if (deptIds != null) wrapper.eq(BizCustomer::getDeptId, -1L); // 空集合: 不可见
        } else {
            java.util.List<Long> userIds = d.getVisibleUserIds();
            if (userIds != null && !userIds.isEmpty()) wrapper.in(BizCustomer::getOwnerId, userIds);
            else if (userIds != null) wrapper.eq(BizCustomer::getOwnerId, -1L); // 空集合: 不可见
        }

        // 规则引擎：追加运行时 WHERE 条件（与统一层 AND 合并，追加条件层）
        applyRuleEngineFilter(wrapper, scopeUser);
    }

    /** 注入权限规则引擎的 WHERE 条件（仅注入 target_entity='customer' 的规则） */
    private void applyRuleEngineFilter(LambdaQueryWrapper<BizCustomer> wrapper, com.aigicrm.security.SecurityUser user) {
        if (user == null || user.getPermissionRules() == null || user.getPermissionRules().isEmpty()) return;
        // 按 target_entity 过滤，只取 customer 的 column_filter/org_filter/time_range 规则
        String ruleWhere = PermissionRuleEngine.evaluateForEntity(user.getPermissionRules(), user, LIT_CUSTOMER);
        if (ruleWhere != null && !ruleWhere.isEmpty()) {
            wrapper.apply(ruleWhere);
        }
    }

    /**
     * 解析当前用户按数据范围可见的用户 ID 集合；返回 null 表示全部可见（all 范围）
     * 【用途】统计/报表等跨实体查询复用与 applyDataScope 同一口径（self/dept/dept_and_children/org/custom）
     * 【修复 FEAT-20260804-DICT-DEPT-READ】经营分析/日报周报此前全量查询，销售员可见全公司数据
     * 【WP1.5 2026-11-13】口径切换: (statistics, view) 数据范围退役(锁定决策#7), 统一走
     * CustomerVisibilityService 的 (customer, view) 单一源——可见负责人=客户可见负责人。
     */
    public List<Long> getCurrentScopeUserIds() {
        return customerVisibilityService.visibleOwnerIds();
    }

    /**
     * 递归获取所有下级部门ID
     */
    List<Long> getDescendantDeptIds(Long parentId) {
        List<SysDept> allDepts = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1));
        List<Long> result = new ArrayList<>();
        collectChildren(allDepts, parentId, result);
        return result;
    }

    private void collectChildren(List<SysDept> all, Long parentId, List<Long> result) {
        for (SysDept dept : all) {
            if (Objects.equals(dept.getParentId(), parentId)) {
                result.add(dept.getId());
                collectChildren(all, dept.getId(), result);
            }
        }
    }

}
