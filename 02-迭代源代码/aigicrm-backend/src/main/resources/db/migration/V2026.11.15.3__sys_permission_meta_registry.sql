-- 【P2 2026-11-15】权限点注册表落表：单一事实源(类型/父码/范围轴/adminOnly/requiresOrgAdmin/废弃/无端点/依赖)
CREATE TABLE IF NOT EXISTS sys_permission_meta (
  code               VARCHAR(100) NOT NULL,
  name               VARCHAR(100) NOT NULL,
  type               VARCHAR(20)  NOT NULL,
  module             VARCHAR(30)  NOT NULL,
  parent_code        VARCHAR(100) NULL,
  scope_module       VARCHAR(30)  NULL,
  scope_action       VARCHAR(30)  NULL,
  scope_mode         VARCHAR(20)  NOT NULL DEFAULT 'NONE',
  admin_only         TINYINT      NOT NULL DEFAULT 0,
  requires_org_admin TINYINT      NOT NULL DEFAULT 0,
  deprecated         TINYINT      NOT NULL DEFAULT 0,
  no_endpoint        TINYINT      NOT NULL DEFAULT 0,
  depends_on         VARCHAR(100) NULL,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限点注册表(2026-11-15)';

INSERT IGNORE INTO sys_permission_meta
  (code, name, type, module, parent_code, scope_module, scope_action, scope_mode,
   admin_only, requires_org_admin, deprecated, no_endpoint, depends_on)
SELECT m.permission,
       m.menu_name,
       CASE
         WHEN m.permission IN ('customer:date:edit','customer:invoice-date:edit') THEN 'FIELD'
         WHEN m.menu_type = 1 THEN 'MENU'
         ELSE 'BUTTON'
       END,
       SUBSTRING_INDEX(m.permission, ':', 1),
       p.permission,
       CASE m.permission
         WHEN 'customer:view' THEN 'customer'
         WHEN 'customer:edit' THEN 'customer'
         WHEN 'customer:delete' THEN 'customer'
         WHEN 'customer:finance:edit' THEN 'customer'
         WHEN 'reminder:view' THEN 'reminder'
         WHEN 'approval:view' THEN 'approval'
         WHEN 'task:view' THEN 'task'
         WHEN 'sea:view' THEN 'sea'
         WHEN 'system:log:view' THEN 'system'
         WHEN 'score:view' THEN 'score'
         WHEN 'incentive:stats:view' THEN 'incentive'
         WHEN 'incentive:rank:view' THEN 'incentive'
         ELSE NULL
       END,
       CASE m.permission
         WHEN 'customer:view' THEN 'view'
         WHEN 'customer:edit' THEN 'edit'
         WHEN 'customer:delete' THEN 'delete'
         WHEN 'customer:finance:edit' THEN 'finance_edit'
         WHEN 'reminder:view' THEN 'view'
         WHEN 'approval:view' THEN 'view'
         WHEN 'task:view' THEN 'view'
         WHEN 'sea:view' THEN 'view'
         WHEN 'system:log:view' THEN 'view'
         WHEN 'score:view' THEN 'view'
         WHEN 'incentive:stats:view' THEN 'stats_view'
         WHEN 'incentive:rank:view' THEN 'stats_view'
         ELSE NULL
       END,
       CASE
         WHEN m.permission IN ('customer:view','customer:edit','customer:delete','customer:finance:edit',
                               'reminder:view','approval:view','task:view','sea:view','system:log:view',
                               'score:view','incentive:stats:view','incentive:rank:view') THEN 'DIRECT'
         WHEN m.permission IN ('customer:date:edit','customer:invoice-date:edit','contact:view','finance:view') THEN 'INHERIT'
         ELSE 'NONE'
       END,
       CASE WHEN m.permission IN ('system:group:add','system:group:edit','system:group:delete','system:task:run') THEN 1 ELSE 0 END,
       CASE WHEN m.permission IN ('system:user:add','system:user:edit','system:user:delete') THEN 1 ELSE 0 END,
       CASE WHEN m.status = 0 THEN 1 ELSE 0 END,
       CASE WHEN m.permission IN ('incentive:view-all','incentive:view-shared',
                                  'incentive:person-rank:export','incentive:rank:export',
                                  'system:role:grant','system:menu:edit') THEN 1 ELSE 0 END,
       CASE m.permission
         WHEN 'customer:date:edit' THEN 'customer:edit'
         WHEN 'customer:invoice-date:edit' THEN 'customer:edit'
         WHEN 'contact:view' THEN 'customer:view'
         WHEN 'finance:view' THEN 'customer:view'
         ELSE NULL
       END
  FROM sys_menu m
  LEFT JOIN sys_menu p ON p.id = m.parent_id
 WHERE m.permission IS NOT NULL AND m.permission <> ''
 ORDER BY (m.status = 1) DESC, (m.menu_type = 3) DESC;
