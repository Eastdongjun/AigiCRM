-- ============================================================
-- V2026.09.23.1__restore_task_permission_on_legacy_id_collision.sql
-- 修复: legacy 库上 V2026.09.21.1 硬编码 id 与 auto_increment 错位, 误删 task 权限行
-- ============================================================
-- 背景(2026-09-23 生产实测):
--   V2026.09.21.1(menu hygiene dedup)按测试库实测 id 硬编码
--   `DELETE FROM sys_menu WHERE id IN (22,54,63,82,89,98,99,100,110,134,135)`。
--   测试库(空库全量重放): V1 基线先造出 134 联系人管理/135 财务管理, task 两行
--   由 V2026.09.19.8 拿到 id 136/137 ⇒ 删除正确。
--   生产库(legacy V23 起步): 没有联系人/财务两行垃圾, auto_increment 把
--   task:view/task:manage 发成 id 134/135 ⇒ 21.1 在生产删掉的是 task 权限行本身。
--   后果: 全库无人持有 task:view ⇒ TaskController 全端点(含工作台必调的
--   GET /api/task/statistics)对所有人 403, 工作台每次加载弹
--   "没有访问权限，请联系管理员授权"。与 V2026.09.19.9 当年修复的
--   "码登记了、没人持有"是同一失效面。
--
--   同源残留: legacy 库上联系人/财务孤儿行真实 id 是 132/133(非 134/135),
--   21.1 没删到 ⇒ 按 permission 码补删, 与测试库终态对齐。
--
-- 本迁移做什么(幂等; 全部按 permission 码操作, 零硬编码 id):
--   1) 重新登记 task:view / task:manage(终态对齐 V2026.09.19.8+19.9:
--      type=3, parent_id=0, sort 99, visible=1, status=1)
--   2) 角色绑定: role_code IN ('admin','0','06') — 与 19.9 同一语义
--   3) 补删 legacy 残留的 contact:manage / finance:manage 孤儿行及其绑定
--      (21.1 判定: 后端零引用、零绑定; 接口侧均有 customer:edit 等 OR 分支替代)
--   4) sys_user.perm_version +1 触发权限缓存自愈(照抄 19.9 第 3 节;
--      不递增则已登录用户权限集最长残留至 Redis TTL 过期)
--
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
--     WHERE m.permission IN ('task:view','task:manage');
--   DELETE FROM sys_menu WHERE permission IN ('task:view','task:manage');
--   注意: 回滚后任务模块恢复为全员 403。
-- ============================================================

-- ---------- 1) 重新登记 task 权限点(幂等) ----------
INSERT INTO `sys_menu` (`menu_name`, `menu_type`, `permission`, `sort_order`, `visible`, `status`)
SELECT '任务查看', 3, 'task:view', 99, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'task:view');

INSERT INTO `sys_menu` (`menu_name`, `menu_type`, `permission`, `sort_order`, `visible`, `status`)
SELECT '任务管理', 3, 'task:manage', 99, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'task:manage');

-- ---------- 2) 角色绑定(幂等, 与 V2026.09.19.9 同语义) ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
    JOIN `sys_menu` m ON m.`permission` IN ('task:view', 'task:manage')
WHERE r.`role_code` IN ('admin', '0', '06')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`
);

-- ---------- 3) 补删 legacy 残留孤儿(按码, 幂等) ----------
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `id` FROM `sys_menu` WHERE `permission` IN ('contact:manage', 'finance:manage'));
DELETE FROM `sys_menu` WHERE `permission` IN ('contact:manage', 'finance:manage');

-- ---------- 4) 权限缓存自愈(照抄 V2026.09.19.9 第 3 节) ----------
UPDATE `sys_user` SET `perm_version` = `perm_version` + 1;
