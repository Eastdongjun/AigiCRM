-- V2026.11.14.8 批量生成到期提醒独立码(全系统 admin 收口第 2 批: 提醒域)
-- 原 ReminderController.ensureExpiring 只有 reminder:view + isAdmin 双门；admin 取消码后仍可生成。
-- 改为 reminder:ensure，默认不授予任何角色(含 admin 最小集)，由权限中心显式勾选。
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT m.id, '批量生成到期提醒', 3, 'reminder:ensure', 96, 1, 1
FROM sys_menu m
WHERE m.permission = 'reminder:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'reminder:ensure')
LIMIT 1;
