package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【2026-11-10 文件权限终版】例外授权(唯一加授通道, 授权必须过期):
 * 谁(人|角色) × 空间(公共/私人) × 对象组(FOLDER: create/rename/delete | FILE: view/upload/download/rename/replace/share/delete)
 * × 逐夹定向(可选) × 组织范围(可选) × 期限(expires_at 必填, 到期自动收回)。
 * 组织矩阵是底座(零配置), 本表只做跨范围特批; 逐夹 ACL 已退役(V2026.11.10.1 转换为 folder_id 定向行)。
 */
@Service
@RequiredArgsConstructor
public class FolderTypeDefaultService {

    public static final String SUBJECT_ROLE = "ROLE";
    public static final String SUBJECT_USER = "USER";
    private static final Set<String> FOLDER_ACTIONS = Set.of(
            FolderPermissionService.P_CREATE, FolderPermissionService.P_RENAME, FolderPermissionService.P_DELETE);
    private static final Set<String> FILE_ACTIONS = Set.of(
            FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
            FolderPermissionService.P_RENAME, FolderPermissionService.P_REPLACE,
            FolderPermissionService.P_DELETE, FolderPermissionService.P_SHARE,
            FolderPermissionService.P_DOWNLOAD);
    private static final Set<String> TYPES = Set.of("public", "private", "incentive");
    private static final Set<String> SCOPES = Set.of("self", "dept", "office", "region", "all");
    private static final String TYPE_INCENTIVE = "incentive";

    private final BizFolderTypeDefaultMapper grantMapper;
    private final BizFileFolderMapper folderMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final com.aigicrm.security.SecurityAuditService securityAudit;

    /** 授权列表(含主体名/定向夹名/过期状态) */
    public List<Map<String, Object>> listGrants() {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (BizFolderTypeDefault g : grantMapper.selectList(
                new LambdaQueryWrapper<BizFolderTypeDefault>()
                        .orderByAsc(BizFolderTypeDefault::getFolderType)
                        .orderByAsc(BizFolderTypeDefault::getObjectType)
                        .orderByAsc(BizFolderTypeDefault::getSubjectType)
                        .orderByAsc(BizFolderTypeDefault::getSubjectId))) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", g.getId());
            row.put("folderType", g.getFolderType());
            row.put("objectType", g.getObjectType());
            row.put("folderId", g.getFolderId());
            if (g.getFolderId() != null) {
                BizFileFolder f = folderMapper.selectById(g.getFolderId());
                row.put("folderName", f != null ? f.getFolderName() : "未知文件夹");
            }
            row.put("scopeOrgId", g.getScopeOrgId());
            row.put("scopeLevel", g.getScopeLevel());
            row.put("subjectType", g.getSubjectType());
            row.put("subjectId", g.getSubjectId());
            row.put("subjectName", subjectNameOf(g));
            row.put("perms", List.of(g.getPerms().split(",")));
            row.put("expiresAt", g.getExpiresAt());
            row.put("expired", g.getExpiresAt() != null && g.getExpiresAt().isBefore(now));
            rows.add(row);
        }
        return rows;
    }

    /** 保存授权: 词表按对象组校验; 'edit' 伞归一 rename+replace; 过期时间必填且须晚于当前 */
    public void saveGrant(String folderType, String objectType, Long folderId, Long scopeOrgId, String scopeLevel,
                          String subjectType, Long subjectId, List<String> perms, LocalDateTime expiresAt) {
        if (!TYPES.contains(folderType)) throw new BusinessException("不支持的文件夹类型");
        if (!FolderPermissionService.OBJ_FOLDER.equals(objectType) && !FolderPermissionService.OBJ_FILE.equals(objectType)) {
            throw new BusinessException("动作对象必须是 FOLDER 或 FILE");
        }
        // 四项激励=纯文件域(无文件夹层)
        if (TYPE_INCENTIVE.equals(folderType) && FolderPermissionService.OBJ_FOLDER.equals(objectType)) {
            throw new BusinessException("四项激励类型只有文件动作");
        }
        if (scopeLevel != null && !SCOPES.contains(scopeLevel)) throw new BusinessException("非法数据范围: " + scopeLevel);
        if (folderId != null && folderMapper.selectById(folderId) == null) throw new BusinessException("定向文件夹不存在");
        if (subjectId == null) throw new BusinessException("授权主体不能为空");
        if (perms == null || perms.isEmpty()) throw new BusinessException("权限不能为空");
        Set<String> allowed = new java.util.HashSet<>(FolderPermissionService.OBJ_FOLDER.equals(objectType) ? FOLDER_ACTIONS : FILE_ACTIONS);
        allowed.add(FolderPermissionService.P_EDIT); // 旧调用方 'edit' 伞: 校验放行, 落库归一 rename+replace
        for (String p : perms) if (!allowed.contains(p)) throw new BusinessException("非法权限项: " + p);
        if (!SUBJECT_ROLE.equals(subjectType) && !SUBJECT_USER.equals(subjectType)) {
            throw new BusinessException("授权主体类型必须是 ROLE 或 USER");
        }
        // 【2026-11-10 用户令】到期可选: NULL=永久(勾选即生效); 填了才校验须晚于当前
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) throw new BusinessException("到期时间必须晚于当前时间");
        if (subjectType.equals(SUBJECT_ROLE)) {
            SysRole role = roleMapper.selectById(subjectId);
            if (role == null || role.getStatus() == null || role.getStatus() != 1) throw new BusinessException("角色不存在或已停用");
        } else {
            if (userMapper.selectById(subjectId) == null) throw new BusinessException("用户不存在");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String p : perms) {
            if (FolderPermissionService.P_EDIT.equals(p)) {
                normalized.add(FolderPermissionService.P_RENAME);
                normalized.add(FolderPermissionService.P_REPLACE);
            } else normalized.add(p);
        }
        String joined = String.join(",", normalized);
        BizFolderTypeDefault existing = grantMapper.selectOne(new LambdaQueryWrapper<BizFolderTypeDefault>()
                .eq(BizFolderTypeDefault::getFolderType, folderType)
                .eq(BizFolderTypeDefault::getObjectType, objectType)
                .eq(folderId != null, BizFolderTypeDefault::getFolderId, folderId)
                .isNull(folderId == null, BizFolderTypeDefault::getFolderId)
                .eq(BizFolderTypeDefault::getSubjectType, subjectType)
                .eq(BizFolderTypeDefault::getSubjectId, subjectId));
        if (existing != null) {
            existing.setPerms(joined);
            existing.setExpiresAt(expiresAt);
            existing.setScopeOrgId(scopeOrgId);
            existing.setScopeLevel(scopeLevel);
            grantMapper.updateById(existing);
        } else {
            BizFolderTypeDefault g = new BizFolderTypeDefault();
            g.setFolderType(folderType);
            g.setObjectType(objectType);
            g.setFolderId(folderId);
            g.setScopeOrgId(scopeOrgId);
            g.setScopeLevel(scopeLevel);
            g.setSubjectType(subjectType);
            g.setSubjectId(subjectId);
            g.setPerms(joined);
            g.setExpiresAt(expiresAt);
            g.setCreatedBy(SecurityUtils.getCurrentUserId());
            g.setCreatedAt(LocalDateTime.now());
            grantMapper.insert(g);
        }
        try { securityAudit.permissionChange("file_grant_save",
                "fileGrant:" + folderType + "/" + objectType + (folderId != null ? "#" + folderId : "")
                        + " subject:" + subjectType + ":" + subjectId + " perms:" + joined
                        + " scope:" + scopeLevel + " expires:" + expiresAt); } catch (Exception ignore) { }
    }

    /** 按 id 删除授权(立即收回) */
    public void removeGrant(Long id) {
        BizFolderTypeDefault g = grantMapper.selectById(id);
        if (g == null) return;
        grantMapper.deleteById(id);
        try { securityAudit.permissionChange("file_grant_remove",
                "fileGrant:" + g.getFolderType() + "/" + g.getObjectType() + "#" + id
                        + " subject:" + g.getSubjectType() + ":" + g.getSubjectId()); } catch (Exception ignore) { }
    }

    /** 续期(顺延至指定时刻; 审计留痕) */
    public void renewGrant(Long id, LocalDateTime expiresAt) {
        if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) throw new BusinessException("续期时间必须晚于当前时间");
        BizFolderTypeDefault g = grantMapper.selectById(id);
        if (g == null) throw new BusinessException("授权不存在");
        g.setExpiresAt(expiresAt);
        grantMapper.updateById(g);
        try { securityAudit.permissionChange("file_grant_renew", "fileGrant#" + id + " expires:" + expiresAt); } catch (Exception ignore) { }
    }

    private String subjectNameOf(BizFolderTypeDefault g) {
        if (SUBJECT_USER.equals(g.getSubjectType())) {
            SysUser u = userMapper.selectById(g.getSubjectId());
            return u != null ? u.getRealName() + "（" + u.getUsername() + "）" : "未知用户";
        }
        SysRole role = roleMapper.selectById(g.getSubjectId());
        return role != null ? role.getRoleName() : "未知角色";
    }
}
