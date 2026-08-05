-- ============================================================
-- 角色/岗位集团归属迁移（统一权限体系 §4.3 遗留①）
-- 用途: sys_role / sys_position 新增 group_id 集团归属字段 + 索引
--       列表查询按集团边界过滤（仿 SysUserService.applyGroupScope 口径，
--       非 admin 仅见本集团角色/岗位）
-- 默认值 1 = 主集团（艾志集团，测试库 sys_group id=1）
-- 执行: mysql -h host -P port -u root -p --default-character-set=utf8mb4 <库名> < group-id-for-role-position.sql
--       必须带 --default-character-set=utf8mb4，否则 COMMENT 中文写入会
--       因客户端字符集非 utf8mb4 出现乱码（历史教训）
-- 幂等: 用 information_schema.COLUMNS 判断列是否已存在:存在则跳过，
--       不存在才执行 ALTER（重复执行安全，不报错）
-- 数据库: MySQL 8.0（PREPARE/EXECUTE 动态 SQL 语法）
-- 前置确认: 生产执行前先测试库演练；执行前请先 mysqldump 备份数据
-- ============================================================

-- 1. sys_role 角色表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'group_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_role ADD COLUMN group_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER id, ADD INDEX idx_role_group (group_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. sys_position 岗位表
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_position' AND COLUMN_NAME = 'group_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_position ADD COLUMN group_id BIGINT NOT NULL DEFAULT 1 COMMENT ''集团ID'' AFTER org_id, ADD INDEX idx_position_group (group_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== 回滚 SQL（需要时手动执行，逐条运行） ====================
-- 0. 回滚前必须先备份数据（DROP COLUMN 不可逆）:
--    mysqldump -h host -P port -u root -p --default-character-set=utf8mb4 <库名> > backup-<日期>.sql
-- 1. 删除字段（idx_role_group / idx_position_group 只含该列，随列一并自动删除）:
--    ALTER TABLE sys_role     DROP COLUMN group_id;
--    ALTER TABLE sys_position DROP COLUMN group_id;
--    注意: 执行前先 SHOW CREATE TABLE <t> 核对当前结构
-- ==================================================================
