-- 【2026-10-21 用户令】「四项激励」全站更名「四项服务」——数据库侧菜单名同步。
-- 只改展示名 menu_name; permission('incentive:view')/path('/incentive-group')/component/incentive-group 系
-- 路由与授权键零变化, 前端(router meta/侧栏)与小程序(tabBar/页面标题)同批改名。
-- 定位按 menu_name+menu_type 精确匹配, 不硬编码 id(V2026.09.11.6 种子行 menu_type=2, 生产实测仅此一行)。
-- 幂等: 更名后 WHERE 不再命中, 重复执行零副作用。
UPDATE `sys_menu`
SET `menu_name` = '四项服务', `updated_at` = NOW()
WHERE `menu_name` = '四项激励' AND `menu_type` = 2;
