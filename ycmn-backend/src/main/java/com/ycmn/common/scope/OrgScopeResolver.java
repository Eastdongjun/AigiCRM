package com.ycmn.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import com.ycmn.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 六级数据范围统一解析器（设计文档 §4.2）
 * 输出可见用户集合 U：null=本集团不过滤(all)；空=仅本人；其余=可见 userIds（恒含本人）
 */
@Component
@RequiredArgsConstructor
public class OrgScopeResolver {

    /** 团队递归上限（manager_id 链） */
    public static final int TEAM_MAX_DEPTH = 5;

    /** 大区/办事处 deptType 标记值（与组织管理页字典一致） */
    public static final int TYPE_COMPANY = 1;
    public static final int TYPE_REGION = 2;
    public static final int TYPE_OFFICE = 3;
    public static final int TYPE_DEPT = 4;

    /** 系统级开关配置键：直接上级默认可见递归下属链 */
    public static final String KEY_SUPERIOR_SEE_SUBORDINATE = "perm.superior.see.subordinate";
    /** 系统级开关配置键：组织负责人默认可见本组织整棵子树 */
    public static final String KEY_ORG_LEADER_SEE_ORG = "perm.org.leader.see.org";

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysConfigService configService;

    /** 开关配置短 TTL 缓存：避免每次数据范围解析都查库（多页面累计卡顿） */
    private static final long SWITCH_CACHE_TTL_MS = 30_000L;
    private volatile long switchCacheExpire = 0L;
    private volatile boolean cachedSuperior = true;
    private volatile boolean cachedLeader = true;

    /** 读取两个开关（30s 缓存；切换开关最长 30s 生效，与"即时生效"权衡取短 TTL） */
    private boolean[] readSwitchFlags() {
        long now = System.currentTimeMillis();
        if (now >= switchCacheExpire) {
            synchronized (this) {
                if (now >= switchCacheExpire) {
                    cachedSuperior = configService.getBooleanValue(KEY_SUPERIOR_SEE_SUBORDINATE, true);
                    cachedLeader = configService.getBooleanValue(KEY_ORG_LEADER_SEE_ORG, true);
                    switchCacheExpire = now + SWITCH_CACHE_TTL_MS;
                }
            }
        }
        return new boolean[]{cachedSuperior, cachedLeader};
    }

    /** 解析当前用户可见 userIds（恒含本人；all 返回 null） */
    public List<Long> resolve(SecurityUser user) {
        if (user == null) return List.of();
        List<String> scopes = user.getDataScopes();
        // 多角色并集（设计 §4.2）：所有角色各自的 code 逐解析后并集合并；任一 all → null 短路
        List<Long> base;
        if (scopes != null && scopes.size() > 1) {
            base = resolveUnion(user, scopes);
        } else {
            base = resolveSingle(user, user.getDataScope() != null ? user.getDataScope() : "self");
        }
        if (base == null) return null; // all：不过滤，开关不生效
        return appendSystemLevelScopes(user, base);
    }

    /**
     * 【系统级默认规则 2026-08-07】在数据范围基础上并集追加系统开关集合：
     * <ul>
     *   <li>开关1 KEY_SUPERIOR_SEE_SUBORDINATE（默认开）：直接上级默认可见递归下属链（≤5层，复用 resolveTeam）。</li>
     *   <li>开关2 KEY_ORG_LEADER_SEE_ORG（默认开）：组织负责人（任意层级 leader_id=本人）默认可见本组织整棵子树。</li>
     * </ul>
     * base 为 null（全量不过滤）时原样返回；供旁路（激励/客户统计的本地分支）显式调用以统一生效。
     */
    public List<Long> appendSystemLevelScopes(SecurityUser user, Collection<Long> base) {
        if (base == null) return null;
        if (user == null || user.getUserId() == null) return new ArrayList<>(base);
        if (configService == null) return new ArrayList<>(base); // 防御（测试匿名子类）

        boolean[] switches = readSwitchFlags();
        boolean superiorOn = switches[0];
        boolean leaderOn = switches[1];
        if (!superiorOn && !leaderOn) return new ArrayList<>(base);

        Set<Long> merged = new LinkedHashSet<>(base);
        Long userId = user.getUserId();
        // 开关1：team 范围已含整条链，守卫避免重复解析
        if (superiorOn && !isTeamScoped(user)) merged.addAll(resolveTeam(userId));
        // 开关2：组织负责人子树
        if (leaderOn) merged.addAll(resolveLedOrgs(userId));
        return new ArrayList<>(merged);
    }

    /** 用户是否已按 team 范围解析（此时开关1 的集合已含在 base，无需重复查询） */
    private boolean isTeamScoped(SecurityUser user) {
        return "team".equals(user.getDataScope())
                || (user.getDataScopes() != null && user.getDataScopes().contains("team"));
    }

    /** 本人负责的组织（任意层级 leader_id=本人）整棵子树全部用户 */
    private List<Long> resolveLedOrgs(Long userId) {
        List<Long> roots = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getLeaderId, userId)
                        .eq(SysDept::getStatus, 1))
                .stream().map(SysDept::getId).toList();
        Set<Long> deptIds = new LinkedHashSet<>();
        for (Long root : roots) {
            if (root == null) continue; // 脏数据防御
            collectSubtree(root, deptIds); // 环保护 + Set 天然去重
        }
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    /** 多角色并集合并（逐 code 各自解析；任一角色为 all → 本集团不过滤） */
    private List<Long> resolveUnion(SecurityUser user, List<String> scopes) {
        Set<Long> merged = new LinkedHashSet<>();
        for (String code : scopes) {
            if (code == null) continue;
            List<Long> part = resolveSingle(user, code);
            if (part == null) return null; // all：短路
            merged.addAll(part);
        }
        if (user.getUserId() != null) merged.add(user.getUserId()); // 恒含本人
        return new ArrayList<>(merged);
    }

    /** 单 code 解析（resolve 与并集共用） */
    private List<Long> resolveSingle(SecurityUser user, String scope) {
        Long userId = user.getUserId();
        Long deptId = user.getDeptId() != null ? user.getDeptId() : user.getOfficeId();

        switch (scope) {
            case "all": return null;
            case "custom": return resolveCustom(user, userId);
            case "region": return resolveByAncestorType(userId, deptId, TYPE_REGION);
            case "office": return resolveByAncestorType(userId, deptId, TYPE_OFFICE);
            case "dept": return resolveDeptTree(userId, deptId);
            case "team": return resolveTeam(userId);
            // 旧码兼容（DataScopeType @Deprecated）：org_and_children=本组织及下级 → 部门树含子级；
            // org=仅本部门（不含子级）。组织管理员升级路径仍产出 org_and_children，必须可解析。
            case "org_and_children": return resolveDeptTree(userId, deptId);
            case "org": return resolveDeptOnly(userId, deptId);
            case "self":
            default: return List.of(userId);
        }
    }

    /** 仅本部门（不含子级）—— 旧码 org 语义 */
    private List<Long> resolveDeptOnly(Long userId, Long deptId) {
        if (deptId == null) return List.of(userId);
        List<Long> u = usersInDepts(new LinkedHashSet<>(List.of(deptId)));
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    /** 可见部门集合（scope 接口用）：U 中用户所属 deptId 去重 + 本人部门 */
    public List<Long> resolveVisibleDeptIds(SecurityUser user) {
        List<Long> u = resolve(user);
        if (u == null) return null; // all：不过滤
        Set<Long> deptIds = new LinkedHashSet<>();
        if (!u.isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(u);
            for (SysUser su : users) {
                if (su.getDeptId() != null) deptIds.add(su.getDeptId());
            }
        }
        if (user.getDeptId() != null) deptIds.add(user.getDeptId());
        return new ArrayList<>(deptIds);
    }

    // ---- 各级别实现 ----

    private List<Long> resolveTeam(Long userId) {
        Set<Long> result = new LinkedHashSet<>();
        result.add(userId);
        List<Long> currentLevel = List.of(userId);
        for (int depth = 0; depth < TEAM_MAX_DEPTH && !currentLevel.isEmpty(); depth++) {
            List<Long> subs = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getManagerId, currentLevel)
                            .eq(SysUser::getDeleted, 0)
                            .eq(SysUser::getStatus, 1))
                    .stream().map(SysUser::getId).toList();
            List<Long> fresh = subs.stream().filter(id -> result.add(id)).toList();
            currentLevel = fresh;
        }
        return new ArrayList<>(result);
    }

    private List<Long> resolveDeptTree(Long userId, Long deptId) {
        if (deptId == null) return List.of(userId);
        Set<Long> deptIds = collectSubtree(deptId, null);
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private List<Long> resolveByAncestorType(Long userId, Long deptId, int targetType) {
        if (deptId == null) return List.of(userId);
        Long anchor = findAncestorOfType(deptId, targetType);
        if (anchor == null) {
            // 无标记：office 退化 dept 树；region 退化集团根（沿祖先链到 parent 为 null 的根）
            return targetType == TYPE_OFFICE
                    ? resolveDeptTree(userId, deptId)
                    : resolveDeptTree(userId, findRoot(deptId));
        }
        Set<Long> deptIds = collectSubtree(anchor, null);
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private List<Long> resolveCustom(SecurityUser user, Long userId) {
        List<Long> managed = user.getManagedOrgIds();
        if (managed == null || managed.isEmpty()) return List.of(userId);
        Set<Long> deptIds = new LinkedHashSet<>();
        for (Long orgId : managed) {
            if (orgId == null) continue; // 脏数据防御
            deptIds.addAll(collectSubtree(orgId, null));
        }
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    private Set<Long> collectSubtree(Long rootId, Set<Long> acc) {
        if (acc == null) acc = new LinkedHashSet<>();
        acc.add(rootId);
        List<Long> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getParentId, rootId)
                        .eq(SysDept::getStatus, 1))
                .stream().map(SysDept::getId).toList();
        // 环保护：已收集过的节点不再递归，防止 parent_id 环数据死循环
        for (Long c : children) {
            if (acc.add(c)) collectSubtree(c, acc);
        }
        return acc;
    }

    private Long findAncestorOfType(Long deptId, int type) {
        Long cur = deptId;
        for (int i = 0; i < 20 && cur != null; i++) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null) return null;
            if (d.getDeptType() != null && d.getDeptType() == type) return d.getId();
            cur = d.getParentId();
        }
        return null;
    }

    private Long findRoot(Long deptId) {
        Long cur = deptId;
        for (int i = 0; i < 20 && cur != null; i++) {
            SysDept d = deptMapper.selectById(cur);
            if (d == null || d.getParentId() == null) return cur;
            cur = d.getParentId();
        }
        return cur;
    }

    private List<Long> usersInDepts(Set<Long> deptIds) {
        if (deptIds.isEmpty()) return new ArrayList<>();
        // 注意：Stream.toList() 返回不可变列表，调用方需要 add(本人)，必须包一层可变 ArrayList
        return new ArrayList<>(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getDeptId, deptIds)
                        .eq(SysUser::getDeleted, 0)
                        .eq(SysUser::getStatus, 1))
                .stream().map(SysUser::getId).toList());
    }
}
