-- ============================================================
-- 提醒口径修正(2026-11-02 用户报障: 新客户在提醒列表里被催"从未开票"):
-- 1) invoice.never.grace.days 30→360: 用户口径=新客户按注册日有一年(360天)保护期,
--    保护期内不催首次开票。旧默认 30 天+基准 created_at(入库时间), 生产实证
--    7月建记录/10月导入才填注册日的客户, 注册 22 天即被催(柳林兴无煤矿等 27 条)。
--    代码侧(CustomerExpireTask)同批改为: 基准 register_date(NULL 回退 created_at)+默认 360。
--    此处把生产已存在的显式配置 30 同步为 360(锚定旧值幂等; 仍可在系统设置再调)。
-- 2) 关闭误生成的 invoice_never 提醒(保护期内新客户): processed 留痕不物理删
--    (与"发票可删留痕"同一审计口径), 提醒工作台/角标按 pending 过滤即自净。
-- 3) 回收悬挂 expiring 提醒: 到期日被推远(延期批准/重新开票刷新保护期)后,
--    窗口期生成的临期提醒不再关闭、一直 pending(生产实证: 张家峁 expire 已推至
--    2027-11, 10-23 的 30 天临期提醒仍挂)。任务代码已加每晚自愈回收, 此处清存量。
--    预警窗口取 customer.warning.days 默认 30(生产未显式配置该键)。
-- 幂等: 均锚定 pending 状态/旧配置值, 重跑无二次影响。
-- ============================================================

UPDATE `sys_config`
   SET `config_value` = '360', `updated_at` = NOW()
 WHERE `config_key` = 'invoice.never.grace.days' AND `config_value` = '30';

-- 保护期内(注册未满 360 天; 注册日为空回退入库时间)新客户被误催的从未开票提醒
UPDATE `biz_reminder` r
  JOIN `biz_customer` c ON c.`id` = r.`customer_id`
   SET r.`status` = 'processed', r.`processed_at` = NOW()
 WHERE r.`status` = 'pending' AND r.`reminder_type` = 'invoice_never'
   AND c.`deleted` = 0
   AND (c.`register_date` > DATE_SUB(CURDATE(), INTERVAL 360 DAY)
        OR (c.`register_date` IS NULL AND c.`created_at` > DATE_SUB(NOW(), INTERVAL 360 DAY)));

-- 悬挂的临期提醒: 到期日已推远至预警窗口外 / 到期日缺失 / 客户已删或不存在
UPDATE `biz_reminder` r
   LEFT JOIN `biz_customer` c ON c.`id` = r.`customer_id`
   SET r.`status` = 'processed', r.`processed_at` = NOW()
 WHERE r.`status` = 'pending' AND r.`reminder_type` = 'expiring'
   AND (c.`id` IS NULL OR c.`deleted` = 1 OR c.`expire_date` IS NULL
        OR c.`expire_date` > DATE_ADD(CURDATE(), INTERVAL 30 DAY));
