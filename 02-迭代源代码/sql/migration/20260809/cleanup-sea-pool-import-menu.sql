-- ============================================================
-- 2026-08-09 B6: 清理 sea:pool:import 死权限码
-- 背景: 9c6e72a 已把公海导入权限统一为 sea:import(0805 设计),
--       废弃 sea:pool:import 双轨。但只改了后端 @PreAuthorize,
--       sys_menu/sys_role_menu 中的残留码未清理, 销售员仍被授予无用的 sea:pool:import。
-- 影响: 删除菜单 id=55 及 role 1/10/11 的绑定。前端按钮已改判 sea:import,
--       清理后无任何功能影响; 销售员(11)本就不持有 sea:import, 行为不变。
-- 执行: 备份后执行, 幂等(按菜单 id 条件删除)。
-- ============================================================

-- 1. 解绑角色-菜单关联(幂等; 复核 Low: 先确认 id=55 确为 sea:pool:import 才解绑,防误删其他菜单绑定)
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE rm.menu_id = 55 AND m.permission = 'sea:pool:import';

-- 2. 删除废弃菜单(幂等)
DELETE FROM sys_menu WHERE id = 55 AND permission = 'sea:pool:import';
