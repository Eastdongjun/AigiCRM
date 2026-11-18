-- 演练 v2 检查（在 COPY_DB 上执行；对应 QC 第2轮清单 C1/C3）
SELECT '== C1: migrated rows by source ==' AS sec;
SELECT source, COUNT(*) AS cnt FROM biz_file_grant GROUP BY source;
SELECT '== C1: distribution (object_type/space_type) ==' AS sec;
SELECT object_type, COALESCE(space_type,'<NULL>') AS space_type, COUNT(*) AS cnt
  FROM biz_file_grant WHERE source='legacy' GROUP BY object_type, space_type;
SELECT '== C1: distribution (scope_mode/scope_level) ==' AS sec;
SELECT scope_mode, COALESCE(scope_level,'<NULL>') AS scope_level, COUNT(*) AS cnt
  FROM biz_file_grant WHERE source='legacy' GROUP BY scope_mode, scope_level;
SELECT '== C1: scope_org_id must all be NULL ==' AS sec;
SELECT COUNT(*) AS nonnull_scope_org FROM biz_file_grant WHERE source='legacy' AND scope_org_id IS NOT NULL;
SELECT '== C3: inherit 取值分布（新 FOLDER 行=1 / SPACE 行=0） ==' AS sec;
SELECT object_type, inherit, COUNT(*) AS cnt
  FROM biz_file_grant WHERE source='legacy' GROUP BY object_type, inherit;
SELECT '== C3 断言：SPACE 行 inherit<>0 的行数（预期 0，B-2） ==' AS sec;
SELECT COUNT(*) AS space_inherit_nonzero FROM biz_file_grant
 WHERE source='legacy' AND object_type='SPACE' AND inherit<>0;
SELECT '== C3 断言：FOLDER 行 inherit<>1 的行数（预期 0） ==' AS sec;
SELECT COUNT(*) AS folder_inherit_not1 FROM biz_file_grant
 WHERE source='legacy' AND object_type='FOLDER' AND inherit<>1;
SELECT '== C1: legacy_ref integrity ==' AS sec;
SELECT COUNT(*) AS total_legacy, COUNT(DISTINCT legacy_ref) AS distinct_legacy,
       SUM(legacy_ref IS NULL) AS null_refs,
       SUM(legacy_ref NOT REGEXP '^ftd:[0-9]+:(view|download|upload|edit|delete|share|manage|create)$') AS bad_format
  FROM biz_file_grant WHERE source='legacy';
