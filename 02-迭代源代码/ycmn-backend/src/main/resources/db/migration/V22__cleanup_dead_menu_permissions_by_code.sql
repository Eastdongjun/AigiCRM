-- ============================================================
-- V22__cleanup_dead_menu_permissions_by_code.sql
-- 按权限码锚定规范化清理废弃菜单权限点（V21 的语义化重做）
--
-- 背景(2026-08-27 权限点引用审计 + V21 写法评审):
--   V21 已停用 12 个废弃权限点(效果正确), 但其 SQL 硬编码 sys_menu 数字 id
--   (22/47/48/54/63/81/89/110/111/120/122/123), 不符合本项目 V17 惯例:
--   "菜单行按 permission 字符串锚定(不写死 menu_id, 防各环境 id 漂移)"。
--   V22 用 permission 码定位同一批菜单行, 幂等重做, 消除 ID 依赖。
--
-- 目标权限码(已核实: 各仅 1 行, 无重复; 均前后端零引用或已废弃):
--   statistics:scoreboard(积分榜空壳, 无前端页面)
--   file:upload / file:edit / file:delete / file:share / file:folder:manage
--   customer:share / incentive:delete
--   incentive:view-all / incentive:view-shared(已废弃)
--   sea:pool:delete / system:menu:edit
-- 保留: 61 积分排行(实际页面入口, 前端路由权限用 score:view 控制)
--
-- 幂等: UPDATE 带 AND status=1(已停用不重更新);
--       DELETE 用 JOIN 按 permission 定位, 绑定已删则自然落空。
-- 安全降级: 某环境缺对应菜单行时 JOIN 落空, 不影响其他行。
-- 回滚: 见文件尾部回滚注释。
-- ============================================================
-- 1) 停用废弃菜单行(按 permission 锚定, 不写死 id)
UPDATE `sys_menu` m
    JOIN (
        SELECT 'statistics:scoreboard' AS permission
        UNION ALL
        SELECT 'file:upload'
        UNION ALL
        SELECT 'file:delete'
        UNION ALL
        SELECT 'sea:pool:delete'
        UNION ALL
        SELECT 'incentive:delete'
        UNION ALL
        SELECT 'file:edit'
        UNION ALL
        SELECT 'system:menu:edit'
        UNION ALL
        SELECT 'customer:share'
        UNION ALL
        SELECT 'file:share'
        UNION ALL
        SELECT 'file:folder:manage'
        UNION ALL
        SELECT 'incentive:view-all'
        UNION ALL
        SELECT 'incentive:view-shared'
    ) t ON t.permission = m.`permission`
SET m.`status` = 0
WHERE m.`status` = 1;
-- 2) 删除对应角色绑定(按 permission JOIN 定位)
DELETE rm
FROM `sys_role_menu` rm
    JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
    JOIN (
        SELECT 'statistics:scoreboard' AS permission
        UNION ALL
        SELECT 'file:upload'
        UNION ALL
        SELECT 'file:delete'
        UNION ALL
        SELECT 'sea:pool:delete'
        UNION ALL
        SELECT 'incentive:delete'
        UNION ALL
        SELECT 'file:edit'
        UNION ALL
        SELECT 'system:menu:edit'
        UNION ALL
        SELECT 'customer:share'
        UNION ALL
        SELECT 'file:share'
        UNION ALL
        SELECT 'file:folder:manage'
        UNION ALL
        SELECT 'incentive:view-all'
        UNION ALL
        SELECT 'incentive:view-shared'
    ) t ON t.permission = m.`permission`;
-- ==================== 回滚(按需手工执行) ====================
-- 执行前先备份: mysqldump --no-data ycmn sys_menu sys_role_menu > menu_backup_$(date +%F).sql
-- 回滚 SQL(恢复菜单行并重建绑定到全部角色):
--   UPDATE sys_menu SET status = 1
--   WHERE permission IN ('statistics:scoreboard','file:upload','file:delete','sea:pool:delete',
--                        'incentive:delete','file:edit','system:menu:edit','customer:share',
--                        'file:share','file:folder:manage','incentive:view-all','incentive:view-shared');
--   INSERT INTO sys_role_menu (role_id, menu_id)
--   SELECT r.id, m.id FROM sys_role r
--   JOIN sys_menu m ON m.permission IN ('statistics:scoreboard','file:upload','file:delete','sea:pool:delete',
--                        'incentive:delete','file:edit','system:menu:edit','customer:share',
--                        'file:share','file:folder:manage','incentive:view-all','incentive:view-shared')
--   WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);
-- ============================================================
