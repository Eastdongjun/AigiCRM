-- V2026.09.24.2 公海捞取审批锁超时配置(裁决 2026-09-24: 超时时长做成系统设置,管理员可手动改)
-- 键 sea.claim.lock.days: 捞取审批锁(pending_approval)超过 N 天未审 → 凌晨任务自动释放回 available
-- 并同步拒绝对应 claim 审批;设为 0 = 不启用自动释放。幂等: 已存在则不重复插入。

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'sea.claim.lock.days', '3', '公海捞取审批锁超时自动释放天数(0=不启用)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sea.claim.lock.days');
