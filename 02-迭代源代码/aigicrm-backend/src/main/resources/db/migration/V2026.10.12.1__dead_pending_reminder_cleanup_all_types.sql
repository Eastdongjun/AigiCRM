-- V2026.10.12.1 全类型死 pending 提醒一次性清偿
-- 背景: 全量体检(2026-10-12)发现客户删除/归档/入海/捞取四个生命周期钩子只清分享类提醒,
--       expiring/invoice_never/invoice_overdue 类死 pending 无常驻清理(V2026.09.23.8 仅一次性
--       清过 expiring, V2026.10.09.1 仅清过 share 三类) → 积压重新出现, 列表再现 customerName
--       「—」死行。2026-10-12 生产摸底: 59 条(54 invoice_never + 2 invoice_overdue + 3 expiring)。
-- 口径: 客户不存在(硬删)/已软删(deleted=1)/已入海(sea)/已归档(archived)的全部类型 pending
--       一并删除(死提醒, 永无合法处理路径; 与 V2026.09.23.8 第 2 段同判据, 放开类型限制)。
-- 代码侧已同步落 INV-05 全量口径清理钩子(CustomerService.cleanPendingReminders 四路径挂载),
--       本迁移清历史积压, 幂等可重放。
-- 按码定位零硬编码 id; tenant_id 由应用层拦截器管理, root 直跑不经过拦截器。

DELETE r FROM biz_reminder r
LEFT JOIN biz_customer c ON c.id = r.customer_id
WHERE r.status = 'pending'
  AND (c.id IS NULL OR c.deleted = 1 OR c.status IN ('sea', 'archived'));

-- 验证锚点(人工复核用, 不影响执行)
SELECT reminder_type, status, COUNT(*) AS remaining
FROM biz_reminder
GROUP BY reminder_type, status;
