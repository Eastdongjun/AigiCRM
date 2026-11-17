-- V2026.11.17.4__chunk_upload_session.sql
-- 【2026-11-17 阶段三】大文件（单文件 ≤200MB）断点续传/秒传会话。
-- 字节先落本地 chunks 临时目录，complete 时按序合并后复用 FileService.upload（配额/权限/审计同源）。
CREATE TABLE IF NOT EXISTS `biz_upload_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `upload_id` VARCHAR(40) NOT NULL,
  `uploader_id` BIGINT NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `file_size` BIGINT NOT NULL,
  `md5` VARCHAR(64) NULL,
  `file_scope` VARCHAR(20) NOT NULL,
  `folder_id` BIGINT NULL,
  `total_chunks` INT NOT NULL,
  `received_chunks` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'uploading' COMMENT 'uploading/completed/expired',
  `file_id` BIGINT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_upload_session` (`upload_id`),
  KEY `idx_upload_session_owner` (`tenant_id`, `uploader_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传会话(断点续传/秒传)';
