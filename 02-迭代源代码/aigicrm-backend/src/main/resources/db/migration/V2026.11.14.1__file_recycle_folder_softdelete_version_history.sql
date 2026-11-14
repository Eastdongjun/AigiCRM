-- V2026.11.14.1 文件管理三功能(用户拍板 2026-11-14: 回收站补全/版本历史/批量操作)
--
-- ① 文件夹软删回收站: biz_file_folder 加 deleted/deleted_at/deleted_by(口径对齐 biz_file
--    的 @TableLogic 三件套)。删除夹改软删(此前物理删), 恢复后 ACL/类型授权行保留原样即生效
--    (删除时不再级联清 ACL——授权跟随夹生命周期, 彻底删除时才清)。
--    @TableLogic 令全部 MP 查询自动过滤已删夹, 无需逐查询点补过滤(影响面最小)。
-- ② 版本历史: biz_file_version 保留 replace 前的旧版本内容(此前 version 仅计数, 旧字节
--    随替换被物理删除, 想回退做不到)。version_no=被替换时的 file.version 值。
-- 幂等: information_schema 守卫(ADD COLUMN 类), CREATE TABLE IF NOT EXISTS。
-- tenant=1 单集团(锁定决策#6)。

-- ① 夹软删三列
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_folder' AND COLUMN_NAME = 'deleted');
SET @sql := IF(@col = 0,
  'ALTER TABLE biz_file_folder ADD COLUMN deleted INT NOT NULL DEFAULT 0 COMMENT ''0=在用 1=回收站(@TableLogic)'' AFTER downloadable',
  'SELECT ''biz_file_folder.deleted 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_folder' AND COLUMN_NAME = 'deleted_at');
SET @sql := IF(@col = 0,
  'ALTER TABLE biz_file_folder ADD COLUMN deleted_at DATETIME NULL COMMENT ''进回收站时间'' AFTER deleted',
  'SELECT ''biz_file_folder.deleted_at 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_folder' AND COLUMN_NAME = 'deleted_by');
SET @sql := IF(@col = 0,
  'ALTER TABLE biz_file_folder ADD COLUMN deleted_by BIGINT NULL COMMENT ''删除人(我的回收站归属口径)'' AFTER deleted_at',
  'SELECT ''biz_file_folder.deleted_by 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 回填历史口径: 物理删的夹本就不在表里, 存量行全部=在用, 无需回填。

-- ①b 文件侧补删除人/删除时间两列(deleted 列已有): "我的回收站"按 deleted_by 归属
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file' AND COLUMN_NAME = 'deleted_at');
SET @sql := IF(@col = 0,
  'ALTER TABLE biz_file ADD COLUMN deleted_at DATETIME NULL COMMENT ''进回收站时间'' AFTER deleted',
  'SELECT ''biz_file.deleted_at 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file' AND COLUMN_NAME = 'deleted_by');
SET @sql := IF(@col = 0,
  'ALTER TABLE biz_file ADD COLUMN deleted_by BIGINT NULL COMMENT ''删除人(我的回收站归属口径)'' AFTER deleted_at',
  'SELECT ''biz_file.deleted_by 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
-- 存量回填: 已在回收站的行删除人未知 → NULL(仅 admin 可恢复, 与旧行为一致)

-- ② 版本历史表
CREATE TABLE IF NOT EXISTS biz_file_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  file_id BIGINT NOT NULL COMMENT '主文件 id(biz_file.id)',
  version_no INT NOT NULL COMMENT '该版本对应的 file.version 值(被替换前主行的版本号)',
  oss_key VARCHAR(500) NOT NULL COMMENT '该版本存储 key',
  oss_url VARCHAR(1000) NULL COMMENT '该版本下载 URL(与主行同方案)',
  file_size BIGINT NULL,
  file_type VARCHAR(200) NULL,
  file_name VARCHAR(500) NULL COMMENT '该版本文件名快照(改名后仍可辨识)',
  uploaded_by BIGINT NULL COMMENT '归档操作人(replace 执行人)',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  INDEX idx_bfv_file (file_id, version_no),
  INDEX idx_bfv_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='文件版本历史(replace 归档旧版本, 支持回滚)';
