-- ============================================
-- YCMN 生产升级迁移脚本 v2.6.2 -> 新版(模块化)
-- 生成时间: 2026-08-02 | 来源: 测试库结构+数据
-- 内容: 新表/视图/加列/菜单/角色绑定/基础数据
-- 执行前必须: 全量备份 + 演练通过; 仅执行一次
-- ============================================
SET NAMES utf8mb4;
USE ycmn;

-- [1] 新建 11 张表
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'æµç¨‹åç§°',
  `flow_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'æµç¨‹ç±»åž‹',
  `approver_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'å®¡æ‰¹äººç±»åž‹ USER/ROLE/DEPT',
  `approver_ids` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'å®¡æ‰¹äººIDåˆ—è¡¨ JSON',
  `sign_mode` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'ç­¾ç½²æ¨¡å¼ ALL/ANY',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_file_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `can_upload` tinyint DEFAULT '0' COMMENT 'å…¬å…±ç›˜ä¸Šä¼ æƒé™',
  `can_delete` tinyint DEFAULT '0' COMMENT 'å…¬å…±ç›˜åˆ é™¤æƒé™',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶ç®¡ç†æƒé™è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_name` varchar(100) NOT NULL,
  `group_code` varchar(50) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_code` (`group_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='é›†å›¢è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_legal_entity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_name` varchar(100) NOT NULL,
  `entity_code` varchar(50) NOT NULL,
  `group_id` bigint NOT NULL,
  `credit_code` varchar(50) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_code` (`entity_code`),
  KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ³•äººå…¬å¸è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_org_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `code` varchar(100) NOT NULL,
  `target_entity` varchar(50) NOT NULL,
  `rule_type` varchar(30) NOT NULL,
  `target_column` varchar(100) DEFAULT NULL,
  `operator` varchar(20) DEFAULT NULL,
  `rule_value` text,
  `extra_config` text,
  `description` varchar(500) DEFAULT NULL,
  `priority` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_target_entity` (`target_entity`),
  KEY `idx_rule_type` (`rule_type`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æƒé™è§„åˆ™è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `code` varchar(50) NOT NULL,
  `description` varchar(200) DEFAULT NULL,
  `org_id` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `sort` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_org_id` (`org_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å²—ä½è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_position_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `position_id` bigint NOT NULL,
  `rule_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_rule` (`position_id`,`rule_id`),
  KEY `idx_position_id` (`position_id`),
  KEY `idx_rule_id` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å²—ä½-æƒé™è§„åˆ™å…³è”è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_org` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `org_id` bigint NOT NULL,
  `include_children` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_org_id` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²-ç»„ç»‡å…³è”è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `rule_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_rule` (`role_id`,`rule_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_rule_id` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²-æƒé™è§„åˆ™å…³è”è¡¨';
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `position_id` bigint NOT NULL,
  `is_primary` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_position` (`user_id`,`position_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_position_id` (`position_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·-å²—ä½å…³è”è¡¨';
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;



-- [2] 5 张表新增 11 列(幂等判断)
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_approval_node' AND column_name='config_id');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `biz_approval_node` ADD COLUMN `config_id` bigint NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_approval_node' AND column_name='sign_mode');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `biz_approval_node` ADD COLUMN `sign_mode` varchar(20) NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_customer' AND column_name='group_id');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `biz_customer` ADD COLUMN `group_id` bigint NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_customer' AND column_name='legal_entity_id');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `biz_customer` ADD COLUMN `legal_entity_id` bigint NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_dept' AND column_name='group_id');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `sys_dept` ADD COLUMN `group_id` bigint NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_dept' AND column_name='legal_entity_id');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `sys_dept` ADD COLUMN `legal_entity_id` bigint NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_role' AND column_name='org_id');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `sys_role` ADD COLUMN `org_id` bigint NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='org_id');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `sys_user` ADD COLUMN `org_id` bigint NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='leave_status');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `sys_user` ADD COLUMN `leave_status` varchar(20) NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='leave_date');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `sys_user` ADD COLUMN `leave_date` datetime NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='leave_reason');
SET @sql := IF(@c=0, CONCAT('ALTER TABLE `sys_user` ADD COLUMN `leave_reason` varchar(500) NULL'), 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- [3] sys_org 兼容视图(utf8mb4 库)
DROP VIEW IF EXISTS sys_org;
CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `sys_org` AS select `sys_dept`.`id` AS `id`,`sys_dept`.`parent_id` AS `parent_id`,`sys_dept`.`group_id` AS `group_id`,`sys_dept`.`legal_entity_id` AS `legal_entity_id`,`sys_dept`.`dept_name` AS `org_name`,`sys_dept`.`dept_code` AS `org_code`,`sys_dept`.`dept_type` AS `org_type`,`sys_dept`.`leader_id` AS `leader_id`,`sys_dept`.`sort_order` AS `sort_order`,`sys_dept`.`status` AS `status`,`sys_dept`.`created_at` AS `created_at`,`sys_dept`.`updated_at` AS `updated_at` from `sys_dept`;

-- [4] sys_menu 新增 32 条 (生产 38 -> 测试 70)
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`) VALUES
(62, 10, '激励编辑', 2, NULL, NULL, 'incentive:edit', NULL, 1, 1, 1, '2026-07-31 14:23:31'),
(63, 10, '激励删除', 2, NULL, NULL, 'incentive:delete', NULL, 2, 1, 1, '2026-07-31 14:23:31'),
(71, 30, '岗位管理', 2, '/system/position', 'system/position/index', 'system:position:view', 'post', 7, 1, 1, '2026-07-31 23:54:31'),
(72, 71, '岗位新增', 3, NULL, NULL, 'system:position:add', NULL, 1, 0, 1, '2026-07-31 23:54:31'),
(73, 71, '岗位编辑', 3, NULL, NULL, 'system:position:edit', NULL, 2, 0, 1, '2026-07-31 23:54:31'),
(74, 30, '权限规则', 2, '/system/rule', 'system/rule/index', 'system:rule:view', 'lock', 8, 1, 1, '2026-07-31 23:54:31'),
(75, 74, '规则新增', 3, NULL, NULL, 'system:rule:add', NULL, 1, 0, 1, '2026-07-31 23:54:31'),
(76, 74, '规则编辑', 3, NULL, NULL, 'system:rule:edit', NULL, 2, 0, 1, '2026-07-31 23:54:31'),
(77, 30, '集团管理', 2, '/system/group', 'system/group/index', 'system:group:view', 'apartment', 9, 1, 1, '2026-07-31 23:54:31'),
(78, 77, '集团新增', 3, NULL, NULL, 'system:group:add', NULL, 1, 0, 1, '2026-07-31 23:54:31'),
(79, 77, '集团编辑', 3, NULL, NULL, 'system:group:edit', NULL, 2, 0, 1, '2026-07-31 23:54:31'),
(80, 10, '客户导入', 3, NULL, NULL, 'customer:import', NULL, 10, 0, 1, '2026-08-01 01:19:02'),
(81, 14, '文件编辑', 3, NULL, NULL, 'file:edit', NULL, 2, 0, 1, '2026-08-01 01:19:02'),
(82, 20, '积分查看', 2, NULL, NULL, 'score:view', NULL, 3, 1, 1, '2026-08-01 01:19:02'),
(83, 20, '积分管理', 3, NULL, NULL, 'score:manage', NULL, 4, 0, 1, '2026-08-01 01:19:02'),
(84, 17, '公海配置查看', 3, NULL, NULL, 'sea:config:view', NULL, 5, 0, 1, '2026-08-01 01:19:02'),
(85, 17, '公海配置编辑', 3, NULL, NULL, 'sea:config:edit', NULL, 6, 0, 1, '2026-08-01 01:19:02'),
(86, 30, '系统配置编辑', 3, NULL, NULL, 'system:config:edit', NULL, 10, 0, 1, '2026-08-01 01:19:02'),
(87, 30, '部门编辑', 3, NULL, NULL, 'system:dept:edit', NULL, 11, 0, 1, '2026-08-01 01:19:02'),
(88, 30, '菜单查看', 3, NULL, NULL, 'system:menu:view', NULL, 12, 0, 1, '2026-08-01 01:19:02'),
(89, 30, '菜单编辑', 3, NULL, NULL, 'system:menu:edit', NULL, 13, 0, 1, '2026-08-01 01:19:02'),
(90, 30, '组织管理员', 3, NULL, NULL, 'system:org:admin', NULL, 14, 0, 1, '2026-08-01 01:19:02'),
(91, 31, '删除用户', 3, NULL, NULL, 'system:user:delete', NULL, 5, 0, 1, '2026-08-01 01:46:08'),
(92, 33, '删除角色', 3, NULL, NULL, 'system:role:delete', NULL, 5, 0, 1, '2026-08-01 01:46:08'),
(93, 32, '新增组织', 3, NULL, NULL, 'system:dept:add', NULL, 5, 0, 1, '2026-08-01 01:46:08'),
(94, 32, '删除组织', 3, NULL, NULL, 'system:dept:delete', NULL, 6, 0, 1, '2026-08-01 01:46:08'),
(95, 71, '删除岗位', 3, NULL, NULL, 'system:position:delete', NULL, 5, 0, 1, '2026-08-01 01:46:08'),
(96, 74, '删除规则', 3, NULL, NULL, 'system:rule:delete', NULL, 5, 0, 1, '2026-08-01 01:46:08'),
(97, 77, '删除集团', 3, NULL, NULL, 'system:group:delete', NULL, 5, 0, 1, '2026-08-01 01:46:08'),
(98, 0, '基础数据-部门', 2, NULL, NULL, 'system:dept:view', NULL, 90, 0, 1, '2026-08-01 16:18:18'),
(99, 0, '基础数据-字典', 2, NULL, NULL, 'system:config:view', NULL, 91, 0, 1, '2026-08-01 16:18:18'),
(100, 0, '基础数据-用户', 2, NULL, NULL, 'system:user:view', NULL, 92, 0, 1, '2026-08-01 16:18:18');

-- [5] sys_role_menu 新增 47 条 (生产 122 -> 测试 168, 保留生产独有绑定)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 62),
(1, 63),
(1, 71),
(1, 72),
(1, 73),
(1, 74),
(1, 75),
(1, 76),
(1, 77),
(1, 78),
(1, 79),
(1, 80),
(1, 81),
(1, 82),
(1, 83),
(1, 84),
(1, 85),
(1, 86),
(1, 87),
(1, 88),
(1, 89),
(1, 90),
(1, 91),
(1, 92),
(1, 93),
(1, 94),
(1, 95),
(1, 96),
(1, 97),
(9, 18),
(9, 45),
(9, 46),
(9, 62),
(9, 63),
(9, 82),
(9, 84),
(9, 98),
(9, 99),
(10, 62),
(10, 63),
(10, 82),
(11, 62),
(11, 63),
(11, 82),
(11, 84),
(11, 98),
(11, 99);

-- [6] 基础配置数据(新表, utf8mb4 修复版)
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

INSERT INTO `sys_group` (`id`, `group_name`, `group_code`, `description`, `sort_order`, `status`, `created_at`, `updated_at`) VALUES (1,'艾志集团','aig','艾志（南京）环保管接技术股份有限公司',0,1,'2026-08-01 03:08:39','2026-08-02 00:55:44');

INSERT INTO `sys_legal_entity` (`id`, `entity_name`, `entity_code`, `group_id`, `credit_code`, `description`, `sort_order`, `status`, `created_at`, `updated_at`) VALUES (1,'艾志（南京）环保管接技术股份有限公司','NJ-AIG',1,NULL,'南京总部法人',0,1,'2026-08-01 03:08:39','2026-08-02 00:55:44');

INSERT INTO `sys_position` (`id`, `name`, `code`, `description`, `org_id`, `status`, `sort`, `create_time`, `update_time`) VALUES (1,'系统管理员','admin','超级管理员岗位',NULL,1,0,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(2,'总部总经理','gm','集团总部总经理',NULL,1,10,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(3,'大区经理','region_manager','负责一个大区的管理',NULL,1,20,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(4,'办事处主任','office_director','办事处负责人',NULL,1,30,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(5,'部门负责人','dept_head','部门负责人',NULL,1,40,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(6,'销售人员','salesman','一线销售人员',NULL,1,50,'2026-07-31 23:08:03','2026-08-01 03:32:29');

INSERT INTO `sys_permission_rule` (`id`, `name`, `code`, `target_entity`, `rule_type`, `target_column`, `operator`, `rule_value`, `extra_config`, `description`, `priority`, `status`, `create_time`, `update_time`) VALUES (1,'仅看本组织及下级客户','org_and_children','customer','org_filter','org_id','in','${user.orgIdsAndChildren}',NULL,'查看本组织及所有下级组织的客户',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(2,'仅看本组织客户','org_only','customer','org_filter','org_id','eq','${user.orgId}',NULL,'仅查看本组织客户',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(3,'仅看自己客户','self_only','customer','column_filter','owner_id','eq','${user.userId}',NULL,'仅查看自己负责的客户',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(4,'本月数据','this_month','customer','time_range','create_time','between','${time.thisMonthStart},${time.thisMonthEnd}',NULL,'仅查看本月创建的数据',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(5,'本季度数据','this_quarter','customer','time_range','create_time','between','${time.thisQuarterStart},${time.thisQuarterEnd}',NULL,'仅查看本季度数据',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(6,'隐藏客户财务数据','hide_finance','customer','field_visible',NULL,NULL,'{\"hidden\":[\"revenue\",\"profit\",\"debt\"]}',NULL,'隐藏财务相关字段',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(7,'隐藏客户联系方式','hide_contact','customer','field_visible',NULL,NULL,'{\"hidden\":[\"mobile\",\"email\",\"address\"]}',NULL,'隐藏联系方式字段',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(8,'禁止导出客户','deny_export','customer','action_control',NULL,NULL,'{\"export\":false}',NULL,'禁止导出客户数据',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(9,'禁止删除客户','deny_delete','customer','action_control',NULL,NULL,'{\"delete\":false}',NULL,'禁止删除客户',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44'),(10,'只读客户','readonly','customer','action_control',NULL,NULL,'{\"create\":false,\"edit\":false,\"delete\":false}',NULL,'客户数据只读',0,1,'2026-07-31 23:08:03','2026-08-02 00:55:44');

INSERT INTO `biz_approval_config` (`id`, `flow_name`, `flow_type`, `approver_type`, `approver_ids`, `sign_mode`, `sort_order`, `status`, `created_at`, `updated_at`) VALUES (2,'捞取客户审批','claim','USER','[11]','ANY',0,1,'2026-08-01 03:21:52','2026-08-01 03:30:40');
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- ============ 迁移脚本结束 ============