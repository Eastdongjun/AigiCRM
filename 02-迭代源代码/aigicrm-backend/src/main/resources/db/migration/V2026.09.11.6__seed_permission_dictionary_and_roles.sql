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
--   既无法登录，也无法在角色管理页配置任何权限。
--   根因：V1__baseline.sql 是纯结构（56 CREATE TABLE / 0 INSERT）；后续涉及
--   sys_menu 的 INSERT 多以 `FROM sys_menu WHERE permission='<既有码>'` 为锚点，
--   在空库上自然产出 0 行（V16/V24/V25/V26/V30 均是此模式）。
--
-- 【本迁移做什么】① 角色 4 个 ② 菜单树 83 行 ③ 角色-菜单绑定 208 条
--
-- ============================================================
-- 【⚠️ 守卫键曾有 3 处错误，全部由实测暴露并修正 —— 记录以免重犯】
--
-- 错误① 用重映射后的 parent_id 作守卫（`AND x.parent_id = 9001`）
--   本迁移用 9000+生产id 作为显式 id 并重映射 parent_id 以在全新库还原层级，
--   但既有库的行用真实 id ⇒ 守卫在**任何已有数据的库上永远匹配不到** ⇒
--   整棵树被重复插入。实测：对生产数据应用 → sys_menu 83→154、
--   sys_role_menu 208→396（绑定翻倍）。
--
-- 错误② 业务键只用 (permission, menu_name, menu_type)，漏掉 path
--   生产里两行都叫「系统设置」(system:config:view, type=2) 被误折叠：
--     id=34  path=/system/config-group  ← 分组锚点
--     id=119 path=/system/config        ← **实际可点击页面**
--   导致全新库**缺失 /system/config 页面**（侧边栏 29 vs 生产 30）。
--
-- 错误③ path 用 <=> NULL 比较，与生产存空串不一致
--   生产 menu_type=3（按钮级）行的 path 存的是**空串 ''**，而 dump 导出/字典序
--   处理过程中被当成 NULL 写入种子 ⇒ `<=> NULL` 与 '' 不等 ⇒ 守卫失效 ⇒
--   全部按钮级权限行被重复插入。实测：对生产数据应用 → sys_menu 83→100
--   （新增 17 行全是 menu_type=3 的按钮行）。
--   ⇒ 修正：**path 一律按"空串语义"处理**——插入用 ''，比较也用 ''，
--     彻底避开 NULL 与 '' 的等价性陷阱（NULL 安全比较 <=> 认为二者不同）。
--
-- ⇒ 最终守卫键（含 permission 的行）：
--      (permission, menu_name, menu_type, COALESCE(path,''))
--   不含 permission 的目录行无业务键可用，退化为 (menu_name, menu_type, parent_id)。
-- ============================================================

-- ① 角色

INSERT INTO `sys_role` (`role_name`,`role_code`,`data_scope`,`status`,`created_at`)
SELECT '系统管理员', 'admin', 'all', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = 'admin');
INSERT INTO `sys_role` (`role_name`,`role_code`,`data_scope`,`status`,`created_at`)
SELECT '行政', '05', 'office', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = '05');
INSERT INTO `sys_role` (`role_name`,`role_code`,`data_scope`,`status`,`created_at`)
SELECT '销售总监', '0', 'custom', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = '0');
INSERT INTO `sys_role` (`role_name`,`role_code`,`data_scope`,`status`,`created_at`)
SELECT '销售员', '06', 'team', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` x WHERE x.`role_code` = '06');

-- ② 菜单树（83 行，已拓扑排序）
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9001, 0, '首页', 1, '', '', '', 'HomeFilled', 0, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '首页' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9002, 9001, '工作台', 2, '/dashboard', 'dashboard/index', 'dashboard:view', 'Monitor', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'dashboard:view' AND x.`menu_name` = '工作台' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/dashboard');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9010, 0, '业务核心', 1, '', '', '', 'Briefcase', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '业务核心' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9011, 9010, '客户管理', 2, '/customer-group', 'customer/list', 'customer:view', 'User', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:view' AND x.`menu_name` = '客户管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/customer-group');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9012, 9011, '新增客户', 2, '/customer/add', 'customer/add', 'customer:add', 'Plus', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:add' AND x.`menu_name` = '新增客户' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/customer/add');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9013, 9010, '客户详情', 2, '/customer/detail/:id', 'customer/detail', 'customer:view', 'Document', 3, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:view' AND x.`menu_name` = '客户详情' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/customer/detail/:id');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9014, 9010, '文件管理', 2, '/file', 'file/index', 'file:view', 'Folder', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:view' AND x.`menu_name` = '文件管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/file');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9015, 9010, '四项激励', 2, '/incentive-group', 'incentive/index', 'incentive:view', 'Trophy', 5, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:view' AND x.`menu_name` = '四项激励' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/incentive-group');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9016, 9010, '提醒管理', 2, '/reminder', 'reminder/index', 'reminder:view', 'Bell', 6, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'reminder:view' AND x.`menu_name` = '提醒管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/reminder');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9017, 9010, '公海管理', 2, '/sea-group', 'sea/index', 'sea:view', 'Ship', 7, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:view' AND x.`menu_name` = '公海管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/sea-group');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9018, 9010, '审批管理', 2, '/approval', 'approval/index', 'approval:view', 'CircleCheck', 8, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'approval:view' AND x.`menu_name` = '审批管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/approval');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9020, 0, '考核与分析', 1, '', '', '', 'DataAnalysis', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '考核与分析' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9021, 9020, '统计分析', 2, '/statistics', 'statistics/index', 'statistics:view', 'TrendCharts', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'statistics:view' AND x.`menu_name` = '统计分析' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/statistics');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9022, 9020, '积分榜', 2, '/scoreboard', 'statistics/scoreboard', 'statistics:scoreboard', 'Medal', 2, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'statistics:scoreboard' AND x.`menu_name` = '积分榜' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/scoreboard');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9030, 0, '系统管理', 1, '', '', '', 'Setting', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`menu_name` = '系统管理' AND x.`menu_type` = 1 AND x.`parent_id` = 0);
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9032, 9030, '组织架构', 2, '/system/org-group', 'system/org', 'system:dept:view', 'OfficeBuilding', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:dept:view' AND x.`menu_name` = '组织架构' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/org-group');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9031, 9032, '人员管理', 2, '/system/user', 'system/user', 'system:user:view', 'UserFilled', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:user:view' AND x.`menu_name` = '人员管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/user');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9033, 9032, '角色管理', 2, '/system/role', 'system/role', 'system:role:view', 'Key', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:role:view' AND x.`menu_name` = '角色管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/role');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9034, 9030, '系统设置', 2, '/system/config-group', 'system/config', 'system:config:view', 'Tools', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:config:view' AND x.`menu_name` = '系统设置' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/config-group');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9035, 9030, '日志管理', 2, '/system/log', 'system/log', 'system:log:view', 'Notebook', 5, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:log:view' AND x.`menu_name` = '日志管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/log');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9040, 9011, '客户新增', 3, '', '', 'customer:add', '', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:add' AND x.`menu_name` = '客户新增' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9041, 9011, '客户编辑', 3, '', '', 'customer:edit', '', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:edit' AND x.`menu_name` = '客户编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9042, 9011, '客户删除', 3, '', '', 'customer:delete', '', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:delete' AND x.`menu_name` = '客户删除' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9043, 9011, '客户导出', 3, '', '', 'customer:export', '', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:export' AND x.`menu_name` = '客户导出' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9044, 9017, '公海捞取', 3, '', '', 'sea:claim', '', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:claim' AND x.`menu_name` = '公海捞取' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9045, 9018, '审批通过', 3, '', '', 'approval:approve', '', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'approval:approve' AND x.`menu_name` = '审批通过' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9046, 9018, '审批驳回', 3, '', '', 'approval:reject', '', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'approval:reject' AND x.`menu_name` = '审批驳回' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9047, 9014, '文件上传', 3, '', '', 'file:upload', '', 1, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:upload' AND x.`menu_name` = '文件上传' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9048, 9014, '文件删除', 3, '', '', 'file:delete', '', 2, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:delete' AND x.`menu_name` = '文件删除' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9049, 9031, '用户新增', 3, '', '', 'system:user:add', '', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:user:add' AND x.`menu_name` = '用户新增' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9050, 9031, '用户编辑', 3, '', '', 'system:user:edit', '', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:user:edit' AND x.`menu_name` = '用户编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9051, 9033, '角色新增', 3, '', '', 'system:role:add', '', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:role:add' AND x.`menu_name` = '角色新增' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9052, 9033, '角色编辑', 3, '', '', 'system:role:edit', '', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:role:edit' AND x.`menu_name` = '角色编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9054, 9017, '公海删除', 3, '', '', 'sea:pool:delete', '', 3, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:pool:delete' AND x.`menu_name` = '公海删除' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9060, 9020, '积分录入', 2, '/score/entry', 'score/entry', 'score:entry', 'Edit', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'score:entry' AND x.`menu_name` = '积分录入' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/score/entry');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9061, 9020, '积分排行', 2, '/score/ranking', 'score/ranking', 'score:ranking', 'Medal', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'score:ranking' AND x.`menu_name` = '积分排行' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/score/ranking');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9062, 9015, '激励编辑', 2, '', '', 'incentive:edit', '', 90, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:edit' AND x.`menu_name` = '激励编辑' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9063, 9015, '激励删除', 2, '', '', 'incentive:delete', '', 91, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:delete' AND x.`menu_name` = '激励删除' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9071, 9032, '岗位管理', 2, '/system/position', 'system/position/index', 'system:position:view', 'post', 7, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:position:view' AND x.`menu_name` = '岗位管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/position');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9072, 9071, '岗位新增', 3, '', '', 'system:position:add', '', 1, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:position:add' AND x.`menu_name` = '岗位新增' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9073, 9071, '岗位编辑', 3, '', '', 'system:position:edit', '', 2, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:position:edit' AND x.`menu_name` = '岗位编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9074, 9034, '权限规则', 2, '/system/rule', 'system/rule/index', 'system:rule:view', 'lock', 8, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:rule:view' AND x.`menu_name` = '权限规则' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/rule');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9075, 9074, '规则新增', 3, '', '', 'system:rule:add', '', 1, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:rule:add' AND x.`menu_name` = '规则新增' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9076, 9074, '规则编辑', 3, '', '', 'system:rule:edit', '', 2, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:rule:edit' AND x.`menu_name` = '规则编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9077, 9032, '集团管理', 2, '/system/group', 'system/group/index', 'system:group:view', 'apartment', 9, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:group:view' AND x.`menu_name` = '集团管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/group');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9078, 9077, '集团新增', 3, '', '', 'system:group:add', '', 1, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:group:add' AND x.`menu_name` = '集团新增' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9079, 9077, '集团编辑', 3, '', '', 'system:group:edit', '', 2, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:group:edit' AND x.`menu_name` = '集团编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9080, 9010, '客户导入', 3, '', '', 'customer:import', '', 10, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:import' AND x.`menu_name` = '客户导入' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9081, 9014, '文件编辑', 3, '', '', 'file:edit', '', 2, 0, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:edit' AND x.`menu_name` = '文件编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9082, 9020, '积分查看', 2, '', '', 'score:view', '', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'score:view' AND x.`menu_name` = '积分查看' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9083, 9020, '积分管理', 3, '', '', 'score:manage', '', 4, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'score:manage' AND x.`menu_name` = '积分管理' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9084, 9017, '公海配置查看', 3, '', '', 'sea:config:view', '', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:config:view' AND x.`menu_name` = '公海配置查看' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9085, 9017, '公海配置编辑', 3, '', '', 'sea:config:edit', '', 6, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:config:edit' AND x.`menu_name` = '公海配置编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9086, 9030, '系统配置编辑', 3, '', '', 'system:config:edit', '', 10, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:config:edit' AND x.`menu_name` = '系统配置编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9087, 9030, '部门编辑', 3, '', '', 'system:dept:edit', '', 11, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:dept:edit' AND x.`menu_name` = '部门编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9088, 9030, '菜单查看', 3, '', '', 'system:menu:view', '', 12, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:menu:view' AND x.`menu_name` = '菜单查看' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9089, 9030, '菜单编辑', 3, '', '', 'system:menu:edit', '', 13, 0, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:menu:edit' AND x.`menu_name` = '菜单编辑' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9090, 9030, '组织管理员', 3, '', '', 'system:org:admin', '', 14, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:org:admin' AND x.`menu_name` = '组织管理员' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9091, 9031, '删除用户', 3, '', '', 'system:user:delete', '', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:user:delete' AND x.`menu_name` = '删除用户' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9092, 9033, '删除角色', 3, '', '', 'system:role:delete', '', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:role:delete' AND x.`menu_name` = '删除角色' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9095, 9071, '删除岗位', 3, '', '', 'system:position:delete', '', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:position:delete' AND x.`menu_name` = '删除岗位' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9096, 9074, '删除规则', 3, '', '', 'system:rule:delete', '', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:rule:delete' AND x.`menu_name` = '删除规则' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9097, 9077, '删除集团', 3, '', '', 'system:group:delete', '', 5, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:group:delete' AND x.`menu_name` = '删除集团' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9098, 0, '基础数据-部门', 2, '', '', 'system:dept:view', '', 90, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:dept:view' AND x.`menu_name` = '基础数据-部门' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9099, 0, '基础数据-字典', 2, '', '', 'system:config:view', '', 91, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:config:view' AND x.`menu_name` = '基础数据-字典' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9100, 0, '基础数据-用户', 2, '', '', 'system:user:view', '', 92, 0, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:user:view' AND x.`menu_name` = '基础数据-用户' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9105, 9015, '激励统计', 2, '/incentive/stats', 'incentive/stats', 'incentive:view', 'TrendCharts', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:view' AND x.`menu_name` = '激励统计' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/incentive/stats');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9106, 9015, '激励查询', 2, '/incentive/search', 'incentive/search', 'incentive:view', 'Search', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:view' AND x.`menu_name` = '激励查询' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/incentive/search');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9107, 9017, '捞取记录', 2, '/sea/records', 'sea/records', 'sea:view', 'Ship', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:view' AND x.`menu_name` = '捞取记录' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/sea/records');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9108, 9020, '核心客户六项指标', 2, '/indicator', 'indicator/index', 'statistics:view', 'Aim', 50, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'statistics:view' AND x.`menu_name` = '核心客户六项指标' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/indicator');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9110, 9011, '客户分享', 3, '', '', 'customer:share', '', 99, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:share' AND x.`menu_name` = '客户分享' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9111, 9014, '文件分享', 3, '', '', 'file:share', '', 99, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:share' AND x.`menu_name` = '文件分享' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9113, 9017, '公海导入', 3, '', '', 'sea:import', '', 99, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:import' AND x.`menu_name` = '公海导入' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9114, 9015, '激励上传', 2, '/incentive', '', 'incentive:add', '', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:add' AND x.`menu_name` = '激励上传' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/incentive');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9115, 9011, '客户列表', 2, '/customer', 'customer/list', 'customer:view', 'User', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'customer:view' AND x.`menu_name` = '客户列表' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/customer');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9116, 9017, '公海池', 2, '/sea', 'sea/index', 'sea:view', 'Ship', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:view' AND x.`menu_name` = '公海池' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/sea');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9118, 9032, '部门管理', 2, '/system/org', 'system/dept', 'system:dept:view', 'OfficeBuilding', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:dept:view' AND x.`menu_name` = '部门管理' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/org');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9119, 9034, '系统设置', 2, '/system/config', 'system/config', 'system:config:view', 'Setting', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'system:config:view' AND x.`menu_name` = '系统设置' AND x.`menu_type` = 2 AND COALESCE(x.`path`,'') = '/system/config');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9120, 9010, '文件夹管理', 3, '', '', 'file:folder:manage', '', 4, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:folder:manage' AND x.`menu_name` = '文件夹管理' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9121, 9015, '激励管理(他人记录)', 3, '', '', 'incentive:manage', '', 96, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:manage' AND x.`menu_name` = '激励管理(他人记录)' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9122, 9015, '激励查看(全部)', 3, '', '', 'incentive:view-all', '', 97, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:view-all' AND x.`menu_name` = '激励查看(全部)' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9123, 9015, '激励查看(已分享)', 3, '', '', 'incentive:view-shared', '', 98, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:view-shared' AND x.`menu_name` = '激励查看(已分享)' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 9124, 9014, '文件ACL管理', 3, '', '', 'file:acl:manage', '', 90, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:acl:manage' AND x.`menu_name` = '文件ACL管理' AND x.`menu_type` = 3 AND COALESCE(x.`path`,'') = '');

-- ③ 角色-菜单绑定（208 条）
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '首页' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/dashboard'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/dashboard'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/dashboard'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'dashboard:view' AND m.`menu_name` = '工作台' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/dashboard'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '业务核心' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer-group'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer-group'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer-group'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer-group'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/add'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/add'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/add'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '新增客户' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/add'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/detail/:id'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/detail/:id'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/detail/:id'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户详情' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer/detail/:id'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/file'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/file'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/file'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:view' AND m.`menu_name` = '文件管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/file'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive-group'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive-group'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive-group'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '四项激励' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive-group'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/reminder'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/reminder'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/reminder'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'reminder:view' AND m.`menu_name` = '提醒管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/reminder'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea-group'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea-group'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea-group'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea-group'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/approval'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/approval'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/approval'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:view' AND m.`menu_name` = '审批管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/approval'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '考核与分析' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'statistics:view' AND m.`menu_name` = '统计分析' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/statistics'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'statistics:view' AND m.`menu_name` = '统计分析' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/statistics'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '系统管理' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '系统管理' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`menu_name` = '系统管理' AND m.`menu_type` = 1 AND m.`parent_id` = 0
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:view' AND m.`menu_name` = '人员管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/user'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:view' AND m.`menu_name` = '人员管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/user'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:view' AND m.`menu_name` = '人员管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/user'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '组织架构' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/org-group'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '组织架构' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/org-group'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '组织架构' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/org-group'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:view' AND m.`menu_name` = '角色管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/role'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:view' AND m.`menu_name` = '角色管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/role'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/config-group'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/config-group'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:log:view' AND m.`menu_name` = '日志管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/log'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:log:view' AND m.`menu_name` = '日志管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/log'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:add' AND m.`menu_name` = '客户新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:edit' AND m.`menu_name` = '客户编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:delete' AND m.`menu_name` = '客户删除' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:export' AND m.`menu_name` = '客户导出' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:export' AND m.`menu_name` = '客户导出' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:export' AND m.`menu_name` = '客户导出' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:claim' AND m.`menu_name` = '公海捞取' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:approve' AND m.`menu_name` = '审批通过' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'approval:reject' AND m.`menu_name` = '审批驳回' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:add' AND m.`menu_name` = '用户新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:add' AND m.`menu_name` = '用户新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:add' AND m.`menu_name` = '用户新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:edit' AND m.`menu_name` = '用户编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:edit' AND m.`menu_name` = '用户编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:edit' AND m.`menu_name` = '用户编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:add' AND m.`menu_name` = '角色新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:add' AND m.`menu_name` = '角色新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:edit' AND m.`menu_name` = '角色编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:edit' AND m.`menu_name` = '角色编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:entry' AND m.`menu_name` = '积分录入' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/score/entry'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:entry' AND m.`menu_name` = '积分录入' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/score/entry'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:entry' AND m.`menu_name` = '积分录入' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/score/entry'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/score/ranking'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/score/ranking'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/score/ranking'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:ranking' AND m.`menu_name` = '积分排行' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/score/ranking'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:edit' AND m.`menu_name` = '激励编辑' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:edit' AND m.`menu_name` = '激励编辑' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:edit' AND m.`menu_name` = '激励编辑' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:view' AND m.`menu_name` = '岗位管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/position'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:view' AND m.`menu_name` = '岗位管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/position'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:add' AND m.`menu_name` = '岗位新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:add' AND m.`menu_name` = '岗位新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:edit' AND m.`menu_name` = '岗位编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:edit' AND m.`menu_name` = '岗位编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:view' AND m.`menu_name` = '权限规则' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/rule'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:view' AND m.`menu_name` = '权限规则' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/rule'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:add' AND m.`menu_name` = '规则新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:add' AND m.`menu_name` = '规则新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:edit' AND m.`menu_name` = '规则编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:edit' AND m.`menu_name` = '规则编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:view' AND m.`menu_name` = '集团管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/group'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:view' AND m.`menu_name` = '集团管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/group'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:add' AND m.`menu_name` = '集团新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:add' AND m.`menu_name` = '集团新增' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:edit' AND m.`menu_name` = '集团编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:edit' AND m.`menu_name` = '集团编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:import' AND m.`menu_name` = '客户导入' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:import' AND m.`menu_name` = '客户导入' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:import' AND m.`menu_name` = '客户导入' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:view' AND m.`menu_name` = '积分查看' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:manage' AND m.`menu_name` = '积分管理' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'score:manage' AND m.`menu_name` = '积分管理' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:config:view' AND m.`menu_name` = '公海配置查看' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:config:view' AND m.`menu_name` = '公海配置查看' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:config:view' AND m.`menu_name` = '公海配置查看' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:config:edit' AND m.`menu_name` = '公海配置编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:config:edit' AND m.`menu_name` = '公海配置编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:edit' AND m.`menu_name` = '系统配置编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:edit' AND m.`menu_name` = '系统配置编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:edit' AND m.`menu_name` = '部门编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:edit' AND m.`menu_name` = '部门编辑' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:menu:view' AND m.`menu_name` = '菜单查看' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:menu:view' AND m.`menu_name` = '菜单查看' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:org:admin' AND m.`menu_name` = '组织管理员' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:org:admin' AND m.`menu_name` = '组织管理员' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:delete' AND m.`menu_name` = '删除用户' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:delete' AND m.`menu_name` = '删除用户' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:delete' AND m.`menu_name` = '删除角色' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:role:delete' AND m.`menu_name` = '删除角色' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:delete' AND m.`menu_name` = '删除岗位' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:position:delete' AND m.`menu_name` = '删除岗位' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:delete' AND m.`menu_name` = '删除规则' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:rule:delete' AND m.`menu_name` = '删除规则' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:delete' AND m.`menu_name` = '删除集团' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:group:delete' AND m.`menu_name` = '删除集团' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '基础数据-部门' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '基础数据-部门' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:view' AND m.`menu_name` = '基础数据-字典' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:view' AND m.`menu_name` = '基础数据-字典' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:user:view' AND m.`menu_name` = '基础数据-用户' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/stats'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/stats'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/stats'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励统计' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/stats'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/search'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/search'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/search'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:view' AND m.`menu_name` = '激励查询' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive/search'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea/records'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea/records'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea/records'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '捞取记录' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea/records'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'statistics:view' AND m.`menu_name` = '核心客户六项指标' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/indicator'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'statistics:view' AND m.`menu_name` = '核心客户六项指标' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/indicator'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'statistics:view' AND m.`menu_name` = '核心客户六项指标' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/indicator'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:import' AND m.`menu_name` = '公海导入' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:import' AND m.`menu_name` = '公海导入' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:import' AND m.`menu_name` = '公海导入' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:add' AND m.`menu_name` = '激励上传' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/incentive'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'customer:view' AND m.`menu_name` = '客户列表' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/customer'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'sea:view' AND m.`menu_name` = '公海池' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/sea'
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '部门管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/org'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '部门管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/org'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:dept:view' AND m.`menu_name` = '部门管理' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/org'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/config'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/config'
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'system:config:view' AND m.`menu_name` = '系统设置' AND m.`menu_type` = 2 AND COALESCE(m.`path`,'') = '/system/config'
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'incentive:manage' AND m.`menu_name` = '激励管理(他人记录)' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '05'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '0'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.`id`, m.`id` FROM `sys_role` r JOIN `sys_menu` m ON m.`permission` = 'file:acl:manage' AND m.`menu_name` = '文件ACL管理' AND m.`menu_type` = 3 AND COALESCE(m.`path`,'') = ''
WHERE r.`role_code` = '06'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id`=r.`id` AND x.`menu_id`=m.`id`);

-- ==================== 回滚 ====================
-- 仅在"确认是全新库"时执行：
--   DELETE rm FROM `sys_role_menu` rm JOIN `sys_role` r ON r.`id`=rm.`role_id`
--     WHERE r.`role_code` IN ('admin','05','0','06');
--   DELETE FROM `sys_role` WHERE `role_code` IN ('admin','05','0','06');
--   DELETE FROM `sys_menu` WHERE `id` BETWEEN 9000 AND 9200;
-- ============================================================
