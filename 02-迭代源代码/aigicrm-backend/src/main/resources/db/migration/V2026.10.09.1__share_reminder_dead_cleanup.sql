-- V2026.10.09.1 分享提醒死 pending 清理
-- 背景: V2026.09.23.8 只清了 expiring 类死 pending, 分享类(share_to/share_by/share_owner)未覆盖。
--       客户删除/入海/捞取撤销分享后, pending 分享提醒悬挂留存——客户已被 @TableLogic 过滤,
--       批量回填查不到客户名, 提醒列表上表现为 customerName 整列「—」(测试环境 E2E 残留实证)。
-- 代码侧已挂钩清理: CustomerService.delete/archive/removeAllShares/removeShare 四路径。
-- 本迁移清历史积压, 幂等可重放; 按码定位零硬编码 id; processed 历史留痕不动。
-- tenant_id 由应用层拦截器管理, root 直跑不经过拦截器。

-- 清理客户侧已失效的死 pending(客户不存在/已删/已入海/已归档), 判据与 V2026.09.23.8 第 2 段一致
DELETE r FROM biz_reminder r
LEFT JOIN biz_customer c ON c.id = r.customer_id
WHERE r.reminder_type IN ('share_to', 'share_by', 'share_owner')
  AND r.status = 'pending'
  AND (c.id IS NULL OR c.deleted = 1 OR c.status IN ('sea', 'archived'));

-- 验证锚点(人工复核用,不影响执行)
SELECT reminder_type, status, COUNT(*) AS remaining
FROM biz_reminder
GROUP BY reminder_type, status;
