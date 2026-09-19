-- 数据库外键加固（2026-08-08，审查发现：零外键是 IDOR 类 bug 的土壤）
-- 前置条件：孤儿数据审计全 0（2026-08-08 生产实测）：
--   biz_invoice/biz_payment/biz_customer_share/biz_reminder/biz_incentive_record 的 customer_id、biz_customer.owner_id 均无孤儿引用
-- 设计说明：
--   1. 全系统软删除（deleted 逻辑删除，无物理删除）→ FK 默认 RESTRICT 不影响软删流程
--   2. FK 是应用层归属校验之外的最后一道防线：DB 层阻止孤儿记录/跨表错误引用
-- 回滚: ALTER TABLE <t> DROP FOREIGN KEY <fk>;
-- 执行: deploy-db.sh（自动备份后带库名执行）

USE aigicrm;

ALTER TABLE biz_invoice ADD CONSTRAINT fk_biz_invoice_customer FOREIGN KEY (customer_id) REFERENCES biz_customer(id);
ALTER TABLE biz_payment ADD CONSTRAINT fk_biz_payment_customer FOREIGN KEY (customer_id) REFERENCES biz_customer(id);
ALTER TABLE biz_customer_share ADD CONSTRAINT fk_biz_customer_share_customer FOREIGN KEY (customer_id) REFERENCES biz_customer(id);
ALTER TABLE biz_reminder ADD CONSTRAINT fk_biz_reminder_customer FOREIGN KEY (customer_id) REFERENCES biz_customer(id);
ALTER TABLE biz_incentive_record ADD CONSTRAINT fk_biz_incentive_record_customer FOREIGN KEY (customer_id) REFERENCES biz_customer(id);
ALTER TABLE biz_customer ADD CONSTRAINT fk_biz_customer_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id);
