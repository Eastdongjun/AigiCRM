-- V2026.11.14.11 文件/文件夹权限细粒度 v2
--
-- 新增:
--   file:download
--   file:folder:create / rename / delete / restore
--   file:reconcile
--
-- 兼容策略:
--   1) 现有 file:view 角色补 file:download，保持“能看=能下”直到管理员取消下载；
--   2) 现有 file:folder:manage 角色补 folder:create/rename/delete，保持原行为；
--   3) 现有 file:delete 角色补 folder:restore，避免删夹后不能恢复；
--   4) 现有 file:folder:manage 角色补 file:reconcile（对账原挂 folder:manage）；
--   5) 旧 file:folder:manage 仍保留，由后端双判兼容。
--
-- 回滚:
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id
--     WHERE m.permission IN ('file:download','file:folder:create','file:folder:rename',
--       'file:folder:delete','file:folder:restore','file:reconcile');
--   DELETE FROM sys_menu WHERE permission IN (...);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 14, '文件下载', 3, NULL, NULL, 'file:download', NULL, 910, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:download');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 14, '新建文件夹', 3, NULL, NULL, 'file:folder:create', NULL, 911, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:folder:create');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 14, '文件夹改名', 3, NULL, NULL, 'file:folder:rename', NULL, 912, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:folder:rename');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 14, '删除文件夹', 3, NULL, NULL, 'file:folder:delete', NULL, 913, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:folder:delete');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 14, '恢复文件夹', 3, NULL, NULL, 'file:folder:restore', NULL, 914, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:folder:restore');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 14, '文件对账', 3, NULL, NULL, 'file:reconcile', NULL, 915, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:reconcile');

-- 回填：file:view 持有者补 file:download
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, nm.id
FROM sys_role_menu rm
JOIN sys_menu om ON om.id=rm.menu_id AND om.permission='file:view'
JOIN sys_menu nm ON nm.permission='file:download'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=nm.id);

-- 回填：file:folder:manage 持有者补 create/rename/delete/reconcile
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, nm.id
FROM sys_role_menu rm
JOIN sys_menu om ON om.id=rm.menu_id AND om.permission='file:folder:manage'
JOIN sys_menu nm ON nm.permission IN ('file:folder:create','file:folder:rename','file:folder:delete','file:reconcile')
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=nm.id);

-- 回填：file:delete 持有者补 file:folder:restore
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, nm.id
FROM sys_role_menu rm
JOIN sys_menu om ON om.id=rm.menu_id AND om.permission='file:delete'
JOIN sys_menu nm ON nm.permission='file:folder:restore'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=nm.id);

-- admin 全部新码（admin 是权限中心操作面，不是业务数据全通）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission IN ('file:download','file:folder:create','file:folder:rename','file:folder:delete','file:folder:restore','file:reconcile')
WHERE r.role_code='admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id);
