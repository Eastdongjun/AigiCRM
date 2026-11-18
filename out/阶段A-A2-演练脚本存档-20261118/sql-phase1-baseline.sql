-- 只读：测试库旧授权基线（在 SRC_DB 上执行）
SELECT '== mysql version ==' AS sec;
SELECT VERSION() AS mysql_version;
SELECT '== old table schema ==' AS sec;
SHOW CREATE TABLE biz_folder_type_default;
SELECT '== old table total ==' AS sec;
SELECT COUNT(*) AS old_rows FROM biz_folder_type_default;
SELECT '== scope_level distribution ==' AS sec;
SELECT COALESCE(scope_level, '<NULL>') AS scope_level, COUNT(*) AS cnt
  FROM biz_folder_type_default GROUP BY scope_level ORDER BY cnt DESC;
SELECT '== folder_type distribution (blank check) ==' AS sec;
SELECT folder_type, (TRIM(folder_type) = '') AS is_blank, COUNT(*) AS cnt
  FROM biz_folder_type_default GROUP BY folder_type, (TRIM(folder_type) = '') ORDER BY cnt DESC;
SELECT '== subject/object distribution ==' AS sec;
SELECT subject_type, object_type, (folder_id IS NULL) AS folder_id_null, COUNT(*) AS cnt
  FROM biz_folder_type_default GROUP BY subject_type, object_type, (folder_id IS NULL);
SELECT '== perms vocabulary check (must be 0) ==' AS sec;
SELECT COUNT(*) AS bad_perms_rows FROM biz_folder_type_default
 WHERE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(COALESCE(perms,''),' ',''),'view',''),'download',''),'upload',''),'edit',''),
        'rename',''),'replace',''),'delete',''),'share',''),'manage',''),'create',''),',','') <> '';
SELECT '== old scope_org_id non-null (design says ignored by old engine) ==' AS sec;
SELECT COUNT(*) AS scope_org_nonnull FROM biz_folder_type_default WHERE scope_org_id IS NOT NULL;
SELECT '== tables present in snapshot ==' AS sec;
SELECT table_name FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name IN ('biz_file_grant','biz_folder_type_default');
SELECT '== flyway latest ==' AS sec;
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
