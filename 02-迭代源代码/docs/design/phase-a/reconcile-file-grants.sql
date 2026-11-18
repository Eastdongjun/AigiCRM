-- DRAFT ONLY — 只读对账，不修改数据。
-- 用途：A2 迁移后比较旧表 biz_folder_type_default 与新表 biz_file_grant(source='legacy')。
-- 要求：任何 missing/extra/diff 非 0 时禁止切 file.grant.mode=new。

WITH old_expanded AS (
  SELECT
    d.tenant_id, d.subject_type, d.subject_id,
    CASE
      WHEN COALESCE(d.object_type, 'FILE') = 'FILE' AND d.folder_id IS NULL THEN 'SPACE'
      WHEN COALESCE(d.object_type, 'FILE') = 'FOLDER' AND d.folder_id IS NULL THEN 'SPACE'
      ELSE 'FOLDER'
    END AS object_type,
    CASE WHEN d.folder_id IS NULL THEN NULL ELSE d.folder_id END AS object_id,
    CASE WHEN a.action IN ('rename', 'replace') THEN 'edit' ELSE a.action END AS action,
    d.scope_org_id, d.scope_level,
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
    CASE WHEN a.action IN ('rename', 'replace') THEN 'edit' ELSE a.action END,
    d.scope_org_id, d.scope_level
),
new_legacy AS (
  SELECT tenant_id, subject_type, subject_id, object_type, object_id, action,
         scope_org_id, scope_level, expire_at
    FROM biz_file_grant
   WHERE source = 'legacy'
)
SELECT 'missing_in_new' AS diff, COUNT(*) AS cnt
  FROM old_expanded o
 WHERE NOT EXISTS (
   SELECT 1 FROM new_legacy n
    WHERE n.tenant_id=o.tenant_id AND n.subject_type=o.subject_type AND n.subject_id=o.subject_id
      AND n.object_type=o.object_type AND n.object_id <=> o.object_id AND n.action=o.action
      AND n.scope_org_id <=> o.scope_org_id AND n.scope_level <=> o.scope_level
 )
UNION ALL
SELECT 'extra_in_new', COUNT(*)
  FROM new_legacy n
 WHERE NOT EXISTS (
   SELECT 1 FROM old_expanded o
    WHERE n.tenant_id=o.tenant_id AND n.subject_type=o.subject_type AND n.subject_id=o.subject_id
      AND n.object_type=o.object_type AND n.object_id <=> o.object_id AND n.action=o.action
      AND n.scope_org_id <=> o.scope_org_id AND n.scope_level <=> o.scope_level
 )
UNION ALL
SELECT 'expire_diff', COUNT(*)
  FROM old_expanded o
  JOIN new_legacy n
    ON n.tenant_id=o.tenant_id AND n.subject_type=o.subject_type AND n.subject_id=o.subject_id
   AND n.object_type=o.object_type AND n.object_id <=> o.object_id AND n.action=o.action
   AND n.scope_org_id <=> o.scope_org_id AND n.scope_level <=> o.scope_level
 WHERE NOT (n.expire_at <=> o.expire_at)
UNION ALL
SELECT 'old_expanded_rows', COUNT(*) FROM old_expanded
UNION ALL
SELECT 'new_legacy_rows', COUNT(*) FROM new_legacy;
