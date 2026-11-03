-- ============================================================
-- V2026.11.03.2__perm_center_menu.sql
-- 【统一权限配置中心 2026-11-03 用户裁决"门面统一+收口单入口"】
-- 新增页面菜单「权限中心」: 聚合 功能勾选/数据范围锚点/公海池名单/激励专项授权 四类配置,
-- 替代原 系统设置→权限体系/权限管理 两个 Tab 与 角色管理的功能勾选弹窗(收口, 防两处可写)。
--
-- 权限模型: 页面读 = system:config:view(与原权限矩阵同码, 已有角色自动可见);
--           写操作沿用 system:config:edit + 后端 requireAdmin, 不新增按钮码。
-- 定位口径: parent 按 menu_name='系统管理' 锚定, 零硬编码行 id(INV-MIG-001)。
-- 幂等: 菜单按 permission NOT EXISTS; 角色绑定双守卫。
-- ============================================================

-- 1. 页面菜单: 系统管理 → 权限中心
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status, created_at)
SELECT COALESCE((SELECT p.id FROM sys_menu p
                  WHERE p.menu_name = '系统管理' AND p.menu_type = 1
                  ORDER BY p.id LIMIT 1), 0),
       '权限中心', 2, '/system/perm-center', NULL, 'system:config:view', 7, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'system:config:view' AND x.path = '/system/perm-center');

-- 2. 默认绑系统管理员(role_code='admin'); 其他角色由管理员勾选
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
    JOIN sys_menu m ON m.path = '/system/perm-center' AND m.menu_type = 2
WHERE r.role_code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 3. 权限即时生效: 绑定角色下用户版本递增(与 UI 保存权限同口径)
UPDATE sys_user u
    JOIN sys_user_role ur ON ur.user_id = u.id
    JOIN sys_role r ON r.id = ur.role_id
SET u.perm_version = u.perm_version + 1
WHERE r.role_code = 'admin';
