-- ============================================================
-- V2026.09.11.4__register_orphan_customer_ranking_permission.sql
-- 为孤儿权限码 ranking:customer:view 补登记菜单行，使其可被管理员配置
--
-- ============================================================
-- 背景(2026-09-11 复核):
--
--   该权限码被两端真实引用，但**任何字典/迁移/种子中都不存在**:
--     · aigicrm-backend/.../customer/controller/CustomerController.java:82
--         @PreAuthorize("hasAuthority('score:view')
--                        or hasAuthority('ranking:customer:view')
--                        or hasAuthority('statistics:view')")
--     · aigicrm-miniprogram/pages/ranking/index.js:21
--         hasPermission('score:view') || hasPermission('ranking:customer:view')
--           || hasPermission('statistics:view')
--
--   生产派生字典 docs/permission/permission-dict.md(66 码)中命中数 = 0;
--   生产只读 dump 的 sys_menu 中同样无此行。
--
-- ============================================================
-- 为什么是"补登记"而不是"删代码分支":
--
--   权限由管理员在角色管理页配置, 而该页的菜单树来自
--   SysMenuService.tree() —— 它带 `.eq(SysMenu::getStatus, 1)`
--   (SysMenuService.java:24-26)。
--   ⇒ **sys_menu 中不存在的权限码，管理员看不到、也勾不上**，
--     即"不可配置"。这与本项目"权限可配置、由管理员配置"的设计相悖。
--   补登记后, 管理员即可单独授予"查看客户排行"的权利,
--   而不必连带给 score:view / statistics:view。
--
-- ============================================================
-- 本迁移做什么(幂等):
--   插入 1 行 menu_type=3(按钮级权限点)的 sys_menu 行:
--     parent_id = 20(考核与分析分组, 与 /ranking/customer 页面同域)
--     menu_name = '客户排行查看'
--     permission = 'ranking:customer:view'
--     path = NULL, menu_type = 3  ⇒ **不产生侧边栏菜单项**
--     visible = 1(在角色配置树中可见), status = 1(启用, 可被配置)
--
--   样式与生产既有按钮级权限行一致(如 id=83 积分管理 / score:manage,
--   parent_id=20, menu_type=3, path=NULL)。
--
-- ============================================================
-- 安全性论证(零回归):
--   该码在两处都位于 `or` 链中且当前恒假 ⇒ 启用它**不改变任何现有用户的
--   可见性**; 只有管理员显式勾选后才会生效。故本迁移无功能回归风险。
--
-- 幂等: INSERT ... WHERE NOT EXISTS(permission='ranking:customer:view')
-- 回滚: 见文件尾部。
-- ============================================================

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
                        `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 20, '客户排行查看', 3, NULL, NULL,
       'ranking:customer:view', NULL, 96, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'ranking:customer:view'
);

-- 若该行已存在但被停用, 置回启用(不新建第二行)
UPDATE `sys_menu` SET `status` = 1
WHERE `permission` = 'ranking:customer:view' AND `status` = 0;

-- ------------------------------------------------------------
-- 复核查询(手工执行):
--   SELECT id, parent_id, menu_name, menu_type, permission, visible, status
--   FROM sys_menu WHERE permission = 'ranking:customer:view';
--   预期: 恰 1 行, menu_type=3, visible=1, status=1
--
-- 校验管理员可见性(等价于 SysMenuService.tree() 的过滤):
--   SELECT COUNT(*) FROM sys_menu
--   WHERE permission = 'ranking:customer:view' AND status = 1;
--   预期: 1
-- ------------------------------------------------------------

-- ==================== 回滚(按需手工执行) ====================
-- 执行前先备份:
--   mysqldump --single-transaction <DB> sys_menu sys_role_menu > menu_backup_$(date +%F).sql
--
-- 回滚 SQL:
--   DELETE rm FROM `sys_role_menu` rm
--     JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
--     WHERE m.`permission` = 'ranking:customer:view';
--   DELETE FROM `sys_menu` WHERE `permission` = 'ranking:customer:view';
-- ============================================================
