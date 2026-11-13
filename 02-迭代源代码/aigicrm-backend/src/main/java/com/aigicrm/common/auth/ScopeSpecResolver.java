package com.aigicrm.common.auth;

import com.aigicrm.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 【WP3 鉴权内核 2026-11-13】12 档范围解析器(规格 §3.2 解析算法; 锁定决策 #1/#2/#6/#8)。
 * 输入=GrantReader 授权行(逐行解析), 输出=ScopeSpec(调用方做并集/开关追加)。
 *
 * 解析源(与旧链路的关键差异):
 *   - "本部门" = sys_employment 全部有效任职组织(锁定决策#2, 多任职并集)——不再单点 user.dept_id
 *   - 子树 = sys_org_closure 闭包表(一次 join 取代逐层递归)
 *   - MY_OFFICE/MY_REGION = 任职组织的最近 dept_type 祖先(3/2); 无祖先 → 空集 fail-closed(继承 WP1.3)
 *   - CUSTOM = scope_config JSON 优先, 未配置回退 sys_user_org/sys_role_org 原始行(includeChildren 语义=WP1.2)
 *   - MY_TEAM = manager_id 链 ≤5 层(与旧 resolveTeam 同算法)
 * 本类不查开关/不做并集决策(那是 AuthorizationService 的编排职责)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScopeSpecResolver {

    /** 闭包/部门递归护栏(与 OrgScopeResolver 同值) */
    public static final int MAX_DEPTH = 20;
    /** team 链上限(与 OrgScopeResolver.TEAM_MAX_DEPTH 同值) */
    public static final int TEAM_MAX_DEPTH = 5;

    private static final int TYPE_REGION = 2;
    private static final int TYPE_OFFICE = 3;

    private final JdbcTemplate jdbc;

    /** 12 档逐行解析(单行不并集; 调用方对多行结果做 union) */
    public ScopeSpec resolve(SecurityUser user, GrantReader.GrantRow grant) {
        if (user == null || grant == null) return ScopeSpec.none();
        Long self = user.getUserId();
        String st = grant.getScopeType() == null ? "SELF" : grant.getScopeType();
        switch (st) {
            case "ALL": return ScopeSpec.all();
            case "SELF": return ScopeSpec.ofOwners(self == null ? List.of() : List.of(self));
            case "MY_DEPT": return ScopeSpec.ofDepts(employmentOrgs(user));
            case "MY_DEPT_SUB": return ScopeSpec.ofDepts(expandSubtrees(employmentOrgs(user)));
            case "MY_PRIMARY_DEPT": {
                Long p = user.getPrimaryEmploymentOrgId();
                return p == null ? ScopeSpec.none() : ScopeSpec.ofDepts(List.of(p));
            }
            case "MY_PRIMARY_DEPT_SUB": {
                Long p = user.getPrimaryEmploymentOrgId();
                return p == null ? ScopeSpec.none() : ScopeSpec.ofDepts(expandSubtrees(List.of(p)));
            }
            case "MY_OFFICE": return ScopeSpec.ofDepts(nearestAncestors(employmentOrgs(user), TYPE_OFFICE));
            case "MY_OFFICE_SUB": return ScopeSpec.ofDepts(expandSubtrees(nearestAncestors(employmentOrgs(user), TYPE_OFFICE)));
            case "MY_REGION": return ScopeSpec.ofDepts(nearestAncestors(employmentOrgs(user), TYPE_REGION));
            case "MY_REGION_SUB": return ScopeSpec.ofDepts(expandSubtrees(nearestAncestors(employmentOrgs(user), TYPE_REGION)));
            case "MY_TEAM": return ScopeSpec.ofOwners(teamOf(self));
            case "CUSTOM": return resolveCustom(user, grant);
            default:
                // 未知档 fail-closed: 空集(不放大不报错)
                log.warn("未知 scope_type={} subject={}/{} → fail-closed 空集", st, grant.getSubjectType(), grant.getSubjectId());
                return ScopeSpec.none();
        }
    }

    /** CUSTOM 授权组织行(orgId + includeChildren) */
    private record CustomOrgRef(Long orgId, boolean includeChildren) {}

    /**
     * 【P0-3 修复 2026-11-13】CUSTOM 解析:
     * ① 精确取当前授权行的 scope_config(subject+action 定位, 不再"按主体取首条"串档);
     * ② 严格按行 includeChildren: false 只加本组织(不展开子树), true 展开闭包——
     *    旧实现无条件 closure 把"指定部门(不含下级)"放大成整棵子树(越权放宽)。
     * ③ scope_config 缺失回退旧表原始行(用户级∪角色级, 逐行 includeChildren 同语义)。
     */
    private ScopeSpec resolveCustom(SecurityUser user, GrantReader.GrantRow grant) {
        Long self = user.getUserId();
        List<CustomOrgRef> refs = new ArrayList<>();
        String cfg = grant.getScopeConfig();
        if (cfg != null && !cfg.isBlank() && !"null".equals(cfg)) {
            refs = parseOrgRefsLoose(cfg);
        }
        if (refs.isEmpty()) {
            refs = legacyOrgRefs(user);
        }
        if (refs.isEmpty()) return self == null ? ScopeSpec.none() : ScopeSpec.ofOwners(List.of(self));

        Set<Long> deptIds = new LinkedHashSet<>();
        for (CustomOrgRef ref : refs) {
            if (ref.orgId() == null) continue;
            deptIds.add(ref.orgId());
            if (ref.includeChildren()) {
                deptIds.addAll(closure(ref.orgId()));
            }
        }
        return ScopeSpec.ofDepts(deptIds);
    }

    /**
     * scope_config 宽松解析: 对象数组 [{"orgId":1,"includeChildren":true},...] 优先(含标志),
     * 纯数字数组 [1,2] 按含子树(管理端默认=勾组织含下级, 与旧 UI 语义一致)。
     */
    private List<CustomOrgRef> parseOrgRefsLoose(String json) {
        List<CustomOrgRef> out = new ArrayList<>();
        java.util.regex.Matcher obj = java.util.regex.Pattern
                .compile("\\{\\s*\"orgId\"\\s*:\\s*(\\d+)\\s*(,\\s*\"includeChildren\"\\s*:\\s*(true|false|1|0))?\\s*\\}")
                .matcher(json);
        while (obj.find()) {
            out.add(new CustomOrgRef(Long.valueOf(obj.group(1)),
                    obj.group(3) == null || "true".equalsIgnoreCase(obj.group(3)) || "1".equals(obj.group(3))));
        }
        if (out.isEmpty()) {
            java.util.regex.Matcher num = java.util.regex.Pattern
                    .compile("(^|[\\[,\\s])(\\d+)(?=[\\],\\s]|$)").matcher(json);
            while (num.find()) {
                out.add(new CustomOrgRef(Long.valueOf(num.group(2)), true));
            }
        }
        return out;
    }

    /** 旧表 CUSTOM 授权组织原始行(用户级∪角色级, 逐行 includeChildren, 不预展开) */
    private List<CustomOrgRef> legacyOrgRefs(SecurityUser user) {
        List<CustomOrgRef> out = new ArrayList<>();
        try {
            jdbc.query("SELECT org_id, include_children FROM sys_user_org WHERE user_id = ?",
                    rs -> {
                        long orgId = rs.getLong(1);
                        int flag = rs.getInt(2);
                        out.add(new CustomOrgRef(orgId, flag == 1));
                    }, user.getUserId());
            if (out.isEmpty() && user.getRoleCodes() != null && !user.getRoleCodes().isEmpty()) {
                String ph = String.join(",", user.getRoleCodes().stream().map(r -> "?").toList());
                jdbc.query("SELECT DISTINCT org_id, include_children FROM sys_role_org WHERE role_code IN (" + ph + ")",
                        rs -> {
                            out.add(new CustomOrgRef(rs.getLong(1), rs.getInt(2) == 1));
                        }, user.getRoleCodes().toArray());
            }
        } catch (Exception e) {
            log.warn("CUSTOM 旧表行装载失败: {}", e.getMessage());
        }
        return out;
    }

    /** 全部有效任职组织(锁定决策#2) */
    private List<Long> employmentOrgs(SecurityUser user) {
        return user.getEmploymentOrgIds() != null ? user.getEmploymentOrgIds() : List.of();
    }

    /** 闭包展开(含自身): sys_org_closure descendant 查询; 表未就绪回退仅自身(fail-closed 不放大) */
    public Set<Long> expandSubtrees(Collection<Long> roots) {
        Set<Long> out = new LinkedHashSet<>();
        if (roots == null || roots.isEmpty()) return out;
        for (Long root : roots) {
            if (root == null) continue;
            out.add(root);
            try {
                out.addAll(jdbc.queryForList(
                        "SELECT descendant_id FROM sys_org_closure WHERE ancestor_id = ?", Long.class, root));
            } catch (Exception e) {
                log.warn("闭包查询失败(表未就绪?): {} → 仅含自身", e.getMessage());
            }
        }
        return out;
    }

    /** 任一组织到根的祖先链(闭包反向) */
    private List<Long> ancestorsOf(Long orgId) {
        try {
            return jdbc.queryForList(
                    "SELECT ancestor_id FROM sys_org_closure WHERE descendant_id = ? AND depth > 0 ORDER BY depth",
                    Long.class, orgId);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 最近指定类型祖先集合(任职组织逐个向上; 无锚的组织跳过=不放大) */
    private Set<Long> nearestAncestors(Collection<Long> orgIds, int deptType) {
        Set<Long> out = new LinkedHashSet<>();
        if (orgIds == null) return out;
        for (Long orgId : orgIds) {
            if (orgId == null) continue;
            // 最近祖先=沿闭包深度升序第一个 dept_type 命中者
            List<Long> ancestors = ancestorsOf(orgId);
            for (Long anc : ancestors) {
                Integer t = deptTypeOf(anc);
                if (t != null && t == deptType) {
                    out.add(anc);
                    break; // 每个任职组织只取最近一个锚
                }
            }
        }
        return out; // 无锚 → 空集(fail-closed, 继承 WP1.3)
    }

    private Integer deptTypeOf(Long deptId) {
        try {
            List<Integer> ts = jdbc.queryForList(
                    "SELECT dept_type FROM sys_dept WHERE id = ?", Integer.class, deptId);
            return ts.isEmpty() ? null : ts.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** manager 链团队(≤5 层, 与旧 resolveTeam 同算法; 经闭包/用户表直查) */
    public List<Long> teamOf(Long userId) {
        if (userId == null) return List.of();
        Set<Long> result = new LinkedHashSet<>();
        result.add(userId);
        List<Long> currentLevel = List.of(userId);
        try {
            for (int depth = 0; depth < TEAM_MAX_DEPTH && !currentLevel.isEmpty(); depth++) {
                String ph = String.join(",", currentLevel.stream().map(x -> "?").toList());
                Object[] args = currentLevel.toArray();
                List<Long> subs = jdbc.queryForList(
                        "SELECT id FROM sys_user WHERE manager_id IN (" + ph + ") AND deleted = 0 AND status = 1",
                        Long.class, args);
                List<Long> fresh = subs.stream().filter(result::add).toList();
                currentLevel = fresh;
            }
        } catch (Exception e) {
            log.warn("team 链解析失败: {}", e.getMessage());
        }
        return new ArrayList<>(result);
    }

    /** 单组织闭包(含自身) */
    public Set<Long> closure(Long rootId) {
        return expandSubtrees(List.of(rootId));
    }
}
