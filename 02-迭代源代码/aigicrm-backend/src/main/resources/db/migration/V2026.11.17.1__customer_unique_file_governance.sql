-- V2026.11.17.1__customer_unique_file_governance.sql
-- 【2026-11-17 权限与文件治理 第一阶段】
-- 1) 客户名称全集团唯一：name_norm + dup_exempt + active_name 生成列 + 唯一索引；
--    现有同名活跃客户 grandfather（dup_exempt=1）保留，不参与唯一索引；新客/改名严格唯一。
-- 2) 客户/人员删除审计字段；文件/版本/回收站/文件引用/统一授权基础表。
-- 3) 法布施文件删除、文件版本回滚两个独立权限码（默认仅 admin）。
-- 说明：本迁移只加列/表/索引与权限登记，不改历史业务数据。
-- ⚠️ 生产执行顺序（2026-11-17）：① 本迁移；② scripts/backfill-customer-name-norm.py
--    （用与应用一致的 NFKC/中文括号/空格规则回填 name_norm，并标记活跃同名组 dup_exempt）；
--    ③ 启动应用。SQL 回填只能做 LOWER(TRIM)，无法处理全角括号/NFKC，必须跑第②步。

-- ============ 1. 客户名称唯一 ============
ALTER TABLE `biz_customer`
  ADD COLUMN `name_norm` VARCHAR(100) NULL COMMENT '归一化客户名（NFKC/空格折叠/小写/括号统一）',
  ADD COLUMN `dup_exempt` TINYINT NOT NULL DEFAULT 0 COMMENT '1=历史同名豁免（祖父条款），不参与活跃唯一',
  ADD COLUMN `deleted_at` DATETIME NULL COMMENT '删除时间',
  ADD COLUMN `deleted_by` BIGINT NULL COMMENT '删除人',
  ADD COLUMN `deleted_reason` VARCHAR(255) NULL COMMENT '删除原因',
  ADD COLUMN `delete_source` VARCHAR(20) NULL COMMENT 'user/migration/system/cleanup';

-- 回填归一化名（保守：LOWER(TRIM)，长度截 100；应用层后续按 NFKC 归一化处理新建/编辑）
UPDATE `biz_customer`
   SET `name_norm` = LEFT(LOWER(TRIM(`customer_name`)), 100)
 WHERE `name_norm` IS NULL AND `customer_name` IS NOT NULL;

-- 现有同名活跃客户：每组保留 id 最小一条，其余标记豁免（不改名、不删除）
UPDATE `biz_customer` c
  JOIN (
    SELECT `id` FROM (
      SELECT `id`,
             ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `name_norm` ORDER BY `id`) AS rn
        FROM `biz_customer`
       WHERE `deleted` = 0 AND `name_norm` IS NOT NULL
    ) t
    WHERE t.rn > 1
  ) d ON d.`id` = c.`id`
   SET c.`dup_exempt` = 1;

-- 已删客户补删除时间（历史数据无删除时间，用 updated_at 近似；不伪造操作人）
UPDATE `biz_customer`
   SET `deleted_at` = `updated_at`
 WHERE `deleted` = 1 AND `deleted_at` IS NULL;

ALTER TABLE `biz_customer`
  ADD COLUMN `active_name` VARCHAR(100)
    GENERATED ALWAYS AS (
      CASE WHEN `deleted` = 0 AND `dup_exempt` = 0 THEN `name_norm` ELSE NULL END
    ) STORED COMMENT '活跃唯一名；已删/豁免为 NULL（MySQL 唯一索引允许多 NULL）',
  ADD UNIQUE KEY `uk_customer_active_name` (`tenant_id`, `active_name`),
  ADD KEY `idx_customer_deleted` (`tenant_id`, `deleted`, `deleted_at`),
  ADD KEY `idx_customer_owner_deleted` (`tenant_id`, `owner_id`, `deleted`),
  ADD KEY `idx_customer_dept_deleted` (`tenant_id`, `dept_id`, `deleted`),
  ADD KEY `idx_customer_office_deleted` (`tenant_id`, `office_id`, `deleted`);

-- ============ 2. 人员删除审计 ============
ALTER TABLE `sys_user`
  ADD COLUMN `deleted_at` DATETIME NULL,
  ADD COLUMN `deleted_by` BIGINT NULL,
  ADD COLUMN `deleted_reason` VARCHAR(255) NULL,
  ADD COLUMN `delete_source` VARCHAR(20) NULL;
UPDATE `sys_user`
   SET `deleted_at` = `updated_at`
 WHERE `deleted` = 1 AND `deleted_at` IS NULL;

-- ============ 3. 文件/版本/回收站/引用/授权基础 ============
ALTER TABLE `biz_file`
  ADD COLUMN `md5` VARCHAR(64) NULL,
  ADD COLUMN `deleted_reason` VARCHAR(255) NULL,
  ADD COLUMN `delete_source` VARCHAR(20) NULL;

ALTER TABLE `biz_file_version`
  ADD COLUMN `md5` VARCHAR(64) NULL,
  ADD COLUMN `change_reason` VARCHAR(255) NULL,
  ADD COLUMN `created_by` BIGINT NULL;

CREATE TABLE IF NOT EXISTS `biz_file_link` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `file_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(30) NOT NULL COMMENT 'incentive_record/fabushi_post/fabushi_book/...',
  `biz_id` BIGINT NOT NULL,
  `slot` VARCHAR(30) NULL,
  `relation_type` VARCHAR(20) NOT NULL DEFAULT 'ATTACHMENT',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_link` (`file_id`, `biz_type`, `biz_id`, `slot`),
  KEY `idx_file_link_biz` (`tenant_id`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件与业务对象统一引用';

CREATE TABLE IF NOT EXISTS `biz_file_grant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `subject_type` VARCHAR(10) NOT NULL COMMENT 'ROLE/POSITION/USER',
  `subject_id` BIGINT NOT NULL,
  `object_type` VARCHAR(10) NOT NULL COMMENT 'SPACE/FOLDER/FILE',
  `object_id` BIGINT NULL,
  `action` VARCHAR(30) NOT NULL,
  `effect` VARCHAR(10) NOT NULL DEFAULT 'ALLOW',
  `inherit` TINYINT NOT NULL DEFAULT 1,
  `scope_org_id` BIGINT NULL,
  `scope_level` VARCHAR(20) NULL,
  `expire_at` DATETIME NULL,
  `created_by` BIGINT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_grant` (`tenant_id`,`subject_type`,`subject_id`,`object_type`,`object_id`,`action`),
  KEY `idx_file_grant_object` (`tenant_id`,`object_type`,`object_id`,`action`),
  KEY `idx_file_grant_subject` (`tenant_id`,`subject_type`,`subject_id`,`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件统一授权（空间/文件夹/文件 + 数据范围 + 继承）';

CREATE TABLE IF NOT EXISTS `biz_recycle_bin` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `biz_type` VARCHAR(20) NOT NULL COMMENT 'customer/user/file/incentive/task/reminder',
  `biz_id` BIGINT NOT NULL,
  `snapshot_json` JSON NULL,
  `snapshot_key` VARCHAR(255) NULL,
  `deleted_by` BIGINT NULL,
  `deleted_at` DATETIME NOT NULL,
  `reason` VARCHAR(255) NULL,
  `source` VARCHAR(20) NULL,
  `restored_at` DATETIME NULL,
  `purged_at` DATETIME NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recycle_bin` (`tenant_id`,`biz_type`,`biz_id`),
  KEY `idx_recycle_type_time` (`tenant_id`,`biz_type`,`deleted_at`),
  KEY `idx_recycle_status` (`tenant_id`,`status`,`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一回收站（主表软删 + 快照）';

-- ============ 4. 激励/任务删除字段（为回收站扩展预留） ============
ALTER TABLE `biz_incentive_record`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN `deleted_at` DATETIME NULL,
  ADD COLUMN `deleted_by` BIGINT NULL,
  ADD COLUMN `deleted_reason` VARCHAR(255) NULL;

ALTER TABLE `biz_task`
  ADD COLUMN `deleted_at` DATETIME NULL,
  ADD COLUMN `deleted_by` BIGINT NULL,
  ADD COLUMN `deleted_reason` VARCHAR(255) NULL;

-- ============ 5. 新权限码：法布施文件删除 / 文件版本回滚 ============
INSERT INTO `sys_permission_meta`
  (`code`, `name`, `type`, `module`, `parent_code`, `scope_module`, `scope_action`, `scope_mode`,
   `admin_only`, `requires_org_admin`, `deprecated`, `no_endpoint`, `depends_on`)
VALUES
  ('fabushi:file:delete','法布施文件删除','BUTTON','fabushi','fabushi:view',NULL,NULL,'NONE',0,0,0,0,'fabushi:view'),
  ('file:version:rollback','文件版本回滚','BUTTON','file','file:edit',NULL,NULL,'NONE',0,0,0,0,'file:edit')
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`), `type`=VALUES(`type`), `module`=VALUES(`module`),
  `parent_code`=VALUES(`parent_code`), `depends_on`=VALUES(`depends_on`);

-- 法布施文件删除按钮：挂到法布施组标签下（隐藏字典行，权限中心可勾）
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`)
SELECT g.`id`, '法布施文件删除', 3, NULL, NULL, 'fabushi:file:delete', NULL, 99, 0, 1
FROM (SELECT `id` FROM `sys_menu` WHERE `menu_name` = '法布施' AND `menu_type` = 1 AND COALESCE(`parent_id`,0) = 0 LIMIT 1) g
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'fabushi:file:delete');

-- 文件版本回滚按钮：挂到文件管理页下（隐藏字典行）
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`)
SELECT p.`id`, '文件版本回滚', 3, NULL, NULL, 'file:version:rollback', NULL, 99, 0, 1
FROM (SELECT `id` FROM `sys_menu` WHERE `permission` = 'file:view' AND `menu_type` = 2 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'file:version:rollback');

-- 默认仅 admin 绑定；05/06 不绑（管理员后续在权限中心勾选）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` IN ('fabushi:file:delete','file:version:rollback')
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);
