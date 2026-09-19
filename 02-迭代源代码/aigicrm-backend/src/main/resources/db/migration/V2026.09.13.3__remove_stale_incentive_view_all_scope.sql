-- 2026-09-13 清理角色权限范围表中的脏行（系统设置/权限管理 核查处置 #5，用户已批准：仅清脏行、不删表）
--
-- 现象：role_perm_scope 中存在 1 行把**权限码**当作**角色码**的记录：
--   id=79, role_code='incentive_view_all', module='incentive', action='view', scope_level='all',
--   assignee_type='ROLE', assignee_key='incentive_view_all', anchor='OWNER'（创建于 2026-08-16 11:33:02）
-- 来源：2026-08-16「激励授权表化」之前的一版设计（激励专项能力曾用权限码 incentive:view-all 承载），
--   改表化后该码废弃（IncentivePermissionController 头注释：incentive:view-shared/view-all/manage 已废弃）。
-- 影响：sys_role 中不存在 role_code='incentive_view_all' 的角色 ⇒ 权限管理页的角色下拉永远选不到它、
--   /api/system/perm-scopes/{roleCode} 也不会被以该键调用 ⇒ 该行**永不生效**，属脏数据（会让"哪些角色配过范围"失真）。
-- 处置：只删除该行。**不删除** 表 biz_incentive_permission（旧授权表，用户明确本轮不删表）。
--
-- 幂等：DELETE 带完整条件，可重复执行。
-- 回滚（如确需恢复该行）：
--   INSERT INTO `role_perm_scope` (`id`,`role_code`,`module`,`action`,`scope_level`,`created_at`,`updated_at`,`assignee_type`,`assignee_key`,`anchor`)
--   VALUES (79,'incentive_view_all','incentive','view','all','2026-08-16 11:33:02','2026-08-16 11:33:02','ROLE','incentive_view_all','OWNER');

DELETE FROM `role_perm_scope`
 WHERE `assignee_type` = 'ROLE'
   AND `assignee_key` = 'incentive_view_all'
   AND `role_code` = 'incentive_view_all'
   AND NOT EXISTS (SELECT 1 FROM `sys_role` r WHERE r.`role_code` = 'incentive_view_all');
