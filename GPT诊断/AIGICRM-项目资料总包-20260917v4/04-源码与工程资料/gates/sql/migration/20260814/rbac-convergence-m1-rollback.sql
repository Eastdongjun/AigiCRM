-- ============================================================
-- M1 回滚脚本（2026-08-14）
-- 撤销 M1 的权限码/角色/绑定变更。列与黑名单表保留（向后兼容，不删列避免数据丢失）。
-- 执行前再次备份 sys_user_role / sys_role_menu。
-- ============================================================

-- 1. 解除全员「激励-分享可见」绑定
DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code = 'incentive_view_shared');
-- 2. 解除权限码绑定
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE permission IN ('incentive:manage','incentive:view-all','incentive:view-shared','file:acl:manage'));
-- 3. 恢复销售员(11) 的 incentive:edit(62)/incentive:delete(63)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 11, id FROM sys_menu WHERE permission IN ('incentive:edit','incentive:delete')
ON DUPLICATE KEY UPDATE menu_id = menu_id;
-- 4. 删除载体角色
DELETE FROM sys_role WHERE role_code IN ('incentive_view_shared','incentive_view_all');
-- 5. 删除新权限码
DELETE FROM sys_menu WHERE permission IN ('incentive:manage','incentive:view-all','incentive:view-shared','file:acl:manage');
