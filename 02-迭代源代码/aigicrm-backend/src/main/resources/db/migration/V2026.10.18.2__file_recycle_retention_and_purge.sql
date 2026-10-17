-- ============================================================
-- V2026.10.18.2__file_recycle_retention_and_purge.sql
-- 【文件回收站 2026-10-18 用户裁决】删除的文件先入回收站, 超过保留期自动清理;
-- 管理员可在「回收站 → 已删除文件」手动清空。
--
-- 变更:
--   1. sys_config 新增 file.recycle.retention.days, 默认 30 天(0=不自动清理);
--   2. sys_menu 新增按钮权限 system:recycle:purge(清空文件回收站), 默认仅绑 admin。
--
-- 幂等:
--   config 按 config_key 守卫; menu 按 permission 守卫; 角色绑定按 (role_code, permission) 双守卫。
-- 定位口径: 全程按 path/permission/role_code/config_key 锚定, 零硬编码行 id(INV-MIG-001)。
--
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--     WHERE m.permission='system:recycle:purge';
--   DELETE FROM sys_menu WHERE permission='system:recycle:purge';
--   DELETE FROM sys_config WHERE config_key='file.recycle.retention.days';
-- ============================================================

-- 1. 保留期配置(天)
INSERT INTO sys_config (config_key, config_value, description, updated_at)
SELECT 'file.recycle.retention.days', '30',
       '文件回收站保留天数(删除的文件先入回收站, 超过此天数自动清理; 0=不自动清理)', NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM sys_config x WHERE x.config_key = 'file.recycle.retention.days');

-- 2. 按钮权限: 清空文件回收站
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status, created_at)
SELECT COALESCE((SELECT p.id FROM sys_menu p
                  WHERE p.path = '/system/recycle' AND p.menu_type = 2
                  ORDER BY p.id LIMIT 1), 0),
       '清空文件回收站', 3, NULL, NULL, 'system:recycle:purge', 98, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'system:recycle:purge');

-- 3. 默认仅绑系统管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
    JOIN sys_menu m ON m.permission = 'system:recycle:purge'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 4. 权限即时生效
UPDATE sys_user u
    JOIN sys_user_role ur ON ur.user_id = u.id
    JOIN sys_role r ON r.id = ur.role_id
SET u.perm_version = u.perm_version + 1
WHERE r.role_code = 'admin';
