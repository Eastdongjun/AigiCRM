-- 财务明细独立查看权限；默认仅系统管理员拥有，其他角色由权限管理显式授予。
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '客户财务查看', 3, 'finance:view', 98, 1, 1
FROM sys_menu
WHERE permission = 'customer:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'finance:view')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission = 'finance:view'
WHERE r.id = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '客户联系人查看', 3, 'contact:view', 97, 1, 1
FROM sys_menu
WHERE permission = 'customer:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'contact:view')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission = 'contact:view'
WHERE r.id = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );
