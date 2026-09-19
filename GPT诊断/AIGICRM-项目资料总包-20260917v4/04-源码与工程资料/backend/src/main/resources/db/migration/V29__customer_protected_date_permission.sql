-- 注册日/最近开票日录入后修改权限。
-- 业务规则：销售新增客户时可录入；后续只有具备 customer:date:edit 的角色可修改。
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, visible, status, created_at)
SELECT 11, '修改客户保护日期', 3, 'customer:date:edit', 11, 0, 1, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE permission = 'customer:date:edit'
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission = 'customer:date:edit'
WHERE r.role_code IN ('admin', 'back_office', 'sales_director', 'director', 'region_manager', 'admin_staff')
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );
