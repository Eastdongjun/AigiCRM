package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFileGrant;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileGrantMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.entity.SysPosition;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 【2026-11-17 阶段二】文件统一授权引擎（biz_file_grant）。
 *
 * <p>与旧表 biz_folder_type_default 灰度双读：旧表仍是“类型默认授权”，本引擎是
 * “空间/文件夹/文件 + ALLOW/DENY + 继承 + 过期 + 组织范围”的通用例外层。</p>
 *
 * <p>判定顺序：DENY 优先于 ALLOW，也优先于上传者本人/组织矩阵；仅管理员不受 DENY 影响。
 * 未命中返回 ABSTAIN，调用方继续走旧判定链，保证存量行为不变。</p>
 *
 * <p>【A2 2026-11-18】主链切换模式开关（用户拍板 #4：FILE_GRANT_MODE 环境变量+重启，默认 legacy）：
 * {@code file.grant.mode} = legacy（本引擎整体旁路，decide* 恒 ABSTAIN，完全旧链）/
 * dual（引擎先判，ABSTAIN 回落旧表）/ new（引擎先判，ABSTAIN 不回落旧表——由
 * FolderPermissionService 按 {@link #legacyFallback()} 跳过旧表读取；owner/公共/私人基础规则仍在）。
 * 开关收口在 decide* 入口，FolderPermissionService 全部 8 处调用点自动统一。
 * Spring 上下文恒注入默认值 legacy；字段 null（仅直构单测）按 dual 处理以保持既有引擎测试语义。</p>
 *
 * <p>【A2】SPACE 匹配改读 space_type（旧 scope_level 只是相对数据范围，不得参与空间匹配）；
 * scope_mode 四态：ANY 不限 / RELATIVE 按查看人（同旧 scopeCovers 口径）/ EXACT 精确 / SUBTREE 闭包子树。</p>
 */
@Service
@RequiredArgsConstructor
public class FileGrantService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_ALLOW = "ALLOW";
    private static final String LIT_POSITION = "POSITION";


    public enum Decision { ALLOW, DENY, ABSTAIN }

    public static final String OBJ_SPACE = "SPACE";
    public static final String OBJ_FOLDER = "FOLDER";
    public static final String OBJ_FILE = "FILE";

    public static final String MODE_LEGACY = "legacy";
    public static final String MODE_DUAL = "dual";
    public static final String MODE_NEW = "new";
    public static final String SCOPE_ANY = "ANY";
    public static final String SCOPE_RELATIVE = "RELATIVE";
    public static final String SCOPE_EXACT = "EXACT";
    public static final String SCOPE_SUBTREE = "SUBTREE";
    /** SPACE 空间类型词表（§2.7；space_type=NULL=任意空间，仅存量迁移行） */
    public static final java.util.Set<String> SPACE_TYPES =
            java.util.Set.of("public", "private", "incentive", "fabushi");
    /** RELATIVE 档位词表（旧表 scope_level 数据范围） */
    public static final java.util.Set<String> RELATIVE_LEVELS =
            java.util.Set.of("self", "dept", "office", "region");

    private final BizFileGrantMapper grantMapper;
    private final SysRoleMapper roleMapper;

    /** 【A2 拍板 #4】file.grant.mode=legacy|dual|new（FILE_GRANT_MODE env + 重启生效）。 */
    @org.springframework.beans.factory.annotation.Value("${file.grant.mode:legacy}")
    private String grantMode;

    /** 目标对象存在性校验（P0 纵深防御）；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BizFileMapper fileMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BizFileFolderMapper folderMapper;
    /** 授权变更审计；单测可缺省。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.security.SecurityAuditService securityAudit;
    /** 组织闭包（A1 子树）；单测可缺省，缺省退精确匹配。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    /** RELATIVE 档位解析（同旧 scopeCovers 口径）；单测可缺省，缺省 fail-closed。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aigicrm.common.scope.OrgScopeResolver scopeResolver;

    /** 文件判定。 */
    public Decision decideFile(BizFile file, String action, Long userId) {
        if (file == null) return Decision.ABSTAIN;
        return decideFiles(List.of(file), action, userId).getOrDefault(file.getId(), Decision.ABSTAIN);
    }

    /** 【A2】当前模式（Spring 恒注入；null 仅直构单测，按 dual=引擎开）。 */
    public String mode() {
        return grantMode == null || grantMode.isBlank() ? MODE_DUAL : grantMode.trim().toLowerCase(Locale.ROOT);
    }

    /** 【A2】mode=legacy：本引擎整体旁路（decide* 恒 ABSTAIN）。 */
    public boolean engineBypassed() {
        return MODE_LEGACY.equals(mode());
    }

    /** 【A2】mode=new：调用方不再回落旧表 biz_folder_type_default（owner/公共/私人基础规则不受影响）。 */
    public boolean legacyFallback() {
        return !MODE_NEW.equals(mode());
    }

    /** 【A1 批量预载】一次查 grants，按 org/folder 缓存后逐文件判定。 */
    public Map<Long, Decision> decideFiles(Collection<BizFile> files, String action, Long userId) {
        Map<Long, Decision> out = new HashMap<>();
        if (files == null || files.isEmpty()) return out;
        boolean admin = SecurityUtils.isAdmin();
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (userId == null || admin || user == null || engineBypassed()) {
            for (BizFile f : files) out.put(f.getId(), Decision.ABSTAIN);
            return out;
        }
        List<BizFileGrant> grants = load(user, action);
        Map<Long, Set<Long>> orgCache = new HashMap<>();
        Map<Long, Set<Long>> folderCache = new HashMap<>();
        for (BizFile file : files) {
            out.put(file.getId(), decideFile(file, grants, orgCache, folderCache));
        }
        return out;
    }

    private Decision decideFile(BizFile file, List<BizFileGrant> grants,
                                Map<Long, Set<Long>> orgCache, Map<Long, Set<Long>> folderCache) {
        Set<Long> orgs = orgCache.computeIfAbsent(file.getOrgId(), this::orgAncestors);
        Set<Long> folders = file.getFolderId() == null ? Set.of()
                : folderCache.computeIfAbsent(file.getFolderId(), this::folderAncestors);
        Decision decision = Decision.ABSTAIN;
        for (BizFileGrant g : grants) {
            if (!coversFile(g, file, orgs, folders)) continue;
            if ("DENY".equalsIgnoreCase(g.getEffect())) return Decision.DENY;
            if (LIT_ALLOW.equalsIgnoreCase(g.getEffect())) decision = Decision.ALLOW;
        }
        return decision;
    }

    // ==================== 【Batch-4 #1】多动作批量判定（按钮视图收口） ====================

    /**
     * 多动作批量文件判定（按钮视图专用）：一次 load，每 (grant,file) 的对象匹配只算一次，
     * 按 actionsFor 归并到各请求动作。返回 Map&lt;fileId, Map&lt;action, Decision&gt;（仅请求的动作）。
     * DENY 优先于 ALLOW（与 decideFiles 同序）；mode=legacy 恒 ABSTAIN（调用方按钮走旧链）。
     */
    public Map<Long, Map<String, Decision>> decideFilesMulti(Collection<BizFile> files,
                                                             Collection<String> actions, Long userId) {
        Map<Long, Map<String, Decision>> out = new HashMap<>();
        if (files == null || files.isEmpty() || actions == null || actions.isEmpty()) return out;
        if (userId == null || SecurityUtils.isAdmin() || SecurityUtils.getCurrentUser() == null
                || engineBypassed()) {
            for (BizFile f : files) out.put(f.getId(), Map.of());
            return out;
        }
        // load 一次：动作集 = 所有请求动作的归并词并集
        Set<String> loadActions = loadActions(actions);
        SecurityUser user = SecurityUtils.getCurrentUser();
        List<BizFileGrant> grants = loadGrants(user, loadActions);
        Map<Long, Set<Long>> orgCache = new HashMap<>();
        Map<Long, Set<Long>> folderCache = new HashMap<>();
        for (BizFile file : files) {
            Set<Long> orgs = orgCache.computeIfAbsent(file.getOrgId(), this::orgAncestors);
            Set<Long> folders = file.getFolderId() == null ? Set.of()
                    : folderCache.computeIfAbsent(file.getFolderId(), this::folderAncestors);
            out.put(file.getId(), decideActionsForFile(file, grants, orgs, folders, actions));
        }
        return out;
    }

    /**
     * 多动作批量夹判定（按钮视图专用）：夹行按钮（对夹内文件的文件动作）与夹自身动作（rename/delete）共用。
     * SPACE 授权对夹按 folderType 匹配；FOLDER 授权按 coversFolder。与 decideFolder 同序。
     */
    public Map<Long, Map<String, Decision>> decideFoldersMulti(Collection<BizFileFolder> folders,
                                                               Collection<String> actions, Long userId) {
        Map<Long, Map<String, Decision>> out = new HashMap<>();
        if (folders == null || folders.isEmpty() || actions == null || actions.isEmpty()) return out;
        if (userId == null || SecurityUtils.isAdmin() || SecurityUtils.getCurrentUser() == null
                || engineBypassed()) {
            for (BizFileFolder f : folders) out.put(f.getId(), Map.of());
            return out;
        }
        Set<String> loadActions = loadActions(actions);
        SecurityUser user = SecurityUtils.getCurrentUser();
        List<BizFileGrant> grants = loadGrants(user, loadActions);
        Map<Long, Set<Long>> orgCache = new HashMap<>();
        Map<Long, Set<Long>> folderCache = new HashMap<>();
        for (BizFileFolder folder : folders) {
            Set<Long> orgs = orgCache.computeIfAbsent(folder.getOrgId(), this::orgAncestors);
            Set<Long> ancestors = folderAncestors(folder.getId());
            out.put(folder.getId(), decideActionsForFolder(folder, grants, orgs, ancestors, actions));
        }
        return out;
    }

    private Set<String> loadActions(Collection<String> actions) {
        Set<String> loadActions = new LinkedHashSet<>();
        for (String a : actions) loadActions.addAll(actionsFor(a));
        return loadActions;
    }

    private Map<String, Decision> decideActionsForFile(BizFile file, List<BizFileGrant> grants,
                                                       Set<Long> orgs, Set<Long> folders, Collection<String> actions) {
        // 【parity 修复】归并语义与 decideFiles 一致：grant.action ∈ actionsFor(a) 即服务请求 a
        //（view grant 服务 download；manage/* 服务全部）——遍历全部 grants 而非按 grant.action 分组。
        Map<String, Decision> per = new HashMap<>();
        for (String a : actions) {
            per.put(a, decideForAction(actionsFor(a), grants, g -> coversFile(g, file, orgs, folders)));
        }
        return per;
    }

    private Map<String, Decision> decideActionsForFolder(BizFileFolder folder, List<BizFileGrant> grants,
                                                         Set<Long> orgs, Set<Long> ancestors, Collection<String> actions) {
        // 【parity 修复】同 decideFilesMulti：归并语义遍历全部 grants
        Map<String, Decision> per = new HashMap<>();
        for (String a : actions) {
            per.put(a, decideForAction(actionsFor(a), grants, g -> coversFolder(g, folder, orgs, ancestors)));
        }
        return per;
    }

    private Decision decideForAction(Set<String> covering, List<BizFileGrant> grants,
                                     java.util.function.Predicate<BizFileGrant> covers) {
        Decision d = Decision.ABSTAIN;
        for (BizFileGrant g : grants) {
            if (g.getAction() == null || !covering.contains(g.getAction())) continue;
            if (!covers.test(g)) continue;
            if ("DENY".equalsIgnoreCase(g.getEffect())) return Decision.DENY;
            if (LIT_ALLOW.equalsIgnoreCase(g.getEffect())) d = Decision.ALLOW;
        }
        return d;
    }

    private Set<Long> resolveRoleIds(SecurityUser user) {
        Set<Long> roleIds = new LinkedHashSet<>();
        List<String> roleCodes = user.getRoleCodes();
        if (roleCodes != null && !roleCodes.isEmpty()) {
            for (SysRole r : roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                    .in(SysRole::getRoleCode, roleCodes).eq(SysRole::getStatus, 1))) {
                if (r.getId() != null) roleIds.add(r.getId());
            }
        }
        return roleIds;
    }

    private Set<Long> resolvePositionIds(SecurityUser user) {
        Set<Long> positionIds = new LinkedHashSet<>();
        if (user.getPositions() != null) {
            for (SysPosition p : user.getPositions()) if (p.getId() != null) positionIds.add(p.getId());
        }
        return positionIds;
    }

    private List<BizFileGrant> loadGrants(SecurityUser user, Set<String> loadActions) {
        Set<Long> roleIds = resolveRoleIds(user);
        Set<Long> positionIds = resolvePositionIds(user);
        LambdaQueryWrapper<BizFileGrant> q = new LambdaQueryWrapper<BizFileGrant>()
                .eq(BizFileGrant::getTenantId, tenantId())
                .in(BizFileGrant::getAction, loadActions)
                .and(w -> {
                    w.and(x -> x.eq(BizFileGrant::getSubjectType, "USER")
                            .eq(BizFileGrant::getSubjectId, user.getUserId()));
                    if (!roleIds.isEmpty()) {
                        w.or(x -> x.eq(BizFileGrant::getSubjectType, "ROLE")
                                .in(BizFileGrant::getSubjectId, roleIds));
                    }
                    if (!positionIds.isEmpty()) {
                        w.or(x -> x.eq(BizFileGrant::getSubjectType, LIT_POSITION)
                                .in(BizFileGrant::getSubjectId, positionIds));
                    }
                });
        return grantMapper.selectList(q);
    }

    /** 文件夹判定（建子夹/改名/删除等夹自身动作）。 */
    public Decision decideFolder(BizFileFolder folder, String action, Long userId) {
        if (folder == null || userId == null || SecurityUtils.isAdmin() || engineBypassed()) return Decision.ABSTAIN;
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Decision.ABSTAIN;
        List<BizFileGrant> grants = load(user, action);
        Set<Long> orgs = orgAncestors(folder.getOrgId());
        Set<Long> folders = folderAncestors(folder.getId());
        boolean allow = false;
        for (BizFileGrant g : grants) {
            if (!coversFolder(g, folder, orgs, folders)) continue;
            if ("DENY".equalsIgnoreCase(g.getEffect())) return Decision.DENY;
            if (LIT_ALLOW.equalsIgnoreCase(g.getEffect())) allow = true;
        }
        return allow ? Decision.ALLOW : Decision.ABSTAIN;
    }

    /** 组织闭包祖先集（含自身）；闭包不可用/异常退精确匹配，fail-closed 不放大。 */
    private Set<Long> orgAncestors(Long orgId) {
        if (orgId == null) return Set.of();
        Set<Long> out = new LinkedHashSet<>();
        out.add(orgId);
        if (jdbc != null) {
            try {
                out.addAll(jdbc.queryForList(
                        "SELECT ancestor_id FROM sys_org_closure WHERE descendant_id=?", Long.class, orgId));
            } catch (Exception ignored) {
                // 闭包未就绪：仅自身
            }
        }
        return out;
    }

    /**
     * 文件夹父链（含自身）；parent_id=0/null/环即停。
     * 【F-1 修复 2026-11-18，QC 拍板 #5】判存后入集：软删/不存在节点（含起点）不入集且停走（fail-closed），
     * 软删夹不再作为授权锚点覆盖其子树；运行时新预期=软删锚点 → 403/ABSTAIN。
     */
    private Set<Long> folderAncestors(Long folderId) {
        if (folderId == null) return Set.of();
        Set<Long> out = new LinkedHashSet<>();
        Set<Long> visited = new LinkedHashSet<>();
        Long cur = folderId;
        int depth = 0;
        while (cur != null && cur != 0L && depth++ < 50 && visited.add(cur)) {
            if (folderMapper == null) break;
            BizFileFolder f = folderMapper.selectById(cur);
            if (f == null) break;      // 已删除/不存在：不入集且停走（fail-closed）
            out.add(cur);
            cur = f.getParentId();
        }
        return out;
    }

    // ==================== 管理端 CRUD（file:acl:manage） ====================

    /** 控制器 DTO：避免 controller 依赖 entity（ArchitectureTest 约束）。
     *  【A2】+spaceType（SPACE 空间类型，SPACE 时必填）+scopeMode（ANY/RELATIVE/EXACT/SUBTREE，缺省按规则推断）。 */
    public record Command(Long id, String subjectType, Long subjectId, String objectType, Long objectId,
                          String spaceType, String action, String effect, Integer inherit, Long scopeOrgId,
                          String scopeLevel, String scopeMode, LocalDateTime expireAt) { }

    public List<Map<String, Object>> list(String objectType, Long objectId) {
        requireAdmin();
        return grantMapper.selectList(new LambdaQueryWrapper<BizFileGrant>()
                        .eq(BizFileGrant::getTenantId, tenantId())
                        .eq(objectType != null && !objectType.isBlank(), BizFileGrant::getObjectType,
                                objectType == null ? null : objectType.toUpperCase(Locale.ROOT))
                        .eq(objectId != null, BizFileGrant::getObjectId, objectId)
                        .orderByDesc(BizFileGrant::getId))
                .stream().map(this::view).toList();
    }

    public Map<String, Object> save(Command c) {
        requireAdmin();
        if (c == null) throw new BusinessException("授权内容不能为空");
        BizFileGrant g = buildGrant(c);
        applyGrantTypeRules(g);
        applyActionAndEffectRules(g);
        applyScopeRules(g);
        persistGrant(g);
        audit("file_grant_save", g);
        return view(g);
    }

    private BizFileGrant buildGrant(Command c) {
        BizFileGrant g = new BizFileGrant();
        g.setId(c.id());
        g.setSubjectType(c.subjectType());
        g.setSubjectId(c.subjectId());
        g.setObjectType(c.objectType());
        g.setObjectId(c.objectId());
        g.setSpaceType(c.spaceType() == null || c.spaceType().isBlank() ? null
                : c.spaceType().trim().toLowerCase(Locale.ROOT));
        g.setAction(c.action());
        g.setEffect(c.effect());
        g.setInherit(c.inherit());
        g.setScopeOrgId(c.scopeOrgId());
        g.setScopeLevel(c.scopeLevel() == null || c.scopeLevel().isBlank() ? null
                : c.scopeLevel().trim().toLowerCase(Locale.ROOT));
        g.setScopeMode(c.scopeMode());
        g.setExpireAt(c.expireAt());
        return g;
    }

    /** 主体/对象类型及空间类型组合校验（SPACE 必填 spaceType；非 SPACE 不得携带）。 */
    private void applyGrantTypeRules(BizFileGrant g) {
        String subjectType = upper(g.getSubjectType());
        // 【2026-11-21 PM-D1】null 必须显式判空：Set.of(...).contains(null) 会抛 NPE，
        // 空 body 的授权保存此前返回 500；这里统一转 400 参数校验。
        if (subjectType == null || !Set.of("USER", "ROLE", LIT_POSITION).contains(subjectType)) {
            throw new BusinessException("主体类型仅支持 USER/ROLE/POSITION");
        }
        if (g.getSubjectId() == null) throw new BusinessException("主体不能为空");
        String objectType = upper(g.getObjectType());
        if (objectType == null || !Set.of(OBJ_SPACE, OBJ_FOLDER, OBJ_FILE).contains(objectType)) {
            throw new BusinessException("对象类型仅支持 SPACE/FOLDER/FILE");
        }
        if (!OBJ_SPACE.equals(objectType) && g.getObjectId() == null) {
            throw new BusinessException("文件夹/文件授权必须指定对象 ID");
        }
        if (OBJ_SPACE.equals(objectType) && g.getObjectId() != null) {
            throw new BusinessException("空间授权的对象 ID 必须为空");
        }
        // 【A2 §2.6/§2.7】SPACE 空间类型必填且限词表；非 SPACE 行不得携带 spaceType。
        // space_type=NULL=任意空间仅属存量迁移行（旧 blank 归一），新保存禁止空值。
        applySpaceTypeRules(g, objectType);
        g.setSubjectType(subjectType);
        g.setObjectType(objectType);
    }

    private void applySpaceTypeRules(BizFileGrant g, String objectType) {
        if (OBJ_SPACE.equals(objectType)) {
            if (g.getSpaceType() == null) throw new BusinessException("空间授权必须指定空间类型");
            if (!SPACE_TYPES.contains(g.getSpaceType())) {
                throw new BusinessException("空间类型仅支持 public/private/incentive/fabushi");
            }
        } else if (g.getSpaceType() != null) {
            throw new BusinessException("空间类型仅 SPACE 授权可指定");
        }
    }

    private void applyActionAndEffectRules(BizFileGrant g) {
        String action = g.getAction() == null ? "" : g.getAction().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("view", "download", "upload", "edit", "delete", "share", "manage", "*", "create").contains(action)) {
            throw new BusinessException("动作仅支持 view/download/upload/edit/delete/share/manage/create/*");
        }
        String effect = upper(g.getEffect());
        if (effect == null || effect.isBlank()) effect = LIT_ALLOW;
        if (!Set.of(LIT_ALLOW, "DENY").contains(effect)) throw new BusinessException("效果仅支持 ALLOW/DENY");
        if (g.getExpireAt() != null && !g.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("到期时间必须晚于当前时间");
        }
        g.setAction(action);
        g.setEffect(effect);
    }

    /** 【A2】scope_mode 归一：缺省按 RELATIVE 线索推断；RELATIVE 必须带档位；ANY 清空范围字段。 */
    private void applyScopeRules(BizFileGrant g) {
        String scopeMode = upper(g.getScopeMode());
        if (scopeMode == null || scopeMode.isBlank()) {
            scopeMode = g.getScopeLevel() != null ? SCOPE_RELATIVE : SCOPE_SUBTREE;
        }
        if (!Set.of(SCOPE_ANY, SCOPE_RELATIVE, SCOPE_EXACT, SCOPE_SUBTREE).contains(scopeMode)) {
            throw new BusinessException("范围模式仅支持 ANY/RELATIVE/EXACT/SUBTREE");
        }
        if (SCOPE_RELATIVE.equals(scopeMode)) {
            validateRelativeScope(g);
        } else {
            if (g.getScopeLevel() != null) {
                throw new BusinessException("scopeLevel 仅在 RELATIVE 范围下有效（空间类型请用 spaceType）");
            }
            if (SCOPE_ANY.equals(scopeMode)) g.setScopeOrgId(null);
        }
        g.setScopeMode(scopeMode);
    }

    private void validateRelativeScope(BizFileGrant g) {
        if (g.getScopeLevel() == null || !RELATIVE_LEVELS.contains(g.getScopeLevel())) {
            throw new BusinessException("RELATIVE 范围必须指定 self/dept/office/region");
        }
        if (g.getScopeOrgId() != null) {
            throw new BusinessException("RELATIVE 范围是相对当前查看人的档位，不能携带组织节点");
        }
    }

    private void persistGrant(BizFileGrant g) {
        g.setInherit(g.getInherit() == null ? 1 : g.getInherit());
        g.setTenantId(tenantId());
        g.setCreatedBy(SecurityUtils.getCurrentUserId());
        validateTarget(g);
        try {
            if (g.getId() == null) {
                g.setSource("manual");
                grantMapper.insert(g);
            } else {
                ensureEditable(g);
                // 编辑不动迁移来源：legacy_ref/source 保持原行值（updateById NOT_NULL 策略跳过 null 字段）
                grantMapper.updateById(g);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("授权保存失败（主体+对象+动作可能已存在）");
        }
    }

    private void ensureEditable(BizFileGrant g) {
        BizFileGrant old = grantMapper.selectById(g.getId());
        if (old == null || !Objects.equals(old.getTenantId(), tenantId())) {
            throw new BusinessException("授权不存在");
        }
    }

    private Map<String, Object> view(BizFileGrant g) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("subjectType", g.getSubjectType());
        m.put("subjectId", g.getSubjectId());
        m.put("objectType", g.getObjectType());
        m.put("objectId", g.getObjectId());
        m.put("spaceType", g.getSpaceType());
        m.put("action", g.getAction());
        m.put("effect", g.getEffect());
        m.put("inherit", g.getInherit());
        m.put("scopeOrgId", g.getScopeOrgId());
        m.put("scopeLevel", g.getScopeLevel());
        m.put("scopeMode", g.getScopeMode());
        m.put("expireAt", g.getExpireAt());
        m.put("legacyRef", g.getLegacyRef());
        m.put("source", g.getSource());
        m.put("createdBy", g.getCreatedBy());
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    public void delete(Long id) {
        requireAdmin();
        BizFileGrant old = id == null ? null : grantMapper.selectById(id);
        if (old == null || !Objects.equals(old.getTenantId(), tenantId())) {
            throw new BusinessException("授权不存在");
        }
        grantMapper.deleteById(id);
        audit("file_grant_delete", old);
    }

    /** P0：文件授权管理仅系统管理员；控制器功能码之外再收口。 */
    private void requireAdmin() {
        if (!SecurityUtils.isAdmin()) throw new BusinessException(403, "文件统一授权仅系统管理员可配置");
    }

    /** 目标对象必须真实存在，避免对任意 ID 建授权（P0 纵深防御）。
     *  【A2】SPACE 分支的词表校验已前移 save()（spaceType 必填）；scopeLevel 仅 RELATIVE 数据范围，不再当空间类型校验。 */
    private void validateTarget(BizFileGrant g) {
        if (OBJ_FILE.equals(g.getObjectType())) {
            if (fileMapper == null || fileMapper.selectById(g.getObjectId()) == null) {
                throw new BusinessException("目标文件不存在");
            }
        } else if (OBJ_FOLDER.equals(g.getObjectType())) {
            if (folderMapper == null || folderMapper.selectById(g.getObjectId()) == null) {
                throw new BusinessException("目标文件夹不存在");
            }
        }
    }

    private void audit(String action, BizFileGrant g) {
        if (securityAudit == null || g == null) return;
        try {
            securityAudit.permissionChange(action, "grantId=" + g.getId() + " subject=" + g.getSubjectType()
                    + "#" + g.getSubjectId() + " object=" + g.getObjectType() + "#" + g.getObjectId()
                    + " action=" + g.getAction() + " effect=" + g.getEffect());
        } catch (Exception ignored) {
            // 审计失败不阻断授权保存；主日志仍有记录。
        }
    }

    private String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private Long tenantId() {
        SecurityUser u = SecurityUtils.getCurrentUser();
        Long id = u == null ? null : u.getCurrentGroupId();
        return id == null ? 1L : id;
    }

    private List<BizFileGrant> load(SecurityUser user, String action) {
        return loadGrants(user, actionsFor(action));
    }

    private boolean coversFile(BizFileGrant g, BizFile file, Set<Long> orgAncestors, Set<Long> folderAncestors) {
        if (!scopeCovers(g, file.getOrgId(), file.getUploaderId(), orgAncestors) || expired(g)) return false;
        String type = g.getObjectType() == null ? "" : g.getObjectType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case OBJ_FILE -> file.getId() != null && file.getId().equals(g.getObjectId());
            // A1: FOLDER 授权递归；inherit=0 只对夹自身，不对文件生效
            case OBJ_FOLDER -> g.getObjectId() != null && !Integer.valueOf(0).equals(g.getInherit())
                    && folderAncestors.contains(g.getObjectId());
            // A2: SPACE 匹配改读 space_type；NULL=任意空间（旧 blank 迁移行兼容，§2.7）
            case OBJ_SPACE -> g.getObjectId() == null
                    && spaceTypeMatches(g.getSpaceType(), file.getFileScope());
            default -> false;
        };
    }

    private boolean coversFolder(BizFileGrant g, BizFileFolder folder, Set<Long> orgAncestors,
                                 Set<Long> folderAncestors) {
        if (!scopeCovers(g, folder.getOrgId(), folder.getOwnerId(), orgAncestors) || expired(g)) return false;
        String type = g.getObjectType() == null ? "" : g.getObjectType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case OBJ_FOLDER -> {
                if (folder.getId() != null && folder.getId().equals(g.getObjectId())) yield true;
                yield g.getObjectId() != null && !Integer.valueOf(0).equals(g.getInherit())
                        && folderAncestors.contains(g.getObjectId());
            }
            case OBJ_SPACE -> g.getObjectId() == null
                    && spaceTypeMatches(g.getSpaceType(), folder.getFolderType());
            default -> false;
        };
    }

    /** SPACE 空间类型匹配：授权 NULL/blank=任意空间；否则忽略大小写精确匹配目标空间。 */
    private boolean spaceTypeMatches(String spaceType, String targetScope) {
        return spaceType == null || spaceType.isBlank()
                || (targetScope != null && spaceType.equalsIgnoreCase(targetScope));
    }

    /**
     * 【A2】范围判定四态：
     * ANY=不限；RELATIVE=按当前查看人档位（同旧 scopeCovers 口径，§1.2/A1-2）；
     * EXACT=绝对组织精确；SUBTREE=绝对组织闭包子树（缺省推断时兼容 A1 行为）。
     * scope_mode 为空（直构/DDL 前存量行）时推断：scopeLevel 非空→RELATIVE，scope_org_id 空→ANY，否则 SUBTREE。
     */
    private boolean scopeCovers(BizFileGrant g, Long targetOrgId, Long targetUploaderId, Set<Long> orgAncestors) {
        String mode = g.getScopeMode() == null ? null : g.getScopeMode().toUpperCase(Locale.ROOT);
        if (mode == null || mode.isBlank()) {
            mode = g.getScopeLevel() != null ? SCOPE_RELATIVE
                    : (g.getScopeOrgId() == null ? SCOPE_ANY : SCOPE_SUBTREE);
        }
        switch (mode) {
            case SCOPE_ANY:
                return true;
            case SCOPE_RELATIVE:
                return relativeCovers(g.getScopeLevel(), targetOrgId, targetUploaderId);
            case SCOPE_EXACT:
                return g.getScopeOrgId() == null || Objects.equals(g.getScopeOrgId(), targetOrgId);
            case SCOPE_SUBTREE:
            default:
                return g.getScopeOrgId() == null || orgAncestors.contains(g.getScopeOrgId());
        }
    }

    /**
     * RELATIVE 档位判定——与旧 FolderPermissionService.scopeCovers 逐口径一致：
     * null/blank/all=不限；self=目标上传人/属主是当前用户；
     * dept/office/region=目标有组织→档位部门集含目标 org；无组织→档位人员集含上传人；解析器缺席 fail-closed。
     */
    private boolean relativeCovers(String level, Long targetOrgId, Long targetUploaderId) {
        if (level == null || level.isBlank() || "all".equals(level)) return true;
        SecurityUser u = SecurityUtils.getCurrentUser();
        if (u == null) return false;
        if ("self".equals(level)) {
            return targetUploaderId != null && targetUploaderId.equals(SecurityUtils.getCurrentUserId());
        }
        if (scopeResolver == null) return false;
        if (targetOrgId != null) {
            List<Long> depts = scopeResolver.resolveDeptScope(u, level);
            return depts == null || depts.contains(targetOrgId);
        }
        if (targetUploaderId != null) {
            List<Long> users = scopeResolver.resolveScope(u, level);
            return users == null || users.contains(targetUploaderId);
        }
        return false;
    }

    private boolean expired(BizFileGrant g) {
        return g.getExpireAt() != null && g.getExpireAt().isBefore(LocalDateTime.now());
    }

    /** 动作归一：具体动作 + manage 伞 + 通配。 */
    private Set<String> actionsFor(String action) {
        Set<String> out = new LinkedHashSet<>();
        if (action == null) return out;
        out.add(action);
        out.add("manage");
        out.add("*");
        if ("download".equals(action)) out.add("view");
        if (List.of("upload", "rename", "replace", "share", "edit").contains(action)) out.add("edit");
        return out;
    }
}
