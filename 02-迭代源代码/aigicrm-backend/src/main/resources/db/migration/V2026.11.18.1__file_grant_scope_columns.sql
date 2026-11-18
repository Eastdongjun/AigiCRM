-- 【A2 转正 2026-11-18】biz_file_grant 结构升级：空间类型列 + 相对/绝对范围模式 + 迁移来源 + 多 scope 唯一键。
-- 由 docs/design/phase-a/V2026.11.18.1__file_grant_inheritance_indexes.sql.draft 转正
-- （QC 复审结论 2026-1118 §四：文件名建议改 scope_columns，内容不变）。
-- 评审要点（已随 QC 结论拍板）：
--   1) 旧 scope_level 是“相对当前查看人”的数据范围，不是空间类型；SPACE 必须用 space_type；
--   2) 新唯一键包含 space_type/scope_mode/scope_level/scope_org_id，保留多 scope 行；
--   3) legacy 旧行默认 scope_mode=ANY/RELATIVE；新手工授权默认 SUBTREE；
--   4) 不删 biz_folder_type_default。
-- 回滚预案（AGENTS.md 迁移双口径登记）：加列/键不做逆 DDL（丢数据风险>收益）——读路径回滚=FILE_GRANT_MODE=legacy；
-- 数据级回滚=DELETE FROM biz_file_grant WHERE source IN ('legacy','dual_write')（保留 manual 行）；旧表全程不动。
-- 副本演练证据：out/阶段A-A2-副本演练原始日志-20261118.txt（MySQL 8.0.46 一次通过）。

ALTER TABLE `biz_file_grant`
  DROP INDEX `uk_file_grant`,
  ADD COLUMN `space_type` VARCHAR(20) NULL COMMENT 'SPACE 空间类型 public/private/incentive/fabushi; NULL=任意空间(旧 blank 迁移行)',
  ADD COLUMN `legacy_ref` VARCHAR(120) NULL COMMENT '旧表迁移来源 ftd:<id>:<action>',
  ADD COLUMN `source` VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT 'manual/legacy/dual_write/migration',
  ADD COLUMN `scope_mode` VARCHAR(10) NOT NULL DEFAULT 'SUBTREE' COMMENT 'ANY/RELATIVE/EXACT/SUBTREE',
  ADD COLUMN `object_key` BIGINT AS (COALESCE(`object_id`, 0)) STORED,
  ADD COLUMN `scope_key` VARCHAR(200) AS (
    CONCAT_WS('|', COALESCE(`space_type`, ''), COALESCE(`scope_mode`, ''),
              COALESCE(`scope_level`, ''), COALESCE(`scope_org_id`, 0))
  ) STORED,
  ADD UNIQUE KEY `uk_file_grant_scope`
    (`tenant_id`, `subject_type`, `subject_id`, `object_type`, `object_key`, `action`, `scope_key`),
  ADD UNIQUE KEY `uk_file_grant_legacy` (`tenant_id`, `legacy_ref`),
  ADD KEY `idx_file_grant_scope_org` (`tenant_id`, `scope_org_id`);

-- 存量新表行（2026-11-17 手工授权）兼容回填：
--   SPACE 行此前把 public/private/incentive/fabushi 写在 scope_level，现移到 space_type，
--   scope_mode=ANY（无绝对组织）。
UPDATE `biz_file_grant`
   SET `space_type` = `scope_level`,
       `scope_level` = NULL,
       `scope_mode` = 'ANY'
 WHERE `object_type` = 'SPACE'
   AND `scope_level` IN ('public', 'private', 'incentive', 'fabushi');

-- 非 SPACE 存量行：有 scope_org_id 的保持当前精确语义；无组织的保持不限。
UPDATE `biz_file_grant`
   SET `scope_mode` = CASE WHEN `scope_org_id` IS NULL THEN 'ANY' ELSE 'EXACT' END
 WHERE `object_type` IN ('FILE', 'FOLDER');

-- 说明：MySQL 唯一索引允许多个 NULL；scope_key 用 COALESCE 避免 NULL 使唯一键失效。
-- space_type NULL 的 SPACE 行按“任意空间”解释（兼容旧 blank，§2.7）；新 UI 保存时必填。
