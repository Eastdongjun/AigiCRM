-- ============================================================
-- fix-duplicate-sidebar-menus.sql(2026-08-15 审计 H4 生产数据清理方案)
-- 状态: 待用户审批, 未执行。同逻辑已在测试库执行并实测验证(菜单 API 23 path 零重复)。
--
-- 背景: 生产 sys_menu 存在 2026-08-05~12 手工写入的冗余行, 导致侧边栏同页渲染两次:
--   114 /incentive incentive:add  menu_type=2(按钮权限误为导航)
--   115 /customer  customer:view(与 11 重复)
--   116 /sea       sea:view      (与 17 重复)
--   118 /system/org  system:dept:view(与 32 重复)
--   119 /system/config system:config:view(与 34 重复)
-- 代码侧去重(后端 buildMenuTree + 前端 menuGroups)已上线, 本脚本清理数据侧根因。
--
-- 执行方式(审批后): ./deploy-db.sh sql/migration/20260815/fix-duplicate-sidebar-menus.sql
-- 回滚方式: 115/116/118/119 为 status=0 软停用 + 解绑, 回滚 = 恢复 status=1 并重建
--           sys_role_menu 绑定(执行前先备份: mysqldump sys_menu sys_role_menu)
-- ============================================================

-- ① 114: 按钮权限 incentive:add 误为导航, 改为按钮类型(menu_type=3)、清 path; 保留角色绑定与权限码
UPDATE `sys_menu` SET `menu_type` = 3, `path` = NULL WHERE `id` = 114 AND `permission` = 'incentive:add';

-- ② 冗余导航行: 解绑角色 + 软停用(不物理删除, 保留审计)
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (115, 116, 118, 119);
UPDATE `sys_menu` SET `status` = 0 WHERE `id` IN (115, 116, 118, 119);
