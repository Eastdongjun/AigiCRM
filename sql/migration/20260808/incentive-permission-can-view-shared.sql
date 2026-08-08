-- ============================================================
-- 激励查看权限改双勾选（2026-08-08）
-- 用途: biz_incentive_permission 增加 can_view_shared 列
--       「查看已分享记录」：默认 1 = 全员基础可见（分享记录默认全员看得到）
--       面板配置该行后可按勾选收窄（取消勾选 → 该用户仅看自己/授权范围内未分享）
--       「查看全部（含未分享）」继续用既有 can_view_all
-- 幂等: ALTER 非幂等，重复执行报 Duplicate column；执行前先查 information_schema
-- 执行: mysql --default-character-set=utf8mb4 ycmn < incentive-permission-can-view-shared.sql
-- ============================================================

ALTER TABLE biz_incentive_permission
  ADD COLUMN can_view_shared TINYINT NOT NULL DEFAULT 1
  COMMENT '查看已分享记录(默认1=全员基础可见；取消则仅看自己/未分享授权)'
  AFTER can_view_all;
