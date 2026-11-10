package com.aigicrm.module.file.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.file.service.FolderAclService;
import com.aigicrm.module.file.service.FolderTypeDefaultService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 【2026-11-10 文件权限终版】文件授权控制器。
 * - 例外授权(type-defaults 五端点): 权限中心「文件权限」域读写/续期——对象组 FOLDER|FILE、
 *   逐夹定向、组织范围、必填过期时间; 全挂 file:acl:manage。
 * - 行级按钮权限({id}/permissions): file:view(与文件夹树 perms 同读权限)。
 * - ACL 角色字典(acl-roles): 授权弹窗角色下拉复用(轻量端点, 最小暴露面)。
 * - 【退役】逐夹 ACL 读写端点(listAcl/saveAcl/removeAcl)——ACL 已由 V2026.11.10.1 转换为
 *   folder_id 定向授权行, 配置一律走例外授权; biz_folder_acl 表冻结留档防回滚。
 */
@RestController
@RequestMapping("/api/file/folders")
@RequiredArgsConstructor
public class FolderAclController {
    private final FolderAclService aclService;
    private final FolderTypeDefaultService grantService;

    @GetMapping("/{id}/permissions")
    // 【2026-11-01 审计修复】行级按钮权限的读取端点: PC 文件中心每次点选文件夹都调它做
    // 替换/删除/下载按钮显隐——挂 file:view(与 folder 树内嵌 perms 同读权限)。
    @PreAuthorize("hasAuthority('file:view')")
    public Result<Map<String, Object>> permissions(@PathVariable Long id) {
        return Result.ok(aclService.permissions(id, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 【体检 P1 2026-10-12】授权面专用的轻量角色字典: 只暴露 id+名称(最小面),
     * 按授权自身权限(file:acl:manage)放行。
     */
    @GetMapping("/acl-roles")
    @PreAuthorize("hasAuthority('file:acl:manage')")
    public Result<List<Map<String, Object>>> aclRoles() {
        return Result.ok(aclService.listAclRoles());
    }

    // ==================== 例外授权(唯一加授通道) ====================

    @PreAuthorize("hasAuthority('file:acl:manage')")
    @GetMapping("/type-defaults")
    public Result<List<Map<String, Object>>> listTypeDefaults() {
        return Result.ok(grantService.listGrants());
    }

    /** body: { folderType, objectType: FOLDER|FILE, folderId?, scopeOrgId?, subjectType: ROLE|USER, subjectId, perms: [...], expiresAt } */
    @PreAuthorize("hasAuthority('file:acl:manage')")
    @PostMapping("/type-defaults")
    public Result<Void> saveTypeDefault(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) throw new com.aigicrm.common.BusinessException("参数错误");
        Object rawSubjectId = body.get("subjectId");
        Object rawPerms = body.get("perms");
        Object rawExpires = body.get("expiresAt");
        if (rawSubjectId == null || !(rawPerms instanceof List)) {
            throw new com.aigicrm.common.BusinessException("参数错误(主体/动作必填)");
        }
        Long subjectId;
        try { subjectId = Long.valueOf(rawSubjectId.toString().trim()); } catch (NumberFormatException e) {
            throw new com.aigicrm.common.BusinessException("参数错误");
        }
        Long folderId = parseOrNull(body.get("folderId"));
        Long scopeOrgId = parseOrNull(body.get("scopeOrgId"));
        LocalDateTime expiresAt = null;
        if (rawExpires != null && String.valueOf(rawExpires).trim().length() >= 10) {
            try {
                expiresAt = LocalDateTime.parse(String.valueOf(rawExpires).replace(" ", "T").substring(0, 19));
            } catch (Exception e) {
                throw new com.aigicrm.common.BusinessException("到期时间格式错误(应为 yyyy-MM-dd HH:mm:ss)");
            }
        }
        List<String> perms = ((List<?>) rawPerms).stream().map(String::valueOf).toList();
        grantService.saveGrant(
                str(body.get("folderType")), str(body.get("objectType")), folderId, scopeOrgId, str(body.get("scopeLevel")),
                str(body.get("subjectType")), subjectId, perms, expiresAt);
        return Result.ok();
    }

    /** 续期(顺延至指定时刻) */
    @PreAuthorize("hasAuthority('file:acl:manage')")
    @PutMapping("/type-defaults/{id}/renew")
    public Result<Void> renewTypeDefault(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object rawExpires = body == null ? null : body.get("expiresAt");
        if (rawExpires == null) throw new com.aigicrm.common.BusinessException("参数错误");
        LocalDateTime expiresAt;
        try {
            expiresAt = LocalDateTime.parse(String.valueOf(rawExpires).replace(" ", "T").substring(0, 19));
        } catch (Exception e) {
            throw new com.aigicrm.common.BusinessException("过期时间格式错误");
        }
        grantService.renewGrant(id, expiresAt);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:acl:manage')")
    @DeleteMapping("/type-defaults/{id}")
    public Result<Void> removeTypeDefault(@PathVariable Long id) {
        grantService.removeGrant(id);
        return Result.ok();
    }

    private static Long parseOrNull(Object raw) {
        if (raw == null) return null;
        try { return Long.valueOf(String.valueOf(raw).trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String str(Object raw) { return raw == null ? null : String.valueOf(raw); }
}
