-- ============================================================
-- V2026.09.11.6__seed_permission_dictionary_and_roles.sql
-- 全新库 bootstrap 种子：角色 + 完整权限字典（三级菜单树）+ 角色绑定
--
-- 数据来源：只读生产 dump（2026-09-05 快照），归档 sha256
--   001d926d9170b3dd502542322e69b52545c2e078b1dae15f961670330f6c1cd3
--   （与同目录 MANIFEST.md 一致）。**未连接生产主机**。
--
-- 【为什么需要】
--   全链重放实测（Flyway 9.22.3 = 生产同镜像 tag）：全新库仅 sys_menu 1 行、
--   sys_role 0 行、sys_role_menu 0 行 ⇒ 0 用户 / 0 角色 / 1 权限码，
--   既无法登录，也无法在角色管理页（SysMenuService.tree() 带 status=1 过滤）
--   配置任何权限。
--   根因：V1__baseline.sql 是纯结构（56 CREATE TABLE / 0 INSERT）；后续涉及
--   sys_menu 的 INSERT 多以 `FROM sys_menu WHERE permission='<既有码>'` 为锚点，
--   在空库上自然产出 0 行（V16/V24/V25/V26/V30 均是此模式）。
--
-- 【本迁移做什么】
--   ① 角色 4 个（生产 role_code / role_name / data_scope）
--   ② 菜单树 83 行（逐行复制生产，完整保留父子层级）
--   ③ 角色-菜单绑定 208 条（逐菜单行精确复制生产 sys_role_menu）
--
-- 【守卫设计：两级，均由实测缺陷倒逼得出】
--   · 常规行：按精确身份键 (permission, menu_name, menu_type, parent_id) 判重。
--     —— 不能用"只按 permission"判重：生产上一部分权限码**对应多行菜单**
--        （customer:view 同时是「客户列表」「客户详情」；statistics:view 同时是
--        「统计分析」「核心客户六项指标」），一刀切会误跳过后继行，
--        导致全新环境**侧边栏缺菜单项**。
--     —— 判重键必须含 parent_id：生产有两行都叫「系统设置」
--        （id=34 挂「系统管理」、id=119 挂 id=34），否则会误跳过并少 3 条绑定。
--   · 链已建码：对本仓库迁移链**先行创建过**的权限码
--        file:upload / file:edit / file:delete / file:share / file:folder:manage
--        （由 V2026.09.11.3）与 ranking:customer:view（由 V2026.09.11.4），
--     额外叠加 permission 全局判重。否则种子会因 parent_id 不同而**再插一份**，
--      产生同码重复行（实测曾出现 menu=90 而非 83）。
--
-- 【ID 策略】
--   菜单行用 **9000 + 生产 id** 显式 id，parent_id 同步映射，完整保留父子层级。
--   9000 区间避开各环境既有 id（全新库跑完迁移链后 max(id) < 200，生产为 128）。
--
-- 【对既有环境的影响】
--   生产/测试库已存在这些行 ⇒ 守卫命中，本迁移 **no-op**，零改动。
--   仅在全新库上生效。
-- ============================================================

-- ------------------------------------------------------------
-- ① 角色
-- ------------------------------------------------------------
INSERT INTO `sys_role` (`role_name`, `role_code`, `data_scope`, `status`, `created_at`)
SELECT '系统管理员', 'admin', 'all', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = 'admin');
INSERT INTO `sys_role` (`role_name`, `role_code`, `data_scope`, `status`, `created_at`)
SELECT '行政', '05', 'office', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = '05');
INSERT INTO `sys_role` (`role_name`, `role_code`, `data_scope`, `status`, `created_at`)
SELECT '销售总监', '0', 'custom', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = '0');
INSERT INTO `sys_role` (`role_name`, `role_code`, `data_scope`, `status`, `created_at`)
SELECT '销售员', '06', 'team', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = '06');

-- ------------------------------------------------------------
-- ② 菜单树（83 行，已拓扑排序：父先于子）
-- ------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9001, 0, '首页', 1, NULL, NULL, NULL, 'HomeFilled', 0, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '首页' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9002, 9001, '工作台', 2, '/dashboard', 'dashboard/index', 'dashboard:view', 'Monitor', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '工作台' AND x.`menu_type` = 2 AND x.`parent_id` = 9001 AND x.`permission` = 'dashboard:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9010, 0, '业务核心', 1, NULL, NULL, NULL, 'Briefcase', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '业务核心' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9011, 9010, '客户管理', 2, '/customer-group', 'customer/list', 'customer:view', 'User', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9010 AND x.`permission` = 'customer:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9012, 9011, '新增客户', 2, '/customer/add', 'customer/add', 'customer:add', 'Plus', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '新增客户' AND x.`menu_type` = 2 AND x.`parent_id` = 9011 AND x.`permission` = 'customer:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9013, 9010, '客户详情', 2, '/customer/detail/:id', 'customer/detail', 'customer:view', 'Document', 3, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户详情' AND x.`menu_type` = 2 AND x.`parent_id` = 9010 AND x.`permission` = 'customer:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9014, 9010, '文件管理', 2, '/file', 'file/index', 'file:view', 'Folder', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '文件管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9010 AND x.`permission` = 'file:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9015, 9010, '四项激励', 2, '/incentive-group', 'incentive/index', 'incentive:view', 'Trophy', 5, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '四项激励' AND x.`menu_type` = 2 AND x.`parent_id` = 9010 AND x.`permission` = 'incentive:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9016, 9010, '提醒管理', 2, '/reminder', 'reminder/index', 'reminder:view', 'Bell', 6, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '提醒管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9010 AND x.`permission` = 'reminder:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9017, 9010, '公海管理', 2, '/sea-group', 'sea/index', 'sea:view', 'Ship', 7, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '公海管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9010 AND x.`permission` = 'sea:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9018, 9010, '审批管理', 2, '/approval', 'approval/index', 'approval:view', 'CircleCheck', 8, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '审批管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9010 AND x.`permission` = 'approval:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9020, 0, '考核与分析', 1, NULL, NULL, NULL, 'DataAnalysis', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '考核与分析' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9021, 9020, '统计分析', 2, '/statistics', 'statistics/index', 'statistics:view', 'TrendCharts', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '统计分析' AND x.`menu_type` = 2 AND x.`parent_id` = 9020 AND x.`permission` = 'statistics:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9022, 9020, '积分榜', 2, '/scoreboard', 'statistics/scoreboard', 'statistics:scoreboard', 'Medal', 2, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '积分榜' AND x.`menu_type` = 2 AND x.`parent_id` = 9020 AND x.`permission` = 'statistics:scoreboard');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9030, 0, '系统管理', 1, NULL, NULL, NULL, 'Setting', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '系统管理' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9032, 9030, '组织架构', 2, '/system/org-group', 'system/org', 'system:dept:view', 'OfficeBuilding', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '组织架构' AND x.`menu_type` = 2 AND x.`parent_id` = 9030 AND x.`permission` = 'system:dept:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9031, 9032, '人员管理', 2, '/system/user', 'system/user', 'system:user:view', 'UserFilled', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '人员管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9032 AND x.`permission` = 'system:user:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9033, 9032, '角色管理', 2, '/system/role', 'system/role', 'system:role:view', 'Key', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '角色管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9032 AND x.`permission` = 'system:role:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9034, 9030, '系统设置', 2, '/system/config-group', 'system/config', 'system:config:view', 'Tools', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '系统设置' AND x.`menu_type` = 2 AND x.`parent_id` = 9030 AND x.`permission` = 'system:config:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9035, 9030, '日志管理', 2, '/system/log', 'system/log', 'system:log:view', 'Notebook', 5, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '日志管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9030 AND x.`permission` = 'system:log:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9040, 9011, '客户新增', 3, NULL, NULL, 'customer:add', NULL, 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户新增' AND x.`menu_type` = 3 AND x.`parent_id` = 9011 AND x.`permission` = 'customer:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9041, 9011, '客户编辑', 3, NULL, NULL, 'customer:edit', NULL, 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9011 AND x.`permission` = 'customer:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9042, 9011, '客户删除', 3, NULL, NULL, 'customer:delete', NULL, 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户删除' AND x.`menu_type` = 3 AND x.`parent_id` = 9011 AND x.`permission` = 'customer:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9043, 9011, '客户导出', 3, NULL, NULL, 'customer:export', NULL, 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户导出' AND x.`menu_type` = 3 AND x.`parent_id` = 9011 AND x.`permission` = 'customer:export');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9044, 9017, '公海捞取', 3, NULL, NULL, 'sea:claim', NULL, 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '公海捞取' AND x.`menu_type` = 3 AND x.`parent_id` = 9017 AND x.`permission` = 'sea:claim');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9045, 9018, '审批通过', 3, NULL, NULL, 'approval:approve', NULL, 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '审批通过' AND x.`menu_type` = 3 AND x.`parent_id` = 9018 AND x.`permission` = 'approval:approve');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9046, 9018, '审批驳回', 3, NULL, NULL, 'approval:reject', NULL, 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '审批驳回' AND x.`menu_type` = 3 AND x.`parent_id` = 9018 AND x.`permission` = 'approval:reject');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9047, 9014, '文件上传', 3, NULL, NULL, 'file:upload', NULL, 1, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` y WHERE y.`permission` = 'file:upload')
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '文件上传' AND x.`menu_type` = 3 AND x.`parent_id` = 9014 AND x.`permission` = 'file:upload');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9048, 9014, '文件删除', 3, NULL, NULL, 'file:delete', NULL, 2, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` y WHERE y.`permission` = 'file:delete')
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '文件删除' AND x.`menu_type` = 3 AND x.`parent_id` = 9014 AND x.`permission` = 'file:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9049, 9031, '用户新增', 3, NULL, NULL, 'system:user:add', NULL, 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '用户新增' AND x.`menu_type` = 3 AND x.`parent_id` = 9031 AND x.`permission` = 'system:user:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9050, 9031, '用户编辑', 3, NULL, NULL, 'system:user:edit', NULL, 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '用户编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9031 AND x.`permission` = 'system:user:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9051, 9033, '角色新增', 3, NULL, NULL, 'system:role:add', NULL, 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '角色新增' AND x.`menu_type` = 3 AND x.`parent_id` = 9033 AND x.`permission` = 'system:role:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9052, 9033, '角色编辑', 3, NULL, NULL, 'system:role:edit', NULL, 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '角色编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9033 AND x.`permission` = 'system:role:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9054, 9017, '公海删除', 3, NULL, NULL, 'sea:pool:delete', NULL, 3, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '公海删除' AND x.`menu_type` = 3 AND x.`parent_id` = 9017 AND x.`permission` = 'sea:pool:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9060, 9020, '积分录入', 2, '/score/entry', 'score/entry', 'score:entry', 'Edit', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '积分录入' AND x.`menu_type` = 2 AND x.`parent_id` = 9020 AND x.`permission` = 'score:entry');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9061, 9020, '积分排行', 2, '/score/ranking', 'score/ranking', 'score:ranking', 'Medal', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '积分排行' AND x.`menu_type` = 2 AND x.`parent_id` = 9020 AND x.`permission` = 'score:ranking');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9062, 9015, '激励编辑', 2, 'N', 'N', 'incentive:edit', 'N', 90, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励编辑' AND x.`menu_type` = 2 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9063, 9015, '激励删除', 2, 'N', 'N', 'incentive:delete', 'N', 91, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励删除' AND x.`menu_type` = 2 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9071, 9032, '岗位管理', 2, '/system/position', 'system/position/index', 'system:position:view', 'post', 7, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '岗位管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9032 AND x.`permission` = 'system:position:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9072, 9071, '岗位新增', 3, 'N', 'N', 'system:position:add', 'N', 1, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '岗位新增' AND x.`menu_type` = 3 AND x.`parent_id` = 9071 AND x.`permission` = 'system:position:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9073, 9071, '岗位编辑', 3, 'N', 'N', 'system:position:edit', 'N', 2, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '岗位编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9071 AND x.`permission` = 'system:position:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9074, 9034, '权限规则', 2, '/system/rule', 'system/rule/index', 'system:rule:view', 'lock', 8, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '权限规则' AND x.`menu_type` = 2 AND x.`parent_id` = 9034 AND x.`permission` = 'system:rule:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9075, 9074, '规则新增', 3, 'N', 'N', 'system:rule:add', 'N', 1, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '规则新增' AND x.`menu_type` = 3 AND x.`parent_id` = 9074 AND x.`permission` = 'system:rule:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9076, 9074, '规则编辑', 3, 'N', 'N', 'system:rule:edit', 'N', 2, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '规则编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9074 AND x.`permission` = 'system:rule:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9077, 9032, '集团管理', 2, '/system/group', 'system/group/index', 'system:group:view', 'apartment', 9, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '集团管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9032 AND x.`permission` = 'system:group:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9078, 9077, '集团新增', 3, 'N', 'N', 'system:group:add', 'N', 1, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '集团新增' AND x.`menu_type` = 3 AND x.`parent_id` = 9077 AND x.`permission` = 'system:group:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9079, 9077, '集团编辑', 3, 'N', 'N', 'system:group:edit', 'N', 2, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '集团编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9077 AND x.`permission` = 'system:group:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9080, 9010, '客户导入', 3, 'N', 'N', 'customer:import', 'N', 10, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户导入' AND x.`menu_type` = 3 AND x.`parent_id` = 9010 AND x.`permission` = 'customer:import');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9081, 9014, '文件编辑', 3, 'N', 'N', 'file:edit', 'N', 2, 0, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` y WHERE y.`permission` = 'file:edit')
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '文件编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9014 AND x.`permission` = 'file:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9082, 9020, '积分查看', 2, 'N', 'N', 'score:view', 'N', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '积分查看' AND x.`menu_type` = 2 AND x.`parent_id` = 9020 AND x.`permission` = 'score:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9083, 9020, '积分管理', 3, 'N', 'N', 'score:manage', 'N', 4, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '积分管理' AND x.`menu_type` = 3 AND x.`parent_id` = 9020 AND x.`permission` = 'score:manage');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9084, 9017, '公海配置查看', 3, 'N', 'N', 'sea:config:view', 'N', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '公海配置查看' AND x.`menu_type` = 3 AND x.`parent_id` = 9017 AND x.`permission` = 'sea:config:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9085, 9017, '公海配置编辑', 3, 'N', 'N', 'sea:config:edit', 'N', 6, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '公海配置编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9017 AND x.`permission` = 'sea:config:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9086, 9030, '系统配置编辑', 3, 'N', 'N', 'system:config:edit', 'N', 10, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '系统配置编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9030 AND x.`permission` = 'system:config:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9087, 9030, '部门编辑', 3, 'N', 'N', 'system:dept:edit', 'N', 11, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '部门编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9030 AND x.`permission` = 'system:dept:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9088, 9030, '菜单查看', 3, 'N', 'N', 'system:menu:view', 'N', 12, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '菜单查看' AND x.`menu_type` = 3 AND x.`parent_id` = 9030 AND x.`permission` = 'system:menu:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9089, 9030, '菜单编辑', 3, 'N', 'N', 'system:menu:edit', 'N', 13, 0, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '菜单编辑' AND x.`menu_type` = 3 AND x.`parent_id` = 9030 AND x.`permission` = 'system:menu:edit');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9090, 9030, '组织管理员', 3, 'N', 'N', 'system:org:admin', 'N', 14, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '组织管理员' AND x.`menu_type` = 3 AND x.`parent_id` = 9030 AND x.`permission` = 'system:org:admin');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9091, 9031, '删除用户', 3, 'N', 'N', 'system:user:delete', 'N', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '删除用户' AND x.`menu_type` = 3 AND x.`parent_id` = 9031 AND x.`permission` = 'system:user:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9092, 9033, '删除角色', 3, 'N', 'N', 'system:role:delete', 'N', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '删除角色' AND x.`menu_type` = 3 AND x.`parent_id` = 9033 AND x.`permission` = 'system:role:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9095, 9071, '删除岗位', 3, 'N', 'N', 'system:position:delete', 'N', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '删除岗位' AND x.`menu_type` = 3 AND x.`parent_id` = 9071 AND x.`permission` = 'system:position:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9096, 9074, '删除规则', 3, 'N', 'N', 'system:rule:delete', 'N', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '删除规则' AND x.`menu_type` = 3 AND x.`parent_id` = 9074 AND x.`permission` = 'system:rule:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9097, 9077, '删除集团', 3, 'N', 'N', 'system:group:delete', 'N', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '删除集团' AND x.`menu_type` = 3 AND x.`parent_id` = 9077 AND x.`permission` = 'system:group:delete');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9098, 0, '基础数据-部门', 2, 'N', 'N', 'system:dept:view', 'N', 90, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '基础数据-部门' AND x.`menu_type` = 2 AND x.`parent_id` = 0 AND x.`permission` = 'system:dept:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9099, 0, '基础数据-字典', 2, 'N', 'N', 'system:config:view', 'N', 91, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '基础数据-字典' AND x.`menu_type` = 2 AND x.`parent_id` = 0 AND x.`permission` = 'system:config:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9100, 0, '基础数据-用户', 2, 'N', 'N', 'system:user:view', 'N', 92, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '基础数据-用户' AND x.`menu_type` = 2 AND x.`parent_id` = 0 AND x.`permission` = 'system:user:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9105, 9015, '激励统计', 2, '/incentive/stats', 'incentive/stats', 'incentive:view', 'TrendCharts', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励统计' AND x.`menu_type` = 2 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9106, 9015, '激励查询', 2, '/incentive/search', 'incentive/search', 'incentive:view', 'Search', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励查询' AND x.`menu_type` = 2 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9107, 9017, '捞取记录', 2, '/sea/records', 'sea/records', 'sea:view', 'Ship', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '捞取记录' AND x.`menu_type` = 2 AND x.`parent_id` = 9017 AND x.`permission` = 'sea:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9108, 9020, '核心客户六项指标', 2, '/indicator', 'indicator/index', 'statistics:view', 'Aim', 50, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '核心客户六项指标' AND x.`menu_type` = 2 AND x.`parent_id` = 9020 AND x.`permission` = 'statistics:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9110, 9011, '客户分享', 3, NULL, 'N', 'customer:share', 'N', 99, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户分享' AND x.`menu_type` = 3 AND x.`parent_id` = 9011 AND x.`permission` = 'customer:share');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9111, 9014, '文件分享', 3, NULL, 'N', 'file:share', 'N', 99, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` y WHERE y.`permission` = 'file:share')
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '文件分享' AND x.`menu_type` = 3 AND x.`parent_id` = 9014 AND x.`permission` = 'file:share');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9113, 9017, '公海导入', 3, NULL, 'N', 'sea:import', 'N', 99, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '公海导入' AND x.`menu_type` = 3 AND x.`parent_id` = 9017 AND x.`permission` = 'sea:import');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9114, 9015, '激励上传', 2, '/incentive', 'N', 'incentive:add', 'N', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励上传' AND x.`menu_type` = 2 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:add');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9115, 9011, '客户列表', 2, '/customer', 'customer/list', 'customer:view', 'User', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '客户列表' AND x.`menu_type` = 2 AND x.`parent_id` = 9011 AND x.`permission` = 'customer:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9116, 9017, '公海池', 2, '/sea', 'sea/index', 'sea:view', 'Ship', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '公海池' AND x.`menu_type` = 2 AND x.`parent_id` = 9017 AND x.`permission` = 'sea:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9118, 9032, '部门管理', 2, '/system/org', 'system/dept', 'system:dept:view', 'OfficeBuilding', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '部门管理' AND x.`menu_type` = 2 AND x.`parent_id` = 9032 AND x.`permission` = 'system:dept:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9119, 9034, '系统设置', 2, '/system/config', 'system/config', 'system:config:view', 'Setting', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '系统设置' AND x.`menu_type` = 2 AND x.`parent_id` = 9034 AND x.`permission` = 'system:config:view');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9120, 9010, '文件夹管理', 3, 'N', 'N', 'file:folder:manage', 'N', 4, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` y WHERE y.`permission` = 'file:folder:manage')
  AND NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '文件夹管理' AND x.`menu_type` = 3 AND x.`parent_id` = 9010 AND x.`permission` = 'file:folder:manage');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9121, 9015, '激励管理(他人记录)', 3, 'N', 'N', 'incentive:manage', 'N', 96, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励管理(他人记录)' AND x.`menu_type` = 3 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:manage');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9122, 9015, '激励查看(全部)', 3, 'N', 'N', 'incentive:view-all', 'N', 97, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励查看(全部)' AND x.`menu_type` = 3 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:view-all');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9123, 9015, '激励查看(已分享)', 3, 'N', 'N', 'incentive:view-shared', 'N', 98, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '激励查看(已分享)' AND x.`menu_type` = 3 AND x.`parent_id` = 9015 AND x.`permission` = 'incentive:view-shared');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT 9124, 9014, '文件ACL管理', 3, 'N', 'N', 'file:acl:manage', 'N', 90, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '文件ACL管理' AND x.`menu_type` = 3 AND x.`parent_id` = 9014 AND x.`permission` = 'file:acl:manage');

-- ------------------------------------------------------------
-- ------------------------------------------------------------
-- ③ 角色-菜单绑定（208 条，逐菜单行精确复制生产）
--    菜单行消歧：同 permission + 同 menu_name 可能有多行（如 system:config:view
--    有两行都叫「系统设置」，分别挂「系统管理」与 id=34）。故 join 条件必须
--    叠加 parent_id（映射后的 9000+生产id），否则会把两行都匹配上，
--    产生多余绑定（实测曾多 1 条）。
--    链已建行（file:* / ranking:customer:view）的 menu_name 与生产同名，
--    但 parent_id 不同（链用真实 id），故对这批码按 (permission, menu_name, menu_type) 匹配。
-- ------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND m.`parent_id` = 9001
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND m.`parent_id` = 9001
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND m.`parent_id` = 9001
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND m.`parent_id` = 9001
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'statistics:view' AND m.`menu_name` = '统计分析' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'statistics:view' AND m.`menu_name` = '统计分析' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '系统管理' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '系统管理' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`menu_name` = '系统管理' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:view' AND m.`menu_name` = '人员管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:view' AND m.`menu_name` = '人员管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:view' AND m.`menu_name` = '人员管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '组织架构' AND m.`menu_type` = 2 AND m.`parent_id` = 9030
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '组织架构' AND m.`menu_type` = 2 AND m.`parent_id` = 9030
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '组织架构' AND m.`menu_type` = 2 AND m.`parent_id` = 9030
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:view' AND m.`menu_name` = '角色管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:view' AND m.`menu_name` = '角色管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND m.`parent_id` = 9030
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND m.`parent_id` = 9030
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:log:view' AND m.`menu_name` = '日志管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9030
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:log:view' AND m.`menu_name` = '日志管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9030
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:export' AND m.`menu_name` = '客户导出' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:export' AND m.`menu_name` = '客户导出' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:export' AND m.`menu_name` = '客户导出' AND m.`menu_type` = 3 AND m.`parent_id` = 9011
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND m.`parent_id` = 9018
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:add' AND m.`menu_name` = '用户新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:add' AND m.`menu_name` = '用户新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:add' AND m.`menu_name` = '用户新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:edit' AND m.`menu_name` = '用户编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:edit' AND m.`menu_name` = '用户编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:edit' AND m.`menu_name` = '用户编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:add' AND m.`menu_name` = '角色新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9033
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:add' AND m.`menu_name` = '角色新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9033
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:edit' AND m.`menu_name` = '角色编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9033
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:edit' AND m.`menu_name` = '角色编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9033
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:entry' AND m.`menu_name` = '积分录入' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:entry' AND m.`menu_name` = '积分录入' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:entry' AND m.`menu_name` = '积分录入' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:edit' AND m.`menu_name` = '激励编辑' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:edit' AND m.`menu_name` = '激励编辑' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:edit' AND m.`menu_name` = '激励编辑' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:view' AND m.`menu_name` = '岗位管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:view' AND m.`menu_name` = '岗位管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:add' AND m.`menu_name` = '岗位新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9071
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:add' AND m.`menu_name` = '岗位新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9071
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:edit' AND m.`menu_name` = '岗位编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9071
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:edit' AND m.`menu_name` = '岗位编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9071
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:view' AND m.`menu_name` = '权限规则' AND m.`menu_type` = 2 AND m.`parent_id` = 9034
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:view' AND m.`menu_name` = '权限规则' AND m.`menu_type` = 2 AND m.`parent_id` = 9034
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:add' AND m.`menu_name` = '规则新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9074
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:add' AND m.`menu_name` = '规则新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9074
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:edit' AND m.`menu_name` = '规则编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9074
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:edit' AND m.`menu_name` = '规则编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9074
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:view' AND m.`menu_name` = '集团管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:view' AND m.`menu_name` = '集团管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:add' AND m.`menu_name` = '集团新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9077
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:add' AND m.`menu_name` = '集团新增' AND m.`menu_type` = 3 AND m.`parent_id` = 9077
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:edit' AND m.`menu_name` = '集团编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9077
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:edit' AND m.`menu_name` = '集团编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9077
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:import' AND m.`menu_name` = '客户导入' AND m.`menu_type` = 3 AND m.`parent_id` = 9010
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:import' AND m.`menu_name` = '客户导入' AND m.`menu_type` = 3 AND m.`parent_id` = 9010
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:import' AND m.`menu_name` = '客户导入' AND m.`menu_type` = 3 AND m.`parent_id` = 9010
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:manage' AND m.`menu_name` = '积分管理' AND m.`menu_type` = 3 AND m.`parent_id` = 9020
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'score:manage' AND m.`menu_name` = '积分管理' AND m.`menu_type` = 3 AND m.`parent_id` = 9020
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:config:view' AND m.`menu_name` = '公海配置查看' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:config:view' AND m.`menu_name` = '公海配置查看' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:config:view' AND m.`menu_name` = '公海配置查看' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:config:edit' AND m.`menu_name` = '公海配置编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:config:edit' AND m.`menu_name` = '公海配置编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:edit' AND m.`menu_name` = '系统配置编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:edit' AND m.`menu_name` = '系统配置编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:edit' AND m.`menu_name` = '部门编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:edit' AND m.`menu_name` = '部门编辑' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:menu:view' AND m.`menu_name` = '菜单查看' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:menu:view' AND m.`menu_name` = '菜单查看' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:org:admin' AND m.`menu_name` = '组织管理员' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:org:admin' AND m.`menu_name` = '组织管理员' AND m.`menu_type` = 3 AND m.`parent_id` = 9030
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:delete' AND m.`menu_name` = '删除用户' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:delete' AND m.`menu_name` = '删除用户' AND m.`menu_type` = 3 AND m.`parent_id` = 9031
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:delete' AND m.`menu_name` = '删除角色' AND m.`menu_type` = 3 AND m.`parent_id` = 9033
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:role:delete' AND m.`menu_name` = '删除角色' AND m.`menu_type` = 3 AND m.`parent_id` = 9033
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:delete' AND m.`menu_name` = '删除岗位' AND m.`menu_type` = 3 AND m.`parent_id` = 9071
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:position:delete' AND m.`menu_name` = '删除岗位' AND m.`menu_type` = 3 AND m.`parent_id` = 9071
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:delete' AND m.`menu_name` = '删除规则' AND m.`menu_type` = 3 AND m.`parent_id` = 9074
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:rule:delete' AND m.`menu_name` = '删除规则' AND m.`menu_type` = 3 AND m.`parent_id` = 9074
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:delete' AND m.`menu_name` = '删除集团' AND m.`menu_type` = 3 AND m.`parent_id` = 9077
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:group:delete' AND m.`menu_name` = '删除集团' AND m.`menu_type` = 3 AND m.`parent_id` = 9077
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '基础数据-部门' AND m.`menu_type` = 2 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '基础数据-部门' AND m.`menu_type` = 2 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:view' AND m.`menu_name` = '基础数据-字典' AND m.`menu_type` = 2 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:view' AND m.`menu_name` = '基础数据-字典' AND m.`menu_type` = 2 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:user:view' AND m.`menu_name` = '基础数据-用户' AND m.`menu_type` = 2 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'statistics:view' AND m.`menu_name` = '核心客户六项指标' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'statistics:view' AND m.`menu_name` = '核心客户六项指标' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'statistics:view' AND m.`menu_name` = '核心客户六项指标' AND m.`menu_type` = 2 AND m.`parent_id` = 9020
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:import' AND m.`menu_name` = '公海导入' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:import' AND m.`menu_name` = '公海导入' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:import' AND m.`menu_name` = '公海导入' AND m.`menu_type` = 3 AND m.`parent_id` = 9017
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND m.`parent_id` = 9015
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND m.`parent_id` = 9011
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND m.`parent_id` = 9017
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '部门管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '部门管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '部门管理' AND m.`menu_type` = 2 AND m.`parent_id` = 9032
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND m.`parent_id` = 9034
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND m.`parent_id` = 9034
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND m.`parent_id` = 9034
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'incentive:manage' AND m.`menu_name` = '激励管理(他人记录)' AND m.`menu_type` = 3 AND m.`parent_id` = 9015
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND m.`parent_id` = 9014
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND m.`parent_id` = 9014
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND m.`parent_id` = 9014
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m
  ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND m.`parent_id` = 9014
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);

-- ==================== 回滚(按需手工执行) ====================
-- 本迁移只新增行、不改既有行。回滚前必须备份，且**不得**按 permission 通配删除
-- （会把生产/测试库的既有行一并删掉）。仅在"确认是全新库"时执行：
--   DELETE rm FROM `sys_role_menu` rm JOIN `sys_role` r ON r.`id`=rm.`role_id`
--     WHERE r.`role_code` IN ('admin','05','0','06');
--   DELETE FROM `sys_role` WHERE `role_code` IN ('admin','05','0','06');
--   DELETE FROM `sys_menu` WHERE `id` BETWEEN 9000 AND 9200;
-- ============================================================
