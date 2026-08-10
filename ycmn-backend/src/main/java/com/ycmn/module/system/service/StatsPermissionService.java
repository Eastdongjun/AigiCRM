package com.ycmn.module.system.service;

import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 公司级统计可见授权(M5 2026-08-10)。
 * 管理员勾选谁能看"统计总览/激励统计/学分排行"的全公司聚合; admin 恒可见。
 */
@Service
@RequiredArgsConstructor
public class StatsPermissionService {

    private final JdbcTemplate jdbc;

    /** 当前用户是否可查看全公司统计聚合(admin 恒 true; 未配置授权→默认全员可见, 已配置→仅勾选用户) */
    public boolean canViewCompany() {
        if (SecurityUtils.isAdmin()) return true;
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;
        try {
            Integer configured = jdbc.queryForObject("SELECT COUNT(*) FROM biz_stats_permission", Integer.class);
            if (configured == null || configured == 0) {
                return true; // 管理员尚未配置 → 全员可见(兼容现状, 不回归)
            }
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM biz_stats_permission WHERE user_id=? AND can_view_company=1",
                    Integer.class, userId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
