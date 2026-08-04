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

@Service
@RequiredArgsConstructor
public class FolderPermissionService {

    public static final String P_VIEW = "view";
    public static final String P_UPLOAD = "upload";
    public static final String P_EDIT = "edit";
    public static final String P_DELETE = "delete";
    public static final String P_SHARE = "share";
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
        if (isManagerChain(userId, folder.getOwnerId())) return true;
        if (aclGranted(folderId, userId, action)) return true;
        return P_VIEW.equals(action) && "public".equals(folder.getFolderType());
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

    /** 个人上级(manager_id)优先，部门 leader 链兜底，各限 5 层 */
    public boolean isManagerChain(Long upperId, Long lowerId) {
        if (upperId == null || lowerId == null) return false;
        SysUser cur = userMapper.selectById(lowerId);
        for (int i = 0; i < MAX_HOP && cur != null; i++) {
            if (cur.getManagerId() != null) {
                if (cur.getManagerId().equals(upperId)) return true;
                cur = userMapper.selectById(cur.getManagerId());
            } else break;
        }
        SysUser lower = userMapper.selectById(lowerId);
        if (lower != null && lower.getDeptId() != null) {
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
        if (result.isEmpty() && "public".equals(folder.getFolderType())) result.add(P_VIEW);
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
