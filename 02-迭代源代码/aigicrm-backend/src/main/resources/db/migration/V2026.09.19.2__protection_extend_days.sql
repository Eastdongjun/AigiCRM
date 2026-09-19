-- 【议题一裁决 2026-09-19】保护期决策表:开票推导与已批延期拆分
-- 公式: expireDate = 开票推导到期日(随开票增删浮动) + approved_extend_days(只增不减)
-- 效果: 删票回算开票部分(纠正误录),已审批延期不收回(信任审批);延期与开票独立叠加。
-- 配置: customer.protection.days(360)/customer.adjust.days(180) 已在 sys_config 可管理;
--       本迁移补 customer.extend.days(延期一次天数,与保护期同配置口径)。

ALTER TABLE biz_customer ADD COLUMN approved_extend_days INT NOT NULL DEFAULT 0
    COMMENT '累计已审批延期天数(只增不减;expireDate=开票推导+本值)';

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'customer.extend.days', '360', '延期审批一次增加天数(与保护期口径同源,管理员可改)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'customer.extend.days');
