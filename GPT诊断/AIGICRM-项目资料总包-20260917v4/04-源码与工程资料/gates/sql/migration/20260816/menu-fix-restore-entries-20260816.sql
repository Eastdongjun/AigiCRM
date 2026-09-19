-- 20260816 菜单入口紧急修复（重复执行幂等）
-- 背景: 今日重放了 08-15 已执行且已被 restore-sidebar-page-entries.sql 修复的
--       fix-duplicate-sidebar-menus.sql（文件头"待审批"注释过时），导致
--       客户列表/公海池/部门管理/系统设置 4 个页面入口再次丢失；
--       同时 114 被改 type=3 使四项激励主页(/incentive, 含上传文件按钮)入口消失。
-- 修复: ① 恢复 115/116/118/119 + 12 条角色绑定(与 08-15 restore 一致);
--       ② 四项激励组对齐"分组标题+页面入口"模式(与 11/17/32/34 一致):
--          15 path 改分组值 /incentive-group, 114 恢复 type=2 path=/incentive 作为入口。
UPDATE `sys_menu` SET `status` = 1 WHERE `id` IN (115, 116, 118, 119);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
 (1,115),(1,116),(1,118),(1,119),
 (9,115),(9,116),
 (10,115),(10,116),(10,118),(10,119),
 (11,115),(11,116);
UPDATE `sys_menu` SET `path` = '/incentive-group' WHERE `id` = 15;
UPDATE `sys_menu` SET `menu_type` = 2, `path` = '/incentive' WHERE `id` = 114;
