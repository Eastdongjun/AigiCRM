package com.ycmn.module.file.controller;

import com.ycmn.common.Result;
import com.ycmn.module.file.service.FolderAclService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/file/folders")
@RequiredArgsConstructor
public class FolderAclController {
    private final FolderAclService aclService;

    @GetMapping("/{id}/acl")
    @PreAuthorize("hasAuthority('file:acl:manage')")
    public Result<List<Map<String, Object>>> listAcl(@PathVariable Long id) { return Result.ok(aclService.listAcl(id)); }

    @PostMapping("/{id}/acl")
    @PreAuthorize("hasAuthority('file:acl:manage')")
    public Result<Void> saveAcl(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        // 【Task 5 审查遗留】畸形输入（缺 roleId/非数字/perms 缺失或非数组）由 service 解析抛 BusinessException，
        // 不再冒泡 500
        aclService.saveAclFromBody(id, body);
        return Result.ok();
    }

    @DeleteMapping("/{id}/acl/{roleId}")
    @PreAuthorize("hasAuthority('file:acl:manage')")
    public Result<Void> removeAcl(@PathVariable Long id, @PathVariable Long roleId) { aclService.removeAcl(id, roleId); return Result.ok(); }

    @PutMapping("/{id}/download")
    @PreAuthorize("hasAuthority('file:acl:manage')")
    public Result<Void> setDownloadable(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        aclService.setDownloadable(id, body.get("downloadable")); return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('file:acl:manage')")
    public Result<Map<String, Object>> permissions(@PathVariable Long id) {
        return Result.ok(aclService.permissions(id, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 【九项权限模型 v2 2026-08-28】ACL 弹窗角色下拉数据源（登录即可）：
     * 私人文件夹 owner 无 system:role:view 权限，调 /system/roles 会 403 导致授权弹窗角色为空；
     * 此接口返回全量角色 id+roleName 轻量视图，仅供 ACL 授权选择用。
     */
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> aclRoles() {
        return Result.ok(aclService.listRoles());
    }
}
