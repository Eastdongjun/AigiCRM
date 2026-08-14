-- 修复：让"角色管理 -> 菜单权限"配置真正驱动前端侧边栏
--
-- 背景：前端侧边栏此前完全硬编码，从不读取后端 /api/auth/info 返回的 menus 树，导致管理员在
-- 角色管理页面勾选/保存菜单权限后，对该角色用户实际看到的侧边栏没有任何影响。现在前端已改为
-- 动态渲染 menus 树，本脚本负责把 sys_menu 表这份此前"名存实亡"的数据修正到与当前前端路由
-- (router/index.js) 一致，并把新增菜单项按"与其功能相近的既有菜单相同的角色授权"补发授权，
-- 尽量保证迁移后各角色看到的页面范围与迁移前（基于权限字符串硬编码判断）保持一致，不产生突然
-- "看不到"某个页面的体验回退。
--
-- 执行前建议：先在测试环境执行并用不同角色登录核对侧边栏，确认无误后再在生产环境执行。
-- 本脚本只新增/修正数据，不删除任何现有 sys_menu / sys_role_menu 行。

-- 1) 修正"组织架构"菜单路径：sys_menu 里还是升级前的 /system/dept，
--    当前前端路由(router/index.js)已经是 /system/org，两者不一致会导致动态菜单点了没反应。
UPDATE sys_menu SET path = '/system/org', component = 'system/org'
WHERE id = 32 AND menu_name = '组织架构';

-- 2) 停用"积分榜"这个死菜单：对应的 /scoreboard 路由在当前前端里根本不存在
--    （页面组件 views/statistics/scoreboard.vue 还在，但从未注册路由），点击必然无效，
--    status=0 后 selectMenusByUserId/selectPermissionsByUserId 都不会再返回它。
UPDATE sys_menu SET status = 0
WHERE id = 22 AND menu_name = '积分榜';

-- 3) 补充当前前端已有、但 sys_menu 里从未登记过的 5 个页面
--    （之前侧边栏硬编码显示，不受 sys_menu/sys_role_menu 管控，现在补齐让它们纳入菜单权限体系）
-- sort_order 统一用 50+ 避免和既有同组菜单撞号，新菜单排在各分组末尾（后续可在角色管理页面调整）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status, created_at)
SELECT * FROM (SELECT
   100 AS id, 10 AS parent_id, '激励统计' AS menu_name, 2 AS menu_type, '/incentive/stats' AS path, 'incentive/stats' AS component, 'incentive:view' AS permission, 'TrendCharts' AS icon, 50 AS sort_order, 1 AS visible, 1 AS status, NOW() AS created_at
UNION ALL SELECT 101, 10, '激励查询', 2, '/incentive/search', 'incentive/search', 'incentive:view', 'Search', 51, 1, 1, NOW()
UNION ALL SELECT 102, 10, '捞取记录', 2, '/sea/records', 'sea/records', 'sea:view', 'Ship', 52, 1, 1, NOW()
UNION ALL SELECT 103, 20, '核心客户六项指标', 2, '/indicator', 'indicator/index', 'statistics:view', 'Aim', 50, 1, 1, NOW()
UNION ALL SELECT 104, 30, '岗位管理', 2, '/system/position', 'system/position', 'system:position:view', 'Avatar', 50, 1, 1, NOW()
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id IN (100,101,102,103,104));

-- 4) 按"与新增菜单功能同组、权限口径相同的既有菜单"补发角色授权，避免迁移后角色看到的页面变少：
--    - 激励统计/激励查询：跟随"四项激励"(id=15)已有的角色授权（此前都是 incentive:view 一起判断）
--    - 捞取记录：跟随"公海管理"(id=17)
--    - 核心客户六项指标：跟随"统计分析"(id=21)（此前都是 statistics:view 一起判断）
--    - 岗位管理：跟随"人员管理"(id=31)（新功能，先与人员管理同批角色可见，后续可在角色管理页单独调整）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 100 FROM sys_role_menu rm WHERE rm.menu_id = 15
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 100);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 101 FROM sys_role_menu rm WHERE rm.menu_id = 15
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 101);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 102 FROM sys_role_menu rm WHERE rm.menu_id = 17
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 102);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 103 FROM sys_role_menu rm WHERE rm.menu_id = 21
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 103);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 104 FROM sys_role_menu rm WHERE rm.menu_id = 31
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 104);

-- 5) 核对结果（人工检查用，不影响数据）：
-- SELECT id,parent_id,menu_name,menu_type,path,permission,visible,status FROM sys_menu WHERE id IN (22,32,100,101,102,103,104);
-- SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id IN (100,101,102,103,104) ORDER BY menu_id, role_id;
