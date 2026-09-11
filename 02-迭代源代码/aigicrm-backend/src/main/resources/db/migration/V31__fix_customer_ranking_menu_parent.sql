-- Correct the customer ranking menu parent after V26 without changing the applied V26 checksum.
-- The customer ranking route is a page menu and must live under the first-level statistics group,
-- not under a permission/action menu that happens to use statistics:view.

UPDATE sys_menu ranking
JOIN (
  SELECT id
  FROM sys_menu
  WHERE menu_type = 1
    AND (menu_name = '统计分析' OR path = '/statistics')
  ORDER BY id
  LIMIT 1
) parent ON 1 = 1
SET ranking.parent_id = parent.id,
    ranking.menu_name = '客户排行',
    ranking.menu_type = 2,
    ranking.path = '/ranking/customer',
    ranking.component = 'ranking/customer',
    ranking.permission = 'statistics:view',
    ranking.icon = 'DataLine',
    ranking.visible = 1,
    ranking.status = 1
WHERE ranking.path = '/ranking/customer';

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status, created_at)
SELECT parent.id, '客户排行', 2, '/ranking/customer', 'ranking/customer', 'statistics:view', 'DataLine', 99, 1, 1, NOW()
FROM (
  SELECT id
  FROM sys_menu
  WHERE menu_type = 1
    AND (menu_name = '统计分析' OR path = '/statistics')
  ORDER BY id
  LIMIT 1
) parent
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_menu existing
  WHERE existing.path = '/ranking/customer'
);
