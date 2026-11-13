package com.aigicrm.module.auth.service;

import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.aigicrm.common.scope.OrgScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 【PR-21 2026-09-19】有效权限解释:把 SecurityUser 上已有的权限计算结果
 * (角色/数据范围/权限码/规则/字段级)结构化输出为"为什么有/没这个权限"。
 * 【WP6 2026-11-13】explainUser(userId): admin 生效预览——真实 UserDetailsService 重建
 * (登录时装载链原样), 展示动作/范围/可见人数(规格 WP6)。
 */
@Service
@RequiredArgsConstructor
public class PermissionExplainService {

    private final OrgScopeResolver scopeResolver;
    private final com.aigicrm.security.UserDetailsServiceImpl userDetailsService;
    private final com.aigicrm.module.system.mapper.SysUserMapper userMapper;

    public Map<String, Object> explain() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Map.of("error", "无会话上下文");
        return explainOn(user);
    }

    /** 【WP6】admin 生效预览: 按目标用户 ID 走真实装载链重建后解释 */
    public Map<String, Object> explainUser(Long userId) {
        com.aigicrm.module.system.entity.SysUser u = userMapper.selectById(userId);
        if (u == null || u.getDeleted() == 1) throw new com.aigicrm.common.BusinessException("用户不存在");
        SecurityUser rebuilt = (SecurityUser) userDetailsService.loadUserByUsername(String.valueOf(userId));
        Map<String, Object> result = explainOn(rebuilt);
        result.put("preview", true);
        return result;
    }

    /** 解释主体(会话用户或预览重建用户共用) */
    private Map<String, Object> explainOn(SecurityUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getUserId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("currentGroupId", user.getCurrentGroupId());
        result.put("groupIds", user.getGroupIds());

        // 角色与数据范围
        List<String> roleCodes = user.getRoleCodes() != null ? user.getRoleCodes() : List.of();
        result.put("roles", roleCodes);
        result.put("dataScope", user.getDataScope());
        result.put("dataScopes", user.getDataScopes());

        List<String> scopeExplanations = new ArrayList<>();
        if (roleCodes.size() <= 1) {
            scopeExplanations.add(String.format("单一角色 %s,数据范围=%s",
                    roleCodes.isEmpty() ? "(无角色)" : roleCodes.get(0), user.getDataScope()));
        } else {
            scopeExplanations.add(String.format("多角色并集:有效数据范围取最宽(%s);各角色范围=%s",
                    user.getDataScope(), user.getDataScopes()));
        }
        result.put("dataScopeExplanation", scopeExplanations);

        // 权限码清单
        Set<String> permissions = user.getPermissions() != null ? user.getPermissions() : Set.of();
        result.put("permissionCount", permissions.size());
        result.put("permissions", new TreeSet<>(permissions));

        // 权限规则命中
        List<Map<String, Object>> rules = new ArrayList<>();
        if (user.getPermissionRules() != null) {
            for (var rule : user.getPermissionRules()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("ruleName", String.valueOf(rule.getName()));
                r.put("ruleCode", String.valueOf(rule.getCode()));
                r.put("targetEntity", String.valueOf(rule.getTargetEntity()));
                r.put("description", String.valueOf(rule.getDescription()));
                rules.add(r);
            }
        }
        result.put("permissionRules", rules);

        // 字段级权限
        var rr = user.getRuleResult();
        if (rr != null) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("hiddenFields", rr.getHiddenFields());
            field.put("visibleFields", rr.getVisibleFields());
            result.put("fieldLevelPermissions", field);
        } else {
            result.put("fieldLevelPermissions", Map.of("note", "无权限规则生效"));
        }

        // 管理范围
        if (user.getManagedOrgIds() != null && !user.getManagedOrgIds().isEmpty()) {
            result.put("managedOrgCount", user.getManagedOrgIds().size());
        }

        // 常见阻断原因
        List<String> blockers = new ArrayList<>();
        if (permissions.isEmpty()) blockers.add("无任何权限码——检查角色是否绑定菜单");
        if ("self".equals(user.getDataScope())) blockers.add("数据范围=self:仅能看自己负责的数据");
        if (user.getCurrentGroupId() == null) blockers.add("无有效集团归属:业务接口将 fail-closed 拒绝");
        result.put("commonBlockers", blockers);

        // 【WP6 2026-11-13】可见人数(各域来源动作, 规格 WP6 生效预览"可见人数")
        java.util.Map<String, Object> visibility = new LinkedHashMap<>();
        visibility.put("customer", domainVisibleCount(user, "customer", "view"));
        visibility.put("task", domainVisibleCount(user, "task", "view"));
        visibility.put("score", domainVisibleCount(user, "score", "view"));
        visibility.put("approval", domainVisibleCount(user, "approval", "view"));
        result.put("visibleUserCounts", visibility);
        result.put("permEpoch", epochNow());

        return result;
    }

    /** 域可见人数(null=全量→-1 标记; 经双轨入口, 读开关生效) */
    private Object domainVisibleCount(SecurityUser user, String module, String action) {
        try {
            List<Long> visible = scopeResolver.resolveModuleUsers(user, module, action);
            return visible == null ? "全量" : visible.size();
        } catch (Exception e) {
            return "解析失败";
        }
    }

    private long epochNow() {
        try {
            return epochProvider.getIfAvailable() == null ? -1 : epochProvider.getIfAvailable().current();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 权限版本号(ObjectProvider 断循环; 表未就绪回 -1) */
    private final org.springframework.beans.factory.ObjectProvider<com.aigicrm.common.auth.PermEpochService> epochProvider;
}
