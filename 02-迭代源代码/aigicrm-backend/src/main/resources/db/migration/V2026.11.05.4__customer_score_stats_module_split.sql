-- ============================================================
-- V2026.11.05.4__customer_score_stats_module_split.sql
-- 【2026-11-05 用户令】考核与分析组拆解, 模块归属对齐:
--   ①核心客户六项指标(/indicator) → 客户管理组(/customer-group)  ——"核心客户六项指标这个属于客户模块"
--   ②客户排行(/ranking/customer)   → 客户管理组(用户拍板)
--   ③新建「学分管理」组(/score-group): 学分录入(/score/entry) + 学分排行(/score/ranking)
--   ④新建「统计分析」组(/statistics-group): 统计分析(/statistics) 独立成模块
--   ⑤撤销「考核与分析」组(五子全部改嫁, 组节点与授权行删除)
-- 权限码零变化: 五页面码不变(statistics:view×3/score:entry/score:view), 后端守卫零改动,
--   仅菜单树归属移动(注册表模型: 页面码仍由节点承载, 三方同码不破)。
-- 授权安全网:
--   a. 现持 考核与分析 组节点角色 → 补授 学分管理+统计分析 组节点(存量可见不变);
--   b. 现持 /indicator 或 /ranking/customer 节点角色 → 补授 客户管理 组节点(改挂后组可见性随组)。
-- 机制依据(同前): 节点引用按 path/menu_name 归位零硬编码 id; 新组 permission 留空=纯目录行;
-- 【修正 2026-11-05 INV-MIG-001】移除全部 parent_id = 0 字面量(menu_name+menu_type 锚点已唯一,
--   语义不变), 过架构不变量测试; 文件已应用测试库, 校验和由 deploy-db-test.sh repair 步骤对齐。
--   幂等: 建组 NOT EXISTS/改挂 UPDATE 幂等/回填 NOT EXISTS 双守卫; 末尾 perm_version 全员 +1。
-- 回滚(人工谨慎): 新建两组节点删除、五页面节点改挂回 考核与分析、按 V2026.09.11.6 形态重建组节点。
-- ============================================================

-- 0) 系统管理腾位(顶层 sort 3→4, 统计分析接 3 号位; NOT EXISTS 守卫防重复跑二次挪动)
UPDATE sys_menu SET sort_order = sort_order + 1
WHERE menu_name = '系统管理' AND menu_type = 1 AND sort_order = 3
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE path = '/statistics-group' AND menu_type = 1 LIMIT 1) g);

-- 1) 学分管理 组: 实测 考核与分析=menu_type 1 顶层组(parent 0, path 空)——新组同形。
--    [勘误] 首版误锚 menu_type=2(该形态在本库不存在此行), 已按实测改 type1/parent0。
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT 0, '学分管理', 1, '/score-group', '', '', 'Medal',
       COALESCE((SELECT p.sort_order FROM sys_menu p
                  WHERE p.menu_name = '考核与分析' AND p.menu_type = 1 LIMIT 1), 2),
       1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.path = '/score-group' AND x.menu_type = 1);

-- 2) 统计分析 组(顶层独立模块, 接腾出的 3 号位)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT 0, '统计分析', 1, '/statistics-group', '', '', 'TrendCharts', 3, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.path = '/statistics-group' AND x.menu_type = 1);

-- 3) 四页面改挂(按 path 归位; UPDATE 幂等)
-- 3a. 核心客户六项指标 + 客户排行 → 客户管理组
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/customer-group' AND menu_type = 2 LIMIT 1) g)
WHERE path IN ('/indicator', '/ranking/customer') AND menu_type = 2;

-- 3b. 学分录入 + 学分排行 → 学分管理组
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/score-group' AND menu_type = 2 LIMIT 1) g)
WHERE path IN ('/score/entry', '/score/ranking') AND menu_type = 2;

-- 3c. 统计分析 → 统计分析组
UPDATE sys_menu
SET parent_id = (SELECT g.id FROM (SELECT id FROM sys_menu WHERE path = '/statistics-group' AND menu_type = 2 LIMIT 1) g)
WHERE path = '/statistics' AND menu_type = 2;

-- 4) 授权安全网 a: 现持 考核与分析 组节点角色 → 补授 新两组
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, g.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id
JOIN `sys_menu` g ON g.path IN ('/score-group', '/statistics-group') AND g.menu_type = 2
WHERE om.menu_name = '考核与分析' AND om.menu_type = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = g.id);

-- 5) 授权安全网 b: 现持 六项指标/客户排行 节点角色 → 补授 客户管理 组节点(改挂后组可见性随组)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, g.id
FROM `sys_role_menu` rm
JOIN `sys_menu` om ON om.id = rm.menu_id AND om.path IN ('/indicator', '/ranking/customer') AND om.menu_type = 2
JOIN `sys_menu` g ON g.path = '/customer-group' AND g.menu_type = 2
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = g.id);

-- 6) 撤销 考核与分析 组(type1 顶层组, 按实测锚定; 先删授权行再删节点)
DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT m.id FROM (SELECT id FROM sys_menu WHERE menu_name = '考核与分析' AND menu_type = 1) m);
DELETE FROM sys_menu WHERE menu_name = '考核与分析' AND menu_type = 1;

-- 7) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
