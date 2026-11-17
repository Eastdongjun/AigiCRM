-- V2026.11.17.2__fabushi_version_rollback_org_scope.sql
-- 【2026-11-17 阶段二】把“法布施文件”和“文件版本回滚”从功能码升级为
-- 功能码 + 组织数据范围（权限中心可按角色/人员勾选大区/办事处/部门/self）。
--
-- 默认口径（用户 2026-11-17 拍板）：
--   - 法布施文件可见 = 公司级 all（法布施内容默认全公司可见）；
--   - 法布施文件删除 = admin all；其他角色先 self（未授权功能码时无实际效果）；
--   - 文件版本回滚 = admin all；其他角色先 self（未授权功能码时无实际效果）。
-- 管理员后续在权限中心调整；本迁移不删除/不改写业务数据。

-- 1) 权限注册表：挂接真实消费的 (module, action)，让权限中心显示范围选择器
UPDATE `sys_permission_meta`
   SET `scope_module` = 'fabushi', `scope_action` = 'file_view', `scope_mode` = 'DIRECT'
 WHERE `code` = 'fabushi:view';

UPDATE `sys_permission_meta`
   SET `scope_module` = 'fabushi', `scope_action` = 'file_delete', `scope_mode` = 'DIRECT'
 WHERE `code` = 'fabushi:file:delete';

UPDATE `sys_permission_meta`
   SET `scope_module` = 'file', `scope_action` = 'version_rollback', `scope_mode` = 'DIRECT'
 WHERE `code` = 'file:version:rollback';

-- 2) 角色默认范围行（存在用户级/岗位级配置时仍按 USER > POSITION > ROLE 覆盖）
--    法布施可见：所有启用角色默认 all
INSERT INTO `role_perm_scope` (`role_code`, `module`, `action`, `scope_level`, `assignee_type`, `assignee_key`, `anchor`)
SELECT r.`role_code`, 'fabushi', 'file_view', 'all', 'ROLE', r.`role_code`, 'OWNER'
  FROM `sys_role` r
 WHERE r.`status` = 1 AND r.`role_code` IS NOT NULL
   AND NOT EXISTS (
     SELECT 1 FROM `role_perm_scope` x
      WHERE x.`role_code` = r.`role_code` AND x.`module` = 'fabushi' AND x.`action` = 'file_view'
        AND x.`assignee_type` = 'ROLE');

-- 法布施删除：admin all，其余角色 self（安全默认；授权功能码后才有意义）
INSERT INTO `role_perm_scope` (`role_code`, `module`, `action`, `scope_level`, `assignee_type`, `assignee_key`, `anchor`)
SELECT r.`role_code`, 'fabushi', 'file_delete',
       CASE WHEN r.`role_code` = 'admin' THEN 'all' ELSE 'self' END,
       'ROLE', r.`role_code`, 'OWNER'
  FROM `sys_role` r
 WHERE r.`status` = 1 AND r.`role_code` IS NOT NULL
   AND NOT EXISTS (
     SELECT 1 FROM `role_perm_scope` x
      WHERE x.`role_code` = r.`role_code` AND x.`module` = 'fabushi' AND x.`action` = 'file_delete'
        AND x.`assignee_type` = 'ROLE');

-- 版本回滚：admin all，其余角色 self（安全默认）
INSERT INTO `role_perm_scope` (`role_code`, `module`, `action`, `scope_level`, `assignee_type`, `assignee_key`, `anchor`)
SELECT r.`role_code`, 'file', 'version_rollback',
       CASE WHEN r.`role_code` = 'admin' THEN 'all' ELSE 'self' END,
       'ROLE', r.`role_code`, 'OWNER'
  FROM `sys_role` r
 WHERE r.`status` = 1 AND r.`role_code` IS NOT NULL
   AND NOT EXISTS (
     SELECT 1 FROM `role_perm_scope` x
      WHERE x.`role_code` = r.`role_code` AND x.`module` = 'file' AND x.`action` = 'version_rollback'
        AND x.`assignee_type` = 'ROLE');
