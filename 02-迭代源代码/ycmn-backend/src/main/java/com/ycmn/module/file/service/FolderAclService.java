package com.ycmn.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.entity.BizFolderAcl;
import com.ycmn.module.file.mapper.BizFileFolderMapper;
import com.ycmn.module.file.mapper.BizFolderAclMapper;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderAclService {
    private static final Set<String> ALLOWED = Set.of(
        FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
        FolderPermissionService.P_EDIT, FolderPermissionService.P_DELETE,
        FolderPermissionService.P_SHARE, FolderPermissionService.P_DOWNLOAD);

    private final BizFolderAclMapper aclMapper;
    private final BizFileFolderMapper folderMapper;
    private final SysRoleMapper roleMapper;
    private final FolderPermissionService permissionService;

    public List<Map<String, Object>> listAcl(Long folderId) {
        requireEdit(folderId);
        return aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>()
                .eq(BizFolderAcl::getFolderId, folderId)).stream().map(acl -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleId", acl.getRoleId());
            SysRole role = roleMapper.selectById(acl.getRoleId());
            row.put("roleName", role != null ? role.getRoleName() : "未知角色");
            row.put("perms", Arrays.asList(acl.getPerms().split(",")));
            return row;
        }).collect(Collectors.toList());
    }

    /**
     * 【Task 5 审查遗留】controller 原始 body 解析入口：body/roleId/perms 缺省或类型非法
     * （roleId 非数字 / perms 非数组）一律抛 BusinessException("参数错误")，
     * 避免 NumberFormatException/ClassCastException/NPE 冒泡成 500；解析成功后走 saveAcl 原有校验。
     */
    public void saveAclFromBody(Long folderId, Map<String, Object> body) {
        if (body == null) throw new BusinessException("参数错误");
        Object rawRoleId = body.get("roleId");
        if (rawRoleId == null) throw new BusinessException("参数错误");
        Long roleId;
        try {
            roleId = Long.valueOf(rawRoleId.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("参数错误");
        }
        Object rawPerms = body.get("perms");
        if (!(rawPerms instanceof List)) {
            throw new BusinessException("参数错误");
        }
        List<String> perms = ((List<?>) rawPerms).stream().map(String::valueOf).collect(Collectors.toList());
        saveAcl(folderId, roleId, perms);
    }

    public void saveAcl(Long folderId, Long roleId, List<String> perms) {
        requireEdit(folderId);
        if (roleId == null) throw new BusinessException("角色不能为空");
        if (perms == null || perms.isEmpty()) throw new BusinessException("权限不能为空");
        for (String p : perms) if (!ALLOWED.contains(p)) throw new BusinessException("非法权限项: " + p);
        // upsert
        BizFolderAcl existing = aclMapper.selectOne(new LambdaQueryWrapper<BizFolderAcl>()
                .eq(BizFolderAcl::getFolderId, folderId).eq(BizFolderAcl::getRoleId, roleId));
        String joined = String.join(",", perms);
        if (existing != null) { existing.setPerms(joined); aclMapper.updateById(existing); }
        else {
            BizFolderAcl acl = new BizFolderAcl();
            acl.setFolderId(folderId); acl.setRoleId(roleId); acl.setPerms(joined);
            acl.setCreatedBy(SecurityUtils.getCurrentUserId()); acl.setCreatedAt(LocalDateTime.now());
            aclMapper.insert(acl);
        }
    }

    public void removeAcl(Long folderId, Long roleId) {
        requireEdit(folderId);
        aclMapper.delete(new LambdaQueryWrapper<BizFolderAcl>()
                .eq(BizFolderAcl::getFolderId, folderId).eq(BizFolderAcl::getRoleId, roleId));
    }

    public void setDownloadable(Long folderId, Integer downloadable) {
        requireEdit(folderId);
        if (downloadable == null || (downloadable != 0 && downloadable != 1)) throw new BusinessException("非法开关值");
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) throw new BusinessException("文件夹不存在");
        folder.setDownloadable(downloadable);
        folderMapper.updateById(folder);
    }

    public Map<String, Object> permissions(Long folderId, Long userId) {
        BizFileFolder folder = folderMapper.selectById(folderId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("perms", folder != null ? permissionService.permsFor(folderId, userId) : List.of());
        row.put("downloadable", folder != null && folder.getDownloadable() != null ? folder.getDownloadable() : 1);
        return row;
    }

    private void requireEdit(Long folderId) {
        if (folderId == null) throw new BusinessException("文件夹不能为空");
        if (!permissionService.check(folderId, FolderPermissionService.P_EDIT, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
    }
}
