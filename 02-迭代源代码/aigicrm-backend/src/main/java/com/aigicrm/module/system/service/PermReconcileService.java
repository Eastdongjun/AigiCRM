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
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_MATCH = "match";


    private final OrgScopeResolver legacyResolver;
    private final com.aigicrm.module.system.mapper.SysUserMapper userMapper;
    private final AuthorizationService authorizationService;
    private final RolePermScopeService rolePermScopeService;
    private final JdbcTemplate jdbc;

    /** 对账槽清单(【P1-10 修复 2026-11-13】全模块; withinGroup=消费走 WithinGroup 集团收口) */
    /**
     * 对账槽清单。anchor 字段【FILE-13 P2 2026-11-14】: 显式锚=该槽**生产消费方实际请求锚**
     * (file manage/delete 的 ORG 行对齐 FolderPermissionService.matrixDeptSet 固定 ORG 的
     * 文件夹矩阵通道; OWNER 行对齐 matrixSet 无夹文件/上传人维度), null=动态
     * (customer 按 anchorOf 配置, 其余 OWNER)。同槽双行=同槽双消费锚各对一次,
     * 防"对账 0 差异只覆盖 OWNER 通道"的验证盲区(FILE-07 的 ORG 通道回归对账抓不到)。
     */
    private static final List<Slot> SLOTS = List.of(
            new Slot("customer", "view", false, null),
            new Slot("file", "view", false, null),
            new Slot("file", "manage", false, ScopeRequest.AnchorType.ORG),
            new Slot("file", "delete", false, ScopeRequest.AnchorType.ORG),
            new Slot("file", "manage", false, ScopeRequest.AnchorType.OWNER),
            new Slot("file", "delete", false, ScopeRequest.AnchorType.OWNER),
            new Slot("approval", "view", false, null),
            new Slot("reminder", "view", false, null),
            new Slot("sea", "view", false, null),
            new Slot("incentive", "stats_view", false, null),
            new Slot("task", "view", true, null),
            new Slot("score", "view", true, null),
            new Slot("system", "view", true, null));

    /** 包级可见: reconcileUser 是 public 方法, 参数类型不得是 private(测试需构造同款槽钉锚点同源) */
    record Slot(String module, String action, boolean withinGroup,
                ScopeRequest.AnchorType anchor) {}

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
                    boolean same = Boolean.TRUE.equals(row.get(LIT_MATCH));
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
        out.put(LIT_MATCH, diff == 0);
        out.put("rows", rows);
        return out;
    }

    /** 单用户单槽双轨对比 */
    public Map<String, Object> reconcileUser(SecurityUser su, Slot slot) {
        // 【FILE-13 P2】对账锚=槽显式锚优先(ORG 行与生产矩阵消费方同源); null=动态
        // (customer 按 anchorOf 配置, 其余 OWNER)——新旧两轨同锚, 防锚点不同出假差异
        ScopeRequest.AnchorType slotAnchor = resolveSlotAnchor(su, slot);
        // 旧轨: customer 域锚点读 role_perm_scope 配置(P2-3 口径), 其余 OWNER; WithinGroup 槽走集团收口
        ScopeDecision oldD = resolveOldDecision(su, slot, slotAnchor);
        // 新轨(授予表; 绕过读开关显式对比; WithinGroup 槽用收口版)
        // 【FILE-07/13】对账锚点与旧轨 req 同源(槽显式锚/动态锚), 防双轨假差异。
        ScopeDecision newD = resolveNewDecision(su, slot, slotAnchor);
        return buildReconcileRow(su, slot, slotAnchor, oldD, newD);
    }

    private ScopeRequest.AnchorType resolveSlotAnchor(SecurityUser su, Slot slot) {
        if (slot.anchor() != null) return slot.anchor();
        return "customer".equals(slot.module())
                ? rolePermScopeService.anchorOf(su, slot.module(), slot.action())
                : ScopeRequest.AnchorType.OWNER;
    }

    private ScopeDecision resolveOldDecision(SecurityUser su, Slot slot, ScopeRequest.AnchorType slotAnchor) {
        ScopeRequest req = ScopeRequest.builder()
                .user(su)
                .module(slot.module())
                .action(slot.action())
                .anchor(slotAnchor)
                .build();
        // 【P1-6 三轮修复】强制旧轨(切 new 后 resolveScope 已是新轨, 直调会假 0 差异)
        ScopeDecision oldD = legacyResolver.resolveScopeLegacy(req);
        if (slot.withinGroup() && oldD.isAll() && !hasAdminRole(su)) {
            return clampToGroup();
        }
        return oldD;
    }

    private ScopeDecision clampToGroup() {
        Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        if (g == null) return ScopeDecision.none();
        List<Long> members = com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, g);
        ScopeDecision clamped = new ScopeDecision();
        clamped.setVisibleUserIds(members.isEmpty() ? List.of() : members);
        clamped.setVisibleDeptIds(List.of());
        return clamped;
    }

    private ScopeDecision resolveNewDecision(SecurityUser su, Slot slot, ScopeRequest.AnchorType slotAnchor) {
        return slot.withinGroup()
                ? authorizationService.scopeWithinGroupForUser(su, codeOf(slot), slotAnchor)
                : authorizationService.scopeForUser(su, codeOf(slot), slotAnchor);
    }

    private Map<String, Object> buildReconcileRow(SecurityUser su, Slot slot, ScopeRequest.AnchorType slotAnchor,
                                                  ScopeDecision oldD, ScopeDecision newD) {
        Set<Long> oldOwners = ownerSetOrNull(oldD);
        Set<Long> newOwners = ownerSetOrNull(newD);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", su.getUserId());
        row.put("username", su.getUsername());
        row.put("slot", slot.module() + ":" + slot.action());
        row.put("anchor", String.valueOf(slotAnchor)); // 【FILE-13】同槽 OWNER/ORG 两行靠此区分
        row.put("oldAll", oldOwners == null);
        row.put("newAll", newOwners == null);
        row.put("oldSize", oldOwners == null ? "全量" : oldOwners.size());
        row.put("newSize", newOwners == null ? "全量" : newOwners.size());
        boolean match = reconcileMatch(oldOwners, newOwners);
        row.put(LIT_MATCH, match);
        if (!match) {
            fillDiff(row, oldOwners, newOwners);
        }
        return row;
    }

    private Set<Long> ownerSetOrNull(ScopeDecision d) {
        return d.isAll() || d.getVisibleUserIds() == null ? null : new HashSet<>(d.getVisibleUserIds());
    }

    private boolean reconcileMatch(Set<Long> oldOwners, Set<Long> newOwners) {
        if (oldOwners == null || newOwners == null) {
            return oldOwners == null && newOwners == null;
        }
        return oldOwners.equals(newOwners);
    }

    private void fillDiff(Map<String, Object> row, Set<Long> oldOwners, Set<Long> newOwners) {
        Set<Long> onlyOld = oldOwners == null || newOwners == null ? new HashSet<>() : new HashSet<>(oldOwners);
        Set<Long> onlyNew = oldOwners == null || newOwners == null ? new HashSet<>() : new HashSet<>(newOwners);
        if (oldOwners != null && newOwners != null) {
            onlyOld.removeAll(newOwners);
            onlyNew.removeAll(oldOwners);
        }
        row.put("onlyOld", onlyOld);
        row.put("onlyNew", onlyNew);
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
