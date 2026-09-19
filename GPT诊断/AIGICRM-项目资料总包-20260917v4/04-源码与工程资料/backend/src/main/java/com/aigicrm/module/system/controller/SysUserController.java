package com.aigicrm.module.system.controller;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.annotation.AigicrmLog;
import com.aigicrm.security.SecurityUtils;
import com.aigicrm.module.system.service.OrgAdminService;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;
    private final OrgAdminService orgAdminService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('system:user:view','system:org:admin')")
    public Result<IPage<SysUser>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deptIds,
            @RequestParam(required = false) Integer status) {
        return Result.ok(userService.page(current, size, keyword, deptIds, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('system:user:view','system:org:admin')")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('system:user:add','system:org:admin')")
    public Result<String> create(@RequestBody SysUser user) {
        // 组织管理员只能在管理范围内创建用户（新用户无 id，按目标组织校验）
        if (SecurityUtils.isOrgAdmin() && user.getOrgId() != null && !orgAdminService.canManageOrg(user.getOrgId())) {
            throw new BusinessException("无权在该组织下创建用户");
        }
        // 【2026-08-14 安全 P0】随机初始口令随响应返回，管理员转交用户并提醒首登改密
        return Result.ok(userService.create(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        if (SecurityUtils.isOrgAdmin() && !orgAdminService.canManageUser(id)) throw new BusinessException("无权操作该用户");
        user.setId(id);
        userService.update(user);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    @AigicrmLog(module = "人员", action = "重置密码", targetType = "user")
    public Result<String> resetPassword(@PathVariable Long id) {
        if (SecurityUtils.isOrgAdmin() && !orgAdminService.canManageUser(id)) throw new BusinessException("无权操作该用户");
        // 【2026-08-14 安全 P0】随机临时口令随响应返回，管理员转交用户并提醒首登改密
        return Result.ok(userService.resetPassword(id));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> params) {
        userService.changePassword(SecurityUtils.getCurrentUserId(),
                params.get("oldPassword"), params.get("newPassword"));
        return Result.ok();
    }

    @PreAuthorize("hasAnyAuthority('system:user:delete','system:org:admin')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (SecurityUtils.isOrgAdmin() && !orgAdminService.canManageUser(id)) throw new BusinessException("无权操作该用户");
        userService.deleteUser(id);
        return Result.ok();
    }
}
