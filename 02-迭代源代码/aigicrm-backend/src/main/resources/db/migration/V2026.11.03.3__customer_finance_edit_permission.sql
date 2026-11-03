-- ============================================================
-- V2026.11.03.3__customer_finance_edit_permission.sql
-- 【2026-11-03 用户裁决"本次一并拆"】新增按钮级权限码 customer:finance:edit「开票回款编辑」。
--
-- 背景: 财务编辑此前借道 customer:edit(整卡编辑权)——行政要按大区管开票回款就必须
-- 同时获得大区内客户全部资料的编辑权(范围过宽)。拆独立码后带自己的数据范围
-- (role_perm_scope: module=customer, action=finance_edit), 行政场景正解 =
--   勾「开票回款编辑」+ 范围=本大区及以下, 不碰客户资料。
-- 兼容: 旧两支门槛(finance:manage 全通 / customer:edit∧finance:view 走编辑范围)原样保留。
-- 【用户原则"配置不硬编码,后台都是勾选的"】本迁移只定义权限码本身, 不绑角色。
-- 写法说明: 常量插入(parent 先落 0)保证权限码登记链可模拟; parent 由第二条
--   按名锚定 UPDATE 归位(零硬编码行 id)。幂等: NOT EXISTS 防重插。
-- ============================================================

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `permission`, `sort_order`, `visible`, `status`)
SELECT 0, '开票回款编辑', 3, 'customer:finance:edit', 94, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `permission` = 'customer:finance:edit');

-- parent 归位到「客户管理」页节点(按名锚定, 不写死行 id; 仅修本迁移新插的行)
UPDATE `sys_menu` c
JOIN `sys_menu` p ON p.`menu_name` = '客户管理' AND p.`menu_type` = 2
SET c.`parent_id` = p.`id`
WHERE c.`permission` = 'customer:finance:edit' AND c.`parent_id` = 0 AND c.`menu_type` = 3;
