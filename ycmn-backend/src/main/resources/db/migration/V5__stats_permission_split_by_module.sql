-- V5: 公司级统计可见 按模块拆分(M5 重构 2026-08-10)
-- 背景: 原单 can_view_company 一把抓(统计/激励/积分/指标全可见), 无法按模块区分。
-- 拆分为: can_view_indicator(核心客户指标-统计总览) / can_view_incentive(激励统计) / can_view_score(学分排行)
-- 迁移: 原 can_view_company=1 的用户 → 三个模块全开(向后兼容)
ALTER TABLE biz_stats_permission
    ADD COLUMN can_view_indicator TINYINT DEFAULT 0 COMMENT '1=可见核心客户指标-统计总览公司级',
    ADD COLUMN can_view_incentive  TINYINT DEFAULT 0 COMMENT '1=可见激励统计公司级',
    ADD COLUMN can_view_score      TINYINT DEFAULT 0 COMMENT '1=可见学分排行公司级';

UPDATE biz_stats_permission
SET can_view_indicator = 1, can_view_incentive = 1, can_view_score = 1
WHERE can_view_company = 1;
