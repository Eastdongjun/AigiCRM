-- ============================================================
-- ycmn 权限升级 — MySQL 8 兼容可重放迁移脚本
-- 合并: sys_org 兼容视图 + file_pre_id varchar + V2/V3 权限模型
-- MySQL 8.0.x 可重复执行（幂等），执行前请先备份数据库
-- ============================================================

-- ============================================================
-- 一、激励文件 ID 列 BIGINT -> VARCHAR(500)
-- 实体为 String 存 JSON 数组字符串，BIGINT 严格模式下写入会报错
-- ============================================================
ALTER TABLE `biz_incentive_record`
    MODIFY COLUMN `file_id`      VARCHAR(500) NULL COMMENT '安装后文件ID',
    MODIFY COLUMN `file_pre_id`  VARCHAR(500) NULL COMMENT '安装前文件ID',
    MODIFY COLUMN `file_mid_id`  VARCHAR(500) NULL COMMENT '安装中文件ID',
    MODIFY COLUMN `file_post_id` VARCHAR(500) NULL COMMENT '安装后文件ID',
    MODIFY COLUMN `file_track_id` VARCHAR(500) NULL COMMENT '跟踪文件ID';

-- ============================================================
-- 二、V2 权限表（CREATE TABLE IF NOT EXISTS 幂等，种子数据用 INSERT IGNORE 防重复）
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_role_org` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `role_id` BIGINT NOT NULL, `org_id` BIGINT NOT NULL,
    `include_children` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`), INDEX `idx_role_id` (`role_id`), INDEX `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-组织关联表';

CREATE TABLE IF NOT EXISTS `sys_position` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `name` VARCHAR(50) NOT NULL, `code` VARCHAR(50) NOT NULL,
    `description` VARCHAR(200) DEFAULT NULL, `org_id` BIGINT DEFAULT NULL, `status` TINYINT DEFAULT 1,
    `sort` INT DEFAULT 0, `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP, `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE INDEX `uk_code` (`code`), INDEX `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位表';

CREATE TABLE IF NOT EXISTS `sys_user_position` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT NOT NULL, `position_id` BIGINT NOT NULL,
    `is_primary` TINYINT DEFAULT 0, `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), INDEX `idx_user_id` (`user_id`), INDEX `idx_position_id` (`position_id`),
    UNIQUE INDEX `uk_user_position` (`user_id`, `position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-岗位关联表';

CREATE TABLE IF NOT EXISTS `sys_permission_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `name` VARCHAR(100) NOT NULL, `code` VARCHAR(100) NOT NULL,
    `target_entity` VARCHAR(50) NOT NULL, `rule_type` VARCHAR(30) NOT NULL,
    `target_column` VARCHAR(100) DEFAULT NULL, `operator` VARCHAR(20) DEFAULT NULL,
    `rule_value` TEXT DEFAULT NULL, `extra_config` TEXT DEFAULT NULL, `description` VARCHAR(500) DEFAULT NULL,
    `priority` INT DEFAULT 0, `status` TINYINT DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP, `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE INDEX `uk_code` (`code`),
    INDEX `idx_target_entity` (`target_entity`), INDEX `idx_rule_type` (`rule_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限规则表';

CREATE TABLE IF NOT EXISTS `sys_position_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `position_id` BIGINT NOT NULL, `rule_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`), INDEX `idx_position_id` (`position_id`), INDEX `idx_rule_id` (`rule_id`),
    UNIQUE INDEX `uk_position_rule` (`position_id`, `rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位-权限规则关联表';

CREATE TABLE IF NOT EXISTS `sys_role_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `role_id` BIGINT NOT NULL, `rule_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`), INDEX `idx_role_id` (`role_id`), INDEX `idx_rule_id` (`rule_id`),
    UNIQUE INDEX `uk_role_rule` (`role_id`, `rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限规则关联表';

-- 种子数据：岗位
INSERT IGNORE INTO `sys_position` (`name`, `code`, `description`, `sort`) VALUES
('系统管理员','admin','超级管理员岗位',0),('总部总经理','gm','集团总部总经理',10),
('大区经理','region_manager','负责一个大区的管理',20),('办事处主任','office_director','办事处负责人',30),
('部门负责人','dept_head','部门负责人',40),('销售人员','salesman','一线销售人员',50);

-- 种子数据：权限规则（target_column 对齐 biz_customer 实际列名: owner_id, created_at）
INSERT IGNORE INTO `sys_permission_rule` (`name`,`code`,`target_entity`,`rule_type`,`target_column`,`operator`,`rule_value`,`description`) VALUES
('仅看自己客户','self_only','customer','column_filter','owner_id','eq','${user.userId}','仅查看自己负责的客户'),
('本月数据','this_month','customer','time_range','created_at','between','${time.thisMonthStart},${time.thisMonthEnd}','仅查看本月创建的数据'),
('本季度数据','this_quarter','customer','time_range','created_at','between','${time.thisQuarterStart},${time.thisQuarterEnd}','仅查看本季度数据'),
-- 组织过滤规则：target_column 用 owner_id 而非 org_id（biz_customer 无 org_id 列）
-- org_filter 通过 rule_value 中的变量绑定，由 DataScopeAspect 在部门维度注入
('仅看本组织及下级客户','org_and_children','customer','org_filter','owner_id','in','${user.orgIdsAndChildren}','查看本组织及所有下级组织的客户'),
('仅看本组织客户','org_only','customer','org_filter','owner_id','eq','${user.orgId}','仅查看本组织客户'),
('隐藏客户财务数据','hide_finance','customer','field_visible',NULL,NULL,'{"hidden":["revenue","profit","debt"]}','隐藏财务相关字段'),
('隐藏客户联系方式','hide_contact','customer','field_visible',NULL,NULL,'{"hidden":["mobile","email","address"]}','隐藏联系方式字段'),
('禁止导出客户','deny_export','customer','action_control',NULL,NULL,'{"export":false}','禁止导出客户数据'),
('禁止删除客户','deny_delete','customer','action_control',NULL,NULL,'{"delete":false}','禁止删除客户'),
('只读客户','readonly','customer','action_control',NULL,NULL,'{"create":false,"edit":false,"delete":false}','客户数据只读');

-- ============================================================
-- 三、V3 集团/法人表 + 列扩展（MySQL8 幂等：存储过程判列/索引存在）
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `group_name` VARCHAR(100) NOT NULL, `group_code` VARCHAR(50) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL, `sort_order` INT DEFAULT 0, `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE INDEX `uk_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集团表';

CREATE TABLE IF NOT EXISTS `sys_legal_entity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `entity_name` VARCHAR(100) NOT NULL, `entity_code` VARCHAR(50) NOT NULL,
    `group_id` BIGINT NOT NULL, `credit_code` VARCHAR(50) DEFAULT NULL, `description` VARCHAR(500) DEFAULT NULL,
    `sort_order` INT DEFAULT 0, `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE INDEX `uk_entity_code` (`entity_code`), INDEX `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法人公司表';

-- 幂等加列/索引（MySQL8 无 ADD COLUMN IF NOT EXISTS，用存储过程模拟）
DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;
DELIMITER //
CREATE PROCEDURE `add_column_if_not_exists`(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` ', ddl);
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

DROP PROCEDURE IF EXISTS `add_index_if_not_exists`;
DELIMITER //
CREATE PROCEDURE `add_index_if_not_exists`(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = tbl AND index_name = idx) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` ', ddl);
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

-- sys_org 是视图（基于 sys_dept），列加到基表 sys_dept
CALL `add_column_if_not_exists`('sys_dept', 'group_id',        'ADD COLUMN `group_id` BIGINT DEFAULT NULL COMMENT ''所属集团ID'' AFTER `parent_id`');
CALL `add_column_if_not_exists`('sys_dept', 'legal_entity_id', 'ADD COLUMN `legal_entity_id` BIGINT DEFAULT NULL COMMENT ''所属法人公司ID'' AFTER `group_id`');
CALL `add_index_if_not_exists`('sys_dept', 'idx_group_id',         'ADD INDEX `idx_group_id` (`group_id`)');
CALL `add_index_if_not_exists`('sys_dept', 'idx_legal_entity_id',  'ADD INDEX `idx_legal_entity_id` (`legal_entity_id`)');

CALL `add_column_if_not_exists`('biz_customer', 'group_id',        'ADD COLUMN `group_id` BIGINT DEFAULT NULL COMMENT ''所属集团ID'' AFTER `region_id`');
CALL `add_column_if_not_exists`('biz_customer', 'legal_entity_id', 'ADD COLUMN `legal_entity_id` BIGINT DEFAULT NULL COMMENT ''所属法人公司ID'' AFTER `group_id`');
CALL `add_index_if_not_exists`('biz_customer', 'idx_group_id',         'ADD INDEX `idx_group_id` (`group_id`)');
CALL `add_index_if_not_exists`('biz_customer', 'idx_legal_entity_id',  'ADD INDEX `idx_legal_entity_id` (`legal_entity_id`)');

DROP PROCEDURE `add_column_if_not_exists`;
DROP PROCEDURE `add_index_if_not_exists`;

-- ============================================================
-- 四、sys_org 兼容视图（在加列之后创建，确保 group_id/legal_entity_id 已存在）
-- 新权限引擎引用 sys_org 表，旧库只有 sys_dept，用视图桥接
-- ============================================================
DROP VIEW IF EXISTS `sys_org`;
CREATE VIEW `sys_org` AS
SELECT
    `id`, `parent_id`, `group_id`, `legal_entity_id`,
    `dept_name` AS `org_name`, `dept_code` AS `org_code`, `dept_type` AS `org_type`,
    `leader_id`, `sort_order`, `status`, `created_at`, `updated_at`
FROM `sys_dept`;

-- 默认集团/法人（幂等）
INSERT IGNORE INTO `sys_group` (`id`, `group_name`, `group_code`, `description`, `sort_order`) VALUES
(1, '陕西青阳耀东科技集团', 'qyyd', '默认集团（系统初始化）', 0);
INSERT IGNORE INTO `sys_legal_entity` (`id`, `entity_name`, `entity_code`, `group_id`, `description`, `sort_order`) VALUES
(1, '陕西青阳耀东科技有限公司', 'qyyd-main', 1, '主法人公司', 0);

-- ============================================================
-- 五、新增菜单权限（岗位/规则/集团/法人管理）
-- 给 admin 角色（id=1）绑定所有新权限
-- ============================================================

-- 岗位管理菜单
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `perms`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `status`, `visible`) VALUES
(51, 2, '岗位管理', 'system:position:view', 'C', '/system/position', 'system/position/index', 'post', 7, 1, '0'),
(52, 51, '岗位新增', 'system:position:add', 'F', NULL, NULL, NULL, 1, 1, '0'),
(53, 51, '岗位编辑', 'system:position:edit', 'F', NULL, NULL, NULL, 2, 1, '0');

-- 权限规则管理菜单
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `perms`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `status`, `visible`) VALUES
(54, 2, '权限规则', 'system:rule:view', 'C', '/system/rule', 'system/rule/index', 'lock', 8, 1, '0'),
(55, 54, '规则新增', 'system:rule:add', 'F', NULL, NULL, NULL, 1, 1, '0'),
(56, 54, '规则编辑', 'system:rule:edit', 'F', NULL, NULL, NULL, 2, 1, '0');

-- 集团管理菜单
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `perms`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `status`, `visible`) VALUES
(57, 2, '集团管理', 'system:group:view', 'C', '/system/group', 'system/group/index', 'apartment', 9, 1, '0'),
(58, 57, '集团新增', 'system:group:add', 'F', NULL, NULL, NULL, 1, 1, '0'),
(59, 57, '集团编辑', 'system:group:edit', 'F', NULL, NULL, NULL, 2, 1, '0');

-- admin 角色绑定所有新权限
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 51),(1, 52),(1, 53),(1, 54),(1, 55),(1, 56),(1, 57),(1, 58),(1, 59);
