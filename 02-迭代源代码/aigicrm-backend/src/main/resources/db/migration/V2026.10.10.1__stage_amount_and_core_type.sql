-- V2026.10.10.1 开票回款阶段总额制 + 核心客户三态判定(真核心/争核心)
-- 裁决 2026-10-10: ①核心判定纯金额门槛(五项评分不再参与) ②逐笔录入入口下线改只读历史,
-- 保护期由阶段末日期接力 ③年度口径=最近有数据年(全年行有数据时优先, 否则上半年+下半年合计)。
-- 口径(与 CustomerService.recalculateCoreStatus 同源, 改动须两处同步):
--   有数据行 = 阶段行开票额或回款额任一 > 0;
--   基准年   = 该客户有数据行的最大 period_year;
--   年度金额 = 基准年 FY 行有数据 → 取 FY 行两值; 否则 H1+H2 合计;
--   core_type = 基准年开票>=100 且 回款>=100 → 'core'(真核心);
--               有数据但未双达标         → 'quasi'(争核心);
--               无任何有数据行           → ''(非核心)。
-- 幂等: 发票回填 NOT EXISTS 守卫零新增; 回款 ON DUPLICATE KEY 直接赋值(重放值不变);
--       core_type 全量重算覆盖写, 重放结果一致。按数据条件定位, 零硬编码 id。

-- 1) 阶段总额表: 一客户一周期一行(开票额与回款额同行), 编辑制无删除状态机
CREATE TABLE IF NOT EXISTS `biz_customer_stage_amount` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `customer_id` bigint NOT NULL,
  `period_year` int NOT NULL COMMENT '周期年份, 如 2026',
  `period_type` varchar(4) NOT NULL COMMENT '周期类型: H1=上半年 H2=下半年 FY=全年',
  `invoice_amount` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '阶段内开票额(万元)',
  `payment_amount` decimal(14,2) NOT NULL DEFAULT '0.00' COMMENT '阶段性回款额(万元)',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_period` (`customer_id`, `period_year`, `period_type`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='客户开票回款阶段总额(半年/全年录入制, 2026-10-10 裁决)';

-- 2) 指标表加三态列; 旧 is_core 保留并同步(core→1 其余→0)兼容历史引用
ALTER TABLE `biz_customer_indicator`
  ADD COLUMN `core_type` varchar(8) NOT NULL DEFAULT '' COMMENT 'core=真核心 quasi=争核心 空=非核心(2026-10-10 纯金额判定)' AFTER `is_core`;

-- 3) 存量回填①: 历史真实发票按年汇总 → FY 行
--    (LEGACY 占位票 amount 为 NULL 不生成行, 其驱动的 lastInvoiceDate/到期日不受影响)
INSERT INTO `biz_customer_stage_amount`
  (`tenant_id`, `customer_id`, `period_year`, `period_type`, `invoice_amount`, `payment_amount`, `created_at`)
SELECT t.tenant_id, t.customer_id, t.y, 'FY', t.amt, 0, NOW()
FROM (
  SELECT i.`tenant_id`, i.`customer_id`, YEAR(i.`invoice_date`) AS y, SUM(i.`amount`) AS amt
  FROM `biz_invoice` i
  WHERE i.`deleted` = 0 AND i.`status` <> 'voided'
    AND i.`amount` IS NOT NULL AND i.`amount` > 0
    AND i.`invoice_date` IS NOT NULL
  GROUP BY i.`tenant_id`, i.`customer_id`, YEAR(i.`invoice_date`)
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `biz_customer_stage_amount` s
  WHERE s.`customer_id` = t.customer_id AND s.`period_year` = t.y AND s.`period_type` = 'FY'
);

-- 4) 存量回填②: 历史回款按年汇总, 合入同客户同年 FY 行(无则新建, 赋值覆盖保证重放幂等)
INSERT INTO `biz_customer_stage_amount`
  (`tenant_id`, `customer_id`, `period_year`, `period_type`, `invoice_amount`, `payment_amount`, `created_at`)
SELECT t.tenant_id, t.customer_id, t.y, 'FY', 0, t.amt, NOW()
FROM (
  SELECT p.`tenant_id`, p.`customer_id`, YEAR(p.`payment_date`) AS y, SUM(p.`payment_amount`) AS amt
  FROM `biz_payment` p
  WHERE p.`deleted` = 0 AND p.`status` <> 'voided'
    AND p.`payment_amount` IS NOT NULL AND p.`payment_amount` > 0
    AND p.`payment_date` IS NOT NULL
  GROUP BY p.`tenant_id`, p.`customer_id`, YEAR(p.`payment_date`)
) t
ON DUPLICATE KEY UPDATE `payment_amount` = VALUES(`payment_amount`);

-- 5) core_type 全量回填(纯金额三态; 无指标行客户由运行时 recalculate 自动建行)
UPDATE `biz_customer_indicator` ind
LEFT JOIN (
  SELECT b.customer_id,
         CASE WHEN fy.id IS NOT NULL THEN fy.invoice_amount
              ELSE IFNULL(h1.invoice_amount, 0) + IFNULL(h2.invoice_amount, 0) END AS inv_amt,
         CASE WHEN fy.id IS NOT NULL THEN fy.payment_amount
              ELSE IFNULL(h1.payment_amount, 0) + IFNULL(h2.payment_amount, 0) END AS pay_amt
  FROM (
    SELECT s.customer_id, MAX(s.period_year) AS y
    FROM `biz_customer_stage_amount` s
    WHERE s.`deleted` = 0 AND (s.`invoice_amount` > 0 OR s.`payment_amount` > 0)
    GROUP BY s.customer_id
  ) b
  LEFT JOIN `biz_customer_stage_amount` fy
    ON fy.customer_id = b.customer_id AND fy.period_year = b.y AND fy.period_type = 'FY' AND fy.`deleted` = 0
       AND (fy.invoice_amount > 0 OR fy.payment_amount > 0)
  LEFT JOIN `biz_customer_stage_amount` h1
    ON h1.customer_id = b.customer_id AND h1.period_year = b.y AND h1.period_type = 'H1' AND h1.`deleted` = 0
  LEFT JOIN `biz_customer_stage_amount` h2
    ON h2.customer_id = b.customer_id AND h2.period_year = b.y AND h2.period_type = 'H2' AND h2.`deleted` = 0
) a ON a.customer_id = ind.customer_id
SET ind.`core_type` = CASE
      WHEN a.customer_id IS NULL THEN ''
      WHEN a.inv_amt >= 100 AND a.pay_amt >= 100 THEN 'core'
      ELSE 'quasi' END,
    ind.`is_core` = CASE
      WHEN a.customer_id IS NOT NULL AND a.inv_amt >= 100 AND a.pay_amt >= 100 THEN 1
      ELSE 0 END;

-- 验证锚点(人工复核用): 真核心/争核心分布 + 与新口径逐客户抽查入口
SELECT ind.`core_type`, COUNT(*) AS cnt
FROM `biz_customer_indicator` ind
GROUP BY ind.`core_type`;
