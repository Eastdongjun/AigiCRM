package com.ycmn.module.system.service;

import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 公司级统计可见授权(M5 2026-08-10, V5 按模块拆分)。
 * 管理员勾选"谁能看全公司聚合", 按模块区分: indicator(核心客户指标-统计总览)/incentive(激励统计)/score(学分排行)。
 * 未配置任何授权 → 默认全员可见(兼容现状); 已配置 → 仅勾选用户可见。
 */
@Service
@RequiredArgsConstructor
public class StatsPermissionService {

    private final JdbcTemplate jdbc;

    /** 合法模块码白名单(防 SQL 注入到列名) */
    private static final Set<String> MODULES = Set.of("indicator", "incentive", "score");

    /** 当前用户是否可查看某模块的公司级聚合 */
    public boolean canView(String module) {
        if (module == null || !MODULES.contains(module)) return false;
        if (SecurityUtils.isAdmin()) return true;
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;
        try {
            Integer configured = jdbc.queryForObject("SELECT COUNT(*) FROM biz_stats_permission", Integer.class);
            if (configured == null || configured == 0) {
                return true; // 管理员尚未配置 → 全员可见(兼容现状, 不回归)
            }
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM biz_stats_permission WHERE user_id=? AND can_view_" + module + "=1",
                    Integer.class, userId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 某用户某模块是否被勾选(配置页用) */
    public boolean granted(Long userId, String module) {
        if (module == null || !MODULES.contains(module)) return false;
        try {
            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM biz_stats_permission WHERE user_id=? AND can_view_" + module + "=1",
                    Integer.class, userId);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
