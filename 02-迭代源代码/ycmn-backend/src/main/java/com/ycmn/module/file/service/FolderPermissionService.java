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

    public static final String P_VIEW = "view";
    public static final String P_UPLOAD = "upload";
    public static final String P_EDIT = "edit";
    public static final String P_DELETE = "delete";
    public static final String P_SHARE = "share";
    public static final String P_DOWNLOAD = "download";
    private static final List<String> ALL_PERMS =
            List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD);
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
        if (isManagerChain(userId, folder.getOwnerId())) return true;
        if (aclGranted(folderId, userId, action)) return true;
        // 【文件权限体系】公共文件夹兜底扩展至 download：全员可见，下载开关允许时全员可下载
        // （开关在 downloadById 控制，管理员全权不受开关约束）
        return (P_VIEW.equals(action) || P_DOWNLOAD.equals(action)) && "public".equals(folder.getFolderType());
    }

    /** 文件权限判定：取 folderId 判定；无文件夹按上传者归属 */
    public boolean checkFile(Long fileId, String action, Long userId) {
        if (SecurityUtils.isAdmin()) return true;
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) return false;
        if (file.getFolderId() != null) return check(file.getFolderId(), action, userId);
        if (Objects.equals(file.getUploaderId(), userId)) return true;
        return isManagerChain(userId, file.getUploaderId());
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
        if (SecurityUtils.isAdmin()) return List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD);
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return List.of();
        if (Objects.equals(folder.getOwnerId(), userId) || isManagerChain(userId, folder.getOwnerId())) {
            return List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD);
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
            if (!result.contains(P_VIEW)) result.add(P_VIEW);
            if (!result.contains(P_DOWNLOAD)) result.add(P_DOWNLOAD);
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
                perms.addAll(ALL_PERMS);
            } else {
                for (BizFolderAcl acl : aclByFolder.getOrDefault(folder.getId(), Collections.emptyList())) {
                    if (userHasRole(userId, acl.getRoleId())) {
                        for (String p : acl.getPerms().split(",")) if (!perms.contains(p)) perms.add(p);
                    }
                }
                // 公共文件夹对全员始终可见，下载权限一并兜底（是否可下载由下载接口开关约束）
                if ("public".equals(folder.getFolderType())) {
                    if (!perms.contains(P_VIEW)) perms.add(P_VIEW);
                    if (!perms.contains(P_DOWNLOAD)) perms.add(P_DOWNLOAD);
                }
            }
            result.put(folder.getId(), perms);
        }
        return result;
    }

    private boolean aclGranted(Long folderId, Long userId, String action) {
        for (BizFolderAcl acl : aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId))) {
            if (userHasRole(userId, acl.getRoleId())) {
                for (String p : acl.getPerms().split(",")) if (p.equals(action)) return true;
            }
        }
        return false;
    }

    private boolean userHasRole(Long userId, Long roleId) {
        // SecurityUser 只携带 roleCodes（角色 code），ACL 存 roleId → 解析 roleId 为 code 后比对
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || role.getRoleCode() == null) return false;
        return SecurityUtils.getCurrentRoleCodes().contains(role.getRoleCode());
    }
}
