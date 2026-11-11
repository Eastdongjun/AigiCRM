-- 【底座评估 2026-11-11 补漏】空库重建兜底: customer:invoice-date:edit 的 admin 绑定
--
-- 背景: V2026.11.02.3__invoice_date_edit_permission.sql 注册迁移只有菜单行无角色绑定——
--   与 finance:manage/customer:finance:edit 同款模式(V2026.11.11.2 ④ 已兜底的那批)。
--   该码此前漏网的原因: 绑定覆盖率契约旧正则只抓 @PreAuthorize 单引号码, 而本码的
--   唯一消费点是命令式 SecurityUtils.hasAuthority("customer:invoice-date:edit")
--   (CustomerService:736, 开票日期修改门)。契约升级(双引号命令式入列)后首跑抓出。
-- 语义: 空库重放后该码零绑定 → 开票日期编辑对全员 403(含 admin, 无隐式旁路);
--   生产/测试库不受影响(其绑定由历史在线变更形成)。
-- 兜底口径: 只给 admin(敏感财务日期字段, 上线后其他角色由权限中心人工勾选),
--   与 V2026.11.11.2 ④ 的 finance 双码 admin 兜底模式一致。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` = 'customer:invoice-date:edit' AND m.`menu_type` = 3
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);
