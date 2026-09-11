-- Some local and migrated databases store `/statistics` as a second-level page under
-- the first-level analytics group. Put the customer ranking page next to `/statistics`,
-- not under the `/statistics` page itself.

UPDATE sys_menu ranking
JOIN (
  SELECT parent.id
  FROM sys_menu statistics_page
  JOIN sys_menu parent ON parent.id = statistics_page.parent_id
  WHERE statistics_page.path = '/statistics'
    AND parent.menu_type = 1
  ORDER BY parent.id
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
  SELECT parent.id
  FROM sys_menu statistics_page
  JOIN sys_menu parent ON parent.id = statistics_page.parent_id
  WHERE statistics_page.path = '/statistics'
    AND parent.menu_type = 1
  ORDER BY parent.id
  LIMIT 1
) parent
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_menu existing
  WHERE existing.path = '/ranking/customer'
);
