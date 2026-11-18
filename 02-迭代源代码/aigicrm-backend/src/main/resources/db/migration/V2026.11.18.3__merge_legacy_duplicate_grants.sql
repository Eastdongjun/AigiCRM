-- 【A2 Batch-3 2026-11-18】存量重复组处置·方案 A（用户拍板 #2，QC 第 2 轮结论 §四工单 3）：
--   biz_folder_type_default 中【完整 scope 完全相同 且 规范化 perms 等值】的真重复组（行数>1）合并为单行。
--
-- 口径（QC 工单 + Batch-3 副本演练实证修正）：
--   * 组键 = (tenant_id, subject_type, subject_id, object_type, folder_id, folder_type,
--             规范化 scope_level, scope_org_id) 完全相同——**不同 scope 的组绝不合并**（红线）；
--   * 【修正】仅当组内**规范化 perms 等值**（规范化=saveGrant 同款归一：edit→rename+replace、词表序重排）
--     才合并。QC 原口径「perms 取并集 + expires 任一 NULL→NULL」在组内 perms 不同、expires 不一致时
--     会与 V2026.11.18.2 迁移的**动作组级** expire 规则错位（演练实测 expire_diff=2：某动作的限时被
--     并集行扩大为永久/拉长），违反红线「合并后权限面=原并集（语义不变）」。收紧后两规则数学一致
--     （演练实证四数 0）；perms 不同的真重复组**不自动合并**，文末附清单 SQL 供切换窗口人工处置。
--   * 保留 MIN(id)（= V2026.11.18.2 legacy_ref 基准）；expires 任一 NULL→NULL 否则 MAX；
--   * 幂等：合并后组内单行；空库重放对空表无操作。
--   * 旧入口 saveGrant 已确认不再产生同 scope 重复（select-before-write 按
--     (folder_type, object_type, folder_id, subject) upsert，update 路径天然去重——跨 scope 亦覆盖）。
--
-- 回滚预案：删除的行可从迁移前备份恢复（deploy-db-test.sh 自动备份）；保留行仅 perms 重排/expire 归一。
-- 前置：V2026.11.18.2 已执行。

-- ① 更新保留行（perms=规范化等值串；expires 任一 NULL→NULL 否则 MAX；
--    内层聚合包派生表绕 MySQL 1093：UPDATE/DELETE 目标表不得直接出现在 FROM 子查询）
UPDATE `biz_folder_type_default` k
JOIN (
  SELECT MIN(`id`) AS keep_id, MAX(`expires_at`) AS max_exp, (COUNT(*) = COUNT(`expires_at`)) AS all_set
    FROM (SELECT `id`, `tenant_id`, `subject_type`, `subject_id`, `object_type`, `folder_id`,
                 `folder_type`, `scope_level`, `scope_org_id`, `perms`, `expires_at`
            FROM `biz_folder_type_default`) src
   GROUP BY `tenant_id`, `subject_type`, `subject_id`, `object_type`, `folder_id`, `folder_type`,
            NULLIF(NULLIF(TRIM(COALESCE(`scope_level`, '')), ''), 'all'), `scope_org_id`
  HAVING COUNT(*) > 1 AND MIN(TRIM(BOTH ',' FROM CONCAT_WS(',',
             IF(FIND_IN_SET('view',    REPLACE(`perms`, ' ', '')) > 0, 'view', NULL),
             IF(FIND_IN_SET('download',REPLACE(`perms`, ' ', '')) > 0, 'download', NULL),
             IF(FIND_IN_SET('upload',  REPLACE(`perms`, ' ', '')) > 0, 'upload', NULL),
             IF(FIND_IN_SET('rename',  REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'rename', NULL),
             IF(FIND_IN_SET('replace', REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'replace', NULL),
             IF(FIND_IN_SET('delete',  REPLACE(`perms`, ' ', '')) > 0, 'delete', NULL),
             IF(FIND_IN_SET('share',   REPLACE(`perms`, ' ', '')) > 0, 'share', NULL),
             IF(FIND_IN_SET('manage',  REPLACE(`perms`, ' ', '')) > 0, 'manage', NULL),
             IF(FIND_IN_SET('create',  REPLACE(`perms`, ' ', '')) > 0, 'create', NULL)))) = MAX(TRIM(BOTH ',' FROM CONCAT_WS(',',
             IF(FIND_IN_SET('view',    REPLACE(`perms`, ' ', '')) > 0, 'view', NULL),
             IF(FIND_IN_SET('download',REPLACE(`perms`, ' ', '')) > 0, 'download', NULL),
             IF(FIND_IN_SET('upload',  REPLACE(`perms`, ' ', '')) > 0, 'upload', NULL),
             IF(FIND_IN_SET('rename',  REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'rename', NULL),
             IF(FIND_IN_SET('replace', REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'replace', NULL),
             IF(FIND_IN_SET('delete',  REPLACE(`perms`, ' ', '')) > 0, 'delete', NULL),
             IF(FIND_IN_SET('share',   REPLACE(`perms`, ' ', '')) > 0, 'share', NULL),
             IF(FIND_IN_SET('manage',  REPLACE(`perms`, ' ', '')) > 0, 'manage', NULL),
             IF(FIND_IN_SET('create',  REPLACE(`perms`, ' ', '')) > 0, 'create', NULL))))
) g ON k.`id` = g.keep_id
SET k.`expires_at` = IF(g.all_set, g.max_exp, NULL),
    k.`perms` = (
      SELECT MIN(TRIM(BOTH ',' FROM CONCAT_WS(',',
             IF(FIND_IN_SET('view',    REPLACE(d.`perms`, ' ', '')) > 0, 'view', NULL),
             IF(FIND_IN_SET('download',REPLACE(d.`perms`, ' ', '')) > 0, 'download', NULL),
             IF(FIND_IN_SET('upload',  REPLACE(d.`perms`, ' ', '')) > 0, 'upload', NULL),
             IF(FIND_IN_SET('rename',  REPLACE(d.`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(d.`perms`, ' ', '')) > 0, 'rename', NULL),
             IF(FIND_IN_SET('replace', REPLACE(d.`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(d.`perms`, ' ', '')) > 0, 'replace', NULL),
             IF(FIND_IN_SET('delete',  REPLACE(d.`perms`, ' ', '')) > 0, 'delete', NULL),
             IF(FIND_IN_SET('share',   REPLACE(d.`perms`, ' ', '')) > 0, 'share', NULL),
             IF(FIND_IN_SET('manage',  REPLACE(d.`perms`, ' ', '')) > 0, 'manage', NULL),
             IF(FIND_IN_SET('create',  REPLACE(d.`perms`, ' ', '')) > 0, 'create', NULL))))
      FROM (SELECT `tenant_id`, `subject_type`, `subject_id`, `object_type`, `folder_id`,
                   `folder_type`, `scope_level`, `scope_org_id`, `perms`
              FROM `biz_folder_type_default`) d
     WHERE d.`tenant_id` = k.`tenant_id` AND d.`subject_type` = k.`subject_type`
       AND d.`subject_id` = k.`subject_id` AND d.`object_type` = k.`object_type`
       AND (d.`folder_id` <=> k.`folder_id`) AND d.`folder_type` = k.`folder_type`
       AND (NULLIF(NULLIF(TRIM(COALESCE(d.`scope_level`, '')), ''), 'all')
            <=> NULLIF(NULLIF(TRIM(COALESCE(k.`scope_level`, '')), ''), 'all'))
       AND (d.`scope_org_id` <=> k.`scope_org_id`)
    );

-- ② 删除已合并组的其余行（组判定与 ① 完全一致：完整 scope 同 + 规范化 perms 等值）
DELETE d FROM `biz_folder_type_default` d
JOIN (
  SELECT `tenant_id`, `subject_type`, `subject_id`, `object_type`, `folder_id`, `folder_type`,
         NULLIF(NULLIF(TRIM(COALESCE(`scope_level`, '')), ''), 'all') AS norm_scope,
         `scope_org_id`, MIN(`id`) AS keep_id
    FROM (SELECT `tenant_id`, `subject_type`, `subject_id`, `object_type`, `folder_id`,
                 `folder_type`, `scope_level`, `scope_org_id`, `id`, `perms`
            FROM `biz_folder_type_default`) src
   GROUP BY `tenant_id`, `subject_type`, `subject_id`, `object_type`, `folder_id`, `folder_type`,
            NULLIF(NULLIF(TRIM(COALESCE(`scope_level`, '')), ''), 'all'), `scope_org_id`
  HAVING COUNT(*) > 1 AND MIN(TRIM(BOTH ',' FROM CONCAT_WS(',',
             IF(FIND_IN_SET('view',    REPLACE(`perms`, ' ', '')) > 0, 'view', NULL),
             IF(FIND_IN_SET('download',REPLACE(`perms`, ' ', '')) > 0, 'download', NULL),
             IF(FIND_IN_SET('upload',  REPLACE(`perms`, ' ', '')) > 0, 'upload', NULL),
             IF(FIND_IN_SET('rename',  REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'rename', NULL),
             IF(FIND_IN_SET('replace', REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'replace', NULL),
             IF(FIND_IN_SET('delete',  REPLACE(`perms`, ' ', '')) > 0, 'delete', NULL),
             IF(FIND_IN_SET('share',   REPLACE(`perms`, ' ', '')) > 0, 'share', NULL),
             IF(FIND_IN_SET('manage',  REPLACE(`perms`, ' ', '')) > 0, 'manage', NULL),
             IF(FIND_IN_SET('create',  REPLACE(`perms`, ' ', '')) > 0, 'create', NULL)))) = MAX(TRIM(BOTH ',' FROM CONCAT_WS(',',
             IF(FIND_IN_SET('view',    REPLACE(`perms`, ' ', '')) > 0, 'view', NULL),
             IF(FIND_IN_SET('download',REPLACE(`perms`, ' ', '')) > 0, 'download', NULL),
             IF(FIND_IN_SET('upload',  REPLACE(`perms`, ' ', '')) > 0, 'upload', NULL),
             IF(FIND_IN_SET('rename',  REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'rename', NULL),
             IF(FIND_IN_SET('replace', REPLACE(`perms`, ' ', '')) > 0
                OR FIND_IN_SET('edit', REPLACE(`perms`, ' ', '')) > 0, 'replace', NULL),
             IF(FIND_IN_SET('delete',  REPLACE(`perms`, ' ', '')) > 0, 'delete', NULL),
             IF(FIND_IN_SET('share',   REPLACE(`perms`, ' ', '')) > 0, 'share', NULL),
             IF(FIND_IN_SET('manage',  REPLACE(`perms`, ' ', '')) > 0, 'manage', NULL),
             IF(FIND_IN_SET('create',  REPLACE(`perms`, ' ', '')) > 0, 'create', NULL))))
) g ON d.`tenant_id` = g.`tenant_id` AND d.`subject_type` = g.`subject_type`
   AND d.`subject_id` = g.`subject_id` AND d.`object_type` = g.`object_type`
   AND (d.`folder_id` <=> g.`folder_id`) AND d.`folder_type` = g.`folder_type`
   AND (NULLIF(NULLIF(TRIM(COALESCE(d.`scope_level`, '')), ''), 'all') <=> g.norm_scope)
   AND (d.`scope_org_id` <=> g.`scope_org_id`)
   AND d.`id` <> g.keep_id;

-- ③ 【清单·不执行】未合并真重复组（含 perms 不同组；切换窗口人工处置）：
-- SELECT subject_type, subject_id, object_type, folder_id, folder_type, scope_level, scope_org_id,
--        COUNT(*) AS rows_cnt, GROUP_CONCAT(CONCAT(id, ':', perms) ORDER BY id SEPARATOR ' | ') AS detail
--   FROM biz_folder_type_default
--  GROUP BY tenant_id, subject_type, subject_id, object_type, folder_id, folder_type,
--           NULLIF(NULLIF(TRIM(COALESCE(scope_level,'')),''),'all'), scope_org_id
-- HAVING COUNT(*) > 1;
