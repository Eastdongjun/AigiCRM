-- DRAFT ONLY — 不要放进 db/migration，未评审/未对账前不得执行。
-- A2：biz_folder_type_default -> biz_file_grant 存量迁移（幂等、不删旧表）。
-- 前置：V2026.11.18.1 草案已执行（space_type/scope_mode/scope_key/唯一键）。
--
-- 语义（按 2026-11-18 评审修正）：
--   * 旧 folder_type 是空间类型 -> 新 space_type；旧 scope_level 是相对数据范围；
--   * 旧 scope_level NULL/空/all -> scope_mode=ANY，scope_level=NULL；
--   * 旧 self/dept/office/region -> scope_mode=RELATIVE，保留 scope_level；
--   * 旧 scope_org_id 被旧引擎忽略 -> 新 scope_org_id=NULL，不得当绝对组织；
--   * FILE + folder_id NULL -> SPACE；FOLDER + folder_id NULL -> SPACE；
--   * FILE/FOLDER + folder_id -> FOLDER（inherit=1）；
--   * edit/rename/replace 归并为 edit；create 保留；
--   * ALLOW；过期时间保留（任一永久则永久）；legacy_ref 幂等。
--
-- ⚠️ 执行前必须先在测试库副本演练，并对账行/行为 0 差异。

INSERT INTO `biz_file_grant`
  (`tenant_id`, `subject_type`, `subject_id`,
   `object_type`, `object_id`, `space_type`, `action`, `effect`, `inherit`,
   `scope_org_id`, `scope_level`, `scope_mode`, `expire_at`,
   `created_by`, `created_at`, `legacy_ref`, `source`)
SELECT
  d.`tenant_id`,
  d.`subject_type`,
  d.`subject_id`,
  CASE
    WHEN COALESCE(d.`object_type`, 'FILE') = 'FILE' AND d.`folder_id` IS NULL THEN 'SPACE'
    WHEN COALESCE(d.`object_type`, 'FILE') = 'FOLDER' AND d.`folder_id` IS NULL THEN 'SPACE'
    ELSE 'FOLDER'
  END AS new_object_type,
  CASE WHEN d.`folder_id` IS NULL THEN NULL ELSE d.`folder_id` END AS new_object_id,
  CASE
    WHEN d.`folder_id` IS NULL THEN d.`folder_type`
    ELSE NULL
  END AS new_space_type,
  CASE WHEN a.`action` IN ('rename', 'replace') THEN 'edit' ELSE a.`action` END AS new_action,
  'ALLOW',
  CASE
    WHEN (COALESCE(d.`object_type`, 'FILE') = 'FOLDER' OR d.`folder_id` IS NOT NULL) THEN 1
    ELSE 0
  END AS new_inherit,
  NULL AS new_scope_org_id,
  CASE
    WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN NULL
    ELSE d.`scope_level`
  END AS new_scope_level,
  CASE
    WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN 'ANY'
    ELSE 'RELATIVE'
  END AS new_scope_mode,
  CASE
    WHEN COUNT(*) > COUNT(d.`expires_at`) THEN NULL
    ELSE MAX(d.`expires_at`)
  END AS new_expire_at,
  MAX(d.`created_by`),
  MIN(d.`created_at`),
  CONCAT('ftd:', MIN(d.`id`), ':', CASE WHEN a.`action` IN ('rename', 'replace') THEN 'edit' ELSE a.`action` END),
  'legacy'
FROM `biz_folder_type_default` d
JOIN (
  SELECT 'view' AS `action`
  UNION ALL SELECT 'download'
  UNION ALL SELECT 'upload'
  UNION ALL SELECT 'edit'
  UNION ALL SELECT 'rename'
  UNION ALL SELECT 'replace'
  UNION ALL SELECT 'delete'
  UNION ALL SELECT 'share'
  UNION ALL SELECT 'manage'
  UNION ALL SELECT 'create'
) a
  ON FIND_IN_SET(a.`action`, REPLACE(COALESCE(d.`perms`, ''), ' ', '')) > 0
WHERE d.`subject_type` IN ('ROLE', 'USER')
  AND d.`subject_id` IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `biz_file_grant` g
     WHERE g.`tenant_id` = d.`tenant_id`
       AND g.`subject_type` = d.`subject_type`
       AND g.`subject_id` = d.`subject_id`
       AND g.`object_type` = CASE
             WHEN COALESCE(d.`object_type`, 'FILE') = 'FILE' AND d.`folder_id` IS NULL THEN 'SPACE'
             WHEN COALESCE(d.`object_type`, 'FILE') = 'FOLDER' AND d.`folder_id` IS NULL THEN 'SPACE'
             ELSE 'FOLDER'
           END
       AND g.`object_id` <=> CASE WHEN d.`folder_id` IS NULL THEN NULL ELSE d.`folder_id` END
       AND g.`space_type` <=> CASE WHEN d.`folder_id` IS NULL THEN d.`folder_type` ELSE NULL END
       AND g.`action` = CASE WHEN a.`action` IN ('rename', 'replace') THEN 'edit' ELSE a.`action` END
       AND g.`scope_mode` = CASE
             WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN 'ANY'
             ELSE 'RELATIVE'
           END
       AND g.`scope_level` <=> CASE
             WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN NULL
             ELSE d.`scope_level`
           END
       AND g.`scope_org_id` IS NULL
  )
GROUP BY
  d.`tenant_id`, d.`subject_type`, d.`subject_id`,
  CASE
    WHEN COALESCE(d.`object_type`, 'FILE') = 'FILE' AND d.`folder_id` IS NULL THEN 'SPACE'
    WHEN COALESCE(d.`object_type`, 'FILE') = 'FOLDER' AND d.`folder_id` IS NULL THEN 'SPACE'
    ELSE 'FOLDER'
  END,
  CASE WHEN d.`folder_id` IS NULL THEN NULL ELSE d.`folder_id` END,
  CASE WHEN d.`folder_id` IS NULL THEN d.`folder_type` ELSE NULL END,
  CASE WHEN a.`action` IN ('rename', 'replace') THEN 'edit' ELSE a.`action` END,
  CASE
    WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN 'ANY'
    ELSE 'RELATIVE'
  END,
  CASE
    WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN NULL
    ELSE d.`scope_level`
  END;
