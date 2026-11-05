-- ============================================================
-- V2026.11.05.10__restore_file_edit_code.sql
-- 【2026-11-05 用户翻案"那么保留吧 恢复"】恢复替换文件功能的权限面:
--   前序 V2026.11.05.9 已在测试/生产双库删除 file:edit 节点与绑定(且 flyway 已落账不可撤跑),
--   本迁移正向还原——功能本体(端点/服务/PC 入口/测试)已由 revert 1f1ee0dc 还原。
-- 还原形态: 节点按 V26 原形态(parent=持 file:view 的节点, '文件编辑', type3, sort 92, 启用可见);
--   绑定按 V26 原来源规则重绑(现持 file:view 的角色自动重获——与 2026-08-16 拆码时的初始生效面一致;
--   期间若有角色被单独手工撤过绑定, 如需收窄请权限中心按角色撤, 不影响本还原的正确性基线)。
-- 幂等: NOT EXISTS 守卫; 末尾 perm_version 全员 +1(直改库缓存自愈)。
-- 回滚(如再次裁决删除): 直接复制 V2026.11.05.9 的三段 DELETE 即可。
-- ============================================================

-- 1) 重建权限节点(type3 按钮行, V26 形态)
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '文件编辑', 3, 'file:edit', 92, 1, 1
FROM sys_menu
WHERE permission = 'file:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:edit')
LIMIT 1;

-- 2) 重绑角色(镜像 V26 绑定来源: 现持 file:view 的角色)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.id
FROM sys_role_menu rm
JOIN sys_menu view_menu ON rm.menu_id = view_menu.id AND view_menu.permission = 'file:view'
JOIN sys_menu m ON m.permission = 'file:edit'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id
);

-- 3) 权限缓存自愈(全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
