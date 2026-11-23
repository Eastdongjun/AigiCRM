package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** B1 首启向导的后端动作：仅系统配置管理员可显式导入演示数据。 */
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/seed-demo")
    @PreAuthorize("hasAuthority('system:config:edit')")
    public Result<Map<String, Object>> seedDemo() {
        return Result.ok(onboardingService.seedDemo());
    }
}
