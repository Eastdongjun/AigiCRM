-- 【2026-11-04 用户令"四项激励/四项服务 逐功能建码"】激励统计与激励排行 从 incentive:view 拆出独立权限码:
--   incentive:stats:view 激励统计查看（激励统计页·统计页签: 分类统计/明细抽屉; PC 激励统计菜单）
--   incentive:rank:view  激励排行查看（激励统计页·排行页签: 客户排行/个人积分榜/部门排行）
-- 存量回填: 现持 incentive:view 的角色自动获得两新码(存量访问不变), 此后管理员可在权限中心分别勾选。
-- 激励统计菜单节点(/incentive/stats)权限改绑 incentive:stats:view(与 MP 页签门禁/后端守卫三方同源)。
-- 接口守卫(代码侧同步): /category-stats=stats:view; /scoreboard、/ranking=stats:view∨rank:view(双页签共用数据面)。
-- 幂等: NOT EXISTS 守卫; 缓存: 末尾 perm_version 全员 +1(直改库自愈)。
-- 回滚:
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE permission IN ('incentive:stats:view','incentive:rank:view'));
--   DELETE FROM sys_menu WHERE permission IN ('incentive:stats:view','incentive:rank:view');
--   UPDATE sys_menu SET permission='incentive:view' WHERE path='/incentive/stats';
--   UPDATE sys_user SET perm_version = perm_version + 1;

-- 1) 权限节点: 激励统计查看（挂四项激励分组=激励:view 节点同级, 按名归位防重复）
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT COALESCE((SELECT p.parent_id FROM `sys_menu` p
                  WHERE p.permission = 'incentive:view' AND p.menu_type = 2
                  ORDER BY p.id LIMIT 1), 0),
       '激励统计查看', 3, '', '', 'incentive:stats:view', '', 30, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.permission = 'incentive:stats:view');

-- 2) 权限节点: 激励排行查看
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT COALESCE((SELECT p.parent_id FROM `sys_menu` p
                  WHERE p.permission = 'incentive:view' AND p.menu_type = 2
                  ORDER BY p.id LIMIT 1), 0),
       '激励排行查看', 3, '', '', 'incentive:rank:view', '', 31, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.permission = 'incentive:rank:view');

-- 3) 存量回填: 现持 incentive:view(任意节点)的角色 → 自动获得两新码(存量访问不变)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, nm.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.permission = 'incentive:view'
JOIN `sys_menu` nm ON nm.permission IN ('incentive:stats:view', 'incentive:rank:view')
WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.role_id = rm.role_id AND x.menu_id = nm.id);

-- 4) 激励统计菜单节点改绑新码(与 MP 页签门禁/后端守卫三方同源; 节点本身保留, 仅换码)
UPDATE `sys_menu` SET permission = 'incentive:stats:view'
WHERE path = '/incentive/stats' AND permission = 'incentive:view';

-- 5) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
