-- 存量"从未开票"提醒迁移（2026-08-08, commit 9a92e9c 配套）
-- 背景: 旧 CustomerExpireTask 把 last_invoice_date 为 NULL 的客户并入开票超期查询,
--       生成 reminder_type=invoice_overdue 且 overdue_days=9999 的误报提醒。
--       新规则(9a92e9c): 从未开票 → 独立类型 invoice_never / overdue_days=0。
--       本脚本把存量 9999 误报行迁移到新类型, 避免新旧并存。
-- 影响面: 仅 biz_reminder 表中 overdue_days>=9999 的 invoice_overdue 行(生产为从未开票客户,量级小)。
-- 幂等: WHERE 条件限定, 重复执行无副作用。
-- 执行: 建议先备份(biz_reminder), 再执行。
--   mysqldump --single-transaction ycmn biz_reminder > backup-biz_reminder-pre-invoice-cleanup.sql

UPDATE biz_reminder
SET reminder_type = 'invoice_never', overdue_days = 0
WHERE reminder_type = 'invoice_overdue' AND overdue_days >= 9999;
