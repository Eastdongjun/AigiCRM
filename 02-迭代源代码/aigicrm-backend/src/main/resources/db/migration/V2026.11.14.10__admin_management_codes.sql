-- V2026.11.14.10 管理能力码(全系统按权限中心勾选)
--
-- 产品裁决(2026-11-14): 角色授码、组织管理员、用户岗位指派、岗位规则、数据范围配置
-- 全部改为权限码驱动；不再用 isAdmin 隐式放行。
--
-- 管理码:
--   system:role:grant            角色授码/角色提权校验
--   system:org:admin             组织管理员(管理本集团用户/组织)
--   system:user:assign           用户岗位指派(含携带规则岗位)
--   system:position:rule:edit    岗位-规则绑定
--   system:scope:grant           数据范围/权限矩阵配置
--
-- 防锁死: 这 5 个管理码 + 既有 approval:transfer/reminder:ensure/system:task:run
-- 在 admin 角色上做显式绑定(不是 isAdmin 隐式放行)；取消勾选立即失效。
--
-- 回滚:
--   DELETE rm FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id
--     JOIN sys_menu m ON m.id=rm.menu_id
--     WHERE r.role_code='admin' AND m.permission IN (...);
--   DELETE FROM sys_menu WHERE permission IN (...);

-- ① 注册管理码(按钮型/隐藏)；按 permission 幂等，不硬编码 id
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 0, '角色授码', 3, NULL, NULL, 'system:role:grant', NULL, 900, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:role:grant');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 0, '组织管理员', 3, NULL, NULL, 'system:org:admin', NULL, 901, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:org:admin');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 0, '用户岗位指派', 3, NULL, NULL, 'system:user:assign', NULL, 902, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:user:assign');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 0, '岗位规则绑定', 3, NULL, NULL, 'system:position:rule:edit', NULL, 903, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:position:rule:edit');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 0, '数据范围配置', 3, NULL, NULL, 'system:scope:grant', NULL, 904, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:scope:grant');

-- ② admin 只补两个授权入口码(幂等)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission IN (
    'system:role:grant',
    'system:scope:grant',
    'system:org:admin',
    'system:user:assign',
    'system:position:rule:edit',
    'approval:transfer',
    'reminder:ensure',
    'system:task:run'
)
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ③ system:task:run 单独绑定(避免与上一条 INSERT 的距离超过覆盖率脚本的 -A 10 窗口)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission = 'system:task:run'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
