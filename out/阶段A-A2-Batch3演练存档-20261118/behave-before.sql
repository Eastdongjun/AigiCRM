DROP TABLE IF EXISTS _a2_before;
CREATE TABLE _a2_before AS
SELECT DISTINCT subject_type, subject_id,
  CASE WHEN folder_id IS NULL THEN 'SPACE' ELSE 'FOLDER' END AS object_type,
  folder_id,
  CASE WHEN folder_id IS NULL THEN NULLIF(TRIM(folder_type),'') ELSE NULL END AS space_type,
  CASE WHEN a.action IN ('rename','replace') THEN 'edit' ELSE a.action END AS action,
  CASE WHEN NULLIF(NULLIF(TRIM(COALESCE(scope_level,'')),''),'all') IS NULL THEN 'ANY' ELSE 'RELATIVE' END AS scope_mode,
  NULLIF(NULLIF(TRIM(COALESCE(scope_level,'')),''),'all') AS norm_level,
  scope_org_id
FROM biz_folder_type_default d
JOIN (SELECT 'view' AS action UNION ALL SELECT 'download' UNION ALL SELECT 'upload' UNION ALL SELECT 'edit'
      UNION ALL SELECT 'rename' UNION ALL SELECT 'replace' UNION ALL SELECT 'delete'
      UNION ALL SELECT 'share' UNION ALL SELECT 'manage' UNION ALL SELECT 'create') a
  ON FIND_IN_SET(a.action, REPLACE(COALESCE(d.perms,''),' ','')) > 0
WHERE d.subject_type IN ('ROLE','USER') AND d.subject_id IS NOT NULL;
