-- ============================================================
-- M1 乱码修复（2026-08-14）
-- 背景：M1 首次执行时 mysql 客户端未显式设置 utf8mb4 连接字符集，
--       中文角色名/菜单名被双重编码成乱码（UTF-8 字节按 latin1 解释存储）。
-- 修复：以 utf8mb4 连接重写 M1 插入的 8 处中文字段（幂等，按 role_code/permission 定位）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p aigicrm < sql/migration/20260814/m1-fix-mojibake.sql
-- ============================================================

SET NAMES utf8mb4;

UPDATE sys_role SET
    role_name = '激励-全量查看',
    description = '激励模块查看全部记录（含未分享）'
WHERE role_code = 'incentive_view_all';

UPDATE sys_role SET
    role_name = '激励-分享可见',
    description = '激励模块查看已分享记录（全员默认；收窄=解绑）'
WHERE role_code = 'incentive_view_shared';

UPDATE sys_menu SET menu_name = '激励管理(他人记录)' WHERE permission = 'incentive:manage';
UPDATE sys_menu SET menu_name = '激励查看(全部)' WHERE permission = 'incentive:view-all';
UPDATE sys_menu SET menu_name = '激励查看(已分享)' WHERE permission = 'incentive:view-shared';
UPDATE sys_menu SET menu_name = '文件ACL管理' WHERE permission = 'file:acl:manage';
