package com.aigicrm.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUser;
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

    /**
     * 【WP1.4 2026-11-13】上级/负责人开关仅对 view_like 动作生效（锁定决策#8）：
     * edit/delete/approve/share 等写动作解析时**不追加**开关集合——
     * "customer|delete=self + 开关开 → 仍不能删下属客户"是验收红线。
     * export 为只读语义（导出所见即列表所见）计入 view_like。
     */
    private static final java.util.Set<String> VIEW_LIKE_ACTIONS =
            java.util.Set.of("view", "stats_view", "export", "rank_view");

    /** 动作是否 view_like（null/空白动作按查看语义处理——存量调用多为查看路径） */
    public static boolean isViewLikeAction(String action) {
        return action == null || action.isBlank() || VIEW_LIKE_ACTIONS.contains(action);
    }

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysConfigService configService;
    private final com.aigicrm.module.system.mapper.SysRoleOrgMapper roleOrgMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
                    // 【G3 2026-09-21 安全默认收敛】配置行缺失/置空时默认 false(收窄):
                    // 原默认 true 属 fail-open——配置丢失时"上级可见下属链/负责人可见组织子树"静默开启。
                    // 现行为"显式开启才放宽"; 两键在用环境应显式配置 true。
                    cachedSuperior = configService.getBooleanValue(KEY_SUPERIOR_SEE_SUBORDINATE, false);
                    cachedLeader = configService.getBooleanValue(KEY_ORG_LEADER_SEE_ORG, false);
                    switchCacheExpire = now + SWITCH_CACHE_TTL_MS;
                }
            }
        }
        return new boolean[]{cachedSuperior, cachedLeader};
    }

    /** 【WP3 2026-11-13】开关旗标单一源公开访问(新内核 AuthorizationService 与旧解析器共用, 不复制实现) */
    public boolean[] switchFlags() {
        return readSwitchFlags();
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

    /** 按指定范围 code 解析可见 userIds（null=全部；含系统级开关）——权限体系重构: 每模块/动作独立范围 */
    public List<Long> resolveScope(SecurityUser user, String scope) {
        return resolveScope(user, scope, "view");
    }

    /** 【WP1.4】带动作版: 系统开关仅 view_like 动作追加（写动作不放宽） */
    public List<Long> resolveScope(SecurityUser user, String scope, String action) {
        if (user == null || scope == null) return List.of();
        List<Long> base = resolveSingle(user, scope);
        if (base == null) return null; // all
        return appendSystemLevelScopes(user, base, action);
    }

    // ==================== 统一数据访问层入口(批② 2026-08-10) ====================

    /**
     * 【权限矩阵统一消费入口 2026-09-19】按模块解析查看范围(OWNER 锚点)。
     * 优先级: role_perm_scope (module, view) 显式配置(USER>POSITION>ROLE, 精确>通配)
     * → 未配置回退角色 dataScope 默认(多角色并集+系统开关)。
     * 供以"负责人"维度过滤数据的模块(学分/提醒/审批/公海等)统一接入权限矩阵,
     * 使系统设置→权限管理勾选的范围对本模块立即生效(变更走 evictAll+permVersion 自愈)。
     *
     * @return 可见 userIds;null=全量不过滤(all);空=仅本人语义由调用方兜底
     */
    public List<Long> resolveModuleUsers(SecurityUser user, String module) {
        return resolveModuleUsers(user, module, "view");
    }

    /**
     * 【2026-11-05 用户拍板 B"四项激励独立数据范围"】指定动作版:
     * 四项激励统计/排行页(incentive:stats:view/rank:view)的可见人员改读 (incentive, stats_view)
     * 独立档位, 不再跟随 (statistics, view); 存量由 V2026.11.05.7 回填拷贝, 行为不变。
     */
    public List<Long> resolveModuleUsers(SecurityUser user, String module, String action) {
        // 【WP7 模块接入 2026-11-13】双轨读开关(统一入口守卫, 调用点/单测桩零迁移):
        // perm.read.<module>=new → 内核授予表(sys_perm_grant 经 AuthorizationService);
        // old(默认/内核未就绪/无槽码) → 本方法既有解析逐字节不变。
        // WithinGroup 消费方内部走本方法, 新轨的 all 已是"本集团"语义(决策#6)。
        com.aigicrm.common.auth.AuthorizationService auth =
                authServiceProvider == null ? null : authServiceProvider.getIfAvailable();
        if (auth != null && auth.isNewTrack(module)) {
            String code = auth.actionCodeOf(module, action);
            if (code != null) {
                var d = auth.scope(code);
                return d.isAll() ? null : d.getVisibleUserIds();
            }
        }
        ScopeRequest req = ScopeRequest.builder()
                .user(user)
                .module(module)
                .action(action)
                .anchor(ScopeRequest.AnchorType.OWNER)
                .build();
        ScopeDecision d = resolveScope(req);
        return d.isAll() ? null : d.getVisibleUserIds();
    }

    // ==================== 【WP7 模块接入 2026-11-13】双轨基建 ====================

    /** 内核门面(经 ObjectProvider 惰性获取, 打破 Resolver↔AuthorizationService 构造循环;
     *  测试直构/内核未就绪时为 null → 恒走旧轨) */
    private final org.springframework.beans.factory.ObjectProvider<com.aigicrm.common.auth.AuthorizationService> authServiceProvider;

    /**
     * 【GPT三轮 ACL-P1-001 修复 2026-09-22】全局表安全版。
     * resolveModuleUsers 的 null(=all 不过滤)语义只在租户拦截表上安全; sys_user/sys_log 等
     * 无 tenant_id 的全局表直接消费 null 会把"当前集团全部"放大为"全平台"。
     * 统一收口(与 ScopeService.visibleUsers 同口径):
     *   非 admin 的 all → 当前集团成员集(无集团归属=空集 fail-closed);
     *   admin 的 all → 保持 null(平台全量,平台管理员语义);
     *   非 null 集合 → 原样返回。
     */
    public List<Long> resolveModuleUsersWithinGroup(SecurityUser user, String module) {
        List<Long> visible = resolveModuleUsers(user, module);
        if (visible != null) return visible;
        if (user != null && user.hasRole("admin")) return null;
        Long g = GroupScopeUtil.currentGroupOrNull();
        if (g == null) return List.of();
        List<Long> inGroup = GroupScopeUtil.userIdsInGroup(jdbcTemplate, g);
        return inGroup.isEmpty() ? List.of() : inGroup;
    }

    /**
     * 统一解析: 列表/统计/单条判定共用。
     * OWNER 锚点 → visibleUserIds(负责人集合, null=全部); ORG 锚点 → visibleDeptIds(部门集合)。
     * 多角色并集; 任一 all → 全量; 系统开关仅 OWNER 锚点生效(ORG 由部门树天然覆盖)。
     * 【A6 2026-10-20 审计】ORG 锚点同时把 visibleDeptIds 展开为部门下在职用户集(deleted=0,status=1)
     * 写入 visibleUserIds——旧实现固定 List.of(), 使按负责人维度消费 ScopeDecision 的链路
     * (CustomerService.getCurrentScopeUserIds → 统计 applyCustomerScope/applyFileScope)在管理员
     * 配置 ORG 锚点后拿到空集: 无守卫时生成 IN () 语法错误 500, 有守卫时统计全 0。
     * 展开口径: 空部门集 → 空 userIds(不可见); all(deptIds==null) → null(不过滤); 其余 → 部门在职用户。
     * 消费方按部门过滤的列表路径(CustomerService.applyDataScope ORG 分支读 visibleDeptIds)不变。
     */
    public ScopeDecision resolveScope(ScopeRequest req) {
        if (req == null || req.getUser() == null) return ScopeDecision.none();
        SecurityUser user = req.getUser();
        ScopeRequest.AnchorType anchor = req.getAnchor() != null ? req.getAnchor() : ScopeRequest.AnchorType.OWNER;
        if (anchor == ScopeRequest.AnchorType.ORG) {
            List<Long> deptIds = null;
            List<String> scopes = scopeCodes(req);
            for (String scope : scopes) {
                List<Long> part = resolveDeptScope(user, scope);
                if (part == null) { deptIds = null; break; } // all
                if (deptIds == null) deptIds = new ArrayList<>(part);
                else deptIds.addAll(part);
            }
            ScopeDecision d = new ScopeDecision();
            d.setVisibleDeptIds(deptIds);
            d.setVisibleUserIds(deptIds == null ? null : usersInDepts(new LinkedHashSet<>(deptIds)));
            return d;
        }
        // OWNER 锚点
        List<Long> visible = null;
        List<String> scopes = scopeCodes(req);
        for (String scope : scopes) {
            // 【WP1.4】动作贯通: 写动作不追加上级/负责人开关
            List<Long> part = resolveScope(user, scope, req.getAction());
            if (part == null) { visible = null; break; }
            if (visible == null) visible = new ArrayList<>(part);
            else visible.addAll(part);
        }
        ScopeDecision d = new ScopeDecision();
        d.setVisibleUserIds(visible);
        d.setVisibleDeptIds(visible == null ? null : List.of());
        return d;
    }

    /** 单条访问判定: 与列表解析共用同一链路(根治 P1-05 口径分裂) */
    public boolean canAccess(ScopeRequest req) {
        if (req == null || req.getUser() == null) return false;
        ScopeDecision d = resolveScope(req);
        if (d == null) return false;
        if (d.isAll()) return true;
        ScopeRequest.AnchorType anchor = req.getAnchor() != null ? req.getAnchor() : ScopeRequest.AnchorType.OWNER;
        if (anchor == ScopeRequest.AnchorType.ORG) {
            return d.canAccessDept(req.getTargetDeptId());
        }
        // OWNER: 本人恒可(兼容现状)
        Long ownerId = req.getTargetOwnerId();
        if (ownerId != null && ownerId.equals(req.getUser().getUserId())) return true;
        return d.canAccessOwner(ownerId);
    }

    /**
     * 范围 code 列表: 批② 直接取 SecurityUser.dataScopes(配置层批③替换为 scopeConfigs)。
     *
     * 【安全 2026-08-11 P2 修复】接入 role_perm_scope.scope_level——优先读登录时装载的
     * scopeConfigs(内存, 无热路径 DB): 匹配 (module, action) 的显式配置(精确优先于通配 `*`),
     * 按 USER>POSITION>ROLE 装载顺序取首个匹配; 无匹配时回退 dataScopes/dataScope(角色 data_scope)。
     * 使权限范围管理器(PermScopeManager)配置的范围真正参与运行时数据范围解析。
     */
    private List<String> scopeCodes(ScopeRequest req) {
        SecurityUser user = req.getUser();
        List<String> configured = configuredScopeCodes(req);
        if (!configured.isEmpty()) {
            return configured;
        }
        if (user.getDataScopes() != null && user.getDataScopes().size() > 1) {
            return user.getDataScopes();
        }
        return List.of(user.getDataScope() != null ? user.getDataScope() : "self");
    }

    /**
     * 从内存 scopeConfigs 匹配 (module,action) 的范围 code 列表。
     * 优先级: 主体层级 USER>POSITION>ROLE(loadScopeConfigs 装载序即隐含, 用户显式配置覆盖角色)——
     * 取第一个有匹配的 assigneeType; 该主体内 精确(module,action)>通配(* *)。
     * 注: 之前实现按"精确>通配"全局排序, 会把 USER 级 `* *` 通配压到 ROLE 级精确之下,
     * 违反"用户级覆盖角色级"设计(如 NM100: USER * * custom vs ROLE customer view region 应取 custom)。
     */
    private List<String> configuredScopeCodes(ScopeRequest req) {
        SecurityUser user = req.getUser();
        if (user == null || user.getScopeConfigs() == null || user.getScopeConfigs().isEmpty()) {
            return List.of();
        }
        String module = req.getModule() != null ? req.getModule() : "";
        String action = req.getAction() != null ? req.getAction() : "";
        String seenType = null;
        List<String> exact = new ArrayList<>();
        List<String> wildcard = new ArrayList<>();
        for (com.aigicrm.common.scope.ScopeConfig c : user.getScopeConfigs()) {
            String cMod = c.getModule() != null ? c.getModule() : "";
            String cAct = c.getAction() != null ? c.getAction() : "";
            boolean moduleMatch = cMod.equals(module) || "*".equals(cMod);
            boolean actionMatch = cAct.equals(action) || "*".equals(cAct);
            if (!moduleMatch || !actionMatch) continue;
            if (c.getScopeLevel() == null || c.getScopeLevel().isBlank()) continue;
            String type = c.getAssigneeType() != null ? c.getAssigneeType() : "ROLE";
            if (seenType == null) {
                seenType = type; // 首个有匹配的主体(USER>POSITION>ROLE 装载序)
            } else if (!seenType.equals(type)) {
                continue; // 更优先主体已有匹配, 忽略低优先主体的配置
            }
            if (cMod.equals(module) && cAct.equals(action)) {
                exact.add(c.getScopeLevel());
            } else {
                wildcard.add(c.getScopeLevel());
            }
        }
        if (seenType == null) return List.of();
        // 该主体内 精确匹配优先(如 dashboard view 覆盖 * *); 否则通配
        if (!exact.isEmpty()) return exact;
        return wildcard;
    }

    /**
     * ORG 锚点: 按组织维度解析可见部门集合(null=全部; 空=不可见; 非空=可见部门)。
     * 直接部门树解析, 不依赖部门内是否有用户("部门无人则客户不可见"的坑)。
     */
    public List<Long> resolveDeptScope(SecurityUser user, String scope) {
        if (user == null || scope == null) return List.of();
        Long deptId = user.getDeptId();
        switch (scope) {
            case "all": return null;
            case "custom": {
                // 【WP1.2 2026-11-13】严格按原始行 includeChildren: true 才展开子树, false 仅本组织。
                // 旧实现消费预展开的 managedOrgIds 平铺表并对每个 id 无条件再展开 ⇒ 0 标志行越权放宽到子树。
                java.util.List<com.aigicrm.security.ManagedOrgRef> refs = user.getManagedOrgRefs();
                if (refs == null || refs.isEmpty()) {
                    refs = loadRoleOrgRefs(user); // P1-06 回退(与 OWNER 口径一致)
                }
                if (refs == null || refs.isEmpty()) return List.of();
                Set<Long> deptIds = new LinkedHashSet<>();
                for (com.aigicrm.security.ManagedOrgRef ref : refs) {
                    if (ref == null || ref.getOrgId() == null) continue;
                    if (ref.expandChildren()) collectSubtree(ref.getOrgId(), deptIds);
                    else deptIds.add(ref.getOrgId());
                }
                return new ArrayList<>(deptIds);
            }
            case "region": {
                // 【WP1.3 2026-11-13 fail-closed】无 dept_type=region 祖先 → 不可见(空集),
                // 不再回退 findRoot(把总部用户放大到全集团)。写入面已拦截无锚配置(400)。
                Long anchor = deptId == null ? null : findAncestorOfType(deptId, TYPE_REGION);
                if (anchor == null) return List.of();
                return collectSubtreeList(anchor);
            }
            case "office": {
                // 【WP1.3 fail-closed】同 region: 无办事处祖先 → 不可见, 不再退化本部门子树
                Long anchor = deptId == null ? null : findAncestorOfType(deptId, TYPE_OFFICE);
                if (anchor == null) return List.of();
                return collectSubtreeList(anchor);
            }
            case "dept":
            case "dept_and_children":
            case "org_and_children": {
                if (deptId == null) return List.of();
                Set<Long> deptIds = new LinkedHashSet<>();
                collectSubtree(deptId, deptIds);
                if ("org_and_children".equals(scope) && user.getManagedOrgIds() != null) {
                    for (Long orgId : user.getManagedOrgIds()) {
                        if (orgId != null) collectSubtree(orgId, deptIds);
                    }
                }
                return new ArrayList<>(deptIds);
            }
            case "org": {
                return deptId == null ? List.of() : List.of(deptId);
            }
            case "team": {
                if (deptId == null) return List.of();
                List<Long> teamUsers = resolveTeam(user.getUserId());
                Set<Long> deptIds = new LinkedHashSet<>();
                if (!teamUsers.isEmpty()) {
                    List<SysUser> users = userMapper.selectByIds(teamUsers);
                    for (SysUser u : users) {
                        if (u.getDeptId() != null) deptIds.add(u.getDeptId());
                    }
                }
                if (!deptIds.contains(deptId)) deptIds.add(deptId); // 恒含本人部门
                return new ArrayList<>(deptIds);
            }
            case "self":
            default: return deptId == null ? List.of() : List.of(deptId);
        }
    }

    private List<Long> collectSubtreeList(Long rootId) {
        Set<Long> acc = new LinkedHashSet<>();
        collectSubtree(rootId, acc);
        return new ArrayList<>(acc);
    }

    /**
     * 【系统级默认规则 2026-08-07】在数据范围基础上并集追加系统开关集合：
     * <ul>
     *   <li>开关1 KEY_SUPERIOR_SEE_SUBORDINATE（默认开）：直接上级默认可见递归下属链（≤5层，复用 resolveTeam）。</li>
     *   <li>开关2 KEY_ORG_LEADER_SEE_ORG（默认开）：组织负责人（任意层级 leader_id=本人）默认可见本组织整棵子树。</li>
     * </ul>
     * base 为 null（全量不过滤）时原样返回；供旁路（激励/客户统计的本地分支）显式调用以统一生效。
     * 【WP1.4 2026-11-13】本重载为存量查看路径兼容入口（按 view_like 处理）；带动作的解析
     * 走 {@link #appendSystemLevelScopes(SecurityUser, Collection, String)}——写动作不追加开关。
     */
    public List<Long> appendSystemLevelScopes(SecurityUser user, Collection<Long> base) {
        return appendSystemLevelScopes(user, base, "view");
    }

    /**
     * 【WP1.4 2026-11-13】带动作语义的系统开关追加：仅 view_like 动作生效（锁定决策#8）。
     * 写动作(edit/delete/approve/share...)恒不追加——开关只放宽"看"，不放宽"改"。
     */
    public List<Long> appendSystemLevelScopes(SecurityUser user, Collection<Long> base, String action) {
        if (base == null) return null;
        if (user == null || user.getUserId() == null) return new ArrayList<>(base);
        if (configService == null) return new ArrayList<>(base); // 防御（测试匿名子类）
        if (!isViewLikeAction(action)) return new ArrayList<>(base); // 写动作 fail-closed: 开关不生效

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
            // 【修复 2026-08-10 P2-03】dept_and_children 是 V7 注释允许值, 此前落入 default 静默降级 self;
            // 与 applyDataScope 的 dept_and_children 分支同语义(本部门及子部门)
            case "dept_and_children": return resolveDeptTree(userId, deptId);
            case "team": return resolveTeam(userId);
            // 旧码兼容（DataScopeType @Deprecated）：org_and_children=本组织及下级 → 部门树含子级；
            // org=仅本部门（不含子级）。组织管理员升级路径仍产出 org_and_children，必须可解析。
            case "org_and_children": return resolveOrgAndChildren(user, userId, deptId);
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
            List<SysUser> users = userMapper.selectByIds(u);
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
        // 【WP1.3 2026-11-13 fail-closed】无对应 dept_type 祖先 → 空集(本解析器契约: 空=仅本人),
        // 不再回退(office 退化 dept 树 / region 沿根放大全集团)。验收: 总部用户选"本大区"→0 条他人数据。
        if (anchor == null) return List.of(userId);
        Set<Long> deptIds = collectSubtree(anchor, null);
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    /** 【WP1.2】custom 解析: 严格按原始行 includeChildren(true 展开子树/false 仅本组织) */
    private List<Long> resolveCustom(SecurityUser user, Long userId) {
        java.util.List<com.aigicrm.security.ManagedOrgRef> refs = user.getManagedOrgRefs();
        if (refs == null || refs.isEmpty()) {
            // 【修复 2026-08-10 P1-06】新模型 role_perm_scope 配 custom 时 refs 未装载
            // (装载仅在 dataScope=custom / override=custom 时发生)——按用户角色现查 sys_role_org 回退,
            // 否则自定义范围对新模型配置静默退化为仅本人
            refs = loadRoleOrgRefs(user);
            if (refs == null || refs.isEmpty()) return List.of(userId);
        }
        Set<Long> deptIds = new LinkedHashSet<>();
        for (com.aigicrm.security.ManagedOrgRef ref : refs) {
            if (ref == null || ref.getOrgId() == null) continue; // 脏数据防御
            if (ref.expandChildren()) {
                deptIds.addAll(collectSubtree(ref.getOrgId(), null));
            } else {
                deptIds.add(ref.getOrgId());
            }
        }
        List<Long> u = usersInDepts(deptIds);
        if (!u.contains(userId)) u.add(userId);
        return u;
    }

    /** 【WP1.2】按用户全部角色现查 sys_role_org 授权组织**原始行**(不预展开)——custom 范围装载回退 */
    private java.util.List<com.aigicrm.security.ManagedOrgRef> loadRoleOrgRefs(SecurityUser user) {
        if (user.getUserId() == null) return null;
        java.util.List<com.aigicrm.module.system.entity.SysRoleOrg> rows = roleOrgMapper.selectByUserId(user.getUserId());
        if (rows == null || rows.isEmpty()) return null;
        java.util.List<com.aigicrm.security.ManagedOrgRef> refs = new ArrayList<>();
        for (com.aigicrm.module.system.entity.SysRoleOrg ro : rows) {
            if (ro.getOrgId() == null) continue;
            refs.add(new com.aigicrm.security.ManagedOrgRef(ro.getOrgId(), ro.getIncludeChildren()));
        }
        return refs;
    }

    /** org_and_children = 本人部门树 ∪ 组织管理员/自定义授权组织子树(与列表 applyDataScope 同口径) */
    private List<Long> resolveOrgAndChildren(SecurityUser user, Long userId, Long deptId) {
        Set<Long> deptIds = new LinkedHashSet<>();
        if (deptId != null) collectSubtree(deptId, deptIds);
        if (user.getManagedOrgIds() != null) {
            for (Long orgId : user.getManagedOrgIds()) {
                if (orgId == null) continue; // 脏数据防御
                collectSubtree(orgId, deptIds);
            }
        }
        if (deptIds.isEmpty()) return List.of(userId);
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

    // 【WP1.3 2026-11-13】findRoot 已随 region/office fail-closed 一并退役（唯一调用方是旧回退路径）

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
