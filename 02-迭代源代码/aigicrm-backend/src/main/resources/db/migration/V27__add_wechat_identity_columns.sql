-- WeChat identity storage foundation. No login/bind endpoint is enabled by this migration.
SET @has_openid := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'wechat_openid');
SET @ddl_openid := IF(@has_openid = 0,
  'ALTER TABLE sys_user ADD COLUMN wechat_openid varchar(128) NULL COMMENT ''微信 openid（绑定后唯一）'' AFTER email',
  'SELECT ''V23: wechat_openid 已存在, 跳过''');
PREPARE stmt FROM @ddl_openid; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_unionid := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'wechat_unionid');
SET @ddl_unionid := IF(@has_unionid = 0,
  'ALTER TABLE sys_user ADD COLUMN wechat_unionid varchar(128) NULL COMMENT ''微信 unionid（可选，绑定后唯一）'' AFTER wechat_openid',
  'SELECT ''V23: wechat_unionid 已存在, 跳过''');
PREPARE stmt FROM @ddl_unionid; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_openid_index := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_wechat_openid');
SET @ddl_openid_index := IF(@has_openid_index = 0,
  'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_wechat_openid (wechat_openid)',
  'SELECT ''V23: uk_sys_user_wechat_openid 已存在, 跳过''');
PREPARE stmt FROM @ddl_openid_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_unionid_index := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_wechat_unionid');
SET @ddl_unionid_index := IF(@has_unionid_index = 0,
  'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_wechat_unionid (wechat_unionid)',
  'SELECT ''V23: uk_sys_user_wechat_unionid 已存在, 跳过''');
PREPARE stmt FROM @ddl_unionid_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;
