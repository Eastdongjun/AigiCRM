-- V2026.11.14.3 彻底删除拆独立权限码 file:purge + 版本上限/保留期配置(外部审查 P1, 2026-11-14)
--
-- 背景: file:delete 同时承担"软删可恢复"与"彻底删除不可逆"两级安全等级——权限中心
-- 无法表达"可删、不可彻底删", 授权模型缺口。拆码后:
--   file:delete = 软删/回收站列表/恢复(可逆动作)
--   file:purge  = 彻底删除/批量彻底删除(不可逆动作, 默认仅 admin, 后续按需授权)
-- 版本上限/保留期 = 止住"无限替换=无限磁盘"的口子(版本计入配额为第二版产品决策)。
-- 幂等: NOT EXISTS 自然键锚定; admin 全码绑定(role_code='admin')。

-- ① sys_action 注册 file:purge(动作门, scope_required=0 不入数据范围轴)
INSERT INTO sys_action (resource_id, action_code, code, name, scope_required, view_like)
SELECT res.id, 'purge', 'file:purge', 'file:purge', 0, 0
FROM sys_resource res
WHERE res.module_code = 'file' AND res.resource_code = 'file'
  AND NOT EXISTS (SELECT 1 FROM sys_action a WHERE a.code = 'file:purge');

-- ② sys_menu 隐藏动作行(挂 file:view 同父; 空库重放场景父行由 V26 保证先建)
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT m.id, '文件彻底删除', 3, 'file:purge', 96, 1, 1
FROM sys_menu m
WHERE m.permission = 'file:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:purge')
LIMIT 1;

-- ③ admin 绑定(role_code='admin' 持全码口径; 非 admin 由权限中心按需勾选)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission = 'file:purge'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ④ 版本上限/保留期配置种子(运维键, 代码侧 getConfigValue 默认值双保险)
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'file.version.max-per-file', '20', '单文件版本历史上限(超出最旧版本被清理)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'file.version.max-per-file');

INSERT INTO sys_config (config_key, config_value, description)
SELECT 'file.version.retention-days', '180', '版本历史保留天数(过期版本每日清理)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'file.version.retention-days');
