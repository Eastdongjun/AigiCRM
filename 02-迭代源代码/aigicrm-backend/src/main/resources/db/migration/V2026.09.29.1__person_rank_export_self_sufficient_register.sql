-- ============================================================
-- V2026.09.29.1__person_rank_export_self_sufficient_register.sql
-- 【登记契约复绿 2026-09-29】个人排行导出权限点补自足登记
--
-- 背景:
--   V2026.09.23.7 已把 incentive:person-rank:export 注册为可勾选权限点
--   (前端 v-if 门控导出按钮, 默认仅 admin),但它的 INSERT 锚定
--   `WHERE p.path='/incentive/stats'` 依赖既有菜单行且无兜底——
--   登记契约(代码在用码必须在迁移链中可证明登记)对"依赖既有行的插入
--   而前置未登记"判不成立。23.7 已在测试/生产执行,按红线不可修改,
--   故以本迁移补一段**自足**(锚定取不到时 parent 兜底 0=顶级)的登记。
--
-- 幂等:
--   按码 NOT EXISTS 守卫——23.7 已插入的库本迁移零变化(no-op);
--   仅在"锚定行缺失且 23.7 因而没有插入"的形态下兜底插入。
--   admin 绑定同样按 (role_code, permission) 双守卫重申,两种形态收敛一致。
--
-- 定位口径:
--   全程按 path/permission/role_code 锚定,零硬编码行 id(INV-MIG-001);
--   parent 兜底值 0 为顶级结构常量,非行 id 定位。
--
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--     WHERE m.permission='incentive:person-rank:export' AND m.parent_id=0;
--   DELETE FROM sys_menu WHERE permission='incentive:person-rank:export' AND parent_id=0;
--   (仅回退本迁移兜底插入的形态;23.7 的原始登记不属本迁移处置范围)
-- ============================================================

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status, created_at)
SELECT COALESCE((SELECT p.id FROM sys_menu p
                   WHERE p.path = '/incentive/stats' AND p.menu_type = 2
                   ORDER BY p.id LIMIT 1), 0),
       '个人排行导出', 3, NULL, NULL, 'incentive:person-rank:export', 99, 1, 1, NOW()
FROM (SELECT 1) AS `_`
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'incentive:person-rank:export');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
    JOIN sys_menu m ON m.permission = 'incentive:person-rank:export'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x
                   WHERE x.role_id = r.id AND x.menu_id = m.id);
