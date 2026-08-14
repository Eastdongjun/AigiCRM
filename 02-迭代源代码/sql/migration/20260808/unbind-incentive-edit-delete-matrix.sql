-- ============================================================
-- 激励矩阵码清理（2026-08-08 分享制）
-- 背景: 激励记录编辑/删除已改用户级守卫（admin ∨ biz_incentive_permission ∨ 本人），
--       矩阵码不再放行非本人改删。清理角色绑定：
--   - incentive:delete(63) 已完全无用途 → 从全部角色(1/9/10/11)解除
--   - incentive:edit(62) 仍管控激励类别管理(分类CRUD/积分重算) → 仅保留 admin(1)，解除 9/10/11
-- 幂等: DELETE 安全可重复执行
-- 执行: mysql --default-character-set=utf8mb4 ycmn < unbind-incentive-edit-delete-matrix.sql
-- ============================================================

-- 63 incentive:delete 无任何后端/前端引用，从全部角色解除
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.permission = 'incentive:delete' AND rm.role_id IN (1,9,10,11);

-- 62 incentive:edit 仅 admin 用于激励类别管理，解除非管理员绑定
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.permission = 'incentive:edit' AND rm.role_id IN (9,10,11);

-- ==================== 回滚 SQL（手动执行） ====================
-- INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
--   SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
--   WHERE m.permission IN ('incentive:edit','incentive:delete') AND r.id IN (1,9,10,11);
