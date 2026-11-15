-- 【产品口径 2026-11-15】回收站恢复/清空改为仅系统管理员：
-- 1) 非 admin 角色回收站写码绑定清理；2) 确保 admin 角色持码；3) 注册表 adminOnly=1。
DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_code <> 'admin'
  AND m.permission IN ('system:recycle:restore','system:recycle:purge');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m
WHERE r.role_code = 'admin'
  AND m.permission IN ('system:recycle:restore','system:recycle:purge')
  AND m.status = 1;

UPDATE sys_permission_meta SET admin_only = 1
WHERE code IN ('system:recycle:restore','system:recycle:purge');
