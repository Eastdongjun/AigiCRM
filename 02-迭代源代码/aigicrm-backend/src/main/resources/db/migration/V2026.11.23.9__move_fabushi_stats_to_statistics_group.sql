-- 将法布施「统计看板」移入顶层「统计分析」分组，和客户管理一样形成统计模块下的子菜单。
UPDATE sys_menu c
JOIN sys_menu g
  ON g.menu_name = '统计分析'
 AND g.menu_type = 1
 AND COALESCE(g.parent_id, 0) = 0
SET c.parent_id = g.id,
    c.sort_order = 2
WHERE c.path = '/fabushi/stats'
  AND c.menu_type = 2;
