package com.aigicrm.module.auth.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.auth.service.PermissionExplainService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * 【PR-21 2026-09-19】有效权限解释端点(自服务,登录即可查自己)。
 * 【WP6 2026-11-13】新增 admin 按用户生效预览(真实 UserDetailsService 重建, 规格 WP6)。
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

    /** 【WP6 2026-11-13】按用户生效预览(admin): 真实 UserDetailsService 重建后结构化解释+可见人数 */
    @GetMapping("/permission-explain/preview/{userId}")
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<Map<String, Object>> preview(@PathVariable Long userId) {
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "生效预览仅系统管理员可用");
        }
        return Result.ok(explainService.explainUser(userId));
    }
}
