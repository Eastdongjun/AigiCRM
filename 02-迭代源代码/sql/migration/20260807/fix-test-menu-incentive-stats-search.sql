-- 修复测试库缺失的 激励统计/激励查询 菜单（2026-08-07）
-- 背景: 测试库 sys_menu 缺 激励统计/激励查询 两个子菜单(挂在四项激励15下)，
--       导致侧边栏不显示这两个页面。生产库已有(105/106)，测试库用空闲 ID 112/113。
-- 参照: 生产 sys_menu 105(激励统计)/106(激励查询)，角色 1/9/10/11 绑定
-- 执行: mysql --default-character-set=utf8mb4 aigicrm < fix-test-menu-incentive-stats-search.sql

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status, created_at) VALUES
(112, 15, '激励统计', 2, '/incentive/stats', 'incentive/stats', 'incentive:view', NULL, 4, 1, 1, NOW()),
(113, 15, '激励查询', 2, '/incentive/search', 'incentive/search', 'incentive:view', NULL, 3, 1, 1, NOW())
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1,112),(1,113),(9,112),(9,113),(10,112),(10,113),(11,112),(11,113)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);
