-- ============================================================
-- V2026.09.13.1__register_missing_used_permission_codes.sql
-- 登记 4 个"代码在用、字典却没有"的权限码，使权限判定不再恒假
-- ============================================================
-- 背景（2026-09-13 测试环境实测）：
--
--   下列权限码被后端/前端真实引用，但 sys_menu 中**一行都不存在**，
--   而权限判定（@PreAuthorize / SecurityUtils.hasAuthority）依赖该行所属角色的绑定：
--
--     · incentive:read-stats
--         IncentiveController.java:167  @PreAuthorize("hasAuthority('incentive:read-stats')")
--         GET /api/incentive/read-stats —— 激励查询页(/incentive/search)与微信小程序均调用
--         ⇒ 码不存在 ⇒ 门禁恒假 ⇒ **全用户（含 admin）403**（用户实测"激励查询 没有权限"）
--     · contact:view
--         CustomerController.java:268  if (!hasAuthority("contact:view")) return Result.ok(List.of())
--         ⇒ 静默守卫 ⇒ 客户联系人列表**恒空**（不报错，看起来像"没数据"）
--     · finance:view
--         CustomerController.java:148 / :156 / :303 同类静默守卫 ⇒ 客户财务**恒空/恒 0**
--     · customer:date:edit
--         CustomerService.java:442  isAdmin() || hasAuthority("customer:date:edit")
--         ⇒ 非 admin 角色**一律不可修改客户日期**
--
--   为什么整条迁移链都没有这些行：
--     V25__add_finance_view_permission.sql 的登记语句用
--       `FROM sys_menu WHERE permission = 'customer:view' ... LIMIT 1`
--     作为 parent_id 来源；而 V1__baseline.sql 是**纯结构（0 条 INSERT）**，权限字典种子
--     直到 V2026.09.11.6 才补 ⇒ 在该 V25 执行时刻该 SELECT 无行，
--     `INSERT ... SELECT` 于是**静默插入 0 行**（与 V2026.09.11.5 注释自述的"JOIN 落空不报错"同类）。
--     customer:date:edit 则从来只有角色绑定（V29→V2026.09.11.5），从未登记菜单行。
--
-- 本迁移做什么（幂等；不扩大任何现有角色的可见性）：
--   1) 把 4 个码登记为 menu_type=3 的**按钮级权限点**：path=NULL ⇒ **不产生侧边栏菜单项**，
--      visible=1（在角色权限树可见）、status=1（启用、可被管理员勾选）。
--      parent_id 用 COALESCE 兜底，**不依赖运行期存在 customer:view/incentive:view 行**（本次缺陷的教训）。
--   2) 角色绑定沿用既有既定口径，不擅自扩权：
--      · finance:view / contact:view  → 仅系统管理员（V25 原文："默认仅系统管理员拥有，其他角色由权限管理显式授予"）
--      · customer:date:edit           → 沿用 V2026.09.11.5 的 role_code IN ('admin','0','06')
--      · incentive:read-stats         → 管理员 + **已持有 incentive:view 的角色**
--        （该码是 /incentive/search 页面的加载依赖；若只给管理员，其他能进该页的角色仍会 403）
--
-- 回滚：见文件尾部注释。
--
-- 运行提示（重要）：本迁移改的是权限定义本身，而鉴权走 Redis 权限缓存
--   （PermissionCacheService：key=perm:ctx:<userId>，TTL 10 分钟），**不会**自动失效。
--   实测：迁移后 admin 的 /api/auth/info 立刻能看到新码，但 @PreAuthorize 仍 403，
--   直到缓存过期或清空。即时生效需清缓存（等价于应用内 SysPermissionRuleService.evictAll()）：
--     docker exec shared-redis sh -c "redis-cli --scan --pattern 'perm:ctx:*' | xargs -r redis-cli DEL"
--   或等 ≤10 分钟 TTL 自然过期。
-- ============================================================

-- ---------- 1) 登记权限点 ----------

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
                        `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'incentive:view' ORDER BY `id` LIMIT 1), 15),
       '激励查询已读统计', 3, NULL, NULL, 'incentive:read-stats', NULL, 99, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:read-stats');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
                        `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'customer:view' ORDER BY `id` LIMIT 1), 11),
       '客户联系人查看', 3, NULL, NULL, 'contact:view', NULL, 97, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'contact:view');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
                        `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'customer:view' ORDER BY `id` LIMIT 1), 11),
       '客户财务查看', 3, NULL, NULL, 'finance:view', NULL, 98, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'finance:view');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
                        `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'customer:view' ORDER BY `id` LIMIT 1), 11),
       '客户日期编辑', 3, NULL, NULL, 'customer:date:edit', NULL, 96, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:date:edit');

-- ---------- 2) 角色绑定 ----------

-- 2.1 仅管理员（id=1 或 role_code='admin'，兼容主键/编码两种口径）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
    JOIN `sys_menu` m ON m.`permission` IN ('finance:view', 'contact:view')
WHERE (r.`id` = 1 OR r.`role_code` = 'admin')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`
);

-- 2.2 customer:date:edit：沿用 V2026.09.11.5 的角色口径
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
    JOIN `sys_menu` m ON m.`permission` = 'customer:date:edit'
WHERE r.`role_code` IN ('admin', '0', '06')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`
);

-- 2.3 incentive:read-stats：管理员 + 已持有 incentive:view 的角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
    JOIN `sys_menu` view_menu ON view_menu.`id` = rm.`menu_id`
                             AND view_menu.`permission` = 'incentive:view'
    JOIN `sys_menu` m ON m.`permission` = 'incentive:read-stats'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id`
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
    JOIN `sys_menu` m ON m.`permission` = 'incentive:read-stats'
WHERE (r.`id` = 1 OR r.`role_code` = 'admin')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`
);

-- ============================================================
-- 回滚（需人工确认后单独执行）：
--   DELETE rm FROM `sys_role_menu` rm JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
--     WHERE m.`permission` IN ('incentive:read-stats','contact:view','finance:view','customer:date:edit');
--   DELETE FROM `sys_menu`
--     WHERE `permission` IN ('incentive:read-stats','contact:view','finance:view','customer:date:edit');
-- 注意：回滚后 incentive:read-stats 会重新变为"全用户 403"（本迁移修复的原始缺陷）。
-- ============================================================
