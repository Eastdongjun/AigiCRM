-- V2026.11.14.4 版本清理查询索引(本次修复配套)
--
-- 版本裁剪/保留期清理新增两类查询:
--   WHERE oss_key = ? [AND id <> ?]        -- 引用计数
--   WHERE created_at < ?                   -- 过期扫描
-- biz_file_version 现有索引只有 (file_id, version_no)，上述查询会全表扫描；
-- 版本表随 replace/rollback 增长，需补索引。只增索引，不改数据。
-- 幂等: information_schema.STATISTICS 守卫。

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_version'
    AND INDEX_NAME = 'idx_bfv_oss_key');
SET @sql := IF(@idx = 0,
  'ALTER TABLE biz_file_version ADD INDEX idx_bfv_oss_key (oss_key)',
  'SELECT ''idx_bfv_oss_key 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_version'
    AND INDEX_NAME = 'idx_bfv_created');
SET @sql := IF(@idx = 0,
  'ALTER TABLE biz_file_version ADD INDEX idx_bfv_created (created_at)',
  'SELECT ''idx_bfv_created 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
