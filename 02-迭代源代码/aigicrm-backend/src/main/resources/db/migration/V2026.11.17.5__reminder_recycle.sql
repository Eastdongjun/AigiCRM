-- V2026.11.17.5__reminder_recycle.sql
-- 【2026-11-17 阶段三】提醒删除进统一回收站：软删 + 审计字段。
-- 提醒仍有 pending/processed 状态机；删除是并列的“回收站”维度，不改变原状态语义。
ALTER TABLE `biz_reminder`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN `deleted_at` DATETIME NULL,
  ADD COLUMN `deleted_by` BIGINT NULL,
  ADD COLUMN `deleted_reason` VARCHAR(255) NULL,
  ADD KEY `idx_reminder_deleted` (`tenant_id`, `deleted`, `deleted_at`);
