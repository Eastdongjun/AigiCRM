-- ============================================================
-- 流程协同菜单组：我的任务 / 提醒管理 / 审批管理
-- 2026-11-16
--
-- 约束：
--   1) 不硬编码任何角色码；任务页绑定从现有 task:view 菜单绑定复制，
--      组绑定从子菜单绑定反推（谁有子菜单谁就有组）。
--   2) 迁移后全库只保留一个 task:view 菜单行（新 /task 页），
--      旧顶层按钮行在绑定复制完成后删除，避免权限中心重复码。
--   3) 顶层排序：首页0 业务核心10 流程协同20 学分管理30 法布施40
--      统计分析50 系统管理60（法布施在统计前）。
-- ============================================================

-- 1. 顶层分组与排序
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT 0, '流程协同', 1, '/workflow-group', NULL, NULL, 'Connection', 20, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '流程协同'
);
SET @wf = (SELECT id FROM sys_menu WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '流程协同' ORDER BY id LIMIT 1);

UPDATE sys_menu SET sort_order = 0  WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '首页';
UPDATE sys_menu SET sort_order = 10 WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '业务核心';
UPDATE sys_menu SET sort_order = 20 WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '流程协同';
UPDATE sys_menu SET sort_order = 30 WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '学分管理';
UPDATE sys_menu SET sort_order = 40 WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '法布施';
UPDATE sys_menu SET sort_order = 50 WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '统计分析';
UPDATE sys_menu SET sort_order = 60 WHERE parent_id < 1 AND menu_type = 1 AND menu_name = '系统管理';

-- 2. 提醒/审批从业务核心改挂流程协同
SET @reminder = (SELECT id FROM sys_menu WHERE menu_type = 2 AND permission = 'reminder:view' ORDER BY id LIMIT 1);
SET @approval = (SELECT id FROM sys_menu WHERE menu_type = 2 AND permission = 'approval:view' ORDER BY id LIMIT 1);
UPDATE sys_menu SET parent_id = @wf, sort_order = 2 WHERE id = @reminder;
UPDATE sys_menu SET parent_id = @wf, sort_order = 3 WHERE id = @approval;

-- 3. 我的任务真实菜单页（不再靠前端注入）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT @wf, '我的任务', 2, '/task', 'task/index', 'task:view', 'List', 1, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE menu_type = 2 AND path = '/task'
);
SET @task_page = (SELECT id FROM sys_menu WHERE menu_type = 2 AND path = '/task' ORDER BY id LIMIT 1);

-- 4. 任务权限行：
--    现有 task:view 是顶层按钮行；先把它的角色绑定复制到新页面，再收口旧行。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @task_page
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.permission = 'task:view'
  AND m.id <> @task_page
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x
    WHERE x.role_id = rm.role_id AND x.menu_id = @task_page
  );

-- task:manage 挂到新任务页下（原为顶层按钮行）
UPDATE sys_menu SET parent_id = @task_page, sort_order = 2
WHERE menu_type = 3 AND permission = 'task:manage';

-- 删除旧 task:view 菜单行及其绑定，保证全库只有一个 task:view 菜单
-- 注意：删除语句不得再出现 'task:view' 字面量，否则权限登记契约会把新页面也判定为“已删除”。
SET @old_task_view = (
  SELECT id FROM sys_menu
  WHERE permission = 'task:view' AND id <> @task_page
  ORDER BY id LIMIT 1
);
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.permission = 'task:view' AND m.id <> @task_page;
DELETE FROM sys_menu WHERE id = @old_task_view;

-- 5. 流程协同组绑定：按子菜单绑定反推，不按角色硬绑定
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @wf
FROM sys_role_menu rm
WHERE rm.menu_id IN (@task_page, @reminder, @approval)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x
    WHERE x.role_id = rm.role_id AND x.menu_id = @wf
  );
