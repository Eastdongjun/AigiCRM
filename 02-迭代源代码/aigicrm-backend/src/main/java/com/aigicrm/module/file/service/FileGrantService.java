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
 */
@Service
@RequiredArgsConstructor
public class FileGrantService {

    public enum Decision { ALLOW, DENY, ABSTAIN }

    public static final String OBJ_SPACE = "SPACE";
    public static final String OBJ_FOLDER = "FOLDER";
    public static final String OBJ_FILE = "FILE";

    private final BizFileGrantMapper grantMapper;
    private final SysRoleMapper roleMapper;

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

    /** 文件判定。 */
    public Decision decideFile(BizFile file, String action, Long userId) {
        if (file == null) return Decision.ABSTAIN;
        return decideFiles(List.of(file), action, userId).getOrDefault(file.getId(), Decision.ABSTAIN);
    }

    /** 【A1 批量预载】一次查 grants，按 org/folder 缓存后逐文件判定。 */
    public Map<Long, Decision> decideFiles(Collection<BizFile> files, String action, Long userId) {
        Map<Long, Decision> out = new HashMap<>();
        if (files == null || files.isEmpty()) return out;
        boolean admin = SecurityUtils.isAdmin();
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (userId == null || admin || user == null) {
            for (BizFile f : files) out.put(f.getId(), Decision.ABSTAIN);
            return out;
        }
        List<BizFileGrant> grants = load(user, action);
        Map<Long, Set<Long>> orgCache = new HashMap<>();
        Map<Long, Set<Long>> folderCache = new HashMap<>();
        for (BizFile file : files) {
            Set<Long> orgs = orgCache.computeIfAbsent(file.getOrgId(), this::orgAncestors);
            Set<Long> folders = file.getFolderId() == null ? Set.of()
                    : folderCache.computeIfAbsent(file.getFolderId(), this::folderAncestors);
            Decision decision = Decision.ABSTAIN;
            for (BizFileGrant g : grants) {
                if (!coversFile(g, file, orgs, folders)) continue;
                if ("DENY".equalsIgnoreCase(g.getEffect())) { decision = Decision.DENY; break; }
                if ("ALLOW".equalsIgnoreCase(g.getEffect())) decision = Decision.ALLOW;
            }
            out.put(file.getId(), decision);
        }
        return out;
    }

    /** 文件夹判定（建子夹/改名/删除等夹自身动作）。 */
    public Decision decideFolder(BizFileFolder folder, String action, Long userId) {
        if (folder == null || userId == null || SecurityUtils.isAdmin()) return Decision.ABSTAIN;
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Decision.ABSTAIN;
        List<BizFileGrant> grants = load(user, action);
        Set<Long> orgs = orgAncestors(folder.getOrgId());
        Set<Long> folders = folderAncestors(folder.getId());
        boolean allow = false;
        for (BizFileGrant g : grants) {
            if (!coversFolder(g, folder, orgs, folders)) continue;
            if ("DENY".equalsIgnoreCase(g.getEffect())) return Decision.DENY;
            if ("ALLOW".equalsIgnoreCase(g.getEffect())) allow = true;
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

    /** 文件夹父链（含自身）；parent_id=0/null/deleted/环即停。 */
    private Set<Long> folderAncestors(Long folderId) {
        if (folderId == null) return Set.of();
        Set<Long> out = new LinkedHashSet<>();
        Set<Long> visited = new LinkedHashSet<>();
        Long cur = folderId;
        int depth = 0;
        while (cur != null && cur != 0L && depth++ < 50 && visited.add(cur)) {
            out.add(cur);
            if (folderMapper == null) break;
            BizFileFolder f = folderMapper.selectById(cur);
            if (f == null) break;
            cur = f.getParentId();
        }
        return out;
    }

    // ==================== 管理端 CRUD（file:acl:manage） ====================

    /** 控制器 DTO：避免 controller 依赖 entity（ArchitectureTest 约束）。 */
    public record Command(Long id, String subjectType, Long subjectId, String objectType, Long objectId,
                          String action, String effect, Integer inherit, Long scopeOrgId,
                          String scopeLevel, LocalDateTime expireAt) { }

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
        BizFileGrant g = new BizFileGrant();
        g.setId(c.id());
        g.setSubjectType(c.subjectType());
        g.setSubjectId(c.subjectId());
        g.setObjectType(c.objectType());
        g.setObjectId(c.objectId());
        g.setAction(c.action());
        g.setEffect(c.effect());
        g.setInherit(c.inherit());
        g.setScopeOrgId(c.scopeOrgId());
        g.setScopeLevel(c.scopeLevel());
        g.setExpireAt(c.expireAt());
        String subjectType = upper(g.getSubjectType());
        if (!Set.of("USER", "ROLE", "POSITION").contains(subjectType)) {
            throw new BusinessException("主体类型仅支持 USER/ROLE/POSITION");
        }
        if (g.getSubjectId() == null) throw new BusinessException("主体不能为空");
        String objectType = upper(g.getObjectType());
        if (!Set.of(OBJ_SPACE, OBJ_FOLDER, OBJ_FILE).contains(objectType)) {
            throw new BusinessException("对象类型仅支持 SPACE/FOLDER/FILE");
        }
        if (!OBJ_SPACE.equals(objectType) && g.getObjectId() == null) {
            throw new BusinessException("文件夹/文件授权必须指定对象 ID");
        }
        if (OBJ_SPACE.equals(objectType) && g.getObjectId() != null) {
            throw new BusinessException("空间授权的对象 ID 必须为空");
        }
        String action = g.getAction() == null ? "" : g.getAction().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("view", "download", "upload", "edit", "delete", "share", "manage", "*").contains(action)) {
            throw new BusinessException("动作仅支持 view/download/upload/edit/delete/share/manage/*");
        }
        String effect = upper(g.getEffect());
        if (effect == null || effect.isBlank()) effect = "ALLOW";
        if (!Set.of("ALLOW", "DENY").contains(effect)) throw new BusinessException("效果仅支持 ALLOW/DENY");
        if (g.getExpireAt() != null && !g.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("到期时间必须晚于当前时间");
        }
        g.setSubjectType(subjectType);
        g.setObjectType(objectType);
        g.setAction(action);
        g.setEffect(effect);
        g.setInherit(g.getInherit() == null ? 1 : g.getInherit());
        g.setTenantId(tenantId());
        g.setCreatedBy(SecurityUtils.getCurrentUserId());
        validateTarget(g);
        try {
            if (g.getId() == null) {
                grantMapper.insert(g);
            } else {
                BizFileGrant old = grantMapper.selectById(g.getId());
                if (old == null || !Objects.equals(old.getTenantId(), tenantId())) {
                    throw new BusinessException("授权不存在");
                }
                grantMapper.updateById(g);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("授权保存失败（主体+对象+动作可能已存在）");
        }
        audit("file_grant_save", g);
        return view(g);
    }

    private Map<String, Object> view(BizFileGrant g) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("subjectType", g.getSubjectType());
        m.put("subjectId", g.getSubjectId());
        m.put("objectType", g.getObjectType());
        m.put("objectId", g.getObjectId());
        m.put("action", g.getAction());
        m.put("effect", g.getEffect());
        m.put("inherit", g.getInherit());
        m.put("scopeOrgId", g.getScopeOrgId());
        m.put("scopeLevel", g.getScopeLevel());
        m.put("expireAt", g.getExpireAt());
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

    /** 目标对象必须真实存在，避免对任意 ID 建授权（P0 纵深防御）。 */
    private void validateTarget(BizFileGrant g) {
        if (OBJ_FILE.equals(g.getObjectType())) {
            if (fileMapper == null || fileMapper.selectById(g.getObjectId()) == null) {
                throw new BusinessException("目标文件不存在");
            }
        } else if (OBJ_FOLDER.equals(g.getObjectType())) {
            if (folderMapper == null || folderMapper.selectById(g.getObjectId()) == null) {
                throw new BusinessException("目标文件夹不存在");
            }
        } else if (g.getScopeLevel() != null && !g.getScopeLevel().isBlank()
                && !Set.of("public", "private", "incentive", "fabushi").contains(g.getScopeLevel())) {
            throw new BusinessException("空间类型非法");
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
        Set<Long> roleIds = new LinkedHashSet<>();
        List<String> roleCodes = user.getRoleCodes();
        if (roleCodes != null && !roleCodes.isEmpty()) {
            for (SysRole r : roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                    .in(SysRole::getRoleCode, roleCodes).eq(SysRole::getStatus, 1))) {
                if (r.getId() != null) roleIds.add(r.getId());
            }
        }
        Set<Long> positionIds = new LinkedHashSet<>();
        if (user.getPositions() != null) {
            for (SysPosition p : user.getPositions()) if (p.getId() != null) positionIds.add(p.getId());
        }
        Set<String> actions = actionsFor(action);
        LambdaQueryWrapper<BizFileGrant> q = new LambdaQueryWrapper<BizFileGrant>()
                .eq(BizFileGrant::getTenantId, tenantId())
                .in(BizFileGrant::getAction, actions)
                .and(w -> {
                    w.and(x -> x.eq(BizFileGrant::getSubjectType, "USER")
                            .eq(BizFileGrant::getSubjectId, user.getUserId()));
                    if (!roleIds.isEmpty()) {
                        w.or(x -> x.eq(BizFileGrant::getSubjectType, "ROLE")
                                .in(BizFileGrant::getSubjectId, roleIds));
                    }
                    if (!positionIds.isEmpty()) {
                        w.or(x -> x.eq(BizFileGrant::getSubjectType, "POSITION")
                                .in(BizFileGrant::getSubjectId, positionIds));
                    }
                });
        return grantMapper.selectList(q);
    }

    private boolean coversFile(BizFileGrant g, BizFile file, Set<Long> orgAncestors, Set<Long> folderAncestors) {
        if (!scopeOrgCovers(g, orgAncestors) || expired(g)) return false;
        String type = g.getObjectType() == null ? "" : g.getObjectType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case OBJ_FILE -> file.getId() != null && file.getId().equals(g.getObjectId());
            // A1: FOLDER 授权递归；inherit=0 只对夹自身，不对文件生效
            case OBJ_FOLDER -> g.getObjectId() != null && !Integer.valueOf(0).equals(g.getInherit())
                    && folderAncestors.contains(g.getObjectId());
            case OBJ_SPACE -> g.getObjectId() == null
                    && (g.getScopeLevel() == null || g.getScopeLevel().isBlank()
                        || g.getScopeLevel().equalsIgnoreCase(file.getFileScope()));
            default -> false;
        };
    }

    private boolean coversFolder(BizFileGrant g, BizFileFolder folder, Set<Long> orgAncestors,
                                 Set<Long> folderAncestors) {
        if (!scopeOrgCovers(g, orgAncestors) || expired(g)) return false;
        String type = g.getObjectType() == null ? "" : g.getObjectType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case OBJ_FOLDER -> {
                if (folder.getId() != null && folder.getId().equals(g.getObjectId())) yield true;
                yield g.getObjectId() != null && !Integer.valueOf(0).equals(g.getInherit())
                        && folderAncestors.contains(g.getObjectId());
            }
            case OBJ_SPACE -> g.getObjectId() == null
                    && (g.getScopeLevel() == null || g.getScopeLevel().isBlank()
                        || g.getScopeLevel().equalsIgnoreCase(folder.getFolderType()));
            default -> false;
        };
    }

    private boolean scopeOrgCovers(BizFileGrant g, Set<Long> orgAncestors) {
        return g.getScopeOrgId() == null || orgAncestors.contains(g.getScopeOrgId());
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
