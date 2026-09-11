-- ============================================================
-- V17__bind_permission_codes_to_roles.sql — 权限码角色绑定补齐
--
-- 背景(2026-08-16 激励上传审查):
--   V16 只给三个权限码(incentive:add / file:acl:manage / sea:import)补了
--   sys_menu 行, 但权限判定 = 菜单行 ∩ sys_role_menu 角色绑定
--   (SysMenuMapper.selectPermissionsByUserId 双表 JOIN)。
--   角色绑定此前只存在于手工脚本(20260805-perm-codes / 20260814-rbac-convergence-m1),
--   未进入 Flyway 链 → 按标准链重建的库三个接口全员 403:
--   POST /api/incentive/records(激励上传) / 文件夹 ACL 4 端点 / 公海导入。
--
-- 绑定矩阵(与既有手工脚本、docs/permission/permission-dict.md 一致):
--   incentive:add   → admin(1) / 行政(9) / 销售总监(10) / 销售员(11)
--   file:acl:manage → admin(1)
--   sea:import      → admin(1) / 行政(9) / 销售总监(10)
--
-- 幂等: sys_role_menu 有 UNIQUE KEY uk_role_menu(role_id, menu_id), 且
--       NOT EXISTS 守卫——生产/测试已手工绑定的行不会被重复插入。
-- 菜单行按 permission 字符串锚定(不写死 menu_id, 防各环境 id 漂移;
-- 生产 id=114 的 incentive:add 行同样会被 JOIN 命中)。
-- 若某库菜单行不存在(如未执行 V16), JOIN 自然落空不产生绑定——安全降级,
-- 待菜单行补上后重放本迁移即可。
-- 回滚: 见文件尾部回滚注释。
-- ============================================================

-- ① 激励上传: 四角色可上传(设计矩阵 §3.2 全 ✅)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` = 'incentive:add'
WHERE r.`id` IN (1, 9, 10, 11)
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

-- ② 文件 ACL 管理: 仅系统管理员(M1 迁移 3c 语义)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` = 'file:acl:manage'
WHERE r.`id` = 1
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

-- ③ 公海导入: 管理员/行政/销售总监(20260805-perm-codes 2.1 语义, 销售员不授)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` = 'sea:import'
WHERE r.`id` IN (1, 9, 10)
  AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);

-- ==================== 回滚(按需手工执行) ====================
-- 执行前先备份: mysqldump --no-data ycmn sys_role_menu > sys_role_menu_backup_$(date +%F).sql
-- 回滚 SQL(删除三码全部角色绑定, 恢复到 V16 补行未绑定状态):
--   DELETE rm FROM `sys_role_menu` rm
--   JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
--   WHERE m.`permission` IN ('incentive:add', 'file:acl:manage', 'sea:import');
-- ============================================================
