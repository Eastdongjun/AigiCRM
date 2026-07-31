package com.ycmn.module.reminder.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ycmn.common.Result;
import com.ycmn.module.reminder.entity.BizReminder;
import com.ycmn.module.reminder.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping("/expiring-list")
    public Result<IPage<Map<String, Object>>> expiringList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long ownerId) {
        return Result.ok(reminderService.expiringPage(current, size, keyword, officeId, ownerId));
    }

    @GetMapping("/list")
    public Result<IPage<BizReminder>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status) {
        return Result.ok(reminderService.page(current, size, type, keyword, officeId, ownerId, status));
    }

    @GetMapping("/pending-count")
    public Result<Long> pendingCount() {
        return Result.ok(reminderService.pendingCount());
    }

    @PreAuthorize("hasAuthority('reminder:view')")
    @PostMapping("/{id}/process")
    public Result<Void> process(@PathVariable Long id) {
        reminderService.process(id);
        return Result.ok();
    }
}
