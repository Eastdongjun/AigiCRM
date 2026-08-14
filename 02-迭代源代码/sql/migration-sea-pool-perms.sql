-- 公海池"编辑/删除"按钮权限 迁移脚本
-- 用途: 已有库(本地已导入 / 生产)新增两个按钮级权限, 并授予管理员(role_id=1)
-- 执行: mysql -h host -P port -u root -p --default-character-set=utf8mb4 <库名> < migration-sea-pool-perms.sql
-- 幂等: 重复执行安全(ON DUPLICATE / NOT EXISTS)

-- 1. 新增按钮菜单(公海管理 parent_id=17 下)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible)
VALUES
  (53, 17, '公海编辑', 3, '', '', 'sea:pool:edit',   '', 2, 1),
  (54, 17, '公海删除', 3, '', '', 'sea:pool:delete', '', 3, 1),
  (55, 17, '公海导入', 3, '', '', 'sea:pool:import', '', 4, 1)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), permission = VALUES(permission);

-- 2. 授予管理员角色(role_id=1)这三个权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (53, 54, 55)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );
