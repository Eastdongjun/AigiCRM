-- 【四待裁项裁决A 2026-11-12】统计双 key 统一单键: dashboard:view 退役, 统一到 statistics:view
--
-- 背景(design.md 已知问题#1, 2026-11-12 用户拍板 A+"行政给全"):
--   StatisticsController 4 端点曾 hasAnyAuthority('statistics:view','dashboard:view') 双码并存,
--   管理员须两处配一致否则"配了工作台/统计页仍 403"。本迁移配套后端/PC/MP 同批改单键。
-- 生产实况(只读 2026-11-12):
--   dashboard:view=工作台菜单行(menu_type=2), 绑 系统管理员/行政/销售员/销售总监;
--   statistics:view=三菜单行(统计分析/六项指标/客户排行), 管理员/销售员/销售总监各有绑定,
--   行政为唯一"绑 dashboard:view 而 0 绑 statistics:view"的角色。
-- 补绑口径(用户拍板"给全"):
--   仅对"绑工作台且未绑任何 statistics:view 菜单"的角色补绑全部统计菜单行——
--   销售员已绑部分统计菜单(六项指标/客户排行)故不触发, 不会误增"统计分析"入口;
--   行政补绑后侧栏新增 统计分析/六项指标/客户排行 三入口, 统计 7 端点全通。
-- 退役口径:
--   工作台菜单行 permission 置 NULL——行保留、role_menu 绑定不动(菜单可见性零变化),
--   仅停止发放 dashboard:view 码(三端消费点已同批清除); tree() 只按 status 过滤, NULL 无影响。
-- 幂等性: NOT EXISTS 双防重 + UPDATE 天然幂等; 按 permission 自然键锚定, 无硬编码 id(INV-MIG-001)。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` d ON d.`permission` = 'dashboard:view' AND d.`menu_type` = 2
JOIN `sys_role_menu` drm ON drm.`role_id` = r.`id` AND drm.`menu_id` = d.`id`
JOIN `sys_menu` m ON m.`permission` = 'statistics:view' AND m.`menu_type` = 2
WHERE NOT EXISTS (
        SELECT 1 FROM `sys_role_menu` srm
        JOIN `sys_menu` sm ON sm.`id` = srm.`menu_id` AND sm.`permission` = 'statistics:view'
        WHERE srm.`role_id` = r.`id`)
  AND NOT EXISTS (
        SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

UPDATE `sys_menu` SET `permission` = NULL
WHERE `permission` = 'dashboard:view' AND `menu_type` = 2;
