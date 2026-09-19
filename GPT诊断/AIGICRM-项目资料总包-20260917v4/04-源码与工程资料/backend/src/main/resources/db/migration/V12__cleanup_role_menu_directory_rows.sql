-- ============================================================
-- V12__cleanup_role_menu_directory_rows.sql — 清理闭包注入的中间目录行污染
--
-- 背景(2026-08-12, 对齐权限链路审计 perm-chain-verification-report.md:59):
--   saveRoleMenus 旧闭包无条件把祖先链(含 menu_type=1 目录行 + menu_type=2 父菜单)
--   写入 sys_role_menu。中间目录行(menu_type=1, permission 为空, parent_id != 0)
--   不产生 RBAC 授权, 但作为 role_menu 绑定回显给前端, 在旧版 el-tree 深传播下
--   放大为"打开即全选"的 P0 越权入口。
--
-- 精确范围: 仅清理【非顶层】目录行(menu_type=1 AND parent_id != 0)的 role_menu 绑定。
--   ⚠️【2026-08-12 修正】顶层分组(menu_type=1 AND parent_id=0, 如 首页/业务核心/
--   考核与分析/系统管理)的 role_menu 绑定是前端菜单树的骨架, AuthService.buildMenuTree
--   依赖 selectMenusByUserId 返回这些分组节点来构建树——删除会导致侧边栏菜单整体消失
--   (生产事故)。因此顶层分组绑定必须保留。
--
-- 幂等: sys_role_menu 有 UNIQUE KEY uk_role_menu(role_id, menu_id), 重复执行同结果。
-- 修复后: saveRoleMenus 闭包改为只补 menu_type=1 目录行(见 SysRoleService),
--   且前端 handleSavePerm 只提交 menu_type=2/3, 不再写目录行——本清理为一次性补救。
-- ============================================================

DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT id FROM sys_menu WHERE menu_type = 1 AND parent_id != 0);
