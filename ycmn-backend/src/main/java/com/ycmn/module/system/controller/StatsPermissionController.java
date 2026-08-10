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
 * 公司级统计可见授权(M5 2026-08-10)。
 * 管理员在系统设置勾选"谁能看全公司统计/激励/学分排行"; 授权仅超管可操作(防提权)。
 */
@RestController
@RequestMapping("/api/stats-permission")
@RequiredArgsConstructor
public class StatsPermissionController {

    private final JdbcTemplate jdbc;
    private final StatsPermissionService statsPermissionService;

    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT u.id AS userId, u.real_name AS realName, u.username, " +
                "COALESCE(p.can_view_company,0) AS canViewCompany, COALESCE(d.dept_name,'—') AS officeName " +
                "FROM sys_user u LEFT JOIN biz_stats_permission p ON p.user_id = u.id " +
                "LEFT JOIN sys_dept d ON u.office_id = d.id " +
                "WHERE u.status != 2 ORDER BY u.id");
        return Result.ok(list);
    }

    /** 勾选保存: userIds=应可见全公司统计的用户; 其余置 0(幂等, 事务) */
    @PreAuthorize("hasAuthority('system:config:edit')")
    @PostMapping("/sync")
    @Transactional
    public Result<Void> sync(@RequestBody Map<String, Object> body) {
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可授权公司级统计可见");
        List<Long> userIds = new ArrayList<>();
        Object uids = body.get("userIds");
        if (uids instanceof List && !((List<?>) uids).isEmpty()) {
            for (Object o : (List<?>) uids) {
                if (o != null) userIds.add(Long.valueOf(o.toString()));
            }
        }
        // 全部清零(不在勾选列表的收回), 再给选中用户置 1
        jdbc.update("UPDATE biz_stats_permission SET can_view_company=0");
        for (Long userId : userIds) {
            jdbc.update("INSERT INTO biz_stats_permission (user_id, can_view_company) VALUES (?,1) " +
                        "ON DUPLICATE KEY UPDATE can_view_company=1", userId);
        }
        return Result.ok();
    }

    /** 当前用户是否可见全公司统计(前端门控用) */
    @GetMapping("/me")
    public Result<Map<String, Boolean>> me() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("canViewCompany", statsPermissionService.canViewCompany());
        return Result.ok(result);
    }
}
