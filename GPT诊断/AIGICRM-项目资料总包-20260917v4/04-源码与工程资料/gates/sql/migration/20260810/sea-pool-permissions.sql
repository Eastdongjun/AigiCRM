-- ============================================================
-- 2026-08-10 公海池级权限: biz_sea_config 加 8 列(编辑/删除/导入/捞取 各 dept+user)
--
-- 背景: 每个公海池各自配置操作权限范围(组织架构树勾选, 复用 visible 模式)。
--        可见范围(visible_dept_ids/visible_user_ids)=查看, 新增 edit/delete/import/claim 四组。
-- 语义: 空 = 该池无此权限给任何人(除 admin); 组织树勾选上级含下级递归。
-- 初始回填: 现有池的 edit/delete/import/claim 默认等于可见范围(过渡平滑, 部署后现有用户操作不变)。
-- 执行: 备份后执行, 幂等(按列存在性判断)。
-- ============================================================

SET @db = DATABASE();

-- 1. 加列(幂等: 列不存在才加)
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

-- 2. 初始回填: 新列默认等于可见范围(幂等, 仅当新列为 NULL 时回填)
UPDATE biz_sea_config
SET edit_dept_ids = COALESCE(edit_dept_ids, visible_dept_ids),
    edit_user_ids = COALESCE(edit_user_ids, visible_user_ids),
    delete_dept_ids = COALESCE(delete_dept_ids, visible_dept_ids),
    delete_user_ids = COALESCE(delete_user_ids, visible_user_ids),
    import_dept_ids = COALESCE(import_dept_ids, visible_dept_ids),
    import_user_ids = COALESCE(import_user_ids, visible_user_ids),
    claim_dept_ids = COALESCE(claim_dept_ids, visible_dept_ids),
    claim_user_ids = COALESCE(claim_user_ids, visible_user_ids);
