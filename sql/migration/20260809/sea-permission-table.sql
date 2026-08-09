-- ============================================================
-- 2026-08-10 公海专项权限表（按组织架构层级勾选授权）
--
-- 背景: 公海查看/编辑/删除/捞取四项各自独立, 每项按组织架构层级
--        (总部→大区→办事处→部门→人员) 勾选授权, 勾选上级含下级递归。
--        专项为准, 存量角色码(sea:claim/sea:pool:edit/sea:pool:delete)失效。
-- 语义:
--   - 每 perm_type 一行(view/edit/delete/claim), dept_ids=授权组织节点,
--     user_ids=授权人员, 逗号分隔
--   - 无授权行或用户未命中 → 无该权限(查看也不例外)
--   - admin 恒有全部权限(不走表判定)
-- 执行: 备份后执行, 幂等。
-- ============================================================

CREATE TABLE IF NOT EXISTS `biz_sea_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `perm_type` varchar(20) NOT NULL COMMENT '权限类型: view/edit/delete/claim',
  `dept_ids` varchar(500) DEFAULT NULL COMMENT '授权组织节点ID(逗号分隔,勾选上级含下级递归)',
  `user_ids` varchar(500) DEFAULT NULL COMMENT '授权人员ID(逗号分隔,树末级)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type` (`perm_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公海专项权限表';

-- 预置四行空授权(admin 恒有效不走表; 其余用户未授权即无权限)
INSERT IGNORE INTO `biz_sea_permission` (`perm_type`, `dept_ids`, `user_ids`) VALUES
('view', NULL, NULL),
('edit', NULL, NULL),
('delete', NULL, NULL),
('claim', NULL, NULL);
