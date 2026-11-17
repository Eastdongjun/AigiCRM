-- V2026.11.17.7__lifecycle_transfer_options.sql
-- 【2026-11-17 P1-5】交接可选迁移“激励记录/直属下级”：
--   默认 0（历史业绩不随人走、组织关系不自动变）；管理员在交接单显式勾选后执行，
--   受影响行写 biz_lifecycle_item，支持精确回滚。
ALTER TABLE `biz_lifecycle_request`
  ADD COLUMN `transfer_incentives` TINYINT NOT NULL DEFAULT 0 COMMENT '1=把离职人当前激励记录 user_id 转给继任人',
  ADD COLUMN `transfer_reports` TINYINT NOT NULL DEFAULT 0 COMMENT '1=把 manager_id=离职人的直属下级改挂继任人';
