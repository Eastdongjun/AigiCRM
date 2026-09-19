package com.aigicrm.module.file.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.file.service.FileReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 【PR-19 2026-09-19】附件核对端点(管理操作)。
 * 权限:file:manage(或 admin)——核对是数据治理,非普通用户自服务。
 */
@RestController
@RequestMapping("/api/file/reconciliation")
@RequiredArgsConstructor
public class FileReconciliationController {

    private final FileReconciliationService reconciliationService;

    /** 全量对账:元数据 vs 磁盘,返回汇总+缺失清单(前 N 条) */
    @GetMapping
    @PreAuthorize("hasAuthority('file:folder:manage')")
    public Result<Map<String, Object>> reconcile(
            @RequestParam(defaultValue = "200") int detailLimit) {
        return Result.ok(reconciliationService.reconcile(Math.min(detailLimit, 500)));
    }
}
