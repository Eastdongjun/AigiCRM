-- ============================================================
-- 法布施 PC 菜单补齐：我的书架(/fabushi/shelf) 入侧栏
-- 2026-11-16 用户令"左侧菜单缺少模块, 对齐客户管理子菜单结构"
--
-- 现状：shelf.vue(书架/阅读记录/提醒设置三页签)路由已注册
-- (fabushi:view 门), 但菜单表无此页 → PC 侧栏无入口, 页面成孤岛。
-- 动作：加「我的阅读」子菜单(排书目浏览之后、管理三页之前),
--       管理三页 sort 顺延; 角色绑定从现有 fabushi:view 绑定复制
--       (不硬编码角色码, 同 V2026.11.16.6 模式)。
-- 回滚：DELETE sys_role_menu WHERE menu_id=(按 path 定位);
--       DELETE sys_menu WHERE path='/fabushi/shelf'; 管理三页 sort 还原 3/4/5。
-- ============================================================

SET @fabushi_group = (
  SELECT id FROM sys_menu
  WHERE menu_name = '法布施' AND parent_id < 1 AND menu_type = 1
  ORDER BY id LIMIT 1
);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT @fabushi_group, '我的阅读', 2, '/fabushi/shelf', 'fabushi/shelf', 'fabushi:view', 'Reading', 3, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_type = 2 AND path = '/fabushi/shelf');
SET @shelf_menu = (SELECT id FROM sys_menu WHERE menu_type = 2 AND path = '/fabushi/shelf' ORDER BY id LIMIT 1);

-- 管理三页顺延: 我的阅读(3) → 书目管理(4) → 内容治理(5) → 统计看板(6)
UPDATE sys_menu SET sort_order = 6 WHERE menu_type = 2 AND path = '/fabushi/stats';
UPDATE sys_menu SET sort_order = 5 WHERE menu_type = 2 AND path = '/fabushi/content';
UPDATE sys_menu SET sort_order = 4 WHERE menu_type = 2 AND path = '/fabushi/book';

-- 页面绑定：从现有 fabushi:view 菜单绑定复制，不写角色码
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @shelf_menu
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.permission = 'fabushi:view'
  AND m.id <> @shelf_menu
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = @shelf_menu
  );
