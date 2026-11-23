-- 存量安装（已有用户）默认视为已完成首启向导，避免升级后把老客户全体踢到 /onboarding。
-- 全新安装此时 sys_user 为空（admin 由安装器在迁移后 seed），不写标记，首登仍走向导。
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'onboarding.completed', 'true', '存量安装默认已完成首启向导'
WHERE EXISTS (SELECT 1 FROM sys_user)
  AND NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'onboarding.completed');
