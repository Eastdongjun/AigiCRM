SELECT COUNT(*) FROM (
  SELECT 1 FROM (
    SELECT d.id, d.subject_type, d.subject_id, d.folder_id, d.scope_level,
           CASE WHEN a.action IN ('rename','replace') THEN 'edit' ELSE a.action END AS action
      FROM biz_folder_type_default d
      JOIN (SELECT 'view' AS action UNION ALL SELECT 'download' UNION ALL SELECT 'upload' UNION ALL SELECT 'edit'
            UNION ALL SELECT 'rename' UNION ALL SELECT 'replace' UNION ALL SELECT 'delete'
            UNION ALL SELECT 'share' UNION ALL SELECT 'manage' UNION ALL SELECT 'create') a
        ON FIND_IN_SET(a.action, REPLACE(COALESCE(d.perms,''),' ','')) > 0
     WHERE d.subject_type IN ('ROLE','USER') AND d.subject_id IS NOT NULL
  ) x GROUP BY subject_type, subject_id, CASE WHEN folder_id IS NULL THEN 'SPACE' ELSE 'FOLDER' END, folder_id, action
  HAVING COUNT(*) > 1) t;
