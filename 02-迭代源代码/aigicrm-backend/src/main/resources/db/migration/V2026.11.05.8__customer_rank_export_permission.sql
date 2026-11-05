-- ============================================================
-- V2026.11.05.8__customer_rank_export_permission.sql
-- 【2026-11-05 用户令"客户排行导出加码"】客户排行导出权限点(可勾选): incentive:rank:export
-- 完全镜像 V2026.09.23.7(个人排行导出) 先例:
--   注册 type=3 按钮行(parent=激励统计页 /incentive/stats, 按 path 锚定零硬编码 id);
--   默认绑定 admin(按角色码), 其他角色由管理员在 权限中心(系统设置→权限中心→功能与范围) 勾选授予。
-- 背景: 客户排行导出按钮此前无任何权限点(对照: 个人排行导出自 2026-09-23 起受
--   incentive:person-rank:export 独立码控制), G2 修复临时以 rank:view 门禁, 本迁移按先例落独立码。
-- 数据面不变: 导出=前端 CSV 组装, 数据源仍 /incentive/scoreboard(stats:view∨rank:view 门禁),
--   权限码仅控按钮显隐, 无码者本就可见列表数据, 仅不能一键导出(无越权面)。
-- 注意: 绑定仅 admin → 上线后 总监/行政 等角色的「客户排行·导出」按钮消失(原按钮无门禁人人可见),
--   需要者在 权限中心 给对应角色勾「客户排行导出」即回。
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--     WHERE m.permission='incentive:rank:export';
--   DELETE FROM sys_menu WHERE permission='incentive:rank:export';
-- ============================================================

-- 1) 注册权限点(parent=激励统计页, 按 path 锚定标量子查询; menu_type=3 按钮行同个人排行导出先例)
--    【注】FROM DUAL+标量子查询 = 登记契约认定的"常量来源插入"(直接证明该码被登记;
--    FROM sys_menu 直连形态会被判"依赖既有行"而无法自证)
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status)
SELECT COALESCE((SELECT p.id FROM sys_menu p
                  WHERE p.path = '/incentive/stats' AND p.menu_type = 2 LIMIT 1), 0),
       '客户排行导出', 3, NULL, NULL, 'incentive:rank:export', 100, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'incentive:rank:export');

-- 2) 默认绑定 admin(按角色码, 同个人排行导出先例)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
    JOIN sys_menu m ON m.permission = 'incentive:rank:export'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- 3) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
