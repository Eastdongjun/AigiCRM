-- ============================================================
-- V13__add_incentive_record_updated_by.sql
-- 激励记录审计：新增"最后修改人"列，配合已有 updated_at(DB ON UPDATE 自动)。
--
-- 背景(2026-08-12):
--   追加/编辑原为 delete+add，addRecord 强制 user_id=当前登录人，导致编辑后上传人被改写、
--   记录 ID 与 created_at 重置。本次编辑改走 updateRecord(PUT)，user_id 天然不变，
--   仅新增 updated_by 记录最后修改人；updated_at 由 DB ON UPDATE CURRENT_TIMESTAMP 自动维护。
--
-- 幂等: Flyway 版本化保证单次执行(写入 flyway_schema_history)。
-- 历史行 updated_by=NULL 表示"从未被新语义编辑过"(正确，不回溯)。
-- 不加索引: 无按 updated_by 查询/排序需求(对齐 biz_file.replace_by 不加索引惯例)。
-- ============================================================

ALTER TABLE `biz_incentive_record`
  ADD COLUMN `updated_by` bigint DEFAULT NULL COMMENT '最后修改人ID' AFTER `shared`;
