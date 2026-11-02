-- ============================================================
-- 保护期口径批(2026-11-02 用户裁决 A/B/C):
--   A. 一年=365天(原360): protection/extend/宽限配置 360→365
--   B. 捞取后保护期=180天: biz_customer 新增 claim_date(捞取日期);
--      到期公式改"三线取最晚": 建档+365 / 开票+365 / 捞取+180
--   C. adjust_date 语义改造: "调整日"(基准+180,纯展示无人消费)→"捞取到期日"
--      (捞取日+180,仅捞取客户有值,其余置NULL)
--
-- 存量重算口径(与 CustomerService.calculateExpireDates 同源):
--   有捞取线: expire = MAX(捞取日+180, 最近开票日+365) + 累计已批延期
--     —— 建档线不参与: 存量捞取户的注册日已被旧捞取逻辑覆写为捞取日(不可信);
--        新流程下捞取线(claim+180)数学上恒压建档线(register+365), 忽略无损
--   无捞取线: expire = MAX(注册日+365, 最近开票日+365) + 累计已批延期
--   基准全空的客户保持现状不动(与代码口径一致)
--
-- 生产实证(2026-11-02 数数): 325 在册客户, 38 户有捞取史;
--   新口径(捞取日+180)下 0 户立即到期、0 户30天内临期 —— 无破坏性回收。
-- ============================================================

ALTER TABLE `biz_customer`
  ADD COLUMN `claim_date` DATE NULL COMMENT '捞取日期(最近一次公海捞取成功日,从未捞取为NULL,多次捞取取最近)' AFTER `transfer_date`;

UPDATE `sys_config` SET `config_value` = '365'
WHERE `config_key` = 'customer.protection.days' AND `config_value` = '360';

UPDATE `sys_config` SET `config_value` = '365'
WHERE `config_key` = 'customer.extend.days' AND `config_value` = '360';

UPDATE `sys_config` SET `config_value` = '365'
WHERE `config_key` = 'invoice.never.grace.days' AND `config_value` = '360';

-- 回填捞取日期(从公海池 claimed 行取最近一次) + 全量重算到期日 + adjust_date 改捞取到期日
UPDATE `biz_customer` c
LEFT JOIN (
    SELECT `customer_id`, MAX(DATE(`claim_date`)) AS `cd`
    FROM `biz_sea_pool`
    WHERE `status` = 'claimed'
    GROUP BY `customer_id`
) p ON p.`customer_id` = c.`id`
SET
    c.`claim_date`  = p.`cd`,
    c.`adjust_date` = CASE WHEN p.`cd` IS NOT NULL
                           THEN DATE_ADD(p.`cd`, INTERVAL 180 DAY)
                           ELSE NULL END,
    c.`expire_date` = CASE
        WHEN p.`cd` IS NOT NULL THEN
            DATE_ADD(
                GREATEST(
                    DATE_ADD(p.`cd`, INTERVAL 180 DAY),
                    IFNULL(DATE_ADD(c.`last_invoice_date`, INTERVAL 365 DAY), DATE('1000-01-01'))
                ),
                INTERVAL IFNULL(c.`approved_extend_days`, 0) DAY)
        WHEN c.`register_date` IS NOT NULL OR c.`last_invoice_date` IS NOT NULL THEN
            DATE_ADD(
                GREATEST(
                    IFNULL(DATE_ADD(c.`register_date`, INTERVAL 365 DAY), DATE('1000-01-01')),
                    IFNULL(DATE_ADD(c.`last_invoice_date`, INTERVAL 365 DAY), DATE('1000-01-01'))
                ),
                INTERVAL IFNULL(c.`approved_extend_days`, 0) DAY)
        ELSE c.`expire_date`
    END
WHERE c.`deleted` = 0;
