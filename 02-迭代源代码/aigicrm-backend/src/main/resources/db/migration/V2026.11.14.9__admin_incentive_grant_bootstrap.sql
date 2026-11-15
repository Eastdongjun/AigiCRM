-- V2026.11.14.9 admin 激励专项授权显式化(全系统 admin 收口: 激励域)
--
-- 背景: 激励判定已改为 biz_incentive_grant 表驱动；此前 admin 靠 isAdmin() 隐式全通。
-- 全系统收口后 admin 也必须持 grant。为避免 admin 迁移后激励模块突然全空，本迁移给
-- admin 角色用户显式写四开关全开；之后可在「系统设置 → 激励专项权限」逐人撤销。
-- 注意: 这是**显式 grant 行**，不是 isAdmin 兜底；撤销后立即失效。
-- 幂等: ON DUPLICATE KEY UPDATE；admin 用户来自 sys_user_role。

INSERT INTO biz_incentive_grant
  (user_id, can_view_shared, can_view_all, can_edit, can_delete, updated_by)
SELECT DISTINCT ur.user_id, 1, 1, 1, 1, NULL
FROM sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
WHERE r.role_code = 'admin'
ON DUPLICATE KEY UPDATE
  can_view_shared = 1,
  can_view_all = 1,
  can_edit = 1,
  can_delete = 1;
