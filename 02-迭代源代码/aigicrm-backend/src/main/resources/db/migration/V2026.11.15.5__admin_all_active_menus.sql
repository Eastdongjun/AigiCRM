-- 【产品口径 2026-11-15】admin 默认拥有全部权限：
-- 覆盖 V2026.11.14.5 的“admin 最小权限集”裁剪，恢复 admin 对所有启用菜单/权限码的绑定。
-- 说明：V14.5 是旧口径（admin 不再隐式全通），已被 2026-11-15 产品裁决取代；
--       此处仅补 admin 角色绑定，不改变其他角色；禁用(status=0)的历史死码不授予。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
  FROM sys_role r
  JOIN sys_menu m ON m.status = 1
 WHERE r.role_code = 'admin';

-- admin 数据范围固定为全部，避免恢复菜单后仍被范围收窄
UPDATE sys_role SET data_scope = 'all' WHERE role_code = 'admin';
