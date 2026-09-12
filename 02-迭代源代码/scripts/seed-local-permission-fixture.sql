-- Local-only fixture for validating scripts/gen-permission-dict.sh local.
-- Never apply this file to a shared, test, or production database.

INSERT INTO `sys_role` (`id`, `group_id`, `role_name`, `role_code`, `description`, `data_scope`, `status`)
VALUES
  (1, 1, '本地管理员', 'local_admin', 'local-only fixture', 'all', 1),
  (9, 1, '本地行政', 'local_operator', 'local-only fixture', 'dept', 1),
  (10, 1, '本地销售总监', 'local_sales_director', 'local-only fixture', 'dept_and_children', 1),
  (11, 1, '本地销售员', 'local_sales', 'local-only fixture', 'self', 1)
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `status` = 1;

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `visible`, `status`)
VALUES
  (9001, 0, '激励新增', 3, 'incentive:add', 1, 1),
  (9002, 0, '文件 ACL 管理', 3, 'file:acl:manage', 1, 1),
  (9003, 0, '公海导入', 3, 'sea:import', 1, 1),
  (9004, 0, '激励查询阅读统计', 3, 'incentive:read-stats', 1, 1),
  (9005, 0, '客户查看', 3, 'customer:view', 1, 1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `status` = 1;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES
  (1, 9001), (9, 9001), (10, 9001), (11, 9001),
  (1, 9002),
  (1, 9003), (9, 9003), (10, 9003),
  (1, 9004), (9, 9004),
  (1, 9005), (9, 9005), (10, 9005), (11, 9005);

-- Keep the local administrator able to exercise every controller guard. These
-- rows mirror permission literals in the backend; production menu data is
-- managed by the application's normal seed/migration pipeline.
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `visible`, `status`)
VALUES
  (9010,0,'approval:approve',3,'approval:approve',1,1),(9011,0,'approval:reject',3,'approval:reject',1,1),(9012,0,'approval:view',3,'approval:view',1,1),
  (9013,0,'customer:add',3,'customer:add',1,1),(9014,0,'customer:delete',3,'customer:delete',1,1),(9015,0,'customer:edit',3,'customer:edit',1,1),(9016,0,'customer:export',3,'customer:export',1,1),(9017,0,'customer:import',3,'customer:import',1,1),
  (9020,0,'file:delete',3,'file:delete',1,1),(9021,0,'file:edit',3,'file:edit',1,1),(9022,0,'file:folder:manage',3,'file:folder:manage',1,1),(9023,0,'file:share',3,'file:share',1,1),(9024,0,'file:upload',3,'file:upload',1,1),(9025,0,'file:view',3,'file:view',1,1),
  (9027,0,'incentive:edit',3,'incentive:edit',1,1),(9029,0,'incentive:view',3,'incentive:view',1,1),(9030,0,'reminder:view',3,'reminder:view',1,1),
  (9031,0,'score:entry',3,'score:entry',1,1),(9032,0,'score:manage',3,'score:manage',1,1),(9033,0,'score:view',3,'score:view',1,1),
  (9034,0,'sea:config:edit',3,'sea:config:edit',1,1),(9035,0,'sea:config:view',3,'sea:config:view',1,1),(9037,0,'sea:view',3,'sea:view',1,1),(9038,0,'statistics:view',3,'statistics:view',1,1),
  (9039,0,'system:config:edit',3,'system:config:edit',1,1),(9040,0,'system:config:view',3,'system:config:view',1,1),(9041,0,'system:dept:edit',3,'system:dept:edit',1,1),(9042,0,'system:dept:view',3,'system:dept:view',1,1),
  (9043,0,'system:group:add',3,'system:group:add',1,1),(9044,0,'system:group:delete',3,'system:group:delete',1,1),(9045,0,'system:group:edit',3,'system:group:edit',1,1),(9046,0,'system:group:view',3,'system:group:view',1,1),(9047,0,'system:log:view',3,'system:log:view',1,1),(9048,0,'system:menu:view',3,'system:menu:view',1,1),
  (9049,0,'system:position:add',3,'system:position:add',1,1),(9050,0,'system:position:delete',3,'system:position:delete',1,1),(9051,0,'system:position:edit',3,'system:position:edit',1,1),(9052,0,'system:position:view',3,'system:position:view',1,1),
  (9053,0,'system:role:add',3,'system:role:add',1,1),(9054,0,'system:role:delete',3,'system:role:delete',1,1),(9055,0,'system:role:edit',3,'system:role:edit',1,1),(9056,0,'system:role:view',3,'system:role:view',1,1),
  (9057,0,'system:rule:add',3,'system:rule:add',1,1),(9058,0,'system:rule:delete',3,'system:rule:delete',1,1),(9059,0,'system:rule:edit',3,'system:rule:edit',1,1),(9060,0,'system:rule:view',3,'system:rule:view',1,1),
  (9061,0,'system:user:add',3,'system:user:add',1,1),(9062,0,'system:user:delete',3,'system:user:delete',1,1),(9063,0,'system:user:edit',3,'system:user:edit',1,1),(9064,0,'system:user:view',3,'system:user:view',1,1),
  (9065,0,'dashboard:view',3,'dashboard:view',1,1),(9066,0,'system:org:admin',3,'system:org:admin',1,1)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `status` = 1;

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE id BETWEEN 9001 AND 9066;
