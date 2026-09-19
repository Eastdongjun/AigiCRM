package com.aigicrm.module.reminder.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aigicrm.common.Result;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @PreAuthorize("hasAuthority('reminder:view')")
    @GetMapping("/expiring-list")
    public Result<IPage<Map<String, Object>>> expiringList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long deptId) {
        return Result.ok(reminderService.expiringPage(current, size, keyword, officeId, ownerId, deptId));
    }

    @PreAuthorize("hasAuthority('reminder:view')")
    @GetMapping("/list")
    public Result<IPage<BizReminder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deptId) {
        return Result.ok(reminderService.page(current, size, type, keyword, officeId, ownerId, status, deptId));
    }

    /** 提醒详情（提醒管理列表行点击下钻）；字面路径 /list、/expiring-list 匹配优先级高于本模板路径，不会冲突 */
    @PreAuthorize("hasAuthority('reminder:view')")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(reminderService.detail(id));
    }

    @PreAuthorize("hasAuthority('reminder:view')")
    @GetMapping("/pending-count")
    public Result<Long> pendingCount() {
        return Result.ok(reminderService.pendingCount());
    }

    @PreAuthorize("hasAuthority('reminder:view')")
    @PostMapping("/ensure-expiring")
    public Result<Void> ensureExpiring() {
        reminderService.ensureExpiring();
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('reminder:view')")
    @PostMapping("/{id}/process")
    public Result<Void> process(@PathVariable Long id) {
        reminderService.process(id);
        return Result.ok();
    }
}
