-- 法布施切片 6：书架管理 D20 + 阅读管理 D23 + 我的觉醒聚合 D7
-- 变更依据：REQUIREMENT-DELTA-20261122.md D7/D20/D22/D23 + plan-sess_f3eb5dec 切片 6。
-- 设计口径：
--   1. 沿用 biz_fabushi_shelf.status 单列扩 wish（想读/在读/已读完/收藏），不新增平行状态列；
--   2. pinned/sort_no 支持置顶与手动排序；group_id 只挂自建分组，10 大类型自动组按书目 category_code 归类；
--   3. finished_at 显式记录完成时刻，finishedThisMonth 不再用 updated_at 近似；
--   4. 自动组以 user_id=0 的共享模板行存在，服务层对全用户只读展开，避免 10×用户 的种子膨胀。
-- 回滚：ALTER TABLE 反向 DROP COLUMN（会丢置顶/排序/分组/完成时间，执行前须备份 biz_fabushi_shelf）；
--       DROP TABLE biz_fabushi_shelf_group；不改已有 status 值，回滚后原 reading/finished/favorite 照常工作。
-- 幂等：本迁移只执行一次（Flyway 版本化）；建表 IF NOT EXISTS + 种子 NOT EXISTS 为重复执行兜底。
-- INV-MIG-001：自动组共享 owner 用会话变量表达，避免在 WHERE 写硬编码 user_id 数字定位行。
SET @fabushi_auto_group_owner = 0;

ALTER TABLE `biz_fabushi_shelf`
  ADD COLUMN `pinned` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶 0否 1是' AFTER `status`,
  ADD COLUMN `sort_no` int NOT NULL DEFAULT 0 COMMENT '手动排序号，越小越前' AFTER `pinned`,
  ADD COLUMN `group_id` bigint DEFAULT NULL COMMENT '自建分组ID；NULL=未分组/按类别自动归类' AFTER `sort_no`,
  ADD COLUMN `finished_at` datetime DEFAULT NULL COMMENT '标记已读完的时刻（本月读完统计口径）' AFTER `group_id`,
  MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'reading' COMMENT 'wish/reading/finished/favorite',
  ADD KEY `idx_fb_shelf_pin_sort` (`user_id`, `pinned`, `sort_no`),
  ADD KEY `idx_fb_shelf_group` (`user_id`, `group_id`);

CREATE TABLE IF NOT EXISTS `biz_fabushi_shelf_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '自建组=所属用户；自动模板组=0',
  `name` varchar(64) NOT NULL COMMENT '分组名（自动组=类别字典 label）',
  `group_type` varchar(20) NOT NULL DEFAULT 'custom' COMMENT 'custom/auto',
  `category_code` varchar(64) DEFAULT NULL COMMENT '自动组对应的 fabushi_category 字典 value',
  `sort_no` int NOT NULL DEFAULT 0,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fb_shelf_group_user_name` (`user_id`, `name`),
  KEY `idx_fb_shelf_group_user` (`user_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施书架分组（自建组 + 10 大类型自动组模板）';

-- 10 大类型自动组：从类别字典派生，label/code 单一源仍是 sys_dict_data，本表只存展示模板。
-- 注意：本表 COLLATE=utf8mb4_general_ci，而 sys_dict_data 为 MySQL 8 默认 utf8mb4_0900_ai_ci
--       （基线里 sys_* 表未显式指定 collation），直接比较会 1267 Illegal mix of collations，
--       故在字典侧显式 COLLATE 到本表排序规则，保证跨库重放稳定。
INSERT INTO `biz_fabushi_shelf_group` (`user_id`, `name`, `group_type`, `category_code`, `sort_no`)
SELECT @fabushi_auto_group_owner, d.dict_label, 'auto', d.dict_value, d.sort_order
FROM `sys_dict_data` d
JOIN `sys_dict_type` t ON t.id = d.dict_type_id
WHERE t.dict_code = 'fabushi_category'
  AND NOT EXISTS (
    SELECT 1 FROM `biz_fabushi_shelf_group` g
    WHERE g.user_id = @fabushi_auto_group_owner AND g.group_type = 'auto'
      AND g.category_code = d.dict_value COLLATE utf8mb4_general_ci
  );

-- 存量复查：status 仍在白名单内；pinned/sort_no 默认值不破坏旧客户端。
-- SELECT status, COUNT(*) FROM biz_fabushi_shelf GROUP BY status;
