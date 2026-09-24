-- V2026.09.23.8 到期提醒 pending 积压清理
-- 背景: ensure-expiring(页面触发,判据=今日没建过)与凌晨任务(判据=存在pending)双去重口径冲突,
--       页面每次触发为窗口内客户新造一条 pending,旧 pending 永不清理 → 生产积压 401 条。
--       代码侧已统一判据(存在 pending 或今日已处理即不建),本迁移清历史积压,幂等可重放。
-- 口径:
--   1) 每客户 expiring 类型只保留最新一条 pending,更早的重复 pending 删除;
--   2) 客户已不存在/已删/已入海/已归档的 expiring pending 一并删除(死提醒,永无合法处理路径)。
-- 按码定位零硬编码 id;tenant_id 由应用层拦截器管理,root 直跑不经过拦截器。

-- 1. 删除每客户更早的重复 pending(保留最新一条)
DELETE r FROM biz_reminder r
JOIN (
    SELECT customer_id, MAX(id) AS keep_id
    FROM biz_reminder
    WHERE reminder_type = 'expiring' AND status = 'pending'
    GROUP BY customer_id
) k ON r.customer_id = k.customer_id
WHERE r.reminder_type = 'expiring'
  AND r.status = 'pending'
  AND r.id < k.keep_id;

-- 2. 清理客户侧已失效的死 pending(客户不存在/已删/入海/归档)
DELETE r FROM biz_reminder r
LEFT JOIN biz_customer c ON c.id = r.customer_id
WHERE r.reminder_type = 'expiring'
  AND r.status = 'pending'
  AND (c.id IS NULL OR c.deleted = 1 OR c.status IN ('sea', 'archived'));

-- 验证锚点(人工复核用,不影响执行)
SELECT reminder_type, status, COUNT(*) AS remaining
FROM biz_reminder
GROUP BY reminder_type, status;
