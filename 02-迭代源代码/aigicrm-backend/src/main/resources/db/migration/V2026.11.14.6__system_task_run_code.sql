-- V2026.11.14.6 运维任务触发码(全系统 admin 收口第 1 批)
--
-- ScheduledTaskController 原来只有 isAdmin() 守卫、没有权限码；admin 取消码后仍可触发。
-- 改为动作码 system:task:run，默认不授予任何角色(包括 admin 最小集)，由权限中心显式勾选。
-- 挂 system:config:view 父行；admin 最小集只有 config:view/edit，可在权限中心自行勾选本码后使用。
-- 幂等: NOT EXISTS 自然键锚定。

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT m.id, '触发到期任务', 3, 'system:task:run', 97, 1, 1
FROM sys_menu m
WHERE m.permission = 'system:config:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'system:task:run')
LIMIT 1;
