-- PC 菜单文案与客户排行入口。
-- 业务约定：接口名和权限码继续使用 score:*；仅调整可见菜单名称并新增客户排行页面。

UPDATE sys_menu
SET menu_name = '积分管理'
WHERE menu_name = '学分管理';

UPDATE sys_menu
SET menu_name = '积分录入'
WHERE menu_name = '学分录入'
   OR permission = 'score:entry'
   OR path = '/score/entry';

UPDATE sys_menu
SET menu_name = '积分排行'
WHERE menu_name = '学分排行'
   OR permission = 'score:view'
   OR path IN ('/score/ranking', '/incentive/ranking');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status, created_at)
SELECT parent.id, '客户排行', 2, '/ranking/customer', 'ranking/customer', 'statistics:view', 'DataLine', 99, 1, 1, NOW()
FROM (
  SELECT id
  FROM sys_menu
  WHERE menu_name = '统计分析' OR permission = 'statistics:view' OR path = '/statistics'
  ORDER BY CASE WHEN menu_type = 1 THEN 0 ELSE 1 END, id
  LIMIT 1
) parent
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu existing
  WHERE existing.path = '/ranking/customer'
);
