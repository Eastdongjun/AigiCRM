
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

