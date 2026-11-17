-- V2026.11.17.3__lifecycle_handover_org_move.sql
-- 【2026-11-17 阶段三】离职交接 / 组织移动（人员跨部门）：
--   - 一级审批：审批人从 biz_approval_config 的 handover / org_move 流程读取（不硬编码人员）；
--   - 只改“当前数据”，历史/已删除数据不动；
--   - 每条受影响行落 biz_lifecycle_item，支持按请求精确回滚；
--   - 交接支持可选复制角色（排除 admin 角色），默认不复制，继任人按自身角色/数据范围生效。
--   - 本迁移不改业务数据。

CREATE TABLE IF NOT EXISTS `biz_lifecycle_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `request_type` VARCHAR(20) NOT NULL COMMENT 'HANDOVER/ORG_MOVE',
  `applicant_id` BIGINT NOT NULL,
  `subject_user_id` BIGINT NOT NULL COMMENT '交接=离职人；组织移动=被移动人',
  `target_user_id` BIGINT NULL COMMENT '交接继任人',
  `target_dept_id` BIGINT NULL COMMENT '组织移动目标部门',
  `reason` VARCHAR(500) NULL,
  `copy_roles` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/rolled_back',
  `approver_id` BIGINT NOT NULL,
  `approved_by` BIGINT NULL,
  `approved_at` DATETIME NULL,
  `rejected_by` BIGINT NULL,
  `rejected_at` DATETIME NULL,
  `reject_reason` VARCHAR(500) NULL,
  `snapshot_json` JSON NULL COMMENT '组织移动=人员原 dept/office/org',
  `result_json` JSON NULL COMMENT '各类型迁移行数',
  `rolled_back_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lifecycle_status` (`tenant_id`, `request_type`, `status`, `created_at`),
  KEY `idx_lifecycle_approver` (`tenant_id`, `approver_id`, `status`),
  KEY `idx_lifecycle_subject` (`tenant_id`, `subject_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离职交接/组织移动一级审批请求';

CREATE TABLE IF NOT EXISTS `biz_lifecycle_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `request_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(30) NOT NULL COMMENT 'customer/task/reminder/file/folder/approval/approval_node/user_role/customer_org/file_org/folder_org',
  `biz_id` BIGINT NOT NULL,
  `old_ref` BIGINT NULL COMMENT '原归属人/原 dept_id/原 org_id',
  `new_ref` BIGINT NULL COMMENT '新归属人/新 dept_id/新 org_id',
  `old_ref2` BIGINT NULL COMMENT '客户组织移动: 原 office_id',
  `new_ref2` BIGINT NULL COMMENT '客户组织移动: 新 office_id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lifecycle_item_request` (`request_id`, `biz_type`),
  KEY `idx_lifecycle_item_biz` (`tenant_id`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交接/组织移动受影响行明细(精确回滚)';

-- 文件 MD5 秒传索引（阶段三上传优化；空值不冲突）
ALTER TABLE `biz_file`
  ADD KEY `idx_file_md5_uploader` (`tenant_id`, `md5`, `uploader_id`, `deleted`);
