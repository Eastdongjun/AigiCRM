-- V2026.09.24.1 legacy 客户开票日回填(裁决 2026-09-24:最近开票日纯以开票记录推导)
-- 背景: 266 个有开票日的活跃客户中 258 个没有任何发票记录(日期为 legacy 批量导入的手工值,
--       全系统真实发票仅 24 张)。切"纯推导"前必须为这些客户回填一张历史导入票,
--       否则 lastInvoiceDate 被清空 → 保护期基准全部回落注册日 → 大量客户一夜过期。
-- 口径: 回填票 invoice_date=现 last_invoice_date, status='confirmed'(驱动保护期=既成事实,
--       且 confirmed 不可删仅可作废,与"保留开票记录"裁决自洽), invoice_type='历史导入' 留痕。
-- 幂等: NOT EXISTS 守卫,重放零新增;按客户数据条件定位,零硬编码 id。

INSERT INTO biz_invoice (tenant_id, customer_id, invoice_date, invoice_number, invoice_type, status, remark, created_at)
SELECT c.tenant_id, c.id, c.last_invoice_date,
       CONCAT('LEGACY-', c.id),
       '历史导入', 'confirmed',
       '系统迁移回填: 承接 legacy 批量导入的最近开票日(裁决 2026-09-24 开票日纯推导)',
       NOW()
FROM biz_customer c
WHERE c.deleted = 0
  AND c.last_invoice_date IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM biz_invoice i
      WHERE i.customer_id = c.id AND i.status <> 'voided'
  );

-- 验证锚点(人工复核用): 回填后"有开票日但无非作废票"应为 0
SELECT COUNT(*) AS lid_without_invoice FROM biz_customer c
WHERE c.deleted = 0 AND c.last_invoice_date IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM biz_invoice i WHERE i.customer_id = c.id AND i.status <> 'voided');
