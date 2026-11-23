-- 法布施切片 7：阅读器 D19/D24 批1（书目版式标记 + 页码书签）
-- 变更依据：REQUIREMENT-DELTA-20261122.md D19/D24 + plan-sess_f3eb5dec 切片 7。
-- 设计口径：
--   1. layout_type 只做「文本版/影印版」人工标记（管理员在挂 PDF 时确认），默认 scan（影印版）走保守版式；
--      自动检测文本层与重排/书内搜索属批 2，不在本迁移实现，避免误判导致经典错字风险；
--   2. 书签按 (user_id, book_id, page) 一行，重复标记在 service 层去重；不建唯一键，
--      以便逻辑删后同页可再次标记（与 biz_fabushi_reading_progress 的同款无唯一键口径）；
--   3. 全量复用本人维度，不动 biz_fabushi_reading_progress 与任何权限码。
-- 回滚：ALTER TABLE 反向 DROP COLUMN / DROP TABLE biz_fabushi_bookmark（书签会丢失，执行前须备份）。
-- 幂等：Flyway 版本化只执行一次；建表 IF NOT EXISTS 为重复执行兜底。

ALTER TABLE `biz_fabushi_book`
  ADD COLUMN `layout_type` varchar(20) NOT NULL DEFAULT 'scan' COMMENT '版式标记 text=文本版 scan=影印版' AFTER `pdf_file_id`;

CREATE TABLE IF NOT EXISTS `biz_fabushi_bookmark` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '书签归属人',
  `book_id` bigint NOT NULL COMMENT '法布施书目ID',
  `page` int NOT NULL COMMENT '页码（1 起）',
  `note` varchar(200) DEFAULT NULL COMMENT '可选书签备注',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删 0否 1是',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fb_bm_user_book` (`user_id`, `book_id`, `deleted`),
  KEY `idx_fb_bm_page` (`book_id`, `page`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法布施阅读器页码书签（批1：标记/跳转/管理）';

-- 存量书目默认 scan（影印版）— 设置面板会显示「影印版 · 原样增强」，
-- 管理员可在书目管理手动改 text（文本版）后，阅读器切到偏重排的默认背景/缩放。
