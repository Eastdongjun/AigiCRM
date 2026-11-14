package com.aigicrm.module.file.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.RecycleService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【V2026.11.14.1 我的回收站】(用户拍板 2026-11-14 "删除→到回收站，可以恢复"):
 * 普通用户视角的文件/文件夹回收站——此前恢复只有平台管理员能做(system/recycle 页),
 * 用户删了文件自己找不回。本控制器=本人删除物(deleted_by 归属)的 查看恢复/彻底删除 + 批量。
 * 权限码=file:delete(删除者天然持有, 不新增码); admin 走任意端点均放行(全量视角仍走 system/recycle)。
 */
@RestController
@RequestMapping("/api/file/recycle")
@RequiredArgsConstructor
public class FileRecycleController {

    private final RecycleService recycleService;

    /** 归属闸门: 非 admin 只能动自己删除的行(deleted_by=me); admin 全量 */
    private void requireOwner(Long deletedBy) {
        if (!SecurityUtils.isAdmin() && !SecurityUtils.getCurrentUserId().equals(deletedBy)) {
            throw new BusinessException(403, "只能恢复/彻底删除自己删除的项目");
        }
    }

    @PreAuthorize("hasAuthority('file:delete')")
    @GetMapping("/files")
    public Result<RecycleService.PageResult<RecycleService.DeletedFileRow>> myDeletedFiles(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        if (SecurityUtils.isAdmin()) {
            return Result.ok(recycleService.deletedFiles(current, size, keyword));
        }
        return Result.ok(recycleService.deletedFilesBy(SecurityUtils.getCurrentUserId(), current, size, keyword));
    }

    @PreAuthorize("hasAuthority('file:delete')")
    @GetMapping("/folders")
    public Result<RecycleService.PageResult<RecycleService.DeletedFolderRow>> myDeletedFolders(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        if (SecurityUtils.isAdmin()) {
            return Result.ok(recycleService.deletedFolders(current, size, keyword));
        }
        return Result.ok(recycleService.deletedFoldersBy(SecurityUtils.getCurrentUserId(), current, size, keyword));
    }

    @PreAuthorize("hasAuthority('file:delete')")
    @PostMapping("/files/{id}/restore")
    public Result<Map<String, String>> restoreFile(@PathVariable Long id) {
        requireOwner(recycleService.fileDeletedBy(id));
        return Result.ok(Map.of("message", recycleService.restoreFile(id)));
    }

    @PreAuthorize("hasAuthority('file:purge')")
    @PostMapping("/files/{id}/purge")
    public Result<Map<String, String>> purgeFile(@PathVariable Long id) {
        requireOwner(recycleService.fileDeletedBy(id));
        return Result.ok(Map.of("message", recycleService.hardDeleteFile(id)));
    }

    @PreAuthorize("hasAuthority('file:delete')")
    @PostMapping("/folders/{id}/restore")
    public Result<Map<String, String>> restoreFolder(@PathVariable Long id) {
        requireOwner(recycleService.folderDeletedBy(id));
        return Result.ok(Map.of("message", recycleService.restoreFolder(id)));
    }

    @PreAuthorize("hasAuthority('file:purge')")
    @PostMapping("/folders/{id}/purge")
    public Result<Map<String, String>> purgeFolder(@PathVariable Long id) {
        requireOwner(recycleService.folderDeletedBy(id));
        return Result.ok(Map.of("message", recycleService.hardDeleteFolder(id)));
    }

    /** 批量恢复(文件+夹混合 ids): 逐条 try/catch, 部分成功不回滚已成功项, 返回逐条结果 */
    @PreAuthorize("hasAuthority('file:delete')")
    @PostMapping("/batch-restore")
    public Result<Map<String, Object>> batchRestore(@RequestBody Map<String, List<Long>> body) {
        List<Long> fileIds = body.getOrDefault("fileIds", List.of());
        List<Long> folderIds = body.getOrDefault("folderIds", List.of());
        Map<String, String> results = new LinkedHashMap<>();
        int ok = 0;
        for (Long id : fileIds) {
            try {
                requireOwner(recycleService.fileDeletedBy(id));
                recycleService.restoreFile(id);
                results.put("file:" + id, "OK");
                ok++;
            } catch (Exception e) {
                results.put("file:" + id, e.getMessage() == null ? "失败" : e.getMessage());
            }
        }
        for (Long id : folderIds) {
            try {
                requireOwner(recycleService.folderDeletedBy(id));
                recycleService.restoreFolder(id);
                results.put("folder:" + id, "OK");
                ok++;
            } catch (Exception e) {
                results.put("folder:" + id, e.getMessage() == null ? "失败" : e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("total", fileIds.size() + folderIds.size());
        out.put("results", results);
        return Result.ok(out);
    }

    /** 批量彻底删除(文件+夹混合 ids): 逐条归属校验+守卫(激励佐证跳过), 返回逐条结果 */
    @PreAuthorize("hasAuthority('file:purge')")
    @PostMapping("/batch-purge")
    public Result<Map<String, Object>> batchPurge(@RequestBody Map<String, List<Long>> body) {
        List<Long> fileIds = body.getOrDefault("fileIds", List.of());
        List<Long> folderIds = body.getOrDefault("folderIds", List.of());
        Map<String, String> results = new LinkedHashMap<>();
        int ok = 0;
        for (Long id : fileIds) {
            try {
                requireOwner(recycleService.fileDeletedBy(id));
                recycleService.hardDeleteFile(id);
                results.put("file:" + id, "OK");
                ok++;
            } catch (Exception e) {
                results.put("file:" + id, e.getMessage() == null ? "失败" : e.getMessage());
            }
        }
        for (Long id : folderIds) {
            try {
                requireOwner(recycleService.folderDeletedBy(id));
                recycleService.hardDeleteFolder(id);
                results.put("folder:" + id, "OK");
                ok++;
            } catch (Exception e) {
                results.put("folder:" + id, e.getMessage() == null ? "失败" : e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("total", fileIds.size() + folderIds.size());
        out.put("results", results);
        return Result.ok(out);
    }
}
