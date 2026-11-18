-- 演练用：按 V2026.11.17.1（测试库尚未应用的迁移）原样创建 biz_file_grant
-- 真实 A2 执行时由 Flyway 应用 V2026.11.17.x 后自然得到同结构
CREATE TABLE IF NOT EXISTS `biz_file_grant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `subject_type` VARCHAR(10) NOT NULL COMMENT 'ROLE/POSITION/USER',
  `subject_id` BIGINT NOT NULL,
  `object_type` VARCHAR(10) NOT NULL COMMENT 'SPACE/FOLDER/FILE',
  `object_id` BIGINT NULL,
  `action` VARCHAR(30) NOT NULL,
  `effect` VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
  `inherit` TINYINT NOT NULL DEFAULT 1,
  `scope_org_id` BIGINT NULL,
  `scope_level` VARCHAR(20) NULL,
  `expire_at` DATETIME NULL,
  `created_by` BIGINT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_grant` (`tenant_id`,`subject_type`,`subject_id`,`object_type`,`object_id`,`action`),
  KEY `idx_file_grant_object` (`tenant_id`,`object_type`,`object_id`,`action`),
  KEY `idx_file_grant_subject` (`tenant_id`,`subject_type`,`subject_id`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件统一授权（空间/文件夹/文件 + 数据范围 + 继承）';
