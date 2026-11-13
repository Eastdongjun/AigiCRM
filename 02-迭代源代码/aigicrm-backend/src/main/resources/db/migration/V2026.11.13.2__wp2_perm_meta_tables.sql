-- V2026.11.13.2 WP2 数据模型与迁移(权限升级施工规格书 WP2.1/2.2/2.3-动作注册部分)
--
-- 新权限元模型九件套(规格 §2.1 最终 DDL, 列名/键一字不改):
--   sys_resource / sys_action / sys_scope_type / sys_perm_grant /
--   sys_user_role 三列 / sys_position_role / sys_employment / sys_org_closure / sys_perm_epoch
-- 本迁移: DDL + scope_type 种子 + 资源注册(WP7 表 10 模块) + 动作注册(从 sys_menu.permission 去重生成,
-- 现有权限码字符串保持不变=uk_code 自然键锚定)。
-- 授权/任职/闭包回填在 V2026.11.13.3。
-- 规约: 每表显式 CHARSET/COLLATE(空库重放教训: 只钉 CHARSET 在 MySQL8 默认 0900_ai_ci);
--       不硬编码行 id, 全部自然键(INV-MIG-001); 幂等(IF NOT EXISTS / NOT EXISTS / INSERT IGNORE)。

-- ============ 1. 资源注册表 ============
CREATE TABLE IF NOT EXISTS sys_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_code VARCHAR(50) NOT NULL,
  resource_code VARCHAR(50) NOT NULL,
  name VARCHAR(100) NOT NULL,
  owner_column VARCHAR(64) NULL,
  dept_column VARCHAR(64) NULL,
  region_column VARCHAR(64) NULL,
  office_column VARCHAR(64) NULL,
  tenant_column VARCHAR(64) NOT NULL DEFAULT 'tenant_id',
  default_anchor VARCHAR(10) NOT NULL DEFAULT 'OWNER',
  object_policy VARCHAR(20) NOT NULL DEFAULT 'NONE',
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_module_resource (module_code, resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限资源注册表(WP2 规格书 §2.1)';

-- ============ 2. 动作注册表 ============
CREATE TABLE IF NOT EXISTS sys_action (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  action_code VARCHAR(50) NOT NULL,
  code VARCHAR(150) NOT NULL,
  name VARCHAR(100) NOT NULL,
  scope_required TINYINT NOT NULL DEFAULT 1,
  view_like TINYINT NOT NULL DEFAULT 0,
  audit_level TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_code (code),
  UNIQUE KEY uk_resource_action (resource_id, action_code),
  KEY idx_resource (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动作注册表(code=现有权限码字符串, 一字不改)';

-- ============ 3. 范围类型字典 ============
CREATE TABLE IF NOT EXISTS sys_scope_type (
  code VARCHAR(24) PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据范围类型(12 档)';

INSERT INTO sys_scope_type (code, name, sort_order) VALUES
  ('SELF', '仅本人', 1),
  ('MY_DEPT', '本部门(不含子级)', 2),
  ('MY_DEPT_SUB', '本部门及以下', 3),
  ('MY_PRIMARY_DEPT', '主职部门(不含子级)', 4),
  ('MY_PRIMARY_DEPT_SUB', '主职部门及以下', 5),
  ('MY_OFFICE', '本办事处(不含子级)', 6),
  ('MY_OFFICE_SUB', '本办事处及以下', 7),
  ('MY_REGION', '本大区(不含子级)', 8),
  ('MY_REGION_SUB', '本大区及以下', 9),
  ('MY_TEAM', '本人及下属链', 10),
  ('CUSTOM', '自定义组织', 11),
  ('ALL', '全部(本集团)', 12)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============ 4. 授权表 ============
CREATE TABLE IF NOT EXISTS sys_perm_grant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  subject_type VARCHAR(10) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  action_code VARCHAR(150) NOT NULL,
  scope_type VARCHAR(24) NOT NULL DEFAULT 'SELF',
  scope_config JSON NULL,
  anchor VARCHAR(10) NOT NULL DEFAULT 'OWNER',
  effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
  expire_at DATETIME NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_subject_action (tenant_id, subject_type, subject_id, action_code),
  KEY idx_action (action_code),
  KEY idx_subject (subject_type, subject_id),
  KEY idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一授权表(主体×动作×范围; effect 仅 ALLOW, 无 DENY=锁定决策#3)';

-- ============ 5. 旧表增列 ============
-- 幂等守卫: 生产 MariaDB 支持 ADD COLUMN IF NOT EXISTS, 但空库重放库是 mysql:8.0(不支持)
-- ——用 information_schema 守卫的动态 ALTER, 双引擎一致可重放。
SET @ddl_user_role = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE sys_user_role ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT ''MANUAL'', ADD COLUMN expire_at DATETIME NULL, ADD COLUMN created_by BIGINT NULL',
  'SELECT ''sys_user_role 增列已存在, 跳过'' AS note')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'source');
PREPARE st_user_role FROM @ddl_user_role;
EXECUTE st_user_role;
DEALLOCATE PREPARE st_user_role;

CREATE TABLE IF NOT EXISTS sys_position_role (
  position_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (position_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位-角色绑定(岗位身份动作来源)';

-- ============ 6. 任职表 ============
CREATE TABLE IF NOT EXISTS sys_employment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  org_id BIGINT NOT NULL,
  position_id BIGINT NOT NULL DEFAULT 0,
  is_primary TINYINT NOT NULL DEFAULT 0,
  valid_from DATE NULL,
  valid_to DATE NULL,
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_user_org_position (user_id, org_id, position_id),
  KEY idx_user (user_id),
  KEY idx_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任职表(多任职=范围并集, 锁定决策#2; 本部门=全部有效任职组织)';

-- ============ 7. 组织闭包表 ============
CREATE TABLE IF NOT EXISTS sys_org_closure (
  ancestor_id BIGINT NOT NULL,
  descendant_id BIGINT NOT NULL,
  depth INT NOT NULL,
  PRIMARY KEY (ancestor_id, descendant_id),
  KEY idx_desc (descendant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织闭包(本部门/大区/办事处解析基建; 组织变更事件重建=WP5)';

-- ============ 8. 版本号表 ============
CREATE TABLE IF NOT EXISTS sys_perm_epoch (
  id TINYINT PRIMARY KEY DEFAULT 1,
  epoch BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限版本号(保存即生效+双端刷新, WP5 消费)';

INSERT INTO sys_perm_epoch (id, epoch) VALUES (1, 1)
ON DUPLICATE KEY UPDATE id = id;

-- ============ 9. 资源种子(WP7 表 10 模块 → 16 资源) ============
-- 对象策略: POOL=公海池名单对象级; TABLE=激励授权表对象级; NONE=暂无对象级内核(夹级ACL/路由码)
INSERT INTO sys_resource (module_code, resource_code, name, owner_column, dept_column, default_anchor, object_policy)
VALUES
  ('customer',  'customer',          '客户',          'owner_id',    'dept_id', 'OWNER', 'NONE'),
  ('file',      'file',              '文件',          'uploader_id', NULL,      'OWNER', 'NONE'),
  ('file',      'folder',            '文件夹',        NULL,          NULL,      'OWNER', 'NONE'),
  ('approval',  'approval',          '审批单',        'applicant_id', NULL,     'OWNER', 'NONE'),
  ('reminder',  'reminder',          '提醒',          'owner_id',    NULL,      'OWNER', 'NONE'),
  ('task',      'task',              '任务',          'owner_id',    NULL,      'OWNER', 'NONE'),
  ('sea',       'sea_pool',          '公海池',        NULL,          NULL,      'OWNER', 'POOL'),
  ('incentive', 'incentive_record',  '激励记录',      'user_id',     NULL,      'OWNER', 'TABLE'),
  ('score',     'score_entry',       '学分记录',      'user_id',     NULL,      'OWNER', 'NONE'),
  ('statistics','statistics',        '统计分析(入口)', NULL,         NULL,      'OWNER', 'NONE'),
  ('finance',   'finance',           '财务(跟随客户)', NULL,          NULL,      'OWNER', 'NONE'),
  ('contact',   'contact',           '联系人(跟随客户)', NULL,        NULL,      'OWNER', 'NONE'),
  ('system',    'sys_user',          '人员',          NULL,          'dept_id', 'ORG',   'NONE'),
  ('system',    'sys_dept',          '组织',          NULL,          NULL,      'ORG',   'NONE'),
  ('system',    'sys_log',           '系统日志',      NULL,          NULL,      'ORG',   'NONE'),
  ('system',    'system_admin',      '系统管理(路由层)', NULL,         NULL,      'ORG',   'NONE')
ON DUPLICATE KEY UPDATE name = VALUES(name), owner_column = VALUES(owner_column),
  dept_column = VALUES(dept_column), object_policy = VALUES(object_policy);

-- ============ 10. 动作注册回填(从 sys_menu.permission 去重生成; 码字符串保持不变) ============
-- resource 归组: 按码前缀; 3 段码展开特例见 CASE。
-- scope_required 初始档位=现行 14 轴+入口码退役口径(WP1.5), WP7 模块接入按消费实况修订:
--   statistics:view=0(入口码, 锁定决策#7); 路由/对象级守卫码=0; 有数据范围档的码=1。
-- view_like: 查看类动作(上级/负责人开关唯一作用面, 锁定决策#8)。
INSERT INTO sys_action (resource_id, action_code, code, name, scope_required, view_like)
SELECT
  res.id,
  CASE   -- 全 searched CASE(混用 simple WHEN+布尔 WHEN 会触发字符串→DOUBLE 强转报错)
    WHEN c.permission LIKE 'system:%'
         AND c.permission NOT LIKE 'system:user:%'
         AND c.permission NOT LIKE 'system:dept:%'
         AND c.permission NOT LIKE 'system:log:%'
      THEN CONCAT(SUBSTRING_INDEX(SUBSTRING_INDEX(c.permission, ':', 2), ':', -1), '_',
                  SUBSTRING_INDEX(c.permission, ':', -1))  -- system_admin 内复合动作(role_edit 等, 防同资源撞车)
    WHEN (LENGTH(c.permission) - LENGTH(REPLACE(c.permission, ':', ''))) = 1
      THEN SUBSTRING_INDEX(c.permission, ':', -1)          -- 两段码: 尾段即动作
    ELSE REPLACE(CONCAT(SUBSTRING_INDEX(SUBSTRING_INDEX(c.permission, ':', 2), ':', -1), '_',
                        SUBSTRING_INDEX(c.permission, ':', -1)), '-', '_')
      -- 三段码: 中段_尾段(通则, 覆盖全部历史码变体, 不再逐个枚举特例)
  END,
  c.permission,
  c.permission,
  CASE
    WHEN c.permission = 'statistics:view' THEN 0  -- 入口码(锁定决策#7)
    WHEN c.permission IN ('customer:view','customer:edit','customer:delete','customer:finance:edit',
                          'customer:export','file:view','file:manage','file:delete',
                          'approval:view','reminder:view','sea:view',
                          'incentive:stats:view','incentive:rank:view',
                          'score:view','task:view','system:log:view') THEN 1
    ELSE 0  -- 路由门码/对象级守卫(夹ACL/池/授权表/经办人)/跟随客户码
  END,
  CASE
    WHEN c.permission IN ('customer:view','customer:export','file:view','approval:view',
                          'reminder:view','sea:view','incentive:stats:view','incentive:rank:view',
                          'score:view','task:view','system:log:view') THEN 1
    ELSE 0
  END
FROM (SELECT DISTINCT permission FROM sys_menu
      WHERE permission IS NOT NULL AND permission <> '' AND status = 1) c
JOIN sys_resource res ON (
     (c.permission LIKE 'customer:%' AND res.resource_code = 'customer')
  OR (c.permission IN ('file:view','file:upload','file:edit','file:delete','file:share','file:manage')
      AND res.resource_code = 'file')
  OR (c.permission IN ('file:folder:manage','file:acl:manage') AND res.resource_code = 'folder')
  OR (c.permission LIKE 'approval:%' AND res.resource_code = 'approval')
  OR (c.permission LIKE 'reminder:%' AND res.resource_code = 'reminder')
  OR (c.permission LIKE 'task:%' AND res.resource_code = 'task')
  OR (c.permission LIKE 'sea:%' AND res.resource_code = 'sea_pool')
  OR (c.permission LIKE 'incentive:%' AND res.resource_code = 'incentive_record')
  OR (c.permission LIKE 'score:%' AND res.resource_code = 'score_entry')
  OR (c.permission LIKE 'statistics:%' AND res.resource_code = 'statistics')
  OR (c.permission LIKE 'finance:%' AND res.resource_code = 'finance')
  OR (c.permission LIKE 'contact:%' AND res.resource_code = 'contact')
  OR (c.permission LIKE 'system:user:%' AND res.resource_code = 'sys_user')
  OR (c.permission LIKE 'system:dept:%' AND res.resource_code = 'sys_dept')
  OR (c.permission LIKE 'system:log:%' AND res.resource_code = 'sys_log')
  -- 兜底分支必须排除上面已归组的 system:* 前缀, 否则 JOIN 扇出同一 code 两次 → uk 撞车
  OR (c.permission LIKE 'system:%'
      AND c.permission NOT LIKE 'system:user:%'
      AND c.permission NOT LIKE 'system:dept:%'
      AND c.permission NOT LIKE 'system:log:%'
      AND res.resource_code = 'system_admin')
)
WHERE NOT EXISTS (SELECT 1 FROM sys_action a WHERE a.code = c.permission);
