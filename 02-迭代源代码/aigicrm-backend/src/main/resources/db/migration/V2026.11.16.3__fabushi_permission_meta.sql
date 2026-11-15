-- ============================================================
-- V2026.11.16.3__fabushi_permission_meta.sql
-- 【权限中心完整性】V2026.11.16.1 已注册菜单/资源/动作，但 sys_permission_meta 在
-- V2026.11.15.3 建表时尚未包含法布施；本迁移补齐 9 个权限点元数据、统计范围轴和依赖。
-- 依赖链：create→view；book:manage→book:view；content:manage→content:view；
--         stats:export→stats:view；activity:manage→view。
-- ============================================================
INSERT INTO `sys_permission_meta`
  (`code`, `name`, `type`, `module`, `parent_code`, `scope_module`, `scope_action`, `scope_mode`,
   `admin_only`, `requires_org_admin`, `deprecated`, `no_endpoint`, `depends_on`)
VALUES
  ('fabushi:view','法布施内容浏览','BUTTON','fabushi',NULL,NULL,NULL,'NONE',0,0,0,0,NULL),
  ('fabushi:create','法布施发布互动','BUTTON','fabushi','fabushi:view',NULL,NULL,'INHERIT',0,0,0,0,'fabushi:view'),
  ('fabushi:book:view','书目管理','MENU','fabushi',NULL,NULL,NULL,'NONE',0,0,0,0,NULL),
  ('fabushi:book:manage','法布施书目审核','BUTTON','fabushi','fabushi:book:view',NULL,NULL,'INHERIT',0,0,0,0,'fabushi:book:view'),
  ('fabushi:content:view','内容治理','MENU','fabushi',NULL,NULL,NULL,'NONE',0,0,0,0,NULL),
  ('fabushi:content:manage','法布施内容下架','BUTTON','fabushi','fabushi:content:view',NULL,NULL,'INHERIT',0,0,0,0,'fabushi:content:view'),
  ('fabushi:stats:view','统计看板','MENU','fabushi',NULL,'fabushi','stats_view','DIRECT',0,0,0,0,NULL),
  ('fabushi:stats:export','法布施统计导出','BUTTON','fabushi','fabushi:stats:view',NULL,NULL,'INHERIT',0,0,0,0,'fabushi:stats:view'),
  ('fabushi:activity:manage','法布施活动管理','BUTTON','fabushi','fabushi:view',NULL,NULL,'INHERIT',0,0,0,0,'fabushi:view')
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`), `type`=VALUES(`type`), `module`=VALUES(`module`),
  `parent_code`=VALUES(`parent_code`), `scope_module`=VALUES(`scope_module`),
  `scope_action`=VALUES(`scope_action`), `scope_mode`=VALUES(`scope_mode`),
  `depends_on`=VALUES(`depends_on`);
