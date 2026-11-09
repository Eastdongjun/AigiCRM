package com.aigicrm.module.file.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.file.service.FolderAclService;
import com.aigicrm.module.file.service.FolderTypeDefaultService;
import com.aigicrm.security.SecurityUtils;
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
    private final FolderTypeDefaultService typeDefaultService;

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
    // 【2026-11-01 审计修复】行级按钮权限的读取端点: PC 文件中心每次点选文件夹都调它做
    // 替换/删除/下载按钮显隐——原挂 file:acl:manage(仅 admin/05/0/06), 普通用户必 403 弹窗,
    // 前端 catch 后按钮权限退化为默认全量。降为 file:view(与 folder 树内嵌 perms 同读权限);
    // ACL 写端点(saveAcl/removeAcl/setDownloadable/listAcl/aclRoles)仍维持 file:acl:manage。
    @PreAuthorize("hasAuthority('file:view')")
    public Result<Map<String, Object>> permissions(@PathVariable Long id) {
        return Result.ok(aclService.permissions(id, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 【体检 P1 2026-10-12】ACL 授权面专用的轻量角色字典: 弹窗原本拉 /system/roles(要求
     * system:role:view, 仅 admin/0 持有), 而 file:acl:manage 授 admin/05/0/06——05/06 与
     * 非 admin 夹主/上级链打开弹窗时角色下拉 403 被静默吞, 恒空且保存按钮恒禁用, 新增授权
     * 路径整体断裂。改走本端点按 ACL 自身权限放行, 只暴露 id+名称(最小面)。
     */
    @GetMapping("/acl-roles")
    @PreAuthorize("hasAuthority('file:acl:manage')")
    public Result<List<Map<String, Object>>> aclRoles() {
        return Result.ok(aclService.listAclRoles());
    }

    // 【2026-11-09 文件权限 v2】文件空间(公共/私人)类型授权三端点: 权限中心「文件权限」页签读写。
    // 主体 ROLE|USER(V2026.11.09.5): 管理员可点名某人或按角色整批; 授权实时生效(判定层 FolderPermissionService
    // .typeGranted), 不再建夹物化。词表=8 细动作(edit 伞已拆 rename/replace) + create(可建该类型根夹)。
    // 与逐夹 ACL 写端点同码 file:acl:manage。
    @PreAuthorize("hasAuthority('file:acl:manage')")
    @GetMapping("/type-defaults")
    public Result<List<Map<String, Object>>> listTypeDefaults() {
        return Result.ok(typeDefaultService.listDefaults());
    }

    /** body: { folderType: "public"|"private", subjectType: "ROLE"|"USER", subjectId, perms: ["view","rename","replace",...] } */
    @PreAuthorize("hasAuthority('file:acl:manage')")
    @PostMapping("/type-defaults")
    public Result<Void> saveTypeDefault(@RequestBody(required = false) Map<String, Object> body) {
        // 畸形输入(缺项/类型非法)由 service 校验抛 BusinessException, 不冒泡 500(同 saveAcl 口径)
        if (body == null) throw new com.aigicrm.common.BusinessException("参数错误");
        Object rawSubjectId = body.get("subjectId");
        Object rawPerms = body.get("perms");
        if (rawSubjectId == null || !(rawPerms instanceof List)) {
            throw new com.aigicrm.common.BusinessException("参数错误");
        }
        Long subjectId;
        try {
            subjectId = Long.valueOf(rawSubjectId.toString().trim());
        } catch (NumberFormatException e) {
            throw new com.aigicrm.common.BusinessException("参数错误");
        }
        List<String> perms = ((List<?>) rawPerms).stream()
                .map(String::valueOf).toList();
        String folderType = body.get("folderType") == null ? null : String.valueOf(body.get("folderType"));
        String subjectType = body.get("subjectType") == null ? null : String.valueOf(body.get("subjectType"));
        typeDefaultService.saveDefault(folderType, subjectType, subjectId, perms);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:acl:manage')")
    @DeleteMapping("/type-defaults/{folderType}/{subjectType}/{subjectId}")
    public Result<Void> removeTypeDefault(@PathVariable String folderType, @PathVariable String subjectType, @PathVariable Long subjectId) {
        typeDefaultService.removeDefault(folderType, subjectType, subjectId);
        return Result.ok();
    }
}
