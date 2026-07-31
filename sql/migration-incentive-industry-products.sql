-- ============================================================
-- 激励上传记录: 新增 行业 / 相关产品 两列
-- 关联客户弹窗顶部新增"选择行业"(字典 industry)+"相关产品"(多行文本)
-- 列表"档位"列改为"行业"+"相关产品"两列展示
-- ============================================================

ALTER TABLE biz_incentive_record
    ADD COLUMN industry VARCHAR(50) COMMENT '行业(取字典 industry)' AFTER period;
ALTER TABLE biz_incentive_record
    ADD COLUMN related_products VARCHAR(1000) COMMENT '相关产品(多行文本)' AFTER industry;
