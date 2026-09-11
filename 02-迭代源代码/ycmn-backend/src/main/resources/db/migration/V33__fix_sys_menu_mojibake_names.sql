-- 修复本地/历史迁移中因客户端字符集不一致造成的 sys_menu 菜单名乱码。
-- 仅按稳定 path / permission 定位，不改权限、组件、可见性或父子结构。

UPDATE sys_menu
SET menu_name = '积分排行'
WHERE path = '/score/ranking'
  AND component = 'score/ranking'
  AND permission = 'score:view';

UPDATE sys_menu
SET menu_name = '积分查看'
WHERE path IS NULL
  AND component IS NULL
  AND permission = 'score:view';

UPDATE sys_menu
SET menu_name = '积分录入'
WHERE path = '/score/entry'
  AND component = 'score/entry'
  AND permission = 'score:entry';

UPDATE sys_menu
SET menu_name = '积分管理'
WHERE path IS NULL
  AND component IS NULL
  AND permission = 'score:manage';

UPDATE sys_menu
SET menu_name = '客户排行'
WHERE path = '/ranking/customer'
  AND component = 'ranking/customer'
  AND permission = 'statistics:view';
