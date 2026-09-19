-- ============================================================
-- restore-sidebar-page-entries.sql (2026-08-15 晚, 已执行于生产)
-- 状态: 已执行(2026-08-15 21:57, 执行前备份 backup-menu-restore-20260815-215743.sql)
--
-- 背景: 同日 21:08 执行的 fix-duplicate-sidebar-menus.sql 停用了
--   115/116/118/119 四个"同 path 冗余导航行"。但当时生产已上线
--   AuthService.dedupeByPath + 前端 menuGroups 两级去重代码,
--   停用子页面行后, 客户列表等页面在侧边栏失去了唯一可点击入口
--   (父级行因有子菜单只渲染为不可点击的展开标题), 用户反馈"菜单不对"。
--
-- 本脚本的修复思路(与冗余清理互补, 不推翻):
--   ① 父级导航行(11/17/32/34)保留为分组标题, path 改为独立分组值,
--      消除与子页面同 path 的去重冲突, 两级去重代码不会误删任何节点;
--   ② 子页面行(115/116/118/119)恢复启用 + 恢复角色绑定,
--      侧边栏恢复"分组标题 ▸ 页面入口"的原有可点击结构;
--   ③ 可见页面(menu_type=2) path 零重复, 同页渲染两次的根因依然消除。
--
-- 验证(2026-08-15 21:58 实测):
--   菜单树 API: 客户管理▸{新增客户,客户列表} 公海管理▸{公海池}
--               组织架构▸{部门管理} 系统设置▸{系统设置} 全部恢复;
--   可见页面 path 重复组数=0; 生产冒烟 20/20。
-- 回滚: 执行前备份 backup-menu-restore-*.sql (sys_menu + sys_role_menu)
-- ============================================================

-- ① 父级导航行 path 改为独立分组值(非真实路由, 仅作分组索引, 前端不导航)
UPDATE `sys_menu` SET `path` = '/customer-group'   WHERE `id` = 11;
UPDATE `sys_menu` SET `path` = '/sea-group'        WHERE `id` = 17;
UPDATE `sys_menu` SET `path` = '/system/org-group'    WHERE `id` = 32;
UPDATE `sys_menu` SET `path` = '/system/config-group' WHERE `id` = 34;

-- ② 恢复子页面入口 + 角色绑定(与停用前一致, 见 20260815-210805 备份)
UPDATE `sys_menu` SET `status` = 1 WHERE `id` IN (115, 116, 118, 119);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
 (1,115),(1,116),(1,118),(1,119),
 (9,115),(9,116),
 (10,115),(10,116),(10,118),(10,119),
 (11,115),(11,116);
