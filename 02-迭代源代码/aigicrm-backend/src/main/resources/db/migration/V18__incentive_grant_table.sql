-- ============================================================
-- V18__incentive_grant_table.sql — 激励专项授权改为用户级授权表
--
-- 背景(2026-08-16 用户确认):
--   「激励-分享可见/激励-全量查看」两个"载体角色"(M1 创建)概念不成立——
--   激励专项授权(谁能看分享/谁能看全部/谁能编辑/谁能删除)是"授权"不是"岗位角色",
--   混入 sys_role 污染角色列表, 且生产实际已空转(权限码被直绑主角色, 面板失效)。
--   本次改为用户级授权表 biz_incentive_grant:
--   - 判定语义: 无记录 = 默认(分享可见=开, 全部/编辑/删除=关);
--   - 白名单能力(can_view_all/can_edit/can_delete)按人勾选;
--   - can_view_shared 默认开, 按人收窄(记录=0)。
--   IncentiveService/IncentivePermissionController 相应改查表(见代码)。
--
-- 数据迁移:
--   ① 销售总监(role_id=10)用户 → can_view_all=1/can_edit=1/can_delete=1
--      (等价 M1 中 incentive:manage/view-all 绑角色 10 的语义);
--      admin 无需迁移(isAdmin 兜底)。
--   ② 解绑并删除两个载体角色(角色列表恢复干净);
--   ③ 解绑主角色上的三个激励专项权限码(不再由角色控制);
--   ④ 全员 perm_version+1(旧权限缓存中残留的 view-all authority 虽不再被
--      判定代码引用, bump 保证缓存与 DB 一致)。
-- 回滚: 见文件尾部。
-- ============================================================

-- ① 建表: 用户级激励专项授权
CREATE TABLE IF NOT EXISTS `biz_incentive_grant` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `can_view_shared` tinyint NOT NULL DEFAULT 1 COMMENT '可查看他人已分享记录(默认开; 无记录=开; 0=收窄)',
  `can_view_all` tinyint NOT NULL DEFAULT 0 COMMENT '可查看全部未分享记录(白名单, 默认关)',
  `can_edit` tinyint NOT NULL DEFAULT 0 COMMENT '可编辑他人记录(白名单, 默认关)',
  `can_delete` tinyint NOT NULL DEFAULT 0 COMMENT '可删除他人记录(白名单, 默认关)',
  `updated_by` bigint DEFAULT NULL COMMENT '最后授权操作人ID',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后授权时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='激励专项授权(用户级, 替代载体角色模式)';

-- ② 销售总监用户迁移(白名单语义对齐 M1: 总监看全部+编辑+删除他人)
INSERT IGNORE INTO `biz_incentive_grant` (`user_id`, `can_view_shared`, `can_view_all`, `can_edit`, `can_delete`)
SELECT ur.`user_id`, 1, 1, 1, 1
FROM `sys_user_role` ur
WHERE ur.`role_id` = 10;

-- ③ 解绑载体角色的用户与权限码, 删除载体角色
DELETE FROM `sys_user_role` WHERE `role_id` IN (
    SELECT `id` FROM `sys_role` WHERE `role_code` IN ('incentive_view_shared', 'incentive_view_all'));
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (
    SELECT `id` FROM `sys_menu` WHERE `permission` IN ('incentive:view-shared', 'incentive:view-all', 'incentive:manage'));
DELETE FROM `sys_role` WHERE `role_code` IN ('incentive_view_shared', 'incentive_view_all');

-- ④ 全员权限版本+1(防旧权限缓存残留)
UPDATE `sys_user` SET `perm_version` = `perm_version` + 1 WHERE `deleted` = 0;

-- ==================== 回滚(按需手工执行) ====================
-- 1. 重建载体角色与绑定(参照 sql/migration/20260814/rbac-convergence-m1.sql §2-§4);
-- 2. 删除表: DROP TABLE biz_incentive_grant;
-- 3. 回滚前备份: mysqldump ycmn sys_role sys_user_role sys_role_menu biz_incentive_grant
-- ============================================================
