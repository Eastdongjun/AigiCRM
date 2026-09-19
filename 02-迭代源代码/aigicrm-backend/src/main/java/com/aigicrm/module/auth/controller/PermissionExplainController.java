package com.aigicrm.module.auth.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.auth.service.PermissionExplainService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 【PR-21 2026-09-19】有效权限解释端点(自服务,登录即可查自己)。
 * 逻辑在 PermissionExplainService(控制器零 entity/mapper 依赖)。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PermissionExplainController {

    private final PermissionExplainService explainService;

    @GetMapping("/permission-explain")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> explain() {
        return Result.ok(explainService.explain());
    }
}
