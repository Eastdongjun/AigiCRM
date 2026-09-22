package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.SysRuleVersionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 【PR-24 2026-09-19】规则版本只读查询(事后可追溯)。
 * 表为 append-only(触发器拒改删);权限与系统配置同码(system:config:view)。
 */
@RestController
@RequestMapping("/api/system/rule-versions")
@RequiredArgsConstructor
public class SysRuleVersionController {

    private final SysRuleVersionService ruleVersionService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<IPage<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String ruleKey) {
        return Result.ok(ruleVersionService.page(current, size, ruleKey)
                .convert(SysRuleVersionService::toRow));
    }

}
