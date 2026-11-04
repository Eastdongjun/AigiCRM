-- ============================================================
-- V2026.11.05.1__pc_menu_restructure_incentive_perm_center.sql
-- 【2026-11-05 用户五令】PC 侧栏结构纠偏(三件事一并):
--   ①"PC端还是叫四项激励，激励统计还是四项激励下面的" → 撤销 V2026.11.04.2 的 PC 拆组:
--     /incentive-group 组名改回「四项激励」(撤销 2026-10-21 更名的库侧), 激励统计页节点改挂回组,
--     两新码节点(激励统计查看/激励排行查看)归位组下, 删除独立组 /incentive-reward-group。
--     激励上传/激励查询改挂名下组不变(path 不动, 仅组名变化), PC 路由 meta 由前端同批归位。
--   ②"PC端也需要增加一个四项服务，定位=给客户展示案例库" → 新建「四项服务」组(/service-case-group)
--     + 案例库页节点(/case-library, 照片墙形态页面由前端同批新增)。
--     权限码复用 incentive:view: 数据面 /incentive/customer-records 本就 incentive:view 守护,
--     不新增码、不做接口∨补丁(注册表模型: 菜单节点/路由 meta/后端数据面三方同码)。
--   ③"权限中心收敛到系统设置里面" → 删除独立菜单节点 /system/perm-center,
--     页面转为 系统设置(config.vue) 内「权限中心」页签(前端同批); 后端 /system/perm-center/preview
--     接口路径不变(API≠页面菜单, 零 Java 改动)。
-- 机制依据(同 V2026.11.04.2): 侧栏分组=菜单树 sys_role_menu 授权制; 节点引用一律按 path/permission
--   归位, 零硬编码 id(快照库与生产 id 不同亦可跑); 新组节点 permission 留空=纯目录行不产生码。
-- 幂等: 建组 NOT EXISTS/改名与改挂 UPDATE 天然幂等/授权回填 NOT EXISTS 双守卫/删除天然幂等。
-- 缓存: 末尾 perm_version 全员 +1(直改库自愈)。
-- 回滚(人工谨慎, 逐条核对后再执行):
--   UPDATE sys_menu SET menu_name='四项服务' WHERE path='/incentive-group' AND menu_type=2;
--   -- 重建独立四项激励组按 V2026.11.04.2 第2~5步正向重跑; 本迁移第2/3步改挂无需单独回滚(其幂等)
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT m.id FROM (SELECT id FROM sys_menu WHERE path IN ('/service-case-group','/case-library') AND menu_type=2) m);
--   DELETE FROM sys_menu WHERE path IN ('/service-case-group','/case-library') AND menu_type=2;
--   -- 权限中心节点回滚: 按 V2026.11.03.2 第1/2步正向重跑
--   UPDATE sys_user SET perm_version = perm_version + 1;
-- ============================================================

-- 1) 组名纠偏: 四项服务 → 四项激励
UPDATE sys_menu SET menu_name = '四项激励'
WHERE path = '/incentive-group' AND menu_type = 2 AND menu_name = '四项服务';

-- 2) 激励统计页节点改挂回 /incentive-group(撤销 V2026.11.04.2 第3步)
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/incentive-group' AND menu_type = 2 LIMIT 1) g)
WHERE path = '/incentive/stats' AND menu_type = 2;

-- 3) 两新码节点归位 /incentive-group 下(纯摆放, 撤销 V2026.11.04.2 第4步)
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/incentive-group' AND menu_type = 2 LIMIT 1) g)
WHERE permission IN ('incentive:stats:view', 'incentive:rank:view') AND menu_type = 3;

-- 4) 删除独立四项激励组及其授权(撤销 V2026.11.04.2 第2/5步; 派生表包装规避 MySQL 同表更新限制)
DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/incentive-reward-group' AND menu_type = 2) g);
DELETE FROM sys_menu WHERE path = '/incentive-reward-group' AND menu_type = 2;

-- 5) 授权安全网: 现持激励统计节点的角色 → 确保持有 /incentive-group 组节点(存量可见不变)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, g.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.path = '/incentive/stats' AND om.menu_type = 2
JOIN `sys_menu` g ON g.path = '/incentive-group' AND g.menu_type = 2
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = g.id);

-- 6) 新组: 四项服务(业务核心下, 与四项激励组同级相邻; sort 6=第4步删除后腾出的空位;
--    permission 留空=纯目录行)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT COALESCE((SELECT p.parent_id FROM (SELECT parent_id FROM sys_menu WHERE path = '/incentive-group' AND menu_type = 2 LIMIT 1) p), 0),
       '四项服务', 2, '/service-case-group', '', '', 'Collection', 6, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.path = '/service-case-group' AND x.menu_type = 2);

-- 7) 新页: 案例库(照片墙案例展示; 码=incentive:view 三方同源)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT g.id, '案例库', 2, '/case-library', 'incentive/case-library', 'incentive:view', 'Picture', 1, 1, 1
FROM (SELECT id FROM sys_menu WHERE path = '/service-case-group' AND menu_type = 2 LIMIT 1) g
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.permission = 'incentive:view' AND x.path = '/case-library' AND x.menu_type = 2);

-- 8) 授权回填: 现持四项激励组(/incentive-group, incentive:view 组节点)的角色 → 补授 四项服务组+案例库
--   (存量访问不变, 此后管理员可在权限中心分别勾选)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, m.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.path = '/incentive-group' AND om.menu_type = 2
JOIN `sys_menu` m ON m.path IN ('/service-case-group', '/case-library') AND m.menu_type = 2
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id);

-- 9) 权限中心收敛: 删除独立菜单节点及其授权(页面转系统设置内页签; 唯一可写入口不变, 只是入口位置)
DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT m.id FROM (SELECT id FROM sys_menu WHERE path = '/system/perm-center' AND menu_type = 2) m);
DELETE FROM sys_menu WHERE path = '/system/perm-center' AND menu_type = 2;

-- 10) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
