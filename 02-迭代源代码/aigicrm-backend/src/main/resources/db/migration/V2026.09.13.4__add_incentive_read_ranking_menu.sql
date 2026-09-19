-- 2026-09-13 新增「本月激励查询阅读排行榜」页 + 其权限码（默认仅系统管理员可见）
--
-- 【需求】系统设置下新作一个页面「本月激励查询阅读排行榜」，**只有管理员勾选后才能看到**
--   —— 即：菜单与接口同一个权限码 incentive:read-ranking:view，本迁移只把它绑定给
--   「系统管理员」角色；管理员在「角色管理 → 权限分配」里勾选该菜单后，其它角色才会看到
--   （sys_role_menu 由角色管理界面写入，无需再改代码）。
--
-- 【页面入口】挂在「系统设置」分组下（该分组 = 系统设置页 /system/config 的父级，
--   现有子项：系统设置、权限规则），新增子项：本月激励查询阅读排行榜 /system/incentive-read-ranking。
--
-- 【接口】GET /api/incentive/read-ranking?period=YYYY-MM（缺省=当月），
--   @PreAuthorize("hasAuthority('incentive:read-ranking:view')") —— 与菜单同码。
--
-- 【口径】与激励查询页"已读/未读"一致：本月发布的记录为分母、该人对其中已读的条数为分子。
--
-- 幂等：全部带 NOT EXISTS / WHERE 条件守卫，可重复执行。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE path='/system/incentive-read-ranking');
--   DELETE FROM sys_menu WHERE path='/system/incentive-read-ranking';

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT COALESCE(
           (SELECT p.`id` FROM `sys_menu` s JOIN `sys_menu` p ON p.`id` = s.`parent_id`
             WHERE s.`path` = '/system/config' ORDER BY p.`id` LIMIT 1),
           (SELECT p.`parent_id` FROM `sys_menu` p
             WHERE p.`path` = '/system/config-group' ORDER BY p.`id` LIMIT 1)
       ),
       '本月激励查询阅读排行榜', 2, '/system/incentive-read-ranking', 'system/incentive-read-ranking',
       'incentive:read-ranking:view', 'DataAnalysis', 9, 1, 1
FROM DUAL
WHERE (SELECT COUNT(*) FROM `sys_menu` WHERE `path` = '/system/incentive-read-ranking') = 0
  AND COALESCE(
          (SELECT p.`id` FROM `sys_menu` s JOIN `sys_menu` p ON p.`id` = s.`parent_id`
            WHERE s.`path` = '/system/config' ORDER BY p.`id` LIMIT 1),
          (SELECT p.`parent_id` FROM `sys_menu` p
            WHERE p.`path` = '/system/config-group' ORDER BY p.`id` LIMIT 1)
      ) IS NOT NULL;

-- 默认只绑「系统管理员」：其它角色需管理员在角色管理里勾选后才可见（需求核心）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, (SELECT `id` FROM `sys_menu` WHERE `path` = '/system/incentive-read-ranking' LIMIT 1)
FROM `sys_role` r
WHERE r.`role_code` = 'admin'
  AND (SELECT COUNT(*) FROM `sys_menu` WHERE `path` = '/system/incentive-read-ranking') = 1
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x
      WHERE x.`role_id` = r.`id`
        AND x.`menu_id` = (SELECT `id` FROM `sys_menu` WHERE `path` = '/system/incentive-read-ranking' LIMIT 1)
  );
