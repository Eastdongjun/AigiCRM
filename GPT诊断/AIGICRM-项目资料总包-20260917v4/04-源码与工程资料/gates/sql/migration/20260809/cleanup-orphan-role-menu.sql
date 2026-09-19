-- ============================================================
-- 2026-08-09 体检 P3-7: 清理孤儿 sys_role_menu 记录(menu_id=0)
--
-- 背景: 生产库 3 条 role_menu(menu_id=0) 引用不存在的菜单 id,
--        selectPermissionsByUserId 按 menu join 取权限, menu_id=0 无对应
--        菜单 → 不授予任何权限, 功能惰性。属历史数据债, 删除安全。
-- 影响: role 1/9/10(系统管理员/行政/销售总监) 移除无效绑定, 不影响其权限。
-- 执行: 备份后执行, 幂等(按条件删除)。
-- ============================================================

-- 1. 清理 menu_id=0 的孤儿绑定(幂等)
DELETE FROM sys_role_menu
WHERE menu_id = 0
   OR menu_id NOT IN (SELECT id FROM sys_menu);
