package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.RecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 【回收站 2026-10-18】已删除客户/人员的查看与恢复(独立页面, 不改动现有列表口径)。
 * 权限默认仅系统管理员(system:recycle:view / system:recycle:restore), 其他角色由管理员在角色管理勾选授予。
 */
@RestController
@RequestMapping("/api/system/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/customers")
    public Result<RecycleService.PageResult<RecycleService.DeletedCustomerRow>> deletedCustomers(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedCustomers(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/customers/{id}/restore")
    public Result<Map<String, String>> restoreCustomer(@PathVariable Long id) {
        return Result.ok(Map.of("message", recycleService.restoreCustomer(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/users")
    public Result<RecycleService.PageResult<RecycleService.DeletedUserRow>> deletedUsers(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedUsers(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/users/{id}/restore")
    public Result<Map<String, String>> restoreUser(@PathVariable Long id) {
        return Result.ok(Map.of("message", recycleService.restoreUser(id)));
    }
}
