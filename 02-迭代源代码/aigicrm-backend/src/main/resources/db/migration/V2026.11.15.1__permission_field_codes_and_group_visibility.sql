-- ============================================================
-- 权限体系 P0-3 / P0-7(2026-11-15)
-- 1) 字段码登记: customer:date:edit / customer:invoice-date:edit 挂到 customer:edit 下并可见、名称标注“字段”
-- 2) 清理“字段码无父码”的历史角色绑定(死配置)
-- 3) 集团可见: system:group:view 默认授予所有角色(只读; 写操作仍仅平台管理员)
-- 4) 死码保持禁用(幂等): incentive:view-all / incentive:view-shared
-- 定位一律按 permission 码, 禁止硬编码行 id(INV-MIG-001)
-- ============================================================

SET @customer_edit_id := (SELECT id FROM sys_menu WHERE permission = 'customer:edit' LIMIT 1);

UPDATE sys_menu
   SET parent_id = @customer_edit_id,
       visible = 1,
       menu_name = '修改客户保护日期（字段）'
 WHERE permission = 'customer:date:edit';

UPDATE sys_menu
   SET parent_id = @customer_edit_id,
       visible = 1,
       menu_name = '最近开票日编辑（字段）'
 WHERE permission = 'customer:invoice-date:edit';

-- 字段码必须依附 customer:edit；无父码的绑定属于“勾了不生效”的死配置，迁移清理
DELETE rm
  FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
  LEFT JOIN sys_role_menu rm2 ON rm2.role_id = rm.role_id
  LEFT JOIN sys_menu p ON p.id = rm2.menu_id AND p.permission = 'customer:edit'
 WHERE m.permission IN ('customer:date:edit', 'customer:invoice-date:edit')
   AND p.id IS NULL;

-- 集团信息登录可见：system:group:view 默认授予所有角色(幂等)
SET @group_view_id := (SELECT id FROM sys_menu WHERE permission = 'system:group:view' LIMIT 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, @group_view_id
  FROM sys_role r
  LEFT JOIN sys_role_menu rm ON rm.role_id = r.id AND rm.menu_id = @group_view_id
 WHERE rm.id IS NULL;

-- 废弃码保持禁用
UPDATE sys_menu SET status = 0
 WHERE permission IN ('incentive:view-all', 'incentive:view-shared');
