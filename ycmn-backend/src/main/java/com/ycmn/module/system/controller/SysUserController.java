package com.ycmn.module.system.controller;

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

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:view')")
    public Result<IPage<SysUser>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deptIds,
            @RequestParam(required = false) Integer status) {
        return Result.ok(userService.page(current, size, keyword, deptIds, status));
    }

    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    public Result<Void> create(@RequestBody SysUser user) {
        userService.create(user);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        userService.update(user);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:edit')")
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
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }
}
