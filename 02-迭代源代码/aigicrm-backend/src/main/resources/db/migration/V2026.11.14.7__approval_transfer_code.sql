-- V2026.11.14.7 审批节点转交独立码(全系统 admin 收口第 2 批: 审批域)
--
-- 原来 transferNode 只有 isAdmin() 守卫；admin 取消码后仍可转交。
-- 改为 approval:transfer，默认不授予任何角色(含 admin 最小集)，由权限中心显式勾选。
-- 挂 approval:view 父行；幂等 NOT EXISTS。

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT m.id, '审批节点转交', 3, 'approval:transfer', 96, 1, 1
FROM sys_menu m
WHERE m.permission = 'approval:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'approval:transfer')
LIMIT 1;
