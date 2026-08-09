-- 生产库修正：fix-menu-dynamic-sidebar.sql 生产版（id 调整为 105-109，避开生产已占用的 id=100"基础数据-用户"）
-- 背景：原脚本固定 id 100-104，生产库 id=100 已被旧菜单占用 → INSERT 整体被 WHERE NOT EXISTS 跳过，
--       但 role_menu 绑定照常执行 → 产生 16 条孤儿绑定 + 4 条误绑。本脚本先清掉这些，再以 105-109 补录。
USE ycmn;

-- 1. 清除脚本误产生的绑定（执行前 role_menu 无任何 100-104 绑定，全部为新增）
DELETE FROM sys_role_menu WHERE menu_id BETWEEN 100 AND 104;

-- 2. 按团队脚本逻辑补录 5 条菜单（id 105-109，与测试库脚本内容一一对应）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status, created_at)
SELECT * FROM (SELECT
   105 AS id, 10 AS parent_id, '激励统计' AS menu_name, 2 AS menu_type, '/incentive/stats' AS path, 'incentive/stats' AS component, 'incentive:view' AS permission, 'TrendCharts' AS icon, 50 AS sort_order, 1 AS visible, 1 AS status, NOW() AS created_at
UNION ALL SELECT 106, 10, '激励查询', 2, '/incentive/search', 'incentive/search', 'incentive:view', 'Search', 51, 1, 1, NOW()
UNION ALL SELECT 107, 10, '捞取记录', 2, '/sea/records', 'sea/records', 'sea:view', 'Ship', 52, 1, 1, NOW()
UNION ALL SELECT 108, 20, '核心客户六项指标', 2, '/indicator', 'indicator/index', 'statistics:view', 'Aim', 50, 1, 1, NOW()
UNION ALL SELECT 109, 30, '岗位管理', 2, '/system/position', 'system/position', 'system:position:view', 'Avatar', 50, 1, 1, NOW()
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id IN (105,106,107,108,109));

-- 3. 按"与新增菜单功能同组、权限口径相同的既有菜单"补发角色授权（与团队脚本逻辑一致）
--    激励统计/激励查询：跟随"四项激励"(id=15)；捞取记录：跟随"公海管理"(id=17)
--    核心客户六项指标：跟随"统计分析"(id=21)；岗位管理：跟随"人员管理"(id=31)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 105 FROM sys_role_menu rm WHERE rm.menu_id = 15
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 105);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 106 FROM sys_role_menu rm WHERE rm.menu_id = 15
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 106);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 107 FROM sys_role_menu rm WHERE rm.menu_id = 17
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 107);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 108 FROM sys_role_menu rm WHERE rm.menu_id = 21
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 108);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 109 FROM sys_role_menu rm WHERE rm.menu_id = 31
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 109);
