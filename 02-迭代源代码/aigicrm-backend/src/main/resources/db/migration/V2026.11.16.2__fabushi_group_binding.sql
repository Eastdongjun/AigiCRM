-- ============================================================
-- V2026.11.16.2__fabushi_group_binding.sql
-- 【修复】V2026.11.16.1 只按权限码绑定了页面/按钮，未绑定「法布施」type=1 组节点；
-- /auth/info 菜单树按角色菜单绑定组装，导致 admin（及销售）看不到法布施组。
-- 本迁移给所有已持有任一 fabushi:* 权限的角色补绑组节点，幂等可重跑。
-- ============================================================
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, g.`id`
FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
JOIN `sys_menu` g ON g.`menu_name` = '法布施' AND g.`menu_type` = 1 AND COALESCE(g.`parent_id`, 0) = 0
WHERE m.`permission` LIKE 'fabushi:%'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = g.`id`
  );
