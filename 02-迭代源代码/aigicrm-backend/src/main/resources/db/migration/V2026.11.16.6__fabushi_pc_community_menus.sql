-- ============================================================
-- 法布施 PC 员工端：内容流 / 书目浏览
-- 2026-11-16
--
-- 目标：PC 与小程序功能一致，仅呈现方式不同。
--   · 员工端（fabushi:view）：内容流 / 详情 / 评论 / 木鱼 / 已读 / 发布 / 书目浏览
--   · 管理端（book:view / content:view / stats:view）：书目管理 / 内容治理 / 统计看板
--
-- 不硬编码角色码：页面绑定从现有 fabushi:view 菜单绑定复制，
-- 组绑定按新页面绑定反推。
-- ============================================================

SET @fabushi_group = (
  SELECT id FROM sys_menu
  WHERE menu_name = '法布施' AND parent_id < 1 AND menu_type = 1
  ORDER BY id LIMIT 1
);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT @fabushi_group, '内容流', 2, '/fabushi/feed', 'fabushi/feed', 'fabushi:view', 'ChatDotRound', 1, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_type = 2 AND path = '/fabushi/feed');
SET @feed_menu = (SELECT id FROM sys_menu WHERE menu_type = 2 AND path = '/fabushi/feed' ORDER BY id LIMIT 1);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible, status)
SELECT @fabushi_group, '书目浏览', 2, '/fabushi/library', 'fabushi/library', 'fabushi:view', 'Reading', 2, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_type = 2 AND path = '/fabushi/library');
SET @library_menu = (SELECT id FROM sys_menu WHERE menu_type = 2 AND path = '/fabushi/library' ORDER BY id LIMIT 1);

-- 管理三页后移，保持 内容流/书目浏览 在管理页之前
UPDATE sys_menu SET sort_order = 3 WHERE menu_type = 2 AND path = '/fabushi/book';
UPDATE sys_menu SET sort_order = 4 WHERE menu_type = 2 AND path = '/fabushi/content';
UPDATE sys_menu SET sort_order = 5 WHERE menu_type = 2 AND path = '/fabushi/stats';

-- 页面绑定：从现有 fabushi:view 菜单绑定复制，不写角色码
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @feed_menu
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.permission = 'fabushi:view'
  AND m.id <> @feed_menu
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = @feed_menu
  );

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @library_menu
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.permission = 'fabushi:view'
  AND m.id <> @library_menu
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = @library_menu
  );

-- 组绑定按新页面绑定反推（组已存在时也补齐）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @fabushi_group
FROM sys_role_menu rm
WHERE rm.menu_id IN (@feed_menu, @library_menu)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = @fabushi_group
  );
