package com.aigicrm.module.system.service;

import com.aigicrm.common.auth.AuthorizationService;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【WP8 对账 2026-11-13】旧/新轨范围对账服务(规格 WP8: 抽样用户比较旧/新可见集, 差异告警)。
 * 切流门槛: 抽样对账 0 差异(规格 §WP8 切流检查单)。
 * 双轨显式调用(绕过读开关): 旧轨=resolveScope(customer,view,anchorOf 配置);
 * 新轨=授予表并集+开关(view_like)。人数按 visibleCustomers 同口径(ownerId 过滤+未分配保持可见)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermReconcileService {

    private final OrgScopeResolver legacyResolver;
    private final com.aigicrm.module.system.mapper.SysUserMapper userMapper;
    private final AuthorizationService authorizationService;
    private final RolePermScopeService rolePermScopeService;
    private final JdbcTemplate jdbc;

    /** 抽样对账: N 个在职用户逐个双轨对比 (customer, view) 可见负责人集与客户数 */
    public Map<String, Object> reconcile(int sample, Long onlyUserId) {
        List<Long> targets = new ArrayList<>();
        if (onlyUserId != null) {
            targets.add(onlyUserId);
        } else {
            targets = jdbc.queryForList(
                    "SELECT id FROM sys_user WHERE deleted = 0 AND status = 1 ORDER BY id LIMIT ?",
                    Long.class, Math.max(1, Math.min(sample, 200)));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int diff = 0;
        for (Long uid : targets) {
            try {
                SecurityUser su = (SecurityUser) userDetailsService().loadUserByUsername(String.valueOf(uid));
                Map<String, Object> row = reconcileUser(su);
                boolean same = Boolean.TRUE.equals(row.get("match"));
                if (!same) diff++;
                rows.add(row);
            } catch (Exception e) {
                rows.add(Map.of("userId", uid, "error", String.valueOf(e.getMessage())));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sampled", rows.size());
        out.put("diffCount", diff);
        out.put("match", diff == 0);
        out.put("rows", rows);
        return out;
    }

    /** 单用户双轨对比(customer:view) */
    public Map<String, Object> reconcileUser(SecurityUser su) {
        // 旧轨(锚点读 role_perm_scope 配置——与 CustomerVisibilityService 现行口径一致)
        ScopeRequest req = ScopeRequest.builder()
                .user(su)
                .module("customer")
                .action("view")
                .anchor(rolePermScopeService.anchorOf(su, "customer", "view"))
                .build();
        ScopeDecision oldD = legacyResolver.resolveScope(req);
        // 新轨(授予表; 绕过读开关显式对比)
        ScopeDecision newD = authorizationService.scopeNewTrack(su, "customer:view");

        Set<Long> oldOwners = oldD.isAll() || oldD.getVisibleUserIds() == null ? null : new HashSet<>(oldD.getVisibleUserIds());
        Set<Long> newOwners = newD.isAll() || newD.getVisibleUserIds() == null ? null : new HashSet<>(newD.getVisibleUserIds());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", su.getUserId());
        row.put("username", su.getUsername());
        row.put("oldAll", oldOwners == null);
        row.put("newAll", newOwners == null);
        row.put("oldSize", oldOwners == null ? "全量" : oldOwners.size());
        row.put("newSize", newOwners == null ? "全量" : newOwners.size());
        boolean match;
        if (oldOwners == null || newOwners == null) {
            match = oldOwners == null && newOwners == null;
        } else {
            match = oldOwners.equals(newOwners);
        }
        row.put("match", match);
        if (!match) {
            Set<Long> onlyOld = oldOwners == null || newOwners == null ? new HashSet<>() : new HashSet<>(oldOwners);
            Set<Long> onlyNew = oldOwners == null || newOwners == null ? new HashSet<>() : new HashSet<>(newOwners);
            if (oldOwners != null && newOwners != null) {
                onlyOld.removeAll(newOwners);
                onlyNew.removeAll(oldOwners);
            }
            row.put("onlyOld", onlyOld);
            row.put("onlyNew", onlyNew);
        }
        return row;
    }

    /** 避免直接注入 UserDetailsService 类型(实现类注入), 经 provider 惰性取 */
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return userDetailProvider.getIfAvailable();
    }

    private final org.springframework.beans.factory.ObjectProvider<org.springframework.security.core.userdetails.UserDetailsService> userDetailProvider;
}
