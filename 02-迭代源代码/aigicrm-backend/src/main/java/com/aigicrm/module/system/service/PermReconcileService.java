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

    /** 对账槽清单(【P1-10 修复 2026-11-13】全模块; withinGroup=消费走 WithinGroup 集团收口) */
    private static final List<Slot> SLOTS = List.of(
            new Slot("customer", "view", false),
            new Slot("file", "view", false),
            new Slot("file", "manage", false),   // 【FILE-12 P2 2026-11-14】锚点修复后 manage/delete 纳入对账面
            new Slot("file", "delete", false),
            new Slot("approval", "view", false),
            new Slot("reminder", "view", false),
            new Slot("sea", "view", false),
            new Slot("incentive", "stats_view", false),
            new Slot("task", "view", true),
            new Slot("score", "view", true),
            new Slot("system", "view", true));

    private record Slot(String module, String action, boolean withinGroup) {}

    /** 抽样对账: N 个在职用户 × 全部 9 槽, 旧/新轨可见负责人集对比(P1-10: 不再只对 customer) */
    public Map<String, Object> reconcile(int sample, Long onlyUserId) {
        List<Long> targets;
        if (onlyUserId != null) {
            targets = List.of(onlyUserId);
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
                for (Slot slot : SLOTS) {
                    Map<String, Object> row = reconcileUser(su, slot);
                    boolean same = Boolean.TRUE.equals(row.get("match"));
                    if (!same) diff++;
                    rows.add(row);
                }
            } catch (Exception e) {
                rows.add(Map.of("userId", uid, "error", String.valueOf(e.getMessage())));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sampled", targets.size());
        out.put("slots", SLOTS.size());
        out.put("diffCount", diff);
        out.put("match", diff == 0);
        out.put("rows", rows);
        return out;
    }

    /** 单用户单槽双轨对比 */
    public Map<String, Object> reconcileUser(SecurityUser su, Slot slot) {
        // 旧轨: customer 域锚点读 role_perm_scope 配置(P2-3 口径), 其余 OWNER; WithinGroup 槽走集团收口
        ScopeDecision oldD;
        ScopeRequest req = ScopeRequest.builder()
                .user(su)
                .module(slot.module())
                .action(slot.action())
                .anchor("customer".equals(slot.module())
                        ? rolePermScopeService.anchorOf(su, slot.module(), slot.action())
                        : ScopeRequest.AnchorType.OWNER)
                .build();
        oldD = legacyResolver.resolveScopeLegacy(req); // 【P1-6 三轮修复】强制旧轨(切 new 后 resolveScope 已是新轨, 直调会假 0 差异)
        if (slot.withinGroup() && oldD.isAll() && !hasAdminRole(su)) {
            Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            if (g == null) {
                oldD = ScopeDecision.none();
            } else {
                List<Long> members = com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, g);
                ScopeDecision clamped = new ScopeDecision();
                clamped.setVisibleUserIds(members.isEmpty() ? List.of() : members);
                clamped.setVisibleDeptIds(List.of());
                oldD = clamped;
            }
        }
        // 新轨(授予表; 绕过读开关显式对比; WithinGroup 槽用收口版)
        // 【FILE-07 P0 修复 2026-11-14】对账锚点与旧轨 req 同源(customer=配置锚, 其余 OWNER),
        // 否则同槽双轨因锚点不同出假差异(旧轨 OWNER 用户维 vs 新轨配置锚 ORG 部门维)。
        ScopeRequest.AnchorType slotAnchor = "customer".equals(slot.module())
                ? rolePermScopeService.anchorOf(su, slot.module(), slot.action())
                : ScopeRequest.AnchorType.OWNER;
        ScopeDecision newD = slot.withinGroup()
                ? authorizationService.scopeWithinGroupForUser(su, codeOf(slot), slotAnchor)
                : authorizationService.scopeForUser(su, codeOf(slot), slotAnchor);

        Set<Long> oldOwners = oldD.isAll() || oldD.getVisibleUserIds() == null ? null : new HashSet<>(oldD.getVisibleUserIds());
        Set<Long> newOwners = newD.isAll() || newD.getVisibleUserIds() == null ? null : new HashSet<>(newD.getVisibleUserIds());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", su.getUserId());
        row.put("username", su.getUsername());
        row.put("slot", slot.module() + ":" + slot.action());
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

    private String codeOf(Slot slot) {
        return authorizationService.actionCodeOf(slot.module(), slot.action());
    }

    private boolean hasAdminRole(SecurityUser su) {
        return su != null && su.hasRole("admin");
    }

    /** 避免直接注入 UserDetailsService 类型(实现类注入), 经 provider 惰性取 */
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return userDetailProvider.getIfAvailable();
    }

    private final org.springframework.beans.factory.ObjectProvider<org.springframework.security.core.userdetails.UserDetailsService> userDetailProvider;
}
