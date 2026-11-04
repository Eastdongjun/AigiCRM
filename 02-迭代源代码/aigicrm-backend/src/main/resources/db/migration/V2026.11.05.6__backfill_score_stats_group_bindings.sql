-- ============================================================
-- V2026.11.05.6__backfill_score_stats_group_bindings.sql
-- 【2026-11-05 自纠补丁 2】授权回填锚点修正:
--   .05.4 安全网 a 按"现持 考核与分析 组节点"回填——实测该组节点在本库 sys_role_menu
--   中本就无绑定行(侧栏组=菜单树构建器按可见子页合成), 回填落空;
--   新组(学分管理/统计分析)因此无任何角色绑定 → 侧栏丢组(实证)。
--   改为按"页面持有者"回填: 持有改挂页面节点的角色必然需要其所属组节点。
-- 幂等: NOT EXISTS 双守卫; 末尾 perm_version 全员 +1。回滚: 无需。
-- ============================================================

-- 1) 持有 学分录入/学分排行 的角色 → 补授 学分管理 组
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, g.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.path IN ('/score/entry', '/score/ranking') AND om.menu_type = 2
JOIN `sys_menu` g ON g.path = '/score-group' AND g.menu_type = 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = g.id);

-- 2) 持有 统计分析 的角色 → 补授 统计分析 组
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, g.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.path = '/statistics' AND om.menu_type = 2
JOIN `sys_menu` g ON g.path = '/statistics-group' AND g.menu_type = 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = g.id);

-- 3) 持有 核心客户六项指标/客户排行 的角色 → 补授 客户管理 组(兜底加强, 与 .05.4 安全网 b 同向)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, g.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.path IN ('/indicator', '/ranking/customer') AND om.menu_type = 2
JOIN `sys_menu` g ON g.path = '/customer-group' AND g.menu_type = 2
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = g.id);

-- 4) 权限缓存自愈
UPDATE sys_user SET perm_version = perm_version + 1;
