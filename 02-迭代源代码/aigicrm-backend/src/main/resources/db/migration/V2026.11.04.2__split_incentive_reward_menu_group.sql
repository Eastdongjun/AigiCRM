-- 【2026-11-04 用户令"小程序的四项服务和四项激励是两个不同的模块"·双端拆开】
-- PC 侧栏同步拆组: 「四项服务」(激励上传/激励查询) 与 「四项激励」(激励统计) 各自成组。
-- MP 应用中心同批拆两模块组(纯前端 pages/app/index.js, 不在本迁移)。
--
-- 机制依据: PC 侧栏分组=登录菜单树(sys_role_menu 授权制, 非权限码匹配),
--   故拆组=建目录节点+改挂子页+授权回填; 组节点 permission 留空(纯目录行, 不产生码)。
-- 节点引用一律按 path/permission 归位, 零硬编码 id(快照库与生产 id 不同亦可跑)。
-- 幂等: 腾位/建组均有 NOT EXISTS 守卫; 缓存: 末尾 perm_version 全员 +1。
-- 回滚:
--   UPDATE sys_menu SET parent_id = (SELECT p.id FROM (SELECT id FROM sys_menu WHERE path='/incentive-group' AND menu_type=2 LIMIT 1) p)
--     WHERE path='/incentive/stats' AND menu_type=2;
--   UPDATE sys_menu SET parent_id = (SELECT p.id FROM (SELECT id FROM sys_menu WHERE path='/incentive-group' AND menu_type=2 LIMIT 1) p)
--     WHERE permission IN ('incentive:stats:view','incentive:rank:view') AND menu_type=3;
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path='/incentive-reward-group' AND menu_type=2) g);
--   DELETE FROM sys_menu WHERE path='/incentive-reward-group' AND menu_type=2;
--   -- 腾位回退须人工核对新组删除后的空档, 谨慎执行:
--   -- UPDATE sys_menu SET sort_order = sort_order - 1 WHERE sort_order >= 7 AND parent_id = (业务核心id);
--   UPDATE sys_user SET perm_version = perm_version + 1;

-- 1) 兄弟腾位: 四项服务组(sort=5)之后的所有同级节点 sort_order>=6 整体 +1, 空出 6 给新组
--    (仅当新组尚不存在时执行, 防重复跑二次挪动; 派生表包装规避 MySQL 同表更新限制)
UPDATE sys_menu
SET sort_order = sort_order + 1
WHERE sort_order >= 6
  AND parent_id = (SELECT p.parent_id FROM (SELECT parent_id FROM sys_menu WHERE path = '/incentive-group' AND menu_type = 2 LIMIT 1) p)
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE path = '/incentive-reward-group' AND menu_type = 2 LIMIT 1) g);

-- 2) 新组节点: 四项激励(业务核心下, 与四项服务组同级相邻; permission 留空=纯目录行)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT COALESCE((SELECT p.parent_id FROM (SELECT parent_id FROM sys_menu WHERE path = '/incentive-group' AND menu_type = 2 LIMIT 1) p), 0),
       '四项激励', 2, '/incentive-reward-group', '', '', 'Medal', 6, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.path = '/incentive-reward-group' AND x.menu_type = 2);

-- 3) 激励统计页节点改挂新组(授权行 sys_role_menu 按 menu_id 引用, 改挂无需动授权)
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/incentive-reward-group' AND menu_type = 2 LIMIT 1) g)
WHERE path = '/incentive/stats' AND menu_type = 2;

-- 4) 两码节点(激励统计查看/激励排行查看, type=3)树上归位到新组下(纯摆放, 零功能影响)
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/incentive-reward-group' AND menu_type = 2 LIMIT 1) g)
WHERE permission IN ('incentive:stats:view', 'incentive:rank:view') AND menu_type = 3;

-- 5) 授权回填: 现持激励统计节点授权的角色 → 补授新组节点(否则组不可见, 激励统计成孤岛)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, g.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.path = '/incentive/stats' AND om.menu_type = 2
JOIN `sys_menu` g ON g.path = '/incentive-reward-group' AND g.menu_type = 2
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = g.id);

-- 6) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
