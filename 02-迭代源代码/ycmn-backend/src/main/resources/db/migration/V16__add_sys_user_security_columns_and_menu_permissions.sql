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
-- 幂等性(2026-08-15 加固, 生产库曾以裸 SQL 提前应用本迁移内容):
--   Flyway 版本化保证单次执行; 但生产/测试库的历史数据里三列三菜单**已存在**而
--   flyway 历史无 V16 行, 按原写法重跑会撞 duplicate column/row。
--   因此:
--   ① ALTER 改为 information_schema 探测 + 动态 SQL——列已存在则跳过(MySQL 8.0
--      不支持 ADD COLUMN IF NOT EXISTS, 只能动态语句);
--   ② 菜单 INSERT 加 NOT EXISTS 守卫——目标权限行已存在则不插;
--   ③ 父菜单锚定 SELECT 加 LIMIT 1——生产 incentive:view/sea:view 各存多行时
--      也最多只插一行。
-- 菜单行按父菜单 permission 字符串锚定（不写死 id，防各环境间 id 漂移）。
-- 历史行: pwd_changed_at=NULL 表示「从未改过密」（存量用户 token 不失效，正确）；
--         perm_version=0 为初始版本；pwd_reset_required=0 表示无需强制改密。
-- 不加索引: 三列均无按列查询/排序需求（pwd_changed_at 仅在登录时逐用户比较）。
-- ============================================================

-- ① 三列幂等补列: 缺哪列补哪列(逐列探测, 避免"部分已加"的中间态重跑失败)
SET @has_pwd_changed := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'pwd_changed_at');
SET @ddl_pwd_changed := IF(@has_pwd_changed = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `pwd_changed_at` datetime DEFAULT NULL COMMENT ''改密时刻：早于该时刻签发的 token 一律失效（DB 级 token 撤销依据，不依赖 Redis）'' AFTER `password`',
  'SELECT ''V16: pwd_changed_at 已存在, 跳过''');
PREPARE stmt FROM @ddl_pwd_changed; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_perm_version := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'perm_version');
SET @ddl_perm_version := IF(@has_perm_version = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `perm_version` int NOT NULL DEFAULT 0 COMMENT ''权限版本号：任一权限变更 +1；缓存命中时与 DB 比对，防 evict 漏网残留'' AFTER `pwd_changed_at`',
  'SELECT ''V16: perm_version 已存在, 跳过''');
PREPARE stmt FROM @ddl_perm_version; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_pwd_reset := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'pwd_reset_required');
SET @ddl_pwd_reset := IF(@has_pwd_reset = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `pwd_reset_required` tinyint NOT NULL DEFAULT 0 COMMENT ''首次登录强制改密标记（1=需改密；配合随机初始口令消除硬编码 123456）'' AFTER `perm_version`',
  'SELECT ''V16: pwd_reset_required 已存在, 跳过''');
PREPARE stmt FROM @ddl_pwd_reset; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ② 三菜单幂等补行(NOT EXISTS 防重 + 父锚定 LIMIT 1 防父权限多行)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT m.`id`, '文件ACL管理', 3, NULL, NULL, 'file:acl:manage', NULL, 4, 1, 1, NOW()
FROM (SELECT id FROM `sys_menu` WHERE `permission` = 'file:view' LIMIT 1) m
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'file:acl:manage');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT m.`id`, '激励新增', 3, NULL, NULL, 'incentive:add', NULL, 3, 1, 1, NOW()
FROM (SELECT id FROM `sys_menu` WHERE `permission` = 'incentive:view' LIMIT 1) m
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'incentive:add');

INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT m.`id`, '公海导入', 3, NULL, NULL, 'sea:import', NULL, 7, 1, 1, NOW()
FROM (SELECT id FROM `sys_menu` WHERE `permission` = 'sea:view' LIMIT 1) m
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = 'sea:import');
