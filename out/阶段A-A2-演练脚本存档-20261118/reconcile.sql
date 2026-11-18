-- DRAFT ONLY — 只读对账，不修改数据。
-- 用途：A2 迁移后比较旧表 biz_folder_type_default 与新表 biz_file_grant(source IN ('legacy','dual_write'))。
-- 行对账之外还必须跑“行为对账”（见设计文档 §2.7），行 0 差异不等于语义不变。

WITH old_expanded AS (
  SELECT
    d.tenant_id, d.subject_type, d.subject_id,
    CASE
      WHEN COALESCE(d.object_type, 'FILE') = 'FILE' AND d.folder_id IS NULL THEN 'SPACE'
      WHEN COALESCE(d.object_type, 'FILE') = 'FOLDER' AND d.folder_id IS NULL THEN 'SPACE'
      ELSE 'FOLDER'
    END AS object_type,
    CASE WHEN d.folder_id IS NULL THEN NULL ELSE d.folder_id END AS object_id,
    CASE WHEN d.folder_id IS NULL THEN d.folder_type ELSE NULL END AS space_type,
    CASE WHEN a.action IN ('rename', 'replace') THEN 'edit' ELSE a.action END AS action,
    CASE
      WHEN d.scope_level IS NULL OR TRIM(d.scope_level) = '' OR d.scope_level = 'all' THEN 'ANY'
      ELSE 'RELATIVE'
    END AS scope_mode,
    CASE
      WHEN d.scope_level IS NULL OR TRIM(d.scope_level) = '' OR d.scope_level = 'all' THEN NULL
      ELSE d.scope_level
    END AS scope_level,
    NULL AS scope_org_id,
    CASE WHEN COUNT(*) > COUNT(d.expires_at) THEN NULL ELSE MAX(d.expires_at) END AS expire_at
  FROM biz_folder_type_default d
  JOIN (
    SELECT 'view' AS action UNION ALL SELECT 'download' UNION ALL SELECT 'upload'
    UNION ALL SELECT 'edit' UNION ALL SELECT 'rename' UNION ALL SELECT 'replace'
    UNION ALL SELECT 'delete' UNION ALL SELECT 'share' UNION ALL SELECT 'manage'
    UNION ALL SELECT 'create'
  ) a ON FIND_IN_SET(a.action, REPLACE(COALESCE(d.perms, ''), ' ', '')) > 0
  WHERE d.subject_type IN ('ROLE', 'USER') AND d.subject_id IS NOT NULL
  GROUP BY d.tenant_id, d.subject_type, d.subject_id,
    CASE
      WHEN COALESCE(d.object_type, 'FILE') = 'FILE' AND d.folder_id IS NULL THEN 'SPACE'
      WHEN COALESCE(d.object_type, 'FILE') = 'FOLDER' AND d.folder_id IS NULL THEN 'SPACE'
      ELSE 'FOLDER'
    END,
    CASE WHEN d.folder_id IS NULL THEN NULL ELSE d.folder_id END,
    CASE WHEN d.folder_id IS NULL THEN d.folder_type ELSE NULL END,
    CASE WHEN a.action IN ('rename', 'replace') THEN 'edit' ELSE a.action END,
    CASE
      WHEN d.scope_level IS NULL OR TRIM(d.scope_level) = '' OR d.scope_level = 'all' THEN 'ANY'
      ELSE 'RELATIVE'
    END,
    CASE
      WHEN d.scope_level IS NULL OR TRIM(d.scope_level) = '' OR d.scope_level = 'all' THEN NULL
      ELSE d.scope_level
    END
),
new_legacy AS (
  SELECT tenant_id, subject_type, subject_id, object_type, object_id, space_type,
         action, scope_mode, scope_level, scope_org_id, expire_at
    FROM biz_file_grant
   WHERE source IN ('legacy', 'dual_write')
)
SELECT 'missing_in_new' AS diff, COUNT(*) AS cnt
  FROM old_expanded o
 WHERE NOT EXISTS (
   SELECT 1 FROM new_legacy n
    WHERE n.tenant_id=o.tenant_id AND n.subject_type=o.subject_type AND n.subject_id=o.subject_id
      AND n.object_type=o.object_type AND n.object_id <=> o.object_id
      AND n.space_type <=> o.space_type AND n.action=o.action
      AND n.scope_mode=o.scope_mode AND n.scope_level <=> o.scope_level
      AND n.scope_org_id <=> o.scope_org_id
 )
UNION ALL
SELECT 'extra_in_new', COUNT(*)
  FROM new_legacy n
 WHERE NOT EXISTS (
   SELECT 1 FROM old_expanded o
    WHERE n.tenant_id=o.tenant_id AND n.subject_type=o.subject_type AND n.subject_id=o.subject_id
      AND n.object_type=o.object_type AND n.object_id <=> o.object_id
      AND n.space_type <=> o.space_type AND n.action=o.action
      AND n.scope_mode=o.scope_mode AND n.scope_level <=> o.scope_level
      AND n.scope_org_id <=> o.scope_org_id
 )
UNION ALL
SELECT 'expire_diff', COUNT(*)
  FROM old_expanded o
  JOIN new_legacy n
    ON n.tenant_id=o.tenant_id AND n.subject_type=o.subject_type AND n.subject_id=o.subject_id
   AND n.object_type=o.object_type AND n.object_id <=> o.object_id
   AND n.space_type <=> o.space_type AND n.action=o.action
   AND n.scope_mode=o.scope_mode AND n.scope_level <=> o.scope_level
   AND n.scope_org_id <=> o.scope_org_id
 WHERE NOT (n.expire_at <=> o.expire_at)
UNION ALL
SELECT 'old_expanded_rows', COUNT(*) FROM old_expanded
UNION ALL
SELECT 'new_legacy_rows', COUNT(*) FROM new_legacy;
