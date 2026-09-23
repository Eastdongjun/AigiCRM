-- ============================================================
-- V2026.09.23.7__person_rank_export_permission.sql
-- 个人排行导出权限点(可勾选): incentive:person-rank:export
-- ============================================================
-- 背景(2026-09-23 用户需求):
--   激励统计页「个人排行」Tab 增加导出能力, 且导出受权限控制——
--   权限点出现在角色管理权限树, 由管理员勾选授予。
--
-- 本迁移做什么(幂等, 按码/路径定位, 零硬编码 id):
--   1) 注册按钮级权限点 menu_type=3「个人排行导出」,
--      parent 锚定激励统计页菜单行(path=/incentive/stats)
--   2) 默认仅绑 admin(超管); 其他角色由管理员在角色管理勾选
--
-- 前端配套(同批提交): PersonRankTableView 导出按钮
--   v-if hasPermission('incentive:person-rank:export')。
-- 导出为前端 CSV 组装(与客户排行 exportRank 同构), 数据源仍是
-- /incentive/scoreboard(已有 incentive:view 门禁), 权限码控按钮显隐,
-- 不引入越权面(无码者本就可见列表数据, 仅不能一键导出)。
--
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--     WHERE m.permission='incentive:person-rank:export';
--   DELETE FROM sys_menu WHERE permission='incentive:person-rank:export';
-- ============================================================

-- 1) 注册权限点(parent=激励统计页, 按 path 锚定)
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status)
SELECT p.id, '个人排行导出', 3, NULL, NULL, 'incentive:person-rank:export', 99, 1, 1
FROM sys_menu p
WHERE p.path = '/incentive/stats' AND p.menu_type = 2
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'incentive:person-rank:export');

-- 2) 默认绑定 admin(按角色码)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
    JOIN sys_menu m ON m.permission = 'incentive:person-rank:export'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- 复核(手工执行, 预期: 菜单 1 行 type=3; 绑定 admin 1 行):
--   SELECT id, menu_name, permission, menu_type, parent_id FROM sys_menu
--     WHERE permission='incentive:person-rank:export';
--   SELECT r.role_code FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id
--     JOIN sys_menu m ON m.id=rm.menu_id WHERE m.permission='incentive:person-rank:export';
