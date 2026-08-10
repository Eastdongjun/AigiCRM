-- V4: 公司级统计可见授权表(M5 2026-08-10)
-- 用途: 管理员勾选"谁能看全公司统计/激励/学分排行"——统计总览(indicatorStats)/激励统计(categoryStats)/学分排行(scoreboard)
-- 语义: can_view_company=1 且用户活跃 → 可见全公司聚合; admin 恒可见(代码层短路); 未勾选 → 只见自己范围
CREATE TABLE IF NOT EXISTS biz_stats_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '被授权用户ID',
    can_view_company TINYINT DEFAULT 0 COMMENT '1=可查看全公司统计/激励/学分排行',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user (user_id)
) COMMENT '公司级统计可见授权';
