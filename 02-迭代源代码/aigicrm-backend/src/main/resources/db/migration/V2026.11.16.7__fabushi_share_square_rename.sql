-- 2026-11-16 用户拍板: PC 法布施首项「内容流」更名「分享广场」
-- 仅改菜单显示名, 不改 path/permission/role_menu 绑定; 按 path 自然键定位, 禁硬编码 id。
UPDATE sys_menu
SET menu_name = '分享广场'
WHERE menu_type = 2
  AND path = '/fabushi/feed';
