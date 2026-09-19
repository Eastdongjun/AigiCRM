-- ============================================================
-- fix-duplicate-sidebar-menus.sql(2026-08-15 审计 H4 生产数据清理方案)
-- ⚠️⚠️ 状态: 已废止, 禁止再执行 ⚠️⚠️
--   本脚本于 2026-08-15 21:08 在生产执行过一次, 停用 115/116/118/119 后
--   客户列表/公海池/部门管理/系统设置四个页面入口消失(父级行有子菜单时不可点击),
--   已由 restore-sidebar-page-entries.sql(2026-08-15 21:58, 4e9b3ce)修复。
--   2026-08-16 误重放本脚本再次引发同事故, 由
--   sql/migration/20260816/menu-fix-restore-entries-20260816.sql 修复。
--   115/116/118/119 是"分组标题(11/17/32/34, path=-group 后缀)▸页面入口"结构中的
--   真实页面入口行, 不是冗余导航——四项激励组同理(15=/incentive-group, 114=/incentive)。
--   正确的数据侧清理目标已不存在(去重由代码 dedupeByPath+前端 menuGroups 承担)。
--
-- 背景(原注释, 仅存档): 生产 sys_menu 存在 2026-08-05~12 手工写入的冗余行, 导致侧边栏同页渲染两次:
--   114 /incentive incentive:add  menu_type=2(按钮权限误为导航)
--   115 /customer  customer:view(与 11 重复)
--   116 /sea       sea:view      (与 17 重复)
--   118 /system/org  system:dept:view(与 32 重复)
--   119 /system/config system:config:view(与 34 重复)
-- 代码侧去重(后端 buildMenuTree + 前端 menuGroups)已上线, 数据侧无需再清理。
-- ============================================================

-- ① 114: 按钮权限 incentive:add 误为导航, 改为按钮类型(menu_type=3)、清 path; 保留角色绑定与权限码
UPDATE `sys_menu` SET `menu_type` = 3, `path` = NULL WHERE `id` = 114 AND `permission` = 'incentive:add';

-- ② 冗余导航行: 解绑角色 + 软停用(不物理删除, 保留审计)
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (115, 116, 118, 119);
UPDATE `sys_menu` SET `status` = 0 WHERE `id` IN (115, 116, 118, 119);
