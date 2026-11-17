package com.aigicrm.module.file.service;

import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFileGrant;
import com.aigicrm.module.file.mapper.BizFileGrantMapper;
import com.aigicrm.module.system.entity.SysPosition;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    /** 文件判定。 */
    public Decision decideFile(BizFile file, String action, Long userId) {
        if (file == null || userId == null || SecurityUtils.isAdmin()) return Decision.ABSTAIN;
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Decision.ABSTAIN;
        List<BizFileGrant> grants = load(user, action);
        boolean allow = false;
        for (BizFileGrant g : grants) {
            if (!coversFile(g, file)) continue;
            if ("DENY".equalsIgnoreCase(g.getEffect())) return Decision.DENY;
            if ("ALLOW".equalsIgnoreCase(g.getEffect())) allow = true;
        }
        return allow ? Decision.ALLOW : Decision.ABSTAIN;
    }

    /** 文件夹判定（建子夹/改名/删除等夹自身动作）。 */
    public Decision decideFolder(BizFileFolder folder, String action, Long userId) {
        if (folder == null || userId == null || SecurityUtils.isAdmin()) return Decision.ABSTAIN;
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return Decision.ABSTAIN;
        List<BizFileGrant> grants = load(user, action);
        boolean allow = false;
        for (BizFileGrant g : grants) {
            if (!coversFolder(g, folder)) continue;
            if ("DENY".equalsIgnoreCase(g.getEffect())) return Decision.DENY;
            if ("ALLOW".equalsIgnoreCase(g.getEffect())) allow = true;
        }
        return allow ? Decision.ALLOW : Decision.ABSTAIN;
    }

    // ==================== 管理端 CRUD（file:acl:manage） ====================

    /** 控制器 DTO：避免 controller 依赖 entity（ArchitectureTest 约束）。 */
    public record Command(Long id, String subjectType, Long subjectId, String objectType, Long objectId,
                          String action, String effect, Integer inherit, Long scopeOrgId,
                          String scopeLevel, LocalDateTime expireAt) { }

    public List<Map<String, Object>> list(String objectType, Long objectId) {
        return grantMapper.selectList(new LambdaQueryWrapper<BizFileGrant>()
                        .eq(BizFileGrant::getTenantId, tenantId())
                        .eq(objectType != null && !objectType.isBlank(), BizFileGrant::getObjectType,
                                objectType == null ? null : objectType.toUpperCase(Locale.ROOT))
                        .eq(objectId != null, BizFileGrant::getObjectId, objectId)
                        .orderByDesc(BizFileGrant::getId))
                .stream().map(this::view).toList();
    }

    public Map<String, Object> save(Command c) {
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
        BizFileGrant old = id == null ? null : grantMapper.selectById(id);
        if (old == null || !Objects.equals(old.getTenantId(), tenantId())) {
            throw new BusinessException("授权不存在");
        }
        grantMapper.deleteById(id);
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

    private boolean coversFile(BizFileGrant g, BizFile file) {
        if (!scopeOrgCovers(g, file.getOrgId())) return false;
        if (expired(g)) return false;
        String type = g.getObjectType() == null ? "" : g.getObjectType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case OBJ_FILE -> file.getId() != null && file.getId().equals(g.getObjectId());
            case OBJ_FOLDER -> file.getFolderId() != null && file.getFolderId().equals(g.getObjectId())
                    && !Integer.valueOf(0).equals(g.getInherit());
            case OBJ_SPACE -> g.getObjectId() == null
                    && (g.getScopeLevel() == null || g.getScopeLevel().isBlank()
                        || g.getScopeLevel().equalsIgnoreCase(file.getFileScope()));
            default -> false;
        };
    }

    private boolean coversFolder(BizFileGrant g, BizFileFolder folder) {
        if (!scopeOrgCovers(g, folder.getOrgId()) || expired(g)) return false;
        String type = g.getObjectType() == null ? "" : g.getObjectType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case OBJ_FOLDER -> folder.getId() != null && folder.getId().equals(g.getObjectId());
            case OBJ_SPACE -> g.getObjectId() == null
                    && (g.getScopeLevel() == null || g.getScopeLevel().isBlank()
                        || g.getScopeLevel().equalsIgnoreCase(folder.getFolderType()));
            default -> false;
        };
    }

    private boolean scopeOrgCovers(BizFileGrant g, Long orgId) {
        return g.getScopeOrgId() == null || Objects.equals(g.getScopeOrgId(), orgId);
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
