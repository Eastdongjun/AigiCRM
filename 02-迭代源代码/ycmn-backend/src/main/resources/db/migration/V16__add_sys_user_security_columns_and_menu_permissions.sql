-- ============================================================
-- V16__add_sys_user_security_columns_and_menu_permissions.sql
-- ① sys_user 安全字段补列（2026-08-14 安全 P0 代码已落地、迁移缺漏——工程门禁 G23 发现）
-- ② 三个 @PreAuthorize 权限字符串补 sys_menu 行（工程门禁 G25 发现，缺行则接口全员 403）
--
-- 背景:
--   SysUser 实体已有 pwdChangedAt / permVersion / pwdResetRequired 三字段且无
--   @TableField(exist=false)，MyBatis-Plus 默认按驼峰→下划线映射，
--   缺列会导致 sys_user 的 INSERT/UPDATE 运行期报「未知列」错误。
--   权限 file:acl:manage / incentive:add / sea:import 被 @PreAuthorize 引用但
--   sys_menu 无对应行——按项目约定权限字符串必须进 sys_menu，
--   否则全用户（含 admin）调用对应接口一律 403。
--
-- 幂等: Flyway 版本化保证单次执行（写入 flyway_schema_history）。
-- 菜单行按父菜单 permission 字符串锚定（不写死 id，防各环境间 id 漂移）。
-- 历史行: pwd_changed_at=NULL 表示「从未改过密」（存量用户 token 不失效，正确）；
--         perm_version=0 为初始版本；pwd_reset_required=0 表示无需强制改密。
-- 不加索引: 三列均无按列查询/排序需求（pwd_changed_at 仅在登录时逐用户比较）。
-- ============================================================

ALTER TABLE `sys_user`
  ADD COLUMN `pwd_changed_at` datetime DEFAULT NULL COMMENT '改密时刻：早于该时刻签发的 token 一律失效（DB 级 token 撤销依据，不依赖 Redis）' AFTER `password`,
  ADD COLUMN `perm_version` int NOT NULL DEFAULT 0 COMMENT '权限版本号：任一权限变更 +1；缓存命中时与 DB 比对，防 evict 漏网残留' AFTER `pwd_changed_at`,
  ADD COLUMN `pwd_reset_required` tinyint NOT NULL DEFAULT 0 COMMENT '首次登录强制改密标记（1=需改密；配合随机初始口令消除硬编码 123456）' AFTER `perm_version`;

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT m.`id`, '文件ACL管理', 3, NULL, NULL, 'file:acl:manage', NULL, 4, 1, 1, NOW()
FROM `sys_menu` m WHERE m.`permission` = 'file:view';

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT m.`id`, '激励新增', 3, NULL, NULL, 'incentive:add', NULL, 3, 1, 1, NOW()
FROM `sys_menu` m WHERE m.`permission` = 'incentive:view';

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT m.`id`, '公海导入', 3, NULL, NULL, 'sea:import', NULL, 7, 1, 1, NOW()
FROM `sys_menu` m WHERE m.`permission` = 'sea:view';
