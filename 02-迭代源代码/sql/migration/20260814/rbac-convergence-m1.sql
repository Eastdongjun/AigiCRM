-- ============================================================
-- M1 · RBAC 收敛迁移（2026-08-14）
-- 用途：激励权限从「角色码硬编码 + 用户直绑」收敛为标准 RBAC 权限码 + 角色承载；
--       同步落地安全 P0 的 DB 支撑列（pwd_changed_at / perm_version / pwd_reset_required / token 黑名单表）。
-- 性质：幂等（权限码/角色/绑定均 NOT EXISTS / ON DUPLICATE 守卫；列添加带 information_schema 守卫）。
-- 前置：执行前备份 5 表：sys_menu / sys_role / sys_role_menu / sys_user_role / biz_incentive_permission。
-- 回滚：见 migration-20260814-rbac-convergence-m1-rollback.sql。
-- 执行：mysql -uroot -p ycmn < sql/migration/20260814/rbac-convergence-m1.sql
-- ============================================================

-- ---------- 1. 新权限码（menu_type=3 按钮码，挂激励菜单 15 下 / 文件菜单下） ----------
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status)
SELECT 15, '激励管理(他人记录)', 3, 'incentive:manage', 96, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'incentive:manage');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status)
SELECT 15, '激励查看(全部)', 3, 'incentive:view-all', 97, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'incentive:view-all');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status)
SELECT 15, '激励查看(已分享)', 3, 'incentive:view-shared', 98, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'incentive:view-shared');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status)
SELECT COALESCE((SELECT id FROM sys_menu WHERE permission = 'file:view' AND menu_type = 2 ORDER BY id LIMIT 1), 10),
       '文件ACL管理', 3, 'file:acl:manage', 90, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'file:acl:manage');

-- ---------- 2. 激励载体角色 ----------
-- 「激励-分享可见」：全员默认绑定（新用户由 SysUserService.create 自动绑），收窄 = 解绑
INSERT INTO sys_role (role_name, role_code, description, data_scope, sort_order, status, group_id)
SELECT '激励-分享可见', 'incentive_view_shared', '激励模块查看已分享记录（全员默认；收窄=解绑）', 'self', 90, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'incentive_view_shared');

-- 「激励-全量查看」：白名单角色（存量直绑 can_view_all=1 为 0 人，故初始为空）
INSERT INTO sys_role (role_name, role_code, description, data_scope, sort_order, status, group_id)
SELECT '激励-全量查看', 'incentive_view_all', '激励模块查看全部记录（含未分享）', 'self', 91, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'incentive_view_all');

-- ---------- 3. 权限码绑定（功能权限走角色，替代 hasRole("0") 与用户直绑） ----------
-- 3a. incentive:manage → admin(1) + 销售总监(10)（编辑/删除他人记录）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.permission = 'incentive:manage'
WHERE r.id IN (1, 10)
ON DUPLICATE KEY UPDATE menu_id = menu_id;

-- 3b. incentive:view-all → admin(1) + 销售总监(10)（看全部）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.permission = 'incentive:view-all'
WHERE r.id IN (1, 10)
ON DUPLICATE KEY UPDATE menu_id = menu_id;

-- 3c. file:acl:manage → admin(1)（文件夹 ACL 管理收敛为独立权限码）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.permission = 'file:acl:manage'
WHERE r.id = 1
ON DUPLICATE KEY UPDATE menu_id = menu_id;

-- 3d. 【关键绑定】incentive:view-shared → 「激励-分享可见」载体角色（缺此绑定全员分享可见性错误收窄）——
--     canViewShared 改为 hasAuthority('incentive:view-shared') 后，权限码必须经 sys_role_menu 落到该角色
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.permission = 'incentive:view-shared'
WHERE r.role_code = 'incentive_view_shared'
ON DUPLICATE KEY UPDATE menu_id = menu_id;

-- 3e. 【关键绑定】incentive:view-all → 「激励-全量查看」载体角色（与 3d 同型缺陷：测试服实测
--     grant 该角色后用户 canViewAll 仍为 false——角色绑了用户但角色本身零权限码）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.permission = 'incentive:view-all'
WHERE r.role_code = 'incentive_view_all'
ON DUPLICATE KEY UPDATE menu_id = menu_id;

-- ---------- 4. 全员默认绑定「激励-分享可见」（存量 29 活跃用户） ----------
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u
JOIN sys_role r ON r.role_code = 'incentive_view_shared'
WHERE u.deleted = 0
ON DUPLICATE KEY UPDATE role_id = role_id;

-- ---------- 5. 销售员(11) 解除 incentive:edit(62)/incentive:delete(63) ----------
-- 【补齐 20260805 迁移意图】生产实测销售员仍绑 62/63：edit 码实际放行激励分类增删改+全量重算 4 端点
DELETE FROM sys_role_menu
WHERE role_id = 11
  AND menu_id IN (SELECT id FROM sys_menu WHERE permission IN ('incentive:edit', 'incentive:delete'));

-- ---------- 6. sys_user 安全/一致性支撑列（守卫式幂等） ----------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'pwd_changed_at');
SET @sql := IF(@col = 0, 'ALTER TABLE sys_user ADD COLUMN pwd_changed_at DATETIME NULL COMMENT ''改密时刻(token撤销依据,DB级)''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'perm_version');
SET @sql := IF(@col = 0, 'ALTER TABLE sys_user ADD COLUMN perm_version INT NOT NULL DEFAULT 0 COMMENT ''权限版本号(缓存自愈比对)''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'pwd_reset_required');
SET @sql := IF(@col = 0, 'ALTER TABLE sys_user ADD COLUMN pwd_reset_required TINYINT NOT NULL DEFAULT 0 COMMENT ''首次登录强制改密(1=需改密)''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 7. token 黑名单 DB 兜底表（Redis 故障时撤销不走 fail-open） ----------
CREATE TABLE IF NOT EXISTS sys_token_blacklist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    jti VARCHAR(64) NOT NULL COMMENT 'JWT jti',
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL COMMENT 'token 过期时刻(行可清理)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jti (jti),
    KEY idx_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'token撤销黑名单(DB兜底)';

-- ---------- 8. 仍在使用公开默认口令哈希的账号 → 首登强制改密 ----------
-- 哈希与 init.sql 种子一致（公开默认口令）；生产已改密账号不受影响
UPDATE sys_user SET pwd_reset_required = 1
WHERE deleted = 0
  AND password = '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO';

-- ============================================================
-- 验证查询（人工核对用，不在本脚本内执行）：
--   SELECT permission FROM sys_menu WHERE permission IN ('incentive:manage','incentive:view-all','incentive:view-shared','file:acl:manage');
--   SELECT role_name, role_code FROM sys_role WHERE role_code LIKE 'incentive%';
--   SELECT COUNT(*) AS bound FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='incentive_view_shared';
--   SELECT COUNT(*) AS sales_edit FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE rm.role_id=11 AND m.permission IN ('incentive:edit','incentive:delete');
--   SELECT id, username FROM sys_user WHERE pwd_reset_required=1;
-- ============================================================
