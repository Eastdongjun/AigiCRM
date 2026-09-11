USE aigicrm;
-- 离职归档表（listArchived / permanentDelete 使用）2026-08-03
CREATE TABLE IF NOT EXISTS `sys_user_archive` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
