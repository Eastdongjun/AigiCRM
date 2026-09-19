-- 【PR-18 2026-09-19】台账状态机:录入—待核对—确认—更正/作废—重算闭环
-- 总册 PR-18:"区分人工暂录、财务确认、外部系统同步,标注来源、时间和状态;
-- 同一来源重复导入不重复累计;已确认更正保留原值、原因及操作者"

-- 开票表:状态+来源+更正留痕
ALTER TABLE biz_invoice
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'confirmed' COMMENT '状态:pending=待核对/confirmed=已确认/voided=已作废(存量默认已确认)',
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '来源:manual=手动录入/import=批量导入/sync=外部同步',
    ADD COLUMN confirmed_by BIGINT NULL COMMENT '确认人',
    ADD COLUMN confirmed_at DATETIME NULL COMMENT '确认时间',
    ADD COLUMN voided_reason VARCHAR(500) NULL COMMENT '作废原因';

-- 回款表:同口径
ALTER TABLE biz_payment
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'confirmed' COMMENT '状态:pending/confirmed/voided',
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '来源:manual/import/sync',
    ADD COLUMN confirmed_by BIGINT NULL COMMENT '确认人',
    ADD COLUMN confirmed_at DATETIME NULL COMMENT '确认时间',
    ADD COLUMN voided_reason VARCHAR(500) NULL COMMENT '作废原因';
