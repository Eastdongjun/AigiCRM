-- ============================================================
-- V2026.11.05.5__fix_score_stats_group_reparent.sql
-- 【2026-11-05 自纠补丁】V2026.11.05.4 第3b/3c步锚点笔误: 新建组实测为 menu_type=1(顶层组,
-- 与 考核与分析 同形), 但改挂 subquery 仍过滤 menu_type=2 → 匹配 0 行,
-- 学分录入/学分排行/统计分析 三页面 parent 残留已删除的旧组 id 成为孤儿(grp=NULL 实证)。
-- 本迁移按 type1 锚点重新改挂; 幂等(重复执行同值); 末尾 perm_version 全员 +1。
-- 回滚: 无需(指向正确归属)。
-- ============================================================

-- 1) 学分录入 + 学分排行 → 学分管理组(type1 锚点)
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/score-group' AND menu_type = 1 LIMIT 1) g)
WHERE path IN ('/score/entry', '/score/ranking') AND menu_type = 2;

-- 2) 统计分析 → 统计分析组(type1 锚点)
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/statistics-group' AND menu_type = 1 LIMIT 1) g)
WHERE path = '/statistics' AND menu_type = 2;

-- 3) 权限缓存自愈
UPDATE sys_user SET perm_version = perm_version + 1;
