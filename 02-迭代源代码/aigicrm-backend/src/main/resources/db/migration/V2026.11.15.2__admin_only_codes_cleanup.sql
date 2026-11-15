-- 【P1-3 2026-11-15】adminOnly 权限码只属于系统管理员角色。
-- 非 admin 角色即使保留这些码, 运行期也会被 isAdmin 硬守卫 403(死配置), 迁移清理。
DELETE rm
  FROM sys_role_menu rm
  JOIN sys_role r ON r.id = rm.role_id
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE r.role_code <> 'admin'
   AND m.permission IN ('system:group:add', 'system:group:edit', 'system:group:delete', 'system:task:run');
