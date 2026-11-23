-- 将管理端「内容治理」统一更名为「内容管理」：菜单名、权限元数据、动作名。
UPDATE sys_menu SET menu_name = '内容管理'
WHERE path = '/fabushi/content' AND menu_type = 2;

UPDATE sys_permission_meta SET name = '内容管理'
WHERE code = 'fabushi:content:view';

UPDATE sys_action SET name = '法布施内容管理页'
WHERE code = 'fabushi:content:view';
