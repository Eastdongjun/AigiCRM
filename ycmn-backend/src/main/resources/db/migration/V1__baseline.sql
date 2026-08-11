-- ============================================================
-- V1__baseline.sql — YCMN 数据库基线（2026-08-11 重建）
--
-- 来源: 生产库全量 schema 快照(mysqldump --no-data, 2026-08-11)
--   表: 56 张(含 role_perm_scope/biz_customer_transfer/26表tenant_id)
--   说明: mysqldump 生成, 自带 FOREIGN_KEY_CHECKS=0, 空库可直接加载(修复旧V1前向FK)
--   历史: 旧 V1-V9 迁移已归档 sql/migration/archive/, 本文件为唯一 schema 真源
--   注意: 保留生产库历史遗留 COMMENT 乱码, 清理作独立后续迁移
-- ============================================================


-- MySQL dump 10.13  Distrib 8.0.28, for Linux (x86_64)
--
-- Host: localhost    Database: ycmn
-- ------------------------------------------------------
-- Server version	8.0.28

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `biz_approval`
--

DROP TABLE IF EXISTS `biz_approval`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `approval_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '审批编号(P2024-001)',
  `approval_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: claim/transfer/extend/storage',
  `applicant_id` bigint NOT NULL COMMENT '申请人ID',
  `customer_id` bigint DEFAULT NULL COMMENT '关联客户ID',
  `current_node` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前审批节点',
  `action_after_approve` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通过后动作描述',
  `extra_data` json DEFAULT NULL COMMENT '扩展数据',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT '状态: pending/reviewing/approved/rejected',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `approval_code` (`approval_code`),
  KEY `idx_applicant_id` (`applicant_id`),
  KEY `idx_status` (`status`),
  KEY `idx_approval_type` (`approval_type`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_approval_config`
--

DROP TABLE IF EXISTS `biz_approval_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'æµç¨‹åç§°',
  `flow_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'æµç¨‹ç±»åž‹',
  `approver_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'å®¡æ‰¹äººç±»åž‹ USER/ROLE/DEPT',
  `approver_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'å®¡æ‰¹äººIDåˆ—è¡¨ JSON',
  `sign_mode` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'ç­¾ç½²æ¨¡å¼ ALL/ANY',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_approval_flow`
--

DROP TABLE IF EXISTS `biz_approval_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型: claim/transfer/extend/storage',
  `flow_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程名称',
  `approver_role_id` bigint DEFAULT NULL COMMENT '审批角色ID',
  `sort_order` int DEFAULT '0' COMMENT '审批顺序(多级审批)',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批流程定义';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_approval_node`
--

DROP TABLE IF EXISTS `biz_approval_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_approval_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approval_id` bigint NOT NULL,
  `approver_id` bigint NOT NULL COMMENT '审批人ID',
  `node_order` int DEFAULT NULL COMMENT '节点顺序',
  `action` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作: approve/reject',
  `comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批意见',
  `operated_at` datetime DEFAULT NULL COMMENT '操作时间',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT '状态',
  `config_id` bigint DEFAULT NULL,
  `sign_mode` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_approval_id` (`approval_id`),
  KEY `idx_approver_id` (`approver_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批节点记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_contact`
--

DROP TABLE IF EXISTS `biz_contact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '姓名',
  `position` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '职位',
  `phone` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '电话',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=188 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户联系人';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_customer`
--

DROP TABLE IF EXISTS `biz_customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `customer_code` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户编号(C001)',
  `customer_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订货客户全称',
  `invoice_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开票名称',
  `industry` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业',
  `dev_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开发类型(成熟/成熟创新/突破)',
  `group_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属集团',
  `branch_company` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属分公司',
  `unit_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属单位',
  `capacity` varchar(1024) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产能(万吨)',
  `province` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '省',
  `city` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '市',
  `district` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '区/县',
  `address` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '厂址',
  `has_terminal` tinyint DEFAULT '0' COMMENT '是否终端 0否 1是',
  `register_date` date DEFAULT NULL COMMENT '注册日',
  `last_invoice_date` date DEFAULT NULL COMMENT '最近开票日',
  `expire_date` date DEFAULT NULL COMMENT '到期日(360天)',
  `adjust_date` date DEFAULT NULL COMMENT '调整日(180天)',
  `transfer_date` date DEFAULT NULL COMMENT '转移日',
  `owner_id` bigint DEFAULT NULL COMMENT '第一负责人ID',
  `manager_id` bigint DEFAULT NULL COMMENT '管理上级ID',
  `office_id` bigint DEFAULT NULL COMMENT '所属办事处ID',
  `dept_id` bigint DEFAULT NULL COMMENT '所属部门(任意层级)',
  `region_id` bigint DEFAULT NULL COMMENT '所属大区ID(dept_type=2)',
  `remark` text COLLATE utf8mb4_general_ci COMMENT '备注',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'normal' COMMENT '状态: normal/warning/expired/sea',
  `protection_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT '保护状态: active/expiring/expired',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `relationship_score` int DEFAULT '0',
  `is_core` tinyint DEFAULT '0',
  `tags` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `group_id` bigint DEFAULT NULL,
  `legal_entity_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `customer_code` (`customer_code`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_office_id` (`office_id`),
  KEY `idx_expire_date` (`expire_date`),
  KEY `idx_status` (`status`),
  KEY `idx_industry` (`industry`),
  KEY `idx_region_id` (`region_id`),
  KEY `idx_tenant` (`tenant_id`),
  CONSTRAINT `fk_biz_customer_owner` FOREIGN KEY (`owner_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=576 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_customer_bak_0624`
--

DROP TABLE IF EXISTS `biz_customer_bak_0624`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer_bak_0624` (
  `id` bigint NOT NULL DEFAULT '0',
  `customer_code` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户编号(C001)',
  `customer_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订货客户全称',
  `invoice_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开票名称',
  `industry` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业',
  `dev_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开发类型(成熟/成熟创新/突破)',
  `group_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属集团',
  `branch_company` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属分公司',
  `unit_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属单位',
  `capacity` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '产能(万吨)',
  `province` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '省',
  `city` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '市',
  `district` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '区/县',
  `address` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '厂址',
  `has_terminal` tinyint DEFAULT '0' COMMENT '是否终端 0否 1是',
  `register_date` date DEFAULT NULL COMMENT '注册日',
  `last_invoice_date` date DEFAULT NULL COMMENT '最近开票日',
  `expire_date` date DEFAULT NULL COMMENT '到期日(360天)',
  `adjust_date` date DEFAULT NULL COMMENT '调整日(180天)',
  `transfer_date` date DEFAULT NULL COMMENT '转移日',
  `owner_id` bigint DEFAULT NULL COMMENT '第一负责人ID',
  `manager_id` bigint DEFAULT NULL COMMENT '管理上级ID',
  `office_id` bigint DEFAULT NULL COMMENT '所属办事处ID',
  `remark` text COLLATE utf8mb4_general_ci COMMENT '备注',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'normal' COMMENT '状态: normal/warning/expired/sea',
  `protection_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'active' COMMENT '保护状态: active/expiring/expired',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `relationship_score` int DEFAULT '0',
  `is_core` tinyint DEFAULT '0',
  `tags` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_customer_indicator`
--

DROP TABLE IF EXISTS `biz_customer_indicator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer_indicator` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `indicator_02` int DEFAULT '0',
  `indicator_03` int DEFAULT '0',
  `indicator_04` int DEFAULT '0',
  `indicator_05` int DEFAULT '0',
  `indicator_06` int DEFAULT '0',
  `is_core` int DEFAULT '0',
  `relationship_score` int DEFAULT '0',
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `customer_id` (`customer_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_customer_log`
--

DROP TABLE IF EXISTS `biz_customer_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `user_id` bigint NOT NULL COMMENT '记录人',
  `log_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型(终端考察/技术交流/产品试点报告/其他)',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '跟进内容',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户跟进日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_customer_share`
--

DROP TABLE IF EXISTS `biz_customer_share`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer_share` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `customer_id` bigint NOT NULL,
  `shared_to_user_id` bigint NOT NULL,
  `shared_by_user_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_user` (`customer_id`,`shared_to_user_id`),
  KEY `idx_tenant` (`tenant_id`),
  CONSTRAINT `fk_biz_customer_share_customer` FOREIGN KEY (`customer_id`) REFERENCES `biz_customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_customer_transfer`
--

DROP TABLE IF EXISTS `biz_customer_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_customer_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_user_id` bigint NOT NULL COMMENT 'è½¬ç§»æºç”¨æˆ·',
  `to_user_id` bigint NOT NULL COMMENT 'æŽ¥æ”¶ç”¨æˆ·',
  `transfer_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'leave=ç¦»èŒè½¬ç§» / manual=æ‰‹åŠ¨è½¬ç§»',
  `customer_count` int NOT NULL DEFAULT '0' COMMENT 'è½¬ç§»å®¢æˆ·æ•°',
  `operator_id` bigint NOT NULL COMMENT 'æ“ä½œäºº',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_transfer_from` (`from_user_id`),
  KEY `idx_transfer_to` (`to_user_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='å®¢æˆ·è½¬ç§»è®°å½•(ç¦»èŒ/æ‰‹åŠ¨)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_file`
--

DROP TABLE IF EXISTS `biz_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `folder_id` bigint DEFAULT NULL COMMENT '所属目录ID',
  `file_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件类型',
  `oss_key` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'OSS对象Key',
  `oss_url` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'OSS访问URL',
  `file_scope` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'private' COMMENT '范围: public/private/incentive',
  `uploader_id` bigint NOT NULL COMMENT '上传者ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `version` int NOT NULL DEFAULT '1' COMMENT 'ç‰ˆæœ¬å·,è¦†ç›–æ›¿æ¢+1',
  `replace_by` bigint DEFAULT NULL COMMENT 'æœ€è¿‘æ›¿æ¢äºº',
  `replaced_at` datetime DEFAULT NULL COMMENT 'æœ€è¿‘æ›¿æ¢æ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_folder_id` (`folder_id`),
  KEY `idx_uploader_id` (`uploader_id`),
  KEY `idx_file_scope` (`file_scope`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=295 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_file_folder`
--

DROP TABLE IF EXISTS `biz_file_folder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_file_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父目录ID',
  `folder_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '目录名称',
  `folder_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型: public/private/incentive',
  `owner_id` bigint DEFAULT NULL COMMENT '所属用户ID(私人目录)',
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `downloadable` tinyint NOT NULL DEFAULT '1' COMMENT 'ä¸‹è½½å¼€å…³:1=å…è®¸ä¸‹è½½,0=ä»…é¢„è§ˆ',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件目录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_file_permission`
--

DROP TABLE IF EXISTS `biz_file_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_file_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `can_upload` tinyint DEFAULT '0' COMMENT 'å…¬å…±ç›˜ä¸Šä¼ æƒé™',
  `can_delete` tinyint DEFAULT '0' COMMENT 'å…¬å…±ç›˜åˆ é™¤æƒé™',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ–‡ä»¶ç®¡ç†æƒé™è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_file_share`
--

DROP TABLE IF EXISTS `biz_file_share`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_file_share` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` bigint NOT NULL,
  `shared_to_user_id` bigint NOT NULL,
  `shared_by_user_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_user` (`file_id`,`shared_to_user_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_folder_acl`
--

DROP TABLE IF EXISTS `biz_folder_acl`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_folder_acl` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `folder_id` bigint NOT NULL COMMENT 'æ–‡ä»¶å¤¹ID',
  `role_id` bigint NOT NULL COMMENT 'è§’è‰²ID',
  `perms` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'æƒé™åˆ—è¡¨,é€—å·åˆ†éš”:view,upload,edit,delete,share,download',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_folder_role` (`folder_id`,`role_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='æ–‡ä»¶å¤¹-è§’è‰²æŽˆæƒè¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_incentive_category`
--

DROP TABLE IF EXISTS `biz_incentive_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_incentive_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID(0为四项大类)',
  `category_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类名称',
  `category_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分类编码',
  `score` int DEFAULT '0' COMMENT '分值',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='激励分类配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_incentive_permission`
--

DROP TABLE IF EXISTS `biz_incentive_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_incentive_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `can_edit` tinyint DEFAULT '0',
  `can_delete` tinyint DEFAULT '0',
  `can_view_all` tinyint NOT NULL DEFAULT '0' COMMENT '看全部激励(含未分享)独立开关',
  `can_view_shared` tinyint NOT NULL DEFAULT '1' COMMENT '查看已分享(默认1)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=160 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æ¿€åŠ±æƒé™è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_incentive_record`
--

DROP TABLE IF EXISTS `biz_incentive_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_incentive_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `customer_id` bigint DEFAULT NULL,
  `period` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '周期(如2026上半年)',
  `industry` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '行业(取字典 industry)',
  `related_products` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关产品(多行文本)',
  `specific_product` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `file_id` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `pre_install_question` text COLLATE utf8mb4_general_ci COMMENT '安装前问题',
  `file_pre_id` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `file_mid_id` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `file_post_id` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `file_track_id` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `score` int DEFAULT '0' COMMENT '得分',
  `shared` tinyint DEFAULT '0',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'uploaded' COMMENT '状态',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_period` (`period`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `fk_biz_incentive_record_customer` (`customer_id`),
  CONSTRAINT `fk_biz_incentive_record_customer` FOREIGN KEY (`customer_id`) REFERENCES `biz_customer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='激励上传记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_invoice`
--

DROP TABLE IF EXISTS `biz_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `invoice_date` date DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `invoice_no` varchar(100) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `invoice_number` varchar(100) DEFAULT NULL,
  `invoice_company` varchar(200) DEFAULT NULL,
  `invoice_type` varchar(50) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `fk_biz_invoice_customer` (`customer_id`),
  KEY `idx_tenant` (`tenant_id`),
  CONSTRAINT `fk_biz_invoice_customer` FOREIGN KEY (`customer_id`) REFERENCES `biz_customer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_payment`
--

DROP TABLE IF EXISTS `biz_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `payment_date` date DEFAULT NULL COMMENT '回款日期',
  `payment_amount` decimal(12,2) DEFAULT NULL COMMENT '回款金额(万元)',
  `payment_method` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回款方式',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_tenant` (`tenant_id`),
  CONSTRAINT `fk_biz_payment_customer` FOREIGN KEY (`customer_id`) REFERENCES `biz_customer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='回款记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_reminder`
--

DROP TABLE IF EXISTS `biz_reminder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `customer_id` bigint NOT NULL,
  `reminder_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型: invoice_overdue/expiring',
  `owner_id` bigint DEFAULT NULL COMMENT '负责人ID',
  `office_id` bigint DEFAULT NULL COMMENT '办事处ID',
  `overdue_days` int DEFAULT NULL COMMENT '已超天数',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'pending' COMMENT '状态: pending/processed',
  `processed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant` (`tenant_id`),
  CONSTRAINT `fk_biz_reminder_customer` FOREIGN KEY (`customer_id`) REFERENCES `biz_customer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=451 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提醒记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_score_entry`
--

DROP TABLE IF EXISTS `biz_score_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_score_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period` varchar(20) NOT NULL,
  `item_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `score` int DEFAULT '0',
  `scorer_id` bigint DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_period_item_user` (`period`,`item_id`,`user_id`),
  KEY `idx_period` (`period`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=95 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_score_item`
--

DROP TABLE IF EXISTS `biz_score_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_score_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(100) NOT NULL,
  `product` varchar(200) NOT NULL,
  `score_min` int DEFAULT '0',
  `score_max` int DEFAULT '5',
  `sort_order` int DEFAULT '0',
  `deleted` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_score_participant`
--

DROP TABLE IF EXISTS `biz_score_participant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_score_participant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period` varchar(20) NOT NULL COMMENT 'å‘¨æœŸï¼Œå¦‚ 2026ä¸ŠåŠå¹´',
  `user_id` bigint NOT NULL COMMENT 'å‚ä¸ŽåŒäº‹çš„ç”¨æˆ·ID',
  `sort_order` int DEFAULT '0' COMMENT 'åˆ—é¡ºåº',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_period` (`period`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=269 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç§¯åˆ†å½•å…¥å‘¨æœŸå‚ä¸ŽåŒäº‹åå•';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_sea_config`
--

DROP TABLE IF EXISTS `biz_sea_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sea_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pool_name` varchar(100) DEFAULT NULL,
  `included_offices` varchar(500) DEFAULT NULL,
  `visible_roles` varchar(500) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `region_ids` varchar(500) DEFAULT NULL,
  `view_role_ids` varchar(500) DEFAULT NULL,
  `visible_dept_ids` varchar(500) DEFAULT NULL COMMENT '可见部门ID(逗号分隔,dept_type=4)',
  `visible_user_ids` varchar(500) DEFAULT NULL COMMENT '可见人员ID(逗号分隔)',
  `status` int DEFAULT '1',
  `edit_dept_ids` varchar(500) DEFAULT NULL COMMENT '编辑权限-授权组织',
  `edit_user_ids` varchar(500) DEFAULT NULL COMMENT '编辑权限-授权人员',
  `delete_dept_ids` varchar(500) DEFAULT NULL COMMENT '删除权限-授权组织',
  `delete_user_ids` varchar(500) DEFAULT NULL COMMENT '删除权限-授权人员',
  `import_dept_ids` varchar(500) DEFAULT NULL COMMENT '导入权限-授权组织',
  `import_user_ids` varchar(500) DEFAULT NULL COMMENT '导入权限-授权人员',
  `claim_dept_ids` varchar(500) DEFAULT NULL COMMENT '捞取权限-授权组织',
  `claim_user_ids` varchar(500) DEFAULT NULL COMMENT '捞取权限-授权人员',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'é›†å›¢ID',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_sea_config_bak_0624`
--

DROP TABLE IF EXISTS `biz_sea_config_bak_0624`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sea_config_bak_0624` (
  `id` bigint NOT NULL DEFAULT '0',
  `pool_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `included_offices` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `visible_roles` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `region_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `view_role_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `status` int DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_sea_config_bak_0629`
--

DROP TABLE IF EXISTS `biz_sea_config_bak_0629`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sea_config_bak_0629` (
  `id` bigint NOT NULL DEFAULT '0',
  `pool_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `included_offices` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `visible_roles` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `region_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `view_role_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
  `status` int DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_sea_pool`
--

DROP TABLE IF EXISTS `biz_sea_pool`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sea_pool` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `original_owner_id` bigint DEFAULT NULL COMMENT '原负责人ID',
  `enter_reason` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '进入原因: protection_expired/abandoned/resignation',
  `enter_date` datetime DEFAULT NULL COMMENT '进入时间',
  `claim_user_id` bigint DEFAULT NULL COMMENT '捞取人ID',
  `claim_date` datetime DEFAULT NULL COMMENT '捞取时间',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'available' COMMENT '状态: available/pending_approval/claimed',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `pool_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=163 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公海池';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_sea_pool_bak_0624`
--

DROP TABLE IF EXISTS `biz_sea_pool_bak_0624`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sea_pool_bak_0624` (
  `id` bigint NOT NULL DEFAULT '0',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `original_owner_id` bigint DEFAULT NULL COMMENT '原负责人ID',
  `enter_reason` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '进入原因: protection_expired/abandoned/resignation',
  `enter_date` datetime DEFAULT NULL COMMENT '进入时间',
  `claim_user_id` bigint DEFAULT NULL COMMENT '捞取人ID',
  `claim_date` datetime DEFAULT NULL COMMENT '捞取时间',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'available' COMMENT '状态: available/pending_approval/claimed',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `pool_id` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_sea_pool_bak_poolid_0721`
--

DROP TABLE IF EXISTS `biz_sea_pool_bak_poolid_0721`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sea_pool_bak_poolid_0721` (
  `id` bigint NOT NULL DEFAULT '0',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `original_owner_id` bigint DEFAULT NULL COMMENT '原负责人ID',
  `enter_reason` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '进入原因: protection_expired/abandoned/resignation',
  `enter_date` datetime DEFAULT NULL COMMENT '进入时间',
  `claim_user_id` bigint DEFAULT NULL COMMENT '捞取人ID',
  `claim_date` datetime DEFAULT NULL COMMENT '捞取时间',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'available' COMMENT '状态: available/pending_approval/claimed',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `pool_id` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_perm_scope`
--

DROP TABLE IF EXISTS `role_perm_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_perm_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'è§’è‰²ç¼–ç (sys_role.role_code)',
  `module` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'æ¨¡å—(å¦‚ customer/sea/file/incentive/approval/statistics/score/indicator/system)',
  `action` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'åŠ¨ä½œ(å¦‚ view/add/edit/delete/share/export/import/claim/approve/reject/config)',
  `scope_level` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'self' COMMENT 'æ•°æ®èŒƒå›´: all/dept_and_children/dept/region/office/org/team/self',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `assignee_type` varchar(10) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ROLE' COMMENT 'ä¸»ä½“ç±»åž‹: ROLE/POSITION/USER',
  `assignee_key` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'ä¸»ä½“é”®: ROLE=è§’è‰²ç¼–ç  / POSITION=å²—ä½ID / USER=ç”¨æˆ·ID',
  `anchor` varchar(10) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'OWNER' COMMENT 'èŒƒå›´é”šç‚¹: OWNER=æŒ‰è´Ÿè´£äºº / ORG=æŒ‰å®¢æˆ·å½’å±žç»„ç»‡(dept_id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assignee_ma` (`assignee_type`,`assignee_key`,`module`,`action`,`anchor`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='è§’è‰²-æ¨¡å—-åŠ¨ä½œ æ•°æ®èŒƒå›´';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `config_value` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统设置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '父部门ID',
  `dept_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '部门名称',
  `dept_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `dept_type` tinyint DEFAULT NULL COMMENT '类型 1总部 2大区 3办事处',
  `leader_id` bigint DEFAULT NULL COMMENT '负责人ID',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `group_id` bigint DEFAULT NULL,
  `legal_entity_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织架构表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_type_id` bigint NOT NULL,
  `dict_label` varchar(200) NOT NULL,
  `dict_value` varchar(200) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `status` int DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=91 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_name` varchar(100) NOT NULL,
  `dict_code` varchar(100) NOT NULL,
  `status` int DEFAULT '1',
  `built_in` tinyint DEFAULT '0' COMMENT '是否内置(1=内置不可删)',
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `dict_code` (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_group`
--

DROP TABLE IF EXISTS `sys_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_legal_entity`
--

DROP TABLE IF EXISTS `sys_legal_entity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_log`
--

DROP TABLE IF EXISTS `sys_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '操作人',
  `username` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人账号',
  `module` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模块',
  `action` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作类型',
  `target_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标类型',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID',
  `detail` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '详情',
  `ip` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=4583 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `menu_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `menu_type` tinyint DEFAULT NULL COMMENT '类型 1目录 2菜单 3按钮',
  `path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由路径',
  `component` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `permission` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '图标',
  `sort_order` int DEFAULT '0',
  `visible` tinyint DEFAULT '1',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_org_admin`
--

DROP TABLE IF EXISTS `sys_org_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_org_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_permission_rule`
--

DROP TABLE IF EXISTS `sys_permission_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_position`
--

DROP TABLE IF EXISTS `sys_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `code` varchar(50) NOT NULL,
  `description` varchar(200) DEFAULT NULL,
  `org_id` bigint DEFAULT NULL,
  `group_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `status` tinyint DEFAULT '1',
  `sort` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_org_id` (`org_id`),
  KEY `idx_position_group` (`group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='å²—ä½è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_position_rule`
--

DROP TABLE IF EXISTS `sys_position_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `role_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
  `description` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `data_scope` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'self' COMMENT '数据范围 all全部 dept_and_children本部门及下级 dept本部门 self仅自己',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `org_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`),
  KEY `idx_role_group` (`group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_bak_20260808`
--

DROP TABLE IF EXISTS `sys_role_bak_20260808`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_bak_20260808` (
  `id` bigint NOT NULL DEFAULT '0',
  `group_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `role_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色编码',
  `description` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `data_scope` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'self' COMMENT '数据范围 all全部 dept_and_children本部门及下级 dept本部门 self仅自己',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `org_id` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3097 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_org`
--

DROP TABLE IF EXISTS `sys_role_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_org` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `org_id` bigint NOT NULL,
  `include_children` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_org_id` (`org_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è§’è‰²-ç»„ç»‡å…³è”è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_org_bak_20260808`
--

DROP TABLE IF EXISTS `sys_role_org_bak_20260808`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_org_bak_20260808` (
  `id` bigint NOT NULL DEFAULT '0',
  `role_id` bigint NOT NULL,
  `org_id` bigint NOT NULL,
  `include_children` tinyint DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_rule`
--

DROP TABLE IF EXISTS `sys_role_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录账号',
  `password` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码(BCrypt)',
  `real_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '真实姓名',
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像URL',
  `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `office_id` bigint DEFAULT NULL COMMENT '所属办事处ID',
  `manager_id` bigint DEFAULT NULL COMMENT 'ç›´æŽ¥ä¸Šçº§',
  `data_scope_override` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户级数据范围覆盖(空=跟随角色；取值见 DataScopeType.code)',
  `storage_quota` bigint DEFAULT '10737418240' COMMENT '私人存储空间配额(字节,默认10GB)',
  `storage_used` bigint DEFAULT '0' COMMENT '已用存储空间(字节)',
  `status` tinyint DEFAULT '1' COMMENT '状态 0禁用 1正常',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `org_id` bigint DEFAULT NULL,
  `leave_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `leave_date` datetime DEFAULT NULL,
  `leave_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_dept_id` (`dept_id`),
  KEY `idx_office_id` (`office_id`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_archive`
--

DROP TABLE IF EXISTS `sys_user_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_archive` (
  `id` bigint NOT NULL,
  `username` varchar(50) DEFAULT NULL,
  `real_name` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `office_id` bigint DEFAULT NULL,
  `org_id` bigint DEFAULT NULL,
  `manager_id` bigint DEFAULT NULL,
  `role_names` varchar(500) DEFAULT NULL,
  `data_scope` varchar(50) DEFAULT NULL,
  `leave_status` int DEFAULT NULL,
  `leave_date` date DEFAULT NULL,
  `leave_reason` varchar(500) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `deleted_reason` varchar(500) DEFAULT NULL,
  `original_created_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_bak_wll_0721`
--

DROP TABLE IF EXISTS `sys_user_bak_wll_0721`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_bak_wll_0721` (
  `id` bigint NOT NULL DEFAULT '0',
  `username` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '登录账号',
  `password` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码(BCrypt)',
  `real_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '真实姓名',
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像URL',
  `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID',
  `office_id` bigint DEFAULT NULL COMMENT '所属办事处ID',
  `storage_quota` bigint DEFAULT '10737418240' COMMENT '私人存储空间配额(字节,默认10GB)',
  `storage_used` bigint DEFAULT '0' COMMENT '已用存储空间(字节)',
  `status` tinyint DEFAULT '1' COMMENT '状态 0禁用 1正常',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_org`
--

DROP TABLE IF EXISTS `sys_user_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_org` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `org_id` bigint NOT NULL,
  `include_children` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”¨æˆ·çº§è‡ªå®šä¹‰æŽˆæƒç»„ç»‡';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_position`
--

DROP TABLE IF EXISTS `sys_user_position`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=260 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-11 10:27:55
