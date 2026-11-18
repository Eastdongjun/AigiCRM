-- 迁移后检查（在 COPY_DB 上执行）
SELECT '== migrated rows by source ==' AS sec;
SELECT source, COUNT(*) AS cnt FROM biz_file_grant GROUP BY source;
SELECT '== legacy rows: object_type/space_type distribution ==' AS sec;
SELECT object_type, COALESCE(space_type,'<NULL>') AS space_type, COUNT(*) AS cnt
  FROM biz_file_grant WHERE source='legacy' GROUP BY object_type, space_type;
SELECT '== legacy rows: scope_mode/scope_level distribution ==' AS sec;
SELECT scope_mode, COALESCE(scope_level,'<NULL>') AS scope_level, COUNT(*) AS cnt
  FROM biz_file_grant WHERE source='legacy' GROUP BY scope_mode, scope_level;
SELECT '== legacy rows: scope_org_id must all be NULL ==' AS sec;
SELECT COUNT(*) AS nonnull_scope_org FROM biz_file_grant WHERE source='legacy' AND scope_org_id IS NOT NULL;
SELECT '== legacy rows: effect/inherit distribution ==' AS sec;
SELECT effect, inherit, COUNT(*) AS cnt FROM biz_file_grant WHERE source='legacy' GROUP BY effect, inherit;
SELECT '== legacy_ref integrity ==' AS sec;
SELECT COUNT(*) AS total_legacy, COUNT(DISTINCT legacy_ref) AS distinct_legacy,
       SUM(legacy_ref IS NULL) AS null_refs,
       SUM(legacy_ref NOT REGEXP '^ftd:[0-9]+:(view|download|upload|edit|delete|share|manage|create)$') AS bad_format
  FROM biz_file_grant WHERE source='legacy';
SELECT '== sample migrated rows ==' AS sec;
SELECT id, tenant_id, subject_type, subject_id, object_type, object_id, space_type, action,
       effect, inherit, scope_mode, scope_level, scope_org_id, expire_at, legacy_ref, source
  FROM biz_file_grant WHERE source='legacy' ORDER BY id LIMIT 8;
SELECT '== dup groups collapse check: expire merge (any permanent -> permanent) ==' AS sec;
SELECT COUNT(*) AS legacy_rows_with_expire FROM biz_file_grant WHERE source='legacy' AND expire_at IS NOT NULL;
