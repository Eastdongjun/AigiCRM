-- ============================================================
-- 集团租户隔离字段迁移（统一权限体系 §4.3）
-- 用途: 8 张核心业务表新增 tenant_id 集团隔离字段 + idx_tenant 索引
--       表清单（与 Task 4 租户插件 TENANT_TABLES 白名单一致，必须同批上线）:
--         biz_customer / biz_customer_share / biz_file_folder / biz_file
--         biz_incentive_record / biz_sea_pool / biz_approval / biz_reminder
--       默认值 1 = 主集团（艾志集团，测试库 sys_group id=1）
-- 执行: mysql -h host -P port -u root -p --default-character-set=utf8mb4 <库名> < migration-20260805-tenant-id.sql
--       必须带 --default-character-set=utf8mb4，否则 COMMENT 中文写入会
--       因客户端字符集非 utf8mb4 出现乱码（历史教训）
-- 幂等: MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），
--       本脚本用 information_schema.COLUMNS 判断列是否已存在:
--       存在则跳过，不存在才执行 ALTER（重复执行安全，不报错）
-- 数据库: MySQL 8.0（PREPARE/EXECUTE 动态 SQL 语法）
-- 前置确认: 生产执行前先测试库演练；执行前请先 mysqldump 备份数据
-- ============================================================

-- 1. biz_customer 客户表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_customer' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_customer ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. biz_customer_share 客户分享表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_customer_share' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_customer_share ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. biz_file_folder 文件文件夹表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_folder' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_file_folder ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. biz_file 文件表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_file ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. biz_incentive_record 激励记录表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_incentive_record' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_incentive_record ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. biz_sea_pool 公海表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sea_pool' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_sea_pool ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. biz_approval 审批表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_approval' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_approval ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 8. biz_reminder 提醒表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reminder' AND COLUMN_NAME = 'tenant_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE biz_reminder ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== 回滚 SQL（需要时手动执行，逐条运行） ====================
-- 0. 回滚前必须先备份数据（DROP COLUMN 不可逆，历史数据无法从回滚中恢复）:
--    mysqldump -h host -P port -u root -p --default-character-set=utf8mb4 <库名> > backup-<日期>.sql
-- 1. 删除各表 tenant_id 字段（idx_tenant 索引只含该列，随列一并自动删除）:
--    ALTER TABLE biz_customer          DROP COLUMN tenant_id;
--    ALTER TABLE biz_customer_share    DROP COLUMN tenant_id;
--    ALTER TABLE biz_file_folder       DROP COLUMN tenant_id;
--    ALTER TABLE biz_file              DROP COLUMN tenant_id;
--    ALTER TABLE biz_incentive_record  DROP COLUMN tenant_id;
--    ALTER TABLE biz_sea_pool          DROP COLUMN tenant_id;
--    ALTER TABLE biz_approval          DROP COLUMN tenant_id;
--    ALTER TABLE biz_reminder          DROP COLUMN tenant_id;
--    注意: 执行前先 SHOW CREATE TABLE <t> 核对当前结构，避免与后续其他
--          迁移追加的列/索引混淆
-- ==================================================================
