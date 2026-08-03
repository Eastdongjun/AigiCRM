package com.ycmn.module.system.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysOrg;
import com.ycmn.module.system.service.SysOrgService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class SysOrgController {

    private final SysOrgService orgService;

    /** 获取组织树 */
    @GetMapping("/tree")
    public Result<List<SysOrg>> getOrgTree() {
        return Result.ok(orgService.getOrgTree());
    }

    /** 创建组织 */
    @PreAuthorize("hasAuthority('system:dept:add')")
    @PostMapping
    public Result<SysOrg> create(@RequestBody SysOrg org) {
        return Result.ok(orgService.create(org));
    }

    /** 更新组织 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysOrg org) {
        org.setId(id);
        orgService.update(org);
        return Result.ok();
    }

    /** 删除组织 */
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return Result.ok();
    }

    /** 获取组织管理员 */
    @GetMapping("/{orgId}/admins")
    @PreAuthorize("hasAuthority('system:dept:view')")
    public Result<List<Map<String, Object>>> getAdmins(@PathVariable Long orgId) {
        return Result.ok(orgService.getOrgAdmins(orgId));
    }

    /** 添加组织管理员 */
    @PostMapping("/{orgId}/admins")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public Result<Void> addAdmin(@PathVariable Long orgId, @RequestParam Long userId) {
        orgService.addOrgAdmin(orgId, userId);
        return Result.ok();
    }

    /** 移除组织管理员 */
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @DeleteMapping("/{orgId}/admins/{id}")
    public Result<Void> removeAdmin(@PathVariable Long orgId, @PathVariable Long id) {
        orgService.removeOrgAdmin(id);
        return Result.ok();
    }

    /** 获取当前用户管理范围 */
    @GetMapping("/my-scope")
    public Result<Map<String, Object>> getMyScope() {
        Map<String, Object> scope = new HashMap<>();
        scope.put("orgId", SecurityUtils.getCurrentOrgId());
        scope.put("dataScope", SecurityUtils.getCurrentDataScope());
        scope.put("managedOrgIds", SecurityUtils.getCurrentManagedOrgIds());
        return Result.ok(scope);
    }
}
