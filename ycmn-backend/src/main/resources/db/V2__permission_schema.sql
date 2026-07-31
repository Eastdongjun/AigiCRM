-- ============================================================
-- ycmn 集团多层级权限体系 — 岗位制 + 规则引擎
-- 执行前请备份数据库
-- ============================================================

-- 1. 角色-组织关联表（已有代码，补充建表语句）
CREATE TABLE IF NOT EXISTS `sys_role_org` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`     BIGINT NOT NULL COMMENT '角色ID',
    `org_id`      BIGINT NOT NULL COMMENT '组织ID',
    `include_children` TINYINT DEFAULT 0 COMMENT '是否包含下级 0=否 1=是',
    PRIMARY KEY (`id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-组织关联表';

-- 2. 岗位表
CREATE TABLE IF NOT EXISTS `sys_position` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(50)  NOT NULL COMMENT '岗位名称（如：华东大区经理）',
    `code`        VARCHAR(50)  NOT NULL COMMENT '岗位编码（唯一）',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '岗位描述',
    `org_id`      BIGINT DEFAULT NULL COMMENT '所属组织ID',
    `status`      TINYINT DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
    `sort`        INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_code` (`code`),
    INDEX `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位表';

-- 3. 用户-岗位关联表
CREATE TABLE IF NOT EXISTS `sys_user_position` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT NOT NULL COMMENT '用户ID',
    `position_id` BIGINT NOT NULL COMMENT '岗位ID',
    `is_primary`  TINYINT DEFAULT 0 COMMENT '是否主岗位 0=否 1=是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_position_id` (`position_id`),
    UNIQUE INDEX `uk_user_position` (`user_id`, `position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-岗位关联表';

-- 4. 权限规则表
CREATE TABLE IF NOT EXISTS `sys_permission_rule` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`              VARCHAR(100) NOT NULL COMMENT '规则名称',
    `code`              VARCHAR(100) NOT NULL COMMENT '规则编码（唯一）',
    `target_entity`     VARCHAR(50)  NOT NULL COMMENT '目标实体（customer/contact/payment/order）',
    `rule_type`         VARCHAR(30)  NOT NULL COMMENT '规则类型: column_filter/org_filter/field_visible/action_control/time_range',
    `target_column`     VARCHAR(100) DEFAULT NULL COMMENT '目标字段（column_filter时必填）',
    `operator`          VARCHAR(20)  DEFAULT NULL COMMENT '操作符: eq/ne/in/between/like/is_null',
    `rule_value`        TEXT         DEFAULT NULL COMMENT '规则值，支持变量: ${user.userId} ${user.deptId} ${user.orgId} ${user.username}',
    `extra_config`      TEXT         DEFAULT NULL COMMENT '扩展配置JSON',
    `description`       VARCHAR(500) DEFAULT NULL COMMENT '规则描述',
    `priority`          INT DEFAULT 0 COMMENT '优先级（越小越优先）',
    `status`            TINYINT DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
    `create_time`       DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_code` (`code`),
    INDEX `idx_target_entity` (`target_entity`),
    INDEX `idx_rule_type` (`rule_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限规则表';

-- 5. 岗位-规则关联表
CREATE TABLE IF NOT EXISTS `sys_position_rule` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `position_id` BIGINT NOT NULL COMMENT '岗位ID',
    `rule_id`     BIGINT NOT NULL COMMENT '规则ID',
    PRIMARY KEY (`id`),
    INDEX `idx_position_id` (`position_id`),
    INDEX `idx_rule_id` (`rule_id`),
    UNIQUE INDEX `uk_position_rule` (`position_id`, `rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位-权限规则关联表';

-- 6. 角色-规则关联表（兼容直接给角色配规则）
CREATE TABLE IF NOT EXISTS `sys_role_rule` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `rule_id` BIGINT NOT NULL COMMENT '规则ID',
    PRIMARY KEY (`id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_rule_id` (`rule_id`),
    UNIQUE INDEX `uk_role_rule` (`role_id`, `rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限规则关联表';

-- ============================================================
-- 初始化示例数据
-- ============================================================

-- 岗位示例
INSERT INTO `sys_position` (`name`, `code`, `description`, `sort`) VALUES
('系统管理员',    'admin',          '超级管理员岗位',   0),
('总部总经理',    'gm',             '集团总部总经理',   10),
('大区经理',      'region_manager', '负责一个大区的管理',20),
('办事处主任',    'office_director','办事处负责人',     30),
('部门负责人',    'dept_head',      '部门负责人',       40),
('销售人员',      'salesman',       '一线销售人员',     50);

-- 权限规则示例
INSERT INTO `sys_permission_rule` (`name`, `code`, `target_entity`, `rule_type`, `target_column`, `operator`, `rule_value`, `description`) VALUES
-- 组织过滤类
('仅看本组织及下级客户', 'org_and_children', 'customer', 'org_filter', 'owner_id', 'in', '${user.orgIdsAndChildren}', '查看本组织及所有下级组织的客户'),
('仅看本组织客户',       'org_only',         'customer', 'org_filter', 'owner_id', 'eq', '${user.orgId}',           '仅查看本组织客户'),
('仅看自己客户',         'self_only',        'customer', 'column_filter', 'owner_id', 'eq', '${user.userId}',     '仅查看自己负责的客户'),

-- 时间范围类
('本月数据',             'this_month',       'customer', 'time_range', 'created_at', 'between', '${time.thisMonthStart},${time.thisMonthEnd}', '仅查看本月创建的数据'),
('本季度数据',           'this_quarter',     'customer', 'time_range', 'created_at', 'between', '${time.thisQuarterStart},${time.thisQuarterEnd}', '仅查看本季度数据'),

-- 字段可见性类
('隐藏客户财务数据',     'hide_finance',     'customer', 'field_visible', NULL, NULL, '{"hidden":["revenue","profit","debt"]}', '隐藏财务相关字段'),
('隐藏客户联系方式',     'hide_contact',      'customer', 'field_visible', NULL, NULL, '{"hidden":["mobile","email","address"]}', '隐藏联系方式字段'),

-- 操作控制类
('禁止导出客户',         'deny_export',      'customer', 'action_control', NULL, NULL, '{"export":false}',             '禁止导出客户数据'),
('禁止删除客户',         'deny_delete',      'customer', 'action_control', NULL, NULL, '{"delete":false}',             '禁止删除客户'),
('只读客户',             'readonly',         'customer', 'action_control', NULL, NULL, '{"create":false,"edit":false,"delete":false}', '客户数据只读');
