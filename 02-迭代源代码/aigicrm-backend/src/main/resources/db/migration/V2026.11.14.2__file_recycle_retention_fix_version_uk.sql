-- V2026.11.14.2 回收站保留期修正 + 级联索引 + 版本唯一键(外部审查 P0/P2, 2026-11-14)
--
-- ① 存量回填: biz_file 已软删但 deleted_at IS NULL 的行(此前删除不写 deleted_at),
--    回填 deleted_at=updated_at 近似值。背景: 保留期清理此前按 updated_at 判过期,
--    老文件改名/替换会刷新 updated_at → "今天删除明天被清"; 改用 deleted_at 判过期后,
--    存量 NULL 行会被 COALESCE 兜底, 回填使保留期从真实删除时刻起算(近似但安全)。
-- ② 回收站索引: (deleted, deleted_at, deleted_by) 两表各一——保留期扫描/我的回收站归属过滤。
-- ③ biz_file_version 唯一键 (file_id, version_no): 防归档/回滚并发重号; 迁移前去重(保留最大 id)。
-- 幂等: information_schema 守卫 + NOT EXISTS 回填; 只增不删(除去重的重复行)。

-- ① 存量回填(条件幂等: 只动 NULL 行)
UPDATE biz_file
SET deleted_at = updated_at
WHERE deleted = 1 AND deleted_at IS NULL;

-- ② 索引
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file' AND INDEX_NAME = 'idx_file_recycle');
SET @sql := IF(@idx = 0,
  'ALTER TABLE biz_file ADD INDEX idx_file_recycle (deleted, deleted_at, deleted_by)',
  'SELECT ''idx_file_recycle 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_folder' AND INDEX_NAME = 'idx_folder_recycle');
SET @sql := IF(@idx = 0,
  'ALTER TABLE biz_file_folder ADD INDEX idx_folder_recycle (deleted, deleted_at, deleted_by)',
  'SELECT ''idx_folder_recycle 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ 版本表唯一键: 先按 (file_id, version_no) 去重(保留最大 id), 再加唯一键
DELETE v1 FROM biz_file_version v1
JOIN biz_file_version v2
  ON v1.file_id = v2.file_id AND v1.version_no = v2.version_no AND v1.id < v2.id;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_file_version' AND INDEX_NAME = 'uk_file_version');
SET @sql := IF(@idx = 0,
  'ALTER TABLE biz_file_version ADD UNIQUE KEY uk_file_version (file_id, version_no)',
  'SELECT ''uk_file_version 已存在''');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
