-- ============================================================
-- V2026.11.08.1__drop_dead_contact_manage.sql
-- 【11-08 死码清理】删除 contact:manage 死权限码: sys_menu 登记行 + sys_role_menu 绑定
--
-- 背景(11-08 代码审计 + 生产实拉确证):
--   · contact:manage 为 PR-06(2026-09-19)职责分离设计引入的"联系人专职授权点",
--     V2026.09.19.6 / V2026.09.23.4 两次登记, 但从未绑定任何角色(零持有者);
--   · 前后端零消费: PC(aigicrm-ui)/MP(aigicrm-miniprogram) 无 hasPermission('contact:manage'),
--     后端仅 CustomerController 联系人三端点的 @PreAuthorize OR 支路引用——
--     本批(2026-11-08)已同步摘除该 OR 支路(联系人写操作统一 customer:edit 单码),
--     代码侧零引用后按权限码锚定删除登记行;
--   · 权限登记契约(check-permission-registration-contract.py)口径:
--     "代码在用的权限码必须登记且末态启用"——代码摘除后删除登记, 契约仍成立。
--
-- 写法(项目惯例, 见 V22):
--   按 permission 字符串锚定, 不写死 menu_id(防各环境 id 漂移);
--   全部固定字面量/子查询定位, 无任何动态拼接。
--
-- 幂等: 按码定位, 绑定/行不存在时 DELETE 自然落空, 可重放。
-- 回滚: 见文件尾部回滚注释(如需恢复请先评估 PR-06 职责分离是否重启)。
-- ============================================================

-- 1) 删除该码的所有角色绑定(子查询按 permission 定位 menu_id)
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `id` FROM `sys_menu` WHERE `permission` = 'contact:manage');

-- 2) 删除该码的 sys_menu 登记行(按码字面量锚定; 理论上至多 1 行, 不限行数防 legacy 重复)
DELETE FROM `sys_menu`
WHERE `permission` = 'contact:manage';

-- ==================== 回滚(按需手工执行) ====================
-- 执行前建议备份: mysqldump --no-data ycmn sys_menu sys_role_menu > menu_backup_$(date +%F).sql
-- 回滚 SQL(照抄 V2026.09.23.4 范式重新登记, 默认零绑定):
--   INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`,
--                           `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
--   SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'customer:view' ORDER BY `id` LIMIT 1), 11),
--          '客户联系人管理', 3, NULL, NULL, 'contact:manage', NULL, 95, 1, 1, NOW()
--   FROM (SELECT 1) AS `_`
--   WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'contact:manage');
--   同时需恢复 CustomerController 联系人三端点的 `or hasAuthority('contact:manage')` OR 支路。
-- ============================================================
