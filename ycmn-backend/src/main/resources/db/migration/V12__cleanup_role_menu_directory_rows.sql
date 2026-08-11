-- ============================================================
-- V12__cleanup_role_menu_directory_rows.sql — 清理 P0 闭包注入的目录行污染
--
-- 背景(2026-08-12, 对齐权限链路审计 perm-chain-verification-report.md:59):
--   saveRoleMenus 旧闭包无条件把祖先链(含 menu_type=1 目录行 + menu_type=2 父菜单)
--   写入 sys_role_menu。目录行(menu_type=1, permission 为空)不产生 RBAC 授权,
--   但作为 role_menu 绑定回显给前端, 在旧版 el-tree 深传播下放大为"打开即全选"的
--   P0 越权入口。审计要求一次性清理已写入的 16 条目录行(4 角色 × 4 目录, 生产+测试)。
--
-- 精确范围: 仅 sys_menu.menu_type=1 的目录行。目录行 permission 为空,
--   删除不会撤销任何真实权限码授权(安全)。
-- 幂等: sys_role_menu 有 UNIQUE KEY uk_role_menu(role_id, menu_id),
--   重复执行同结果。
-- 修复后: saveRoleMenus 闭包改为只补 menu_type=1 目录行(见 SysRoleService),
--   且前端 handleSavePerm 只提交 menu_type=2/3, 不再写目录行——本清理为一次性补救,
--   修复后按需补入的目录行(permission 空)无害。
-- ============================================================

DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT id FROM sys_menu WHERE menu_type = 1);
