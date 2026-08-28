package com.ycmn.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.entity.BizFolderAcl;
import com.ycmn.module.file.mapper.BizFileFolderMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.file.mapper.BizFolderAclMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderPermissionService {

    // ==================== 九项权限模型 v2（2026-08-27 用户拍板：文件夹操作与文件操作分离）====================
    // 文件夹操作
    public static final String FOLDER_CREATE = "folder:create";   // 创建文件夹
    public static final String FOLDER_RENAME = "folder:rename";   // 重命名文件夹
    public static final String FOLDER_DELETE = "folder:delete";   // 删除文件夹
    // 文件操作
    public static final String FILE_UPLOAD = "file:upload";       // 上传文件
    public static final String FILE_DOWNLOAD = "file:download";   // 下载文件
    public static final String FILE_VIEW = "file:view";           // 查看文件列表
    public static final String FILE_EDIT = "file:edit";           // 替换文件
    public static final String FILE_DELETE = "file:delete";       // 删除文件
    public static final String FILE_SHARE = "file:share";         // 分享文件
    /** 九项权限全量（admin/owner 全权限用） */
    private static final List<String> ALL_PERMS =
            List.of(FOLDER_CREATE, FOLDER_RENAME, FOLDER_DELETE,
                    FILE_UPLOAD, FILE_DOWNLOAD, FILE_VIEW, FILE_EDIT, FILE_DELETE, FILE_SHARE);
    // ==================== 旧 6 项常量（兼容别名，V23 迁移后删除）====================
    /** @deprecated 旧 upload 语义已拆分为 FILE_UPLOAD + FOLDER_CREATE */
    @Deprecated
    public static final String P_VIEW = "view";
    /** @deprecated 旧 upload 语义已拆分为 FILE_UPLOAD + FOLDER_CREATE */
    @Deprecated
    public static final String P_UPLOAD = "upload";
    /** @deprecated 旧 edit 语义已拆分为 FILE_EDIT + FOLDER_RENAME */
    @Deprecated
    public static final String P_EDIT = "edit";
    /** @deprecated 旧 delete 语义已拆分为 FILE_DELETE + FOLDER_DELETE */
    @Deprecated
    public static final String P_DELETE = "delete";
    /** @deprecated 旧 share 语义 = FILE_SHARE */
    @Deprecated
    public static final String P_SHARE = "share";
    /** @deprecated 旧 download 语义 = FILE_DOWNLOAD */
    @Deprecated
    public static final String P_DOWNLOAD = "download";
    private static final int MAX_HOP = 5;

    private final BizFolderAclMapper aclMapper;
    private final BizFileFolderMapper folderMapper;
    private final BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;

    /** 文件夹权限判定：管理员>本人>直属上级>ACL角色>公共仅查看 */
    public boolean check(Long folderId, String action, Long userId) {
        if (folderId == null) return false;
        if (SecurityUtils.isAdmin()) return true;
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return false;
        if (Objects.equals(folder.getOwnerId(), userId)) return true;
        // 【九项权限模型 v2】上级链：私人数据默认只读(view+download)，不再全放行；
        // 公共数据维持上级链管理权限（公共夹是组织资产，上级可代管）。
        if (isManagerChain(userId, folder.getOwnerId())) {
            boolean isPrivate = "private".equals(folder.getFolderType());
            if (!isPrivate) return true;
            return FILE_VIEW.equals(action) || FILE_DOWNLOAD.equals(action);
        }
        if (aclGranted(folderId, userId, action)) return true;
        // 【文件权限体系】公共文件夹兜底扩展至 download：全员可见，下载开关允许时全员可下载
        // （开关在 downloadById 控制，管理员全权不受开关约束）
        return (FILE_VIEW.equals(action) || FILE_DOWNLOAD.equals(action)) && "public".equals(folder.getFolderType());
    }

    /** 文件权限判定：取 folderId 判定；无文件夹按上传者归属 */
    public boolean checkFile(Long fileId, String action, Long userId) {
        if (SecurityUtils.isAdmin()) return true;
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) return false;
        if (file.getFolderId() != null) return check(file.getFolderId(), action, userId);
        if (Objects.equals(file.getUploaderId(), userId)) return true;
        // 【九项权限模型 v2】无文件夹的私人文件：上级链仅只读(view/download)
        if (isManagerChain(userId, file.getUploaderId())) {
            return FILE_VIEW.equals(action) || FILE_DOWNLOAD.equals(action);
        }
        return false;
    }

    /** 个人上级(manager_id)优先，部门 leader 链兜底（仅当 lower 的 manager_id 为空），各限 5 层（设计 §6） */
    public boolean isManagerChain(Long upperId, Long lowerId) {
        if (upperId == null || lowerId == null) return false;
        SysUser lower = userMapper.selectById(lowerId);
        if (lower == null) return false;
        SysUser cur = lower;
        for (int i = 0; i < MAX_HOP && cur != null; i++) {
            if (cur.getManagerId() != null) {
                if (cur.getManagerId().equals(upperId)) return true;
                cur = userMapper.selectById(cur.getManagerId());
            } else break;
        }
        // 部门 leader 链兜底：仅当 lower 的 manager_id 为空（设计 §6：个人上级优先，未设置才走部门链）
        if (lower.getManagerId() == null && lower.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(lower.getDeptId());
            for (int i = 0; i < MAX_HOP && dept != null; i++) {
                if (dept.getLeaderId() != null && dept.getLeaderId().equals(upperId)) return true;
                if (dept.getParentId() == null || dept.getParentId() == 0) break;
                dept = deptMapper.selectById(dept.getParentId());
            }
        }
        return false;
    }

    /** 当前用户对文件夹的权限列表（前端按钮控制） */
    public List<String> permsFor(Long folderId, Long userId) {
        if (folderId == null) return List.of();
        if (SecurityUtils.isAdmin()) return ALL_PERMS;
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return List.of();
        if (Objects.equals(folder.getOwnerId(), userId)) {
            return ALL_PERMS;
        }
        if (isManagerChain(userId, folder.getOwnerId())) {
            // 【九项权限模型 v2】上级链：私人数据只读，公共数据全权限
            if ("private".equals(folder.getFolderType())) {
                return List.of(FILE_VIEW, FILE_DOWNLOAD);
            }
            return ALL_PERMS;
        }
        List<String> result = new ArrayList<>();
        for (BizFolderAcl acl : aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId))) {
            if (userHasRole(userId, acl.getRoleId())) {
                for (String p : acl.getPerms().split(",")) if (!result.contains(p)) result.add(p);
            }
        }
        // 【文件权限体系】公共文件夹对全员始终可见（ACL 只叠加管理权限，未匹配角色的用户同样可见），
        // 下载权限一并兜底（是否实际可下载由下载接口的开关约束）
        if ("public".equals(folder.getFolderType())) {
            if (!result.contains(FILE_VIEW)) result.add(FILE_VIEW);
            if (!result.contains(FILE_DOWNLOAD)) result.add(FILE_DOWNLOAD);
        }
        return result;
    }

    /** 批量权限视图：getFolderTree 组装时使用。ACL 一次批量查询，上级链按 owner 缓存，避免逐文件夹 N+1 */
    public Map<Long, List<String>> permsForBatch(Collection<BizFileFolder> folders, Long userId) {
        Map<Long, List<String>> result = new HashMap<>();
        if (folders == null || folders.isEmpty()) return result;
        if (SecurityUtils.isAdmin()) {
            for (BizFileFolder f : folders) result.put(f.getId(), ALL_PERMS);
            return result;
        }
        Set<Long> ids = folders.stream().map(BizFileFolder::getId).collect(Collectors.toSet());
        Map<Long, List<BizFolderAcl>> aclByFolder = new HashMap<>();
        for (BizFolderAcl acl : aclMapper.selectList(
                new LambdaQueryWrapper<BizFolderAcl>().in(BizFolderAcl::getFolderId, ids))) {
            aclByFolder.computeIfAbsent(acl.getFolderId(), k -> new ArrayList<>()).add(acl);
        }
        // 上级链按 owner 缓存：同一 owner 的多个文件夹只爬一次链
        Map<Long, Boolean> manageCache = new HashMap<>();
        for (BizFileFolder folder : folders) {
            Long ownerId = folder.getOwnerId();
            boolean privileged = manageCache.computeIfAbsent(ownerId, oid ->
                    oid != null && (oid.equals(userId) || isManagerChain(userId, oid)));
            List<String> perms = new ArrayList<>();
            if (privileged) {
                // 【九项权限模型 v2】上级链对私人数据只读（owner 本人仍全权限）
                boolean isPrivate = "private".equals(folder.getFolderType());
                if (ownerId != null && !ownerId.equals(userId) && isPrivate) {
                    perms.add(FILE_VIEW);
                    perms.add(FILE_DOWNLOAD);
                } else {
                    perms.addAll(ALL_PERMS);
                }
            } else {
                for (BizFolderAcl acl : aclByFolder.getOrDefault(folder.getId(), Collections.emptyList())) {
                    if (userHasRole(userId, acl.getRoleId())) {
                        for (String p : acl.getPerms().split(",")) if (!perms.contains(p)) perms.add(p);
                    }
                }
                // 公共文件夹对全员始终可见，下载权限一并兜底（是否可下载由下载接口开关约束）
                if ("public".equals(folder.getFolderType())) {
                    if (!perms.contains(FILE_VIEW)) perms.add(FILE_VIEW);
                    if (!perms.contains(FILE_DOWNLOAD)) perms.add(FILE_DOWNLOAD);
                }
            }
            result.put(folder.getId(), perms);
        }
        return result;
    }

    private boolean aclGranted(Long folderId, Long userId, String action) {
        for (BizFolderAcl acl : aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId))) {
            if (userHasRole(userId, acl.getRoleId())) {
                for (String p : acl.getPerms().split(",")) {
                    String trimmed = p.trim();
                    // 【九项权限模型 v2】新权限码直接比对；旧码(V23 迁移前)按映射兼容
                    if (trimmed.equals(action)) return true;
                    if (legacyPermMatches(trimmed, action)) return true;
                }
            }
        }
        return false;
    }

    /** 旧 6 项权限码 → 新 9 项映射兼容（V23 迁移完成后此方法可删） */
    private boolean legacyPermMatches(String legacy, String action) {
        return switch (legacy) {
            case "upload" -> FILE_UPLOAD.equals(action) || FOLDER_CREATE.equals(action);
            case "edit" -> FILE_EDIT.equals(action) || FOLDER_RENAME.equals(action);
            case "delete" -> FILE_DELETE.equals(action) || FOLDER_DELETE.equals(action);
            case "share" -> FILE_SHARE.equals(action);
            case "download" -> FILE_DOWNLOAD.equals(action);
            case "view" -> FILE_VIEW.equals(action);
            default -> false;
        };
    }

    private boolean userHasRole(Long userId, Long roleId) {
        // SecurityUser 只携带 roleCodes（角色 code），ACL 存 roleId → 解析 roleId 为 code 后比对
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || role.getRoleCode() == null) return false;
        return SecurityUtils.getCurrentRoleCodes().contains(role.getRoleCode());
    }
}
