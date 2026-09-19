-- V3: 同步 biz_sea_config 公海池级权限 8 列(幂等)
-- 背景: 公海池级权限(2026-08-10)的 8 列由 dated 脚本 sql/migration/20260810/sea-pool-permissions.sql
--       添加(不在 Flyway), V1 baseline(2026-08-09)未含 → 全新环境按基线部署缺列, 公海权限功能不可用。
-- 处理: 幂等加列(列已存在则 no-op)——生产/测试已存在走 SELECT 1, 全新环境补齐。
-- 语义: edit/delete/import/claim × dept/user 各一列; 空 = 该池无此权限(除 admin)。
-- ============================================================

SET @db = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='edit_dept_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN edit_dept_ids VARCHAR(500) DEFAULT NULL COMMENT ''编辑权限-授权组织''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='edit_user_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN edit_user_ids VARCHAR(500) DEFAULT NULL COMMENT ''编辑权限-授权人员''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='delete_dept_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN delete_dept_ids VARCHAR(500) DEFAULT NULL COMMENT ''删除权限-授权组织''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='delete_user_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN delete_user_ids VARCHAR(500) DEFAULT NULL COMMENT ''删除权限-授权人员''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='import_dept_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN import_dept_ids VARCHAR(500) DEFAULT NULL COMMENT ''导入权限-授权组织''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='import_user_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN import_user_ids VARCHAR(500) DEFAULT NULL COMMENT ''导入权限-授权人员''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='claim_dept_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN claim_dept_ids VARCHAR(500) DEFAULT NULL COMMENT ''捞取权限-授权组织''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='biz_sea_config' AND COLUMN_NAME='claim_user_ids') = 0,
  'ALTER TABLE biz_sea_config ADD COLUMN claim_user_ids VARCHAR(500) DEFAULT NULL COMMENT ''捞取权限-授权人员''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
