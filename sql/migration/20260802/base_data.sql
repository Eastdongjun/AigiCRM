
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `sys_group` WRITE;
/*!40000 ALTER TABLE `sys_group` DISABLE KEYS */;
INSERT INTO `sys_group` (`id`, `group_name`, `group_code`, `description`, `sort_order`, `status`, `created_at`, `updated_at`) VALUES (1,'è‰¾å¿—é›†å›¢','aig','è‰¾å¿—ï¼ˆå—äº¬ï¼‰çŽ¯ä¿ç®¡æŽ¥æŠ€æœ¯è‚¡ä»½æœ‰é™å…¬å¸',0,1,'2026-08-01 03:08:39','2026-08-01 03:08:39');
/*!40000 ALTER TABLE `sys_group` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `sys_legal_entity` WRITE;
/*!40000 ALTER TABLE `sys_legal_entity` DISABLE KEYS */;
INSERT INTO `sys_legal_entity` (`id`, `entity_name`, `entity_code`, `group_id`, `credit_code`, `description`, `sort_order`, `status`, `created_at`, `updated_at`) VALUES (1,'è‰¾å¿—ï¼ˆå—äº¬ï¼‰çŽ¯ä¿ç®¡æŽ¥æŠ€æœ¯è‚¡ä»½æœ‰é™å…¬å¸','NJ-AIG',1,NULL,'å—äº¬æ€»éƒ¨æ³•äºº',0,1,'2026-08-01 03:08:39','2026-08-01 03:08:39');
/*!40000 ALTER TABLE `sys_legal_entity` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `sys_position` WRITE;
/*!40000 ALTER TABLE `sys_position` DISABLE KEYS */;
INSERT INTO `sys_position` (`id`, `name`, `code`, `description`, `org_id`, `status`, `sort`, `create_time`, `update_time`) VALUES (1,'系统管理员','admin','超级管理员岗位',NULL,1,0,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(2,'总部总经理','gm','集团总部总经理',NULL,1,10,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(3,'大区经理','region_manager','负责一个大区的管理',NULL,1,20,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(4,'办事处主任','office_director','办事处负责人',NULL,1,30,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(5,'部门负责人','dept_head','部门负责人',NULL,1,40,'2026-07-31 23:08:03','2026-08-01 03:32:29'),(6,'销售人员','salesman','一线销售人员',NULL,1,50,'2026-07-31 23:08:03','2026-08-01 03:32:29');
/*!40000 ALTER TABLE `sys_position` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `sys_permission_rule` WRITE;
/*!40000 ALTER TABLE `sys_permission_rule` DISABLE KEYS */;
INSERT INTO `sys_permission_rule` (`id`, `name`, `code`, `target_entity`, `rule_type`, `target_column`, `operator`, `rule_value`, `extra_config`, `description`, `priority`, `status`, `create_time`, `update_time`) VALUES (1,'ä»…çœ‹æœ¬ç»„ç»‡åŠä¸‹çº§å®¢æˆ·','org_and_children','customer','org_filter','org_id','in','${user.orgIdsAndChildren}',NULL,'æŸ¥çœ‹æœ¬ç»„ç»‡åŠæ‰€æœ‰ä¸‹çº§ç»„ç»‡çš„å®¢æˆ·',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(2,'ä»…çœ‹æœ¬ç»„ç»‡å®¢æˆ·','org_only','customer','org_filter','org_id','eq','${user.orgId}',NULL,'ä»…æŸ¥çœ‹æœ¬ç»„ç»‡å®¢æˆ·',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(3,'ä»…çœ‹è‡ªå·±å®¢æˆ·','self_only','customer','column_filter','owner_id','eq','${user.userId}',NULL,'ä»…æŸ¥çœ‹è‡ªå·±è´Ÿè´£çš„å®¢æˆ·',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(4,'æœ¬æœˆæ•°æ®','this_month','customer','time_range','create_time','between','${time.thisMonthStart},${time.thisMonthEnd}',NULL,'ä»…æŸ¥çœ‹æœ¬æœˆåˆ›å»ºçš„æ•°æ®',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(5,'æœ¬å­£åº¦æ•°æ®','this_quarter','customer','time_range','create_time','between','${time.thisQuarterStart},${time.thisQuarterEnd}',NULL,'ä»…æŸ¥çœ‹æœ¬å­£åº¦æ•°æ®',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(6,'éšè—å®¢æˆ·è´¢åŠ¡æ•°æ®','hide_finance','customer','field_visible',NULL,NULL,'{\"hidden\":[\"revenue\",\"profit\",\"debt\"]}',NULL,'éšè—è´¢åŠ¡ç›¸å…³å­—æ®µ',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(7,'éšè—å®¢æˆ·è”ç³»æ–¹å¼','hide_contact','customer','field_visible',NULL,NULL,'{\"hidden\":[\"mobile\",\"email\",\"address\"]}',NULL,'éšè—è”ç³»æ–¹å¼å­—æ®µ',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(8,'ç¦æ­¢å¯¼å‡ºå®¢æˆ·','deny_export','customer','action_control',NULL,NULL,'{\"export\":false}',NULL,'ç¦æ­¢å¯¼å‡ºå®¢æˆ·æ•°æ®',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(9,'ç¦æ­¢åˆ é™¤å®¢æˆ·','deny_delete','customer','action_control',NULL,NULL,'{\"delete\":false}',NULL,'ç¦æ­¢åˆ é™¤å®¢æˆ·',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03'),(10,'åªè¯»å®¢æˆ·','readonly','customer','action_control',NULL,NULL,'{\"create\":false,\"edit\":false,\"delete\":false}',NULL,'å®¢æˆ·æ•°æ®åªè¯»',0,1,'2026-07-31 23:08:03','2026-07-31 23:08:03');
/*!40000 ALTER TABLE `sys_permission_rule` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `biz_approval_config` WRITE;
/*!40000 ALTER TABLE `biz_approval_config` DISABLE KEYS */;
INSERT INTO `biz_approval_config` (`id`, `flow_name`, `flow_type`, `approver_type`, `approver_ids`, `sign_mode`, `sort_order`, `status`, `created_at`, `updated_at`) VALUES (2,'捞取客户审批','claim','USER','[11]','ANY',0,1,'2026-08-01 03:21:52','2026-08-01 03:30:40');
/*!40000 ALTER TABLE `biz_approval_config` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

