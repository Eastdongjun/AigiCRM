-- V2026.09.24.3 从未开票提醒建客宽限期配置(裁决 2026-09-24 GPT清单P1-018 带默认值)
-- 键 invoice.never.grace.days: 客户建客(created_at)超过 N 天仍未开票, 凌晨任务才创建 invoice_never 提醒,
-- 刚建的客户不再当晚被催首次开票;设为 0 = 无宽限(保持原行为, 从未开票即提醒)。幂等: 已存在则不重复插入。

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'invoice.never.grace.days', '30', '从未开票提醒建客宽限天数(建客超N天未开票才提醒,0=无宽限)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'invoice.never.grace.days');
