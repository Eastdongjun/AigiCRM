package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.PermCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 【统一权限配置中心 2026-11-03 用户裁决"门面统一+收口单入口"】生效预览端点。
 *
 * 【需求】权限中心页签③: 管理员搜任意人 → 引擎实时算出其"真实拥有的一切"
 *         (功能码/各模块范围/公海逐池判定/激励四开关), 配完立刻看到效果
 * 【方法】PermCenterService 复用每请求鉴权同链路重建目标用户, 只读不落库
 * 【业务规则】读挂 system:config:view(与权限矩阵同域); 无写端点(配置写仍走各既有端点)
 */
@RestController
@RequestMapping("/api/system/perm-center")
@RequiredArgsConstructor
public class PermCenterController {

    private final PermCenterService permCenterService;

    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<Map<String, Object>> preview(@RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("请指定要预览的用户");
        }
        return Result.ok(permCenterService.preview(userId));
    }
}
