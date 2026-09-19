package com.aigicrm.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService logService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:log:view')")
    public Result<IPage<SysLog>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword) {
        return Result.ok(logService.page(current, size, module, username, startDate, endDate, keyword));
    }

    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('system:log:view')")
    public Result<Map<String, Long>> counts(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(logService.countByModule(startDate, endDate));
    }
}
