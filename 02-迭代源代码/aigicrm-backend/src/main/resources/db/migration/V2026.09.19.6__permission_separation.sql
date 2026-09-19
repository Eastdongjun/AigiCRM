-- 【PR-06 2026-09-19】权限分离:新增 contact:manage / finance:manage(OR 兼容旧码)
-- 存量角色零破坏:旧权限(customer:edit / finance:view)行为不变;
-- 新角色可单独授予联系人管理或财务录入,实现职责分离。

INSERT INTO sys_menu (menu_name, menu_type, permission, sort_order, visible, status)
SELECT '联系人管理', 3, 'contact:manage', 99, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'contact:manage');

INSERT INTO sys_menu (menu_name, menu_type, permission, sort_order, visible, status)
SELECT '财务管理', 3, 'finance:manage', 99, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'finance:manage');
