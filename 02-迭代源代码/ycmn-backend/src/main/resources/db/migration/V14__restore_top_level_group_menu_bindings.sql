-- ============================================================
-- V14__restore_top_level_group_menu_bindings.sql — 纠正 V12 旧版误删顶层分组绑定
--
-- 背景(2026-08-12 生产事故):
--   V12 初版执行 `DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE menu_type=1)`,
--   把 4 个顶层分组(首页/业务核心/考核与分析/系统管理, menu_type=1 AND parent_id=0)
--   的 role_menu 绑定全部删除 → AuthService.buildMenuTree 无法构建菜单树根 →
--   前端侧边栏菜单整体消失。
--
-- 本迁移: 给所有【已有任意菜单绑定】的角色补回 4 个顶层分组的绑定(幂等)。
--   依赖前提: 顶层分组 id 固定为 1/10/20/30(生产+测试一致, 见 sys_menu 基线)。
--   若角色无任何菜单绑定(全新角色), 不补(该角色本就看不到菜单)。
--
-- 幂等: sys_role_menu 有 UNIQUE KEY uk_role_menu(role_id, menu_id), 重复执行同结果。
-- 配合: V12 已修正为只删非顶层目录行(parent_id != 0), 本迁移防旧版副作用重放。
-- ============================================================

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, g.id
FROM sys_role_menu rm
CROSS JOIN sys_menu g
WHERE g.menu_type = 1 AND g.parent_id = 0 AND g.id IN (1, 10, 20, 30)
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu x
      WHERE x.role_id = rm.role_id AND x.menu_id = g.id
  );
