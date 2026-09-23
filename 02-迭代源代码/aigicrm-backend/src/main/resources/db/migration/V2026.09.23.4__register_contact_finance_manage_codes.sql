-- ============================================================
-- V2026.09.23.4__register_contact_finance_manage_codes.sql
-- 【登记契约复绿 2026-09-23】登记代码在用但迁移链缺失的两个权限码
--
-- 背景:
--   CustomerController 联系人三端点(add/update/delete)与财务六端点(发票/回款
--   add/delete/confirm/void)使用 `customer:edit or contact:manage` /
--   `finance:manage or (customer:edit and finance:view)` 复合授权——
--   contact:manage / finance:manage 为职责分离设计(PR-06)的专职授权点。
--   V2026.09.23.1 在生产按"孤儿行(零绑定)"清理了 legacy 时代的这两行,
--   但迁移链从未登记过它们 → 登记契约(代码在用码必须在链中登记且末态启用)不成立。
--
-- 修复:
--   按码幂等登记(照抄 V2026.09.13.1 范式):type=3 / visible=1 / status=1,
--   parent 锚定 customer:view 节点(取不到兜底 11),零硬编码 id(21.1 教训)。
--   **不做任何角色绑定**——默认无人持有,授权路径=系统设置→权限矩阵按需勾选
--   (admin 天然旁路);零绑定是本设计的有意状态,非孤儿(代码在用即非孤儿)。
--
-- 幂等: WHERE NOT EXISTS 按码守卫,重复执行零变化。
-- 缓存: 仅登记不绑角色,权限缓存(perm:ctx)内容不变,无需失效;
--   首次给某角色勾选时权限矩阵保存即会 bump perm_version 自愈。
--
-- 回滚: DELETE FROM sys_menu WHERE menu_type=3 AND permission IN
--   ('contact:manage','finance:manage') AND id NOT IN
--   (SELECT menu_id FROM sys_role_menu);  -- 仅删未绑定行
-- ============================================================

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
                        `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'customer:view' ORDER BY `id` LIMIT 1), 11),
       '客户联系人管理', 3, NULL, NULL, 'contact:manage', NULL, 95, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'contact:manage');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
                        `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'customer:view' ORDER BY `id` LIMIT 1), 11),
       '客户财务管理', 3, NULL, NULL, 'finance:manage', NULL, 94, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'finance:manage');
