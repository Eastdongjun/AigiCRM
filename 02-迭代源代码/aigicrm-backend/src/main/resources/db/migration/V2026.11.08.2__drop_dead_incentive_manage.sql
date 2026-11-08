-- ============================================================
-- V2026.11.08.2__drop_dead_incentive_manage.sql
-- 【11-08 复查批·死码清理】删除 incentive:manage 死权限码: sys_menu 登记行 + sys_role_menu 绑定
--
-- 背景(11-08 二轮对齐检查全库复核确证):
--   · incentive:manage 为 2026-08-14 RBAC 收敛引入的"编辑他人激励记录"授权点,
--     V2026.09.11.6 登记 sys_menu 并绑定角色(生产现绑 admin+行政+销售总监);
--   · 2026-08-16 起 IncentiveService.canEdit()/canDelete() 改判授权表
--     biz_incentive_grant(can_edit/can_delete 列), 该权限码在运行时零消费——
--     全库(main/test/双端前端/scripts)无任何 hasAuthority('incentive:manage')
--     真实调用, 仅 4 处陈旧注释误述(本批同日修正为授权表口径);
--   · 即生产三角色"持有"该码但永不生效, 属与 contact:manage 同族的死登记
--     (V2026.11.08.1 范式复用);
--   · 权限登记契约口径: "代码在用的权限码必须登记且末态启用"——
--     代码零消费, 删除登记不违约; check-charset.sh 对该码仅为过滤查询非存在断言。
--
-- 写法: 按 permission 字符串锚定, 不写死 menu_id; 全部固定字面量/子查询, 无动态拼接。
-- 幂等: 按码定位, 绑定/行不存在时 DELETE 自然落空, 可重放。
-- 回滚: 见文件尾部回滚注释。
-- ============================================================

-- 1) 删除该码的所有角色绑定(子查询按 permission 定位 menu_id)
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `id` FROM `sys_menu` WHERE `permission` = 'incentive:manage');

-- 2) 删除该码的 sys_menu 登记行(按码字面量锚定)
DELETE FROM `sys_menu`
WHERE `permission` = 'incentive:manage';

-- ==================== 回滚(按需手工执行) ====================
-- 执行前建议备份: mysqldump --no-data ycmn sys_menu sys_role_menu > menu_backup_$(date +%F).sql
-- 回滚 = 照抄 V2026.09.11.6 范式重新登记并重绑三角色(如确需恢复, 须同时把
-- IncentiveService.canEdit/canDelete 改回 hasAuthority 判定, 否则登记仍不生效):
--   INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
--                           `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
--   SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'incentive:view' ORDER BY `id` LIMIT 1), 15),
--          '激励管理(他人记录)', 3, NULL, NULL, 'incentive:manage', NULL, 96, 1, 1, NOW()
--   FROM (SELECT 1) AS `_`
--   WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:manage');
--   角色重绑: INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
--   SELECT r.`id`, m.`id` FROM `sys_role` r, `sys_menu` m
--   WHERE m.`permission`='incentive:manage' AND r.`role_code` IN ('admin','0','05');
-- ============================================================
