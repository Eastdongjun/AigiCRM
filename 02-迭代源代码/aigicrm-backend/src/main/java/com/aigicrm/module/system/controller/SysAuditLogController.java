package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.SysAuditLogService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 【BS-R 2026-09-19】安全审计日志只读查询(事后可追溯)。
 * 表为 append-only(库层触发器拒绝改删);本端点只读,行数据经服务层转 Map
 * (控制器不依赖 entity/mapper,分层约束)。权限与操作日志查看同码(system:log:view)。
 */
@RestController
@RequestMapping("/api/system/audit-logs")
@RequiredArgsConstructor
public class SysAuditLogController {

    private final SysAuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:log:view')")
    public Result<IPage<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long actorId) {
        return Result.ok(auditLogService.page(current, size, eventType, actorId));
    }
}
