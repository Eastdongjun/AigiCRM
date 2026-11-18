package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【回收站 2026-10-18】已删除客户/人员的查看与恢复(独立页面, 不改动现有列表口径)。
 * 权限: 每个端点由 system:recycle:* 权限码控制(权限中心勾选);
 * 定时任务直调 RecycleService, 不经过本控制器。
 */
@RestController
@RequestMapping("/api/system/recycle")
@RequiredArgsConstructor
public class RecycleController {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_MESSAGE = "message";


    private final RecycleService recycleService;

    /** 【产品口径 2026-11-15】回收站恢复/清空仅系统管理员可操作 */
    private void requireAdmin() {
        if (!com.aigicrm.security.SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "仅系统管理员可操作回收站");
        }
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/customers")
    public Result<RecycleService.PageResult<RecycleService.DeletedCustomerRow>> deletedCustomers(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedCustomers(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/customers/{id}")
    public Result<RecycleService.DeletedCustomerDetail> deletedCustomerDetail(@PathVariable Long id) {
        return Result.ok(recycleService.deletedCustomerDetail(id));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/customers/{id}/restore")
    public Result<Map<String, String>> restoreCustomer(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreCustomer(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/customers/{id}/purge")
    public Result<Map<String, String>> hardDeleteCustomer(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteCustomer(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/users")
    public Result<RecycleService.PageResult<RecycleService.DeletedUserRow>> deletedUsers(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedUsers(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/users/{id}")
    public Result<RecycleService.DeletedUserDetail> deletedUserDetail(@PathVariable Long id) {
        return Result.ok(recycleService.deletedUserDetail(id));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/users/{id}/restore")
    public Result<Map<String, String>> restoreUser(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreUser(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/users/{id}/purge")
    public Result<Map<String, String>> hardDeleteUser(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteUser(id)));
    }

    // ==================== 【2026-11-17 阶段三】任务/激励 ====================

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/tasks")
    public Result<RecycleService.PageResult<RecycleService.DeletedTaskRow>> deletedTasks(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedTasks(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/tasks/{id}/restore")
    public Result<Map<String, String>> restoreTask(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreTask(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/tasks/{id}/purge")
    public Result<Map<String, String>> hardDeleteTask(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteTask(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/reminders")
    public Result<RecycleService.PageResult<RecycleService.DeletedReminderRow>> deletedReminders(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedReminders(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/reminders/{id}/restore")
    public Result<Map<String, String>> restoreReminder(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreReminder(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/reminders/{id}/purge")
    public Result<Map<String, String>> hardDeleteReminder(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteReminder(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/incentives")
    public Result<RecycleService.PageResult<RecycleService.DeletedIncentiveRow>> deletedIncentives(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedIncentives(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/incentives/{id}/restore")
    public Result<Map<String, String>> restoreIncentive(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreIncentive(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/incentives/{id}/purge")
    public Result<Map<String, String>> hardDeleteIncentive(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteIncentive(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/files")
    public Result<RecycleService.PageResult<RecycleService.DeletedFileRow>> deletedFiles(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedFiles(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/files/{id}")
    public Result<RecycleService.DeletedFileDetail> deletedFileDetail(@PathVariable Long id) {
        return Result.ok(recycleService.deletedFileDetail(id));
    }

    /** 详情下载: 仅回收站查看权限, 按 deleted=1 行读出存储内容 */
    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/files/{id}/content")
    public void fileContent(@PathVariable Long id, jakarta.servlet.http.HttpServletResponse response) {
        recycleService.writeFileContent(id, response);
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/files/{id}/restore")
    public Result<Map<String, String>> restoreFile(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreFile(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/files/{id}/purge")
    public Result<Map<String, String>> hardDeleteFile(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteFile(id)));
    }

    /** 【文件回收站 2026-10-18】管理员手动清空: 物理删除全部已删文件的存储内容与台账行 */
    // ==================== 【V2026.11.14.3 P2 4.2】文件夹回收站(admin 全量视角) ====================

    @PreAuthorize("hasAuthority('system:recycle:view')")
    @GetMapping("/folders")
    public Result<RecycleService.PageResult<RecycleService.DeletedFolderRow>> deletedFolders(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(recycleService.deletedFolders(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('system:recycle:restore')")
    @PostMapping("/folders/{id}/restore")
    public Result<Map<String, String>> restoreFolder(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreFolder(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/folders/{id}/purge")
    public Result<Map<String, String>> hardDeleteFolder(@PathVariable Long id) {
        requireAdmin();
        return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteFolder(id)));
    }

    @PreAuthorize("hasAuthority('system:recycle:purge')")
    @PostMapping("/files/purge")
    public Result<Map<String, Object>> purgeFiles() {
        requireAdmin();
        RecycleService.PurgeFilesResult result = recycleService.purgeAllDeletedFiles();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(LIT_MESSAGE, "已清空文件回收站：清理 " + result.purged() + " 个，跳过 "
                + result.skipped() + " 个，失败 " + result.failed() + " 个");
        body.put("purged", result.purged());
        body.put("skipped", result.skipped());
        body.put("failed", result.failed());
        return Result.ok(body);
    }
}
