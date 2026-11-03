-- V2026.11.03.1 核心客户判定双门槛配置化(用户令 2026-11-03: 门槛做成管理员可改配置, 改后自动刷新核心标签, 不硬编码)
-- 三档口径(命名沿用现名):
--   真核心 core  = 年度开票、回款均 >= 真核心门槛(customer.core.major.wan, 默认 100 万)
--   争核心 quasi = 年度开票、回款均 >= 争核心门槛(customer.core.minor.wan, 默认 50 万), 未双达真核心门槛
--   非核心 ''    = 任一低于争核心门槛(含无阶段数据)
-- 金额口径不变: biz_customer_stage_amount 年度口径(最近有数据年, 全年行优先否则上下半年合计)。
-- customer. 前缀键由 SysConfigService.batchUpdate 自动记 sys_rule_version 审计。
-- 幂等: 配置行已存在不重复插入; 回填 UPDATE 可重放。无指标行的客户由运行时 recalculate 自动建行(沿 V2026.10.10.1 先例)。

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'customer.core.minor.wan', '50', '核心客户判定·争核心门槛(万元):年度开票与回款均≥该值为争核心'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'customer.core.minor.wan');

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'customer.core.major.wan', '100', '核心客户判定·真核心门槛(万元):年度开票与回款均≥该值为真核心'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'customer.core.major.wan');

-- 存量回填: 按双门槛三档刷新 core_type/is_core; 门槛从 sys_config 读值(SQL 不写死数字),
-- 年度聚合与 V2026.10.10.1 回填/运行时 computeCoreType 同口径。
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
CROSS JOIN (
  SELECT CAST(MAX(CASE WHEN config_key = 'customer.core.minor.wan' THEN config_value END) AS DECIMAL(14,2)) AS minor_wan,
         CAST(MAX(CASE WHEN config_key = 'customer.core.major.wan' THEN config_value END) AS DECIMAL(14,2)) AS major_wan
  FROM sys_config
  WHERE config_key IN ('customer.core.minor.wan', 'customer.core.major.wan')
) th
SET ind.`core_type` = CASE
      WHEN a.customer_id IS NULL THEN ''
      WHEN a.inv_amt >= th.major_wan AND a.pay_amt >= th.major_wan THEN 'core'
      WHEN a.inv_amt >= th.minor_wan AND a.pay_amt >= th.minor_wan THEN 'quasi'
      ELSE '' END,
    ind.`is_core` = CASE
      WHEN a.customer_id IS NOT NULL AND a.inv_amt >= th.major_wan AND a.pay_amt >= th.major_wan THEN 1
      ELSE 0 END;
