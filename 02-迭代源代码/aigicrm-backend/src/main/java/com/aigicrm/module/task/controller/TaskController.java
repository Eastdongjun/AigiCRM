package com.aigicrm.module.task.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.task.service.TaskService;
import com.aigicrm.module.task.service.TaskViewService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 【PR-10 2026-09-19】任务控制器。
 * 权限:task:view 查看(登录即可看自己的);task:manage 管理(创建/完成/转交)。
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskViewService taskViewService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('task:view')")
    public Result<IPage<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Boolean overdue) {
        return Result.ok(taskViewService.toPage(
                taskService.page(current, size, status, assigneeId, customerId, overdue)));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('task:view')")
    public Result<Map<String, Long>> statistics(
            @RequestParam(required = false) Long userId) {
        return Result.ok(taskService.statistics(userId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('task:manage')")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.ok(taskViewService.toRow(taskService.createFromMap(body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('task:manage')")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        taskService.updateFromMap(id, body);
        return Result.ok();
    }

    /** 完成任务(必须带结果) */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('task:manage')")
    public Result<Void> complete(@PathVariable Long id, @RequestBody Map<String, String> body) {
        taskService.complete(id, body.get("completionNote"));
        return Result.ok();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('task:manage')")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        taskService.cancel(id, body.get("reason"));
        return Result.ok();
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('task:manage')")
    public Result<Void> transfer(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        taskService.transfer(id, body.get("newAssigneeId"));
        return Result.ok();
    }
}
