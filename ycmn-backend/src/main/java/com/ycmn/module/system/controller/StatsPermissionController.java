package com.ycmn.module.system.controller;

import com.ycmn.common.BusinessException;
import com.ycmn.common.Result;
import com.ycmn.module.system.service.StatsPermissionService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公司级统计可见授权(M5 2026-08-10, V5 按模块拆分)。
 * 管理员在系统设置勾选"谁能看全公司聚合", 按模块: indicator/incentive/score; 授权仅超管可操作。
 */
@RestController
@RequestMapping("/api/stats-permission")
@RequiredArgsConstructor
public class StatsPermissionController {

    private final JdbcTemplate jdbc;
    private final StatsPermissionService statsPermissionService;

    /** 全部用户 + 三个模块的勾选状态(配置页用) */
    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT u.id AS userId, u.real_name AS realName, u.username, " +
                "COALESCE(p.can_view_indicator,0) AS canViewIndicator, " +
                "COALESCE(p.can_view_incentive,0) AS canViewIncentive, " +
                "COALESCE(p.can_view_score,0) AS canViewScore, " +
                "COALESCE(d.dept_name,'—') AS officeName " +
                "FROM sys_user u LEFT JOIN biz_stats_permission p ON p.user_id = u.id " +
                "LEFT JOIN sys_dept d ON u.office_id = d.id " +
                "WHERE u.status != 2 ORDER BY u.id");
        return Result.ok(list);
    }

    /** 勾选保存(按模块): {module, userIds} → 该模块仅这些用户可见; 其余清 0(事务) */
    @PreAuthorize("hasAuthority('system:config:edit')")
    @PostMapping("/sync")
    @Transactional
    public Result<Void> sync(@RequestBody Map<String, Object> body) {
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可授权公司级统计可见");
        String module = body.get("module") != null ? body.get("module").toString() : "";
        if (!module.equals("indicator") && !module.equals("incentive") && !module.equals("score")) {
            throw new BusinessException("非法的统计模块");
        }
        List<Long> userIds = new ArrayList<>();
        Object uids = body.get("userIds");
        if (uids instanceof List && !((List<?>) uids).isEmpty()) {
            for (Object o : (List<?>) uids) {
                if (o != null) userIds.add(Long.valueOf(o.toString()));
            }
        }
        // 该模块全部清 0(不在勾选列表的收回), 再给选中用户置 1
        jdbc.update("UPDATE biz_stats_permission SET can_view_" + module + "=0");
        for (Long userId : userIds) {
            jdbc.update("INSERT INTO biz_stats_permission (user_id, can_view_" + module + ") VALUES (?,1) " +
                        "ON DUPLICATE KEY UPDATE can_view_" + module + "=1", userId);
        }
        return Result.ok();
    }

    /** 当前用户三个模块的可见状态(前端门控) */
    @GetMapping("/me")
    public Result<Map<String, Boolean>> me() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("canViewIndicator", statsPermissionService.canView("indicator"));
        result.put("canViewIncentive", statsPermissionService.canView("incentive"));
        result.put("canViewScore", statsPermissionService.canView("score"));
        return Result.ok(result);
    }
}
