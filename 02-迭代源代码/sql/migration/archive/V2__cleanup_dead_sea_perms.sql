-- V2: 清理公海模块死码权限(sea:export / sea:home / sea:pool:edit)
-- 依据 2026-08-10 反向漂移核对(权限字典 ↔ 后端端点 ↔ 前端引用): 三码均 DEAD。
--   sea:export    (menu 112) 08-05 迁移登记"公海导出", 从未落地导出功能——后端无端点、前端无按钮
--   sea:home      (menu 117) 生产遗留幽灵菜单("公海主页", type=2, path=/sea)——
--                         与 menu 17"公海管理"(path=/sea, sea:view)重复, 造成前端双"公海"菜单
--   sea:pool:edit (menu  53) 已被池级编辑权限(SeaConfigService.PERM_EDIT, biz_sea_config.edit_dept_ids)替代, 角色码失效
-- 保留在用码: sea:view(17) / sea:claim(44) / sea:pool:delete(54, 测试白名单) / sea:import(113)
-- 定位策略: 按权限码而非硬编码 menu_id —— 防 id 漂移、幂等(目标码不存在时无操作)
-- 执行: 经 scripts/deploy-db.sh(Flyway docker 镜像), 执行前已自动备份

-- 1. 先解除角色绑定(生产绑定: 53→role 1/10/11; 112→role 1/9/10; 117→role 1/9/10/11)
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.permission IN ('sea:export', 'sea:home', 'sea:pool:edit');

-- 2. 删除菜单行
DELETE FROM sys_menu
WHERE permission IN ('sea:export', 'sea:home', 'sea:pool:edit');

-- ==================== 回滚 SQL(需要时手动执行, 逐段运行) ====================
-- 1. 恢复菜单行(按权限码定位+幂等, id 走自增, 不硬编码原 id; 生产原值: sea:export=112, sea:home=117, sea:pool:edit=53)
-- INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible)
-- SELECT 17, '公海编辑', 3, '', '',        'sea:pool:edit', '',    2, 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='sea:pool:edit');
-- INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible)
-- SELECT 17, '公海导出', 3, '', NULL,      'sea:export',    NULL, 98, 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='sea:export');
-- INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible)
-- SELECT 17, '公海主页', 2, '/sea', 'sea/index', 'sea:home', 'Ship', 1, 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='sea:home');
--
-- 2. 恢复角色绑定(依赖 uk_role_menu 唯一键, 幂等; 按权限码+角色精确匹配原绑定: 53→1/10/11, 112→1/9/10, 117→1/9/10/11)
-- INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
-- SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
-- WHERE (m.permission='sea:pool:edit' AND r.id IN (1,10,11))
--    OR (m.permission='sea:export'    AND r.id IN (1,9,10))
--    OR (m.permission='sea:home'      AND r.id IN (1,9,10,11));
-- ==================================================================
