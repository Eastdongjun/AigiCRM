-- ============================================================
-- 激励专项权限新增"看全部"独立开关（2026-08-08 分享制）
-- 用途: biz_incentive_permission 增加 can_view_all 列
--       看全部 = 能看到全部激励记录(含未分享)，但不隐含编辑/删除他人
--       编辑(can_edit)/删除(can_delete) 开启时自动含"看全部"(后端 canSeeAllIncentive)
-- 幂等: ALTER 非幂等，重复执行会报 Duplicate column；执行前可先查 information_schema
-- 执行: mysql --default-character-set=utf8mb4 aigicrm < incentive-permission-can-view-all.sql
-- ============================================================

ALTER TABLE biz_incentive_permission
  ADD COLUMN can_view_all TINYINT NOT NULL DEFAULT 0
  COMMENT '看全部激励(含未分享)独立开关，不隐含编辑/删除'
  AFTER can_delete;
