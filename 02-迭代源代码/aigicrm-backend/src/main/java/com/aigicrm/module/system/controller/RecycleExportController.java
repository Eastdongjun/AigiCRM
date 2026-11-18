package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.RecycleExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 【A3 2026-11-18】回收站 ZIP 导出四端点（拍板 #2: system:recycle:export ADMIN_ONLY + 服务层 admin 双收口）。
 * 下载走流式（FileSystemResource），大 ZIP 不整包进堆。
 */
@RestController
@RequestMapping("/api/system/recycle/exports")
@RequiredArgsConstructor
public class RecycleExportController {

    private final RecycleExportService recycleExportService;

    @PreAuthorize("hasAuthority('system:recycle:export')")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody ExportRequest body) {
        return Result.ok(recycleExportService.create(body.types, body.filter));
    }

    @PreAuthorize("hasAuthority('system:recycle:export')")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(recycleExportService.list());
    }

    @PreAuthorize("hasAuthority('system:recycle:export')")
    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        Map<String, Object> d = recycleExportService.download(id);
        Path p = Paths.get(String.valueOf(d.get("filePath")));
        if (!Files.exists(p)) {
            return ResponseEntity.status(410).build();
        }
        String sha = String.valueOf(d.get("checksumSha256"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.get("fileName") + "\"")
                .header("X-Checksum-SHA256", sha)
                .contentLength(p.toFile().length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(p));
    }

    @PreAuthorize("hasAuthority('system:recycle:export')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recycleExportService.delete(id);
        return Result.ok();
    }

    /** 创建请求 DTO：types=六类白名单子集；filter=deletedFrom/deletedTo。 */
    public static class ExportRequest {
        public List<String> types;
        public Map<String, Object> filter;
    }
}
