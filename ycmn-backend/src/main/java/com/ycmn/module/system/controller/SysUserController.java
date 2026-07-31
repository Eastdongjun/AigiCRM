package com.ycmn.module.system.controller;
import com.ycmn.common.BusinessException;
import com.ycmn.security.SecurityUtils;
import com.ycmn.module.system.service.SysOrgService;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.service.SysUserService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;
    private final SysOrgService orgService;

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
    public Result<Void> create(@RequestBody SysUser user) {
        userService.create(user);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        if (SecurityUtils.isOrgAdmin() && !orgService.canManageUser(id)) throw new BusinessException("无权操作该用户");
        user.setId(id);
        userService.update(user);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.ok();
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> params) {
        userService.changePassword(SecurityUtils.getCurrentUserId(),
                params.get("oldPassword"), params.get("newPassword"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }

    // ====== 离职/返聘/转移 ======

    @PostMapping("/{id}/leave")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Map<String, Object>> leave(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long transferToUserId = body.get("transferToUserId") != null ? Long.valueOf(body.get("transferToUserId").toString()) : null;
        String reason = (String) body.getOrDefault("reason", "");
        return Result.ok(userService.markLeave(id, transferToUserId, reason));
    }

    @PostMapping("/{id}/rehire")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Void> rehire(@PathVariable Long id) {
        userService.markRehire(id);
        return Result.ok();
    }

    @PostMapping("/{id}/transfer-customers")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Map<String, Object>> transferCustomers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long toUserId = Long.valueOf(body.get("toUserId").toString());
        return Result.ok(userService.transferCustomers(id, toUserId));
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:org:admin')")
    public Result<Void> permanentDelete(@PathVariable Long id, @RequestParam(defaultValue = "") String reason) {
        userService.permanentDelete(id, reason);
        return Result.ok();
    }

    @GetMapping("/archived")
    @PreAuthorize("hasAnyAuthority('system:user:view','system:org:admin')")
    public Result<List<Map<String, Object>>> listArchived() {
        return Result.ok(userService.listArchived());
    }
}
