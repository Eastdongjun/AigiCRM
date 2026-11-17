-- V2026.11.17.6__file_acl_manage_admin_only.sql
-- 【2026-11-17 P0 独立质检】file:acl:manage 此前被授予 9/10/11 等非 admin 角色，
-- 普通销售因此可创建任意 FILE 级 ALLOW 自授他人夹内文件下载。收紧为系统管理员专属：
--   ① 删除所有非 admin 角色的 file:acl:manage 菜单绑定；
--   ② 确保 admin 角色仍绑定；
--   ③ 权限注册表 admin_only=1（权限中心显示管理员专属）。
-- 本迁移只改权限绑定，不改业务数据。

DELETE rm FROM `sys_role_menu` rm
  JOIN `sys_role` r ON r.`id` = rm.`role_id`
  JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
 WHERE m.`permission` = 'file:acl:manage'
   AND r.`role_code` <> 'admin';

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
  FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:acl:manage'
 WHERE r.`role_code` = 'admin'
   AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

UPDATE `sys_permission_meta`
   SET `admin_only` = 1
 WHERE `code` = 'file:acl:manage';
