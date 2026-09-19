-- ============================================================
-- V2026.09.19.10__fix_sidebar_orphan_and_missing_parent.sql
-- 修复权限矩阵「勾选了但侧边栏不出」的两类菜单断链(2026-09-19 活体验证发现)
-- ============================================================
-- 缺陷 1:菜单 9061(积分排行, V2026.09.11.6 种子, parent=9020)是孤儿——
--   9020(考核与分析)整块种子后续已被清理合并到 menu 20, 9061 残留且与
--   menu 61(积分排行, parent=20, 同路径 /score/ranking 同权限 score:view)重复。
--   AuthService.dedupeByPath 按返回顺序保留首条(61/9061 同 sort_order=4, 顺序不稳定):
--   保留到 9061 时其父级不存在 → buildMenuTree 挂不上 → 积分排行从侧边栏消失。
--   实测:同一份配置下 admin/行政侧边栏丢失「积分排行」, 总监/销售员正常。
--   修复:删除 9061 及其角色绑定(四个角色均已绑定正主 61, 无功能损失)。
--
-- 缺陷 2:行政(role 9)勾选了 119(系统设置页)但未勾选其父分组 34(系统设置组)——
--   buildMenuTree 从根递归, 父不在集合 → 子成孤儿被丢弃 → 行政侧边栏没有「系统设置」入口,
--   但权限码 system:config:view 经 119 已生效(能直连页面, "有权限没入口")。
--   修复:补绑 34。34 自身携带的权限码 system:config:view 行政已持有(经 119), 零扩权。
--
-- 幂等;第 3 节 perm_version 全量递增触发 Redis 权限缓存自愈(教训同 V2026.09.19.9)。
--
-- 回滚(需人工确认后单独执行):
--   DELETE FROM sys_role_menu WHERE role_id=9 AND menu_id=34;
--   INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,path,component,permission,icon,sort_order,visible,status,created_at)
--     VALUES (9061,9020,'积分排行',2,'/score/ranking','score/ranking','score:ranking','Medal',4,1,1,NOW());
--   (9061 的角色绑定不恢复即恢复至本迁移前"侧边栏不稳定"状态;如需完整回滚按 V2026.09.11.6 重放)
-- ============================================================

-- ---------- 1) 清理孤儿重复菜单 9061(仅当其父级仍不存在时执行, 防御性条件) ----------
DELETE rm FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE m.`id` = 9061
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_menu` p WHERE p.`id` = m.`parent_id`) t);

DELETE FROM `sys_menu`
WHERE `id` = 9061
  AND `parent_id` <> 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_menu` p WHERE p.`id` = `sys_menu`.`parent_id`) t);

-- ---------- 2) 行政补绑父分组 34, 使已勾选的系统设置页(119)出现在侧边栏 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, 34
FROM `sys_role` r
WHERE r.`id` = 9
  AND EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = 9 AND x.`menu_id` = 119)
  AND EXISTS (SELECT 1 FROM `sys_menu` m WHERE m.`id` = 34)
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = 9 AND x.`menu_id` = 34);

-- ---------- 3) 权限缓存自愈 ----------
UPDATE `sys_user` SET `perm_version` = `perm_version` + 1;
