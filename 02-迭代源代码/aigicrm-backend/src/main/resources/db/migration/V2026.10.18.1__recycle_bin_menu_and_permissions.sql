-- ============================================================
-- V2026.10.18.1__recycle_bin_menu_and_permissions.sql
-- 【回收站 2026-10-18 用户需求】已删除客户/已删除人员查看与恢复
--
-- 背景:
--   删除是终态(逻辑删 deleted=1), 现有界面无回收站入口; 管理员只能查 sys_log 留痕,
--   无法查看已删除客户/人员档案, 也无法恢复。本迁移登记独立页面「回收站」及两个权限点。
--
-- 权限模型:
--   system:recycle:view    页面/列表(默认仅 admin)
--   system:recycle:restore 恢复动作(默认仅 admin)
--   其他角色由管理员在「角色管理 → 权限分配」勾选授予(perm_version 即时生效)。
--
-- 幂等:
--   两处菜单按 permission NOT EXISTS 守卫; 角色绑定按 (role_code, permission) 双守卫。
--
-- 定位口径:
--   全程按 menu_name/path/permission/role_code 锚定, 零硬编码行 id(INV-MIG-001);
--   parent 兜底值 0 为顶级结构常量, 非行 id 定位。
--
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--     WHERE m.permission IN ('system:recycle:view','system:recycle:restore');
--   DELETE FROM sys_menu WHERE permission IN ('system:recycle:view','system:recycle:restore');
-- ============================================================

-- 1. 菜单: 系统管理 → 回收站 (page, menu_type=2, 有 path 才进侧边栏)
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status, created_at)
SELECT COALESCE((SELECT p.id FROM sys_menu p
                  WHERE p.menu_name = '系统管理' AND p.menu_type = 1
                  ORDER BY p.id LIMIT 1), 0),
       '回收站', 2, '/system/recycle', NULL, 'system:recycle:view', 6, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'system:recycle:view');

-- 2. 按钮权限: 恢复已删除数据 (menu_type=3, 不进侧边栏)
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status, created_at)
SELECT COALESCE((SELECT p.id FROM sys_menu p
                  WHERE p.path = '/system/recycle' AND p.menu_type = 2
                  ORDER BY p.id LIMIT 1), 0),
       '恢复已删除数据', 3, NULL, NULL, 'system:recycle:restore', 99, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'system:recycle:restore');

-- 3. 默认仅绑系统管理员(role_code='admin'); 其他角色由管理员在角色管理勾选
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
    JOIN sys_menu m ON m.permission IN ('system:recycle:view', 'system:recycle:restore')
WHERE r.role_code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 4. 权限即时生效: 已绑定角色下的用户版本递增(与 UI 保存权限的既有做法同口径)
UPDATE sys_user u
    JOIN sys_user_role ur ON ur.user_id = u.id
    JOIN sys_role r ON r.id = ur.role_id
SET u.perm_version = u.perm_version + 1
WHERE r.role_code = 'admin';
