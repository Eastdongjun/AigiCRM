-- 【A2 转正 2026-11-18】biz_folder_type_default -> biz_file_grant 存量迁移（幂等、不删旧表）。
-- 由 docs/design/phase-a/V2026.11.18.2__migrate_folder_type_default_to_file_grant.sql.draft 转正，
-- 已并入 QC 复审结论（2026-11-18）附录 A 三处修订：
--   B-1【实测阻断】两处 MAX(CASE…) 包裹：MySQL 8 默认 sql_mode=only_full_group_by 下
--      new_inherit（裸 CASE）与 legacy_ref（CONCAT 内 CASE）报 1055，不修则 Flyway 必失败；
--   B-2【语义】inherit 按新 object_type 判定：新表 FOLDER 行 ⟺ folder_id IS NOT NULL，
--      故 folder_id 单条件即可；旧 object_type='FOLDER' AND folder_id IS NULL 行（迁成 SPACE）
--      由 inherit=1 改为 0，与设计 §2.2「SPACE=0」对齐（现引擎 SPACE 分支不读 inherit，行为无变化）；
--   B-3【§2.7 缺口】blank→NULL 归一：NULLIF(TRIM(folder_type),'')——空串若不归一将按
--      space_type 非空走精确匹配，恒不命中任何文件=死授权。
--
-- 语义（按 2026-11-18 评审修正 + QC 复审）：
--   * 旧 folder_type 是空间类型 -> 新 space_type（blank 归一 NULL=任意空间）；
--     旧 scope_level 是相对查看人的数据范围 -> scope_mode=RELATIVE 保留 level；
--   * 旧 scope_level NULL/空/all -> scope_mode=ANY，scope_level=NULL；
--   * 旧 scope_org_id 被旧引擎忽略 -> 新 scope_org_id=NULL，不得当绝对组织；
--   * FILE/FOLDER + folder_id NULL -> SPACE；FILE/FOLDER + folder_id -> FOLDER（inherit=1）；
--   * edit/rename/replace 归并为 edit；create 保留；ALLOW；
--   * 过期保留（分组内任一永久则永久）；legacy_ref 幂等（ftd:<MIN(旧行id)>:<动作>，唯一键）。
--
-- 前置：V2026.11.18.1 已执行（space_type/scope_mode/scope_key/唯一键）。
-- 回滚预案（AGENTS.md 迁移双口径登记）：本脚本只插入 source='legacy' 行、不删不改旧表——
-- 回滚=DELETE FROM biz_file_grant WHERE source='legacy'（或 FILE_GRANT_MODE=legacy 读路径回滚）。
-- 副本演练证据：out/阶段A-A2-副本演练原始日志-20261118.txt + 转正后复跑（见本轮报告）。

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
    WHEN d.`folder_id` IS NULL THEN NULLIF(TRIM(d.`folder_type`), '')
    ELSE NULL
  END AS new_space_type,
  CASE WHEN a.`action` IN ('rename', 'replace') THEN 'edit' ELSE a.`action` END AS new_action,
  'ALLOW',
  MAX(CASE
    WHEN d.`folder_id` IS NOT NULL THEN 1
    ELSE 0
  END) AS new_inherit,
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
  CONCAT('ftd:', MIN(d.`id`), ':', MAX(CASE WHEN a.`action` IN ('rename', 'replace') THEN 'edit' ELSE a.`action` END)),
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
       AND g.`space_type` <=> CASE
             WHEN d.`folder_id` IS NULL THEN NULLIF(TRIM(d.`folder_type`), '')
             ELSE NULL
           END
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
  CASE
    WHEN d.`folder_id` IS NULL THEN NULLIF(TRIM(d.`folder_type`), '')
    ELSE NULL
  END,
  CASE WHEN a.`action` IN ('rename', 'replace') THEN 'edit' ELSE a.`action` END,
  CASE
    WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN 'ANY'
    ELSE 'RELATIVE'
  END,
  CASE
    WHEN d.`scope_level` IS NULL OR TRIM(d.`scope_level`) = '' OR d.`scope_level` = 'all' THEN NULL
    ELSE d.`scope_level`
  END;
