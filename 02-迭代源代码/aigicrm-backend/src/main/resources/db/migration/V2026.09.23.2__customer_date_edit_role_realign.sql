-- ============================================================
-- V2026.09.23.2__customer_date_edit_role_realign.sql
-- 注册日/最近开票日修改权对齐业务口径: 销售员(06)移除, 行政(05)补入
-- ============================================================
-- 背景(2026-09-23 用户口径拍板):
--   "注册日和最近一次开票日, 销售录入客户后销售无法更改,
--    只有总监和行政可以修改。"
--
--   现状(V2026.09.11.5 绑定, 测试库实测): customer:date:edit →
--     admin(系统管理员) / 0(销售总监) / 06(销售员)
--   与口径的两处偏差:
--     · 06(销售员)持有 → 销售**能**改, 违背"销售无法更改"
--     · 05(行政)未持有 → 行政**不能**改, 违背"总监和行政可修改"
--
--   V2026.09.11.5 绑 06 的依据是 V29 头注释"销售新增客户时可录入";
--   但录入走 create 路径(protectCustomerDateFields 只挂 update,
--   新建不校验该权限码), 销售员持码并不为录入所需, 只会让其
--   能改已有客户的保护日期——与本次口径直接冲突, 故移除。
--   行政(05)按口径补入; admin 保留(超管全权)。
--
-- 影响面: 前后端判定均为纯权限码驱动(PC useCustomerForm
--   canEditProtectedDates / 小程序 canEditDates 均判
--   hasPermission('customer:date:edit')), 绑定调整后自动生效,
--   代码零改动; 后端 CustomerService 守卫语义不变。
--
-- 本迁移做什么(幂等, 全部按 permission/role_code 定位, 零硬编码 id):
--   1) 删除 role_code='06'(销售员)对该权限点的绑定
--   2) 补绑 role_code='05'(行政)
--   admin/'0' 已持有, NOT EXISTS 守卫下为 no-op
--
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id
--     JOIN sys_menu m ON m.id=rm.menu_id
--     WHERE m.permission='customer:date:edit' AND r.role_code IN ('05');
--   INSERT INTO sys_role_menu (role_id, menu_id)
--     SELECT r.id, m.id FROM sys_role r JOIN sys_menu m
--       ON m.permission='customer:date:edit'
--     WHERE r.role_code='06' AND NOT EXISTS (
--       SELECT 1 FROM sys_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id);
-- ============================================================

-- 1) 销售员(06)解除绑定
DELETE rm FROM `sys_role_menu` rm
    JOIN `sys_role` r ON r.`id` = rm.`role_id`
    JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE m.`permission` = 'customer:date:edit'
  AND r.`role_code` = '06';

-- 2) 行政(05)补绑(幂等)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
    JOIN `sys_menu` m ON m.`permission` = 'customer:date:edit'
WHERE r.`role_code` IN ('admin', '0', '05')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`
  );

-- 复核查询(手工执行, 预期三行: admin / 0 销售总监 / 05 行政):
--   SELECT r.role_code, r.role_name FROM sys_role_menu rm
--     JOIN sys_menu m ON rm.menu_id=m.id JOIN sys_role r ON r.id=rm.role_id
--   WHERE m.permission='customer:date:edit';
