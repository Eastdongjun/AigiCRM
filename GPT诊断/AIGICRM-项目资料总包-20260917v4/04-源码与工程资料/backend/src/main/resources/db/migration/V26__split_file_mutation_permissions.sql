-- Split file mutations from file:view while preserving every existing file:view role.
-- Each statement is idempotent and role bindings are inherited from file:view.
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '文件上传', 3, 'file:upload', 91, 1, 1
FROM sys_menu
WHERE permission = 'file:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:upload')
LIMIT 1;

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '文件编辑', 3, 'file:edit', 92, 1, 1
FROM sys_menu
WHERE permission = 'file:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:edit')
LIMIT 1;

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '文件删除', 3, 'file:delete', 93, 1, 1
FROM sys_menu
WHERE permission = 'file:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:delete')
LIMIT 1;

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '文件分享', 3, 'file:share', 94, 1, 1
FROM sys_menu
WHERE permission = 'file:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:share')
LIMIT 1;

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '文件夹管理', 3, 'file:folder:manage', 95, 1, 1
FROM sys_menu
WHERE permission = 'file:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:folder:manage')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT legacy.role_id, split_menu.id
FROM sys_role_menu legacy
JOIN sys_menu view_menu ON view_menu.id = legacy.menu_id AND view_menu.permission = 'file:view'
JOIN sys_menu split_menu ON split_menu.permission IN ('file:upload', 'file:edit', 'file:delete', 'file:share', 'file:folder:manage')
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu existing
  WHERE existing.role_id = legacy.role_id AND existing.menu_id = split_menu.id
);
