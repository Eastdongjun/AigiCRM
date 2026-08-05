-- ============================================================
-- 权限码补齐迁移（统一权限体系 §3.2）
-- 用途: 新增 customer:share / file:share / sea:export / sea:import / incentive:add
--       五个按钮级权限菜单行 + 角色绑定矩阵；并调整 incentive 矩阵
--       （销售员 role_id=11 解除 incentive:edit / incentive:delete）
-- 执行: mysql -h host -P port -u root -p --default-character-set=utf8mb4 <库名> < migration-20260805-perm-codes.sql
-- 幂等: 菜单行 INSERT 带 WHERE NOT EXISTS 判断；绑定 INSERT 使用 INSERT IGNORE
--       （依赖 sys_role_menu.uk_role_menu 唯一键）；DELETE 可重复执行安全
-- 数据库: MySQL 8.0（FROM DUAL 语法兼容）
-- 前置确认: 执行前请先用下方"备份查询"记录 sys_role_menu 现状，再执行本脚本
-- ============================================================

-- 1. 新增菜单行（按钮权限点，menu_type=3）
-- 1.1 客户分享：挂在"客户管理"(parent_id=11) 下
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 11, '客户分享', 3, '', 'customer:share', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='customer:share');

-- 1.2 文件分享：挂在"文件管理"(parent_id=14) 下
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 14, '文件分享', 3, '', 'file:share', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:share');

-- 1.3 公海导出：挂在"公海管理"(parent_id=17) 下（与既有 sea:pool:import 同区，sort 98 排在导入前）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 17, '公海导出', 3, '', 'sea:export', NULL, 98, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='sea:export');

-- 1.4 公海导入：挂在"公海管理"(parent_id=17) 下
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 17, '公海导入', 3, '', 'sea:import', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='sea:import');

-- 1.5 激励上传：挂在"四项激励Plus版"(parent_id=15) 下
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, permission, icon, sort_order, visible, status)
SELECT 15, '激励上传', 3, '', 'incentive:add', NULL, 99, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='incentive:add');

-- 2. 角色绑定矩阵
-- 2.1 新码 customer:share / file:share / sea:export / sea:import 绑定 admin(1)/行政(9)/销售总监(10)
--     INSERT IGNORE 依赖 uk_role_menu(role_id, menu_id) 唯一键，重复执行不报错、不重复插入
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE m.permission IN ('customer:share','file:share','sea:export','sea:import')
  AND r.id IN (1,9,10);

-- 2.2 incentive:add 额外绑定销售员(11)（销售员可自行上传激励记录）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 11, m.id FROM sys_menu m WHERE m.permission='incentive:add';

-- 3. 备份查询（务必在 DELETE 之前执行，记录 incentive:edit/incentive:delete 的现有绑定，供回滚使用）
SELECT role_id, menu_id, permission
FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.permission IN ('incentive:edit','incentive:delete')
ORDER BY m.permission, rm.role_id;

-- 4. incentive 矩阵调整：销售员(11) 解除 incentive:edit / incentive:delete 绑定
--    按权限标识定位而非硬编码 menu_id（62/63 为测试库现状，按权限定位可防 id 漂移）
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON rm.menu_id = m.id
WHERE rm.role_id = 11 AND m.permission IN ('incentive:edit','incentive:delete');

-- ==================== 回滚 SQL（需要时手动执行，逐条运行） ====================
-- 1. 删除新增菜单行（先解除其绑定，再删行）
--    DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--      WHERE m.permission IN ('customer:share','file:share','sea:export','sea:import','incentive:add');
--    DELETE FROM sys_menu WHERE permission IN ('customer:share','file:share','sea:export','sea:import','incentive:add');
-- 2. 恢复销售员(11) 的 incentive:edit / incentive:delete 绑定（menu_id 以第 3 步备份查询结果为准）
--    INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
--      SELECT 11, id FROM sys_menu WHERE permission IN ('incentive:edit','incentive:delete');
-- ==================================================================
