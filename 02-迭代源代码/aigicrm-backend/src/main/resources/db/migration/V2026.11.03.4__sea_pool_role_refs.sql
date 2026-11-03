-- ============================================================
-- V2026.11.03.4__sea_pool_role_refs.sql
-- 【统一权限中心 2026-11-03 用户裁决 P3】公海池操作名单支持"按角色引用"。
--
-- 背景: 池级 查看/编辑/删除/导入 名单此前只支持勾部门/人员; 用户场景"销售总监能修改
-- 公海的客户"= 三池逐个勾人, 人员换届要回头改名单。加角色引用列后:
--   各池编辑名单直接勾「销售总监」角色, 角色成员变动自动跟随。
-- 判定: SeaConfigService.isInScope 扩展第三参 roleIdsCsv, 与用户角色交集命中。
-- 可见范围(visible)不加角色列: VIEW 已允许 dataScope=all 放行, 且列名 view_role_ids
-- 为历史废弃列不复用(防脏数据), 新列统一 *_role_ids 命名。
-- 幂等: flyway 单次执行, 直接 ALTER(flyway history 保证不重放)。
-- ============================================================

ALTER TABLE `biz_sea_config`
  ADD COLUMN `edit_role_ids` varchar(500) DEFAULT NULL COMMENT '编辑角色ID(逗号分隔,命中角色成员即可编辑)' AFTER `edit_user_ids`,
  ADD COLUMN `delete_role_ids` varchar(500) DEFAULT NULL COMMENT '删除角色ID(逗号分隔)' AFTER `delete_user_ids`,
  ADD COLUMN `import_role_ids` varchar(500) DEFAULT NULL COMMENT '导入角色ID(逗号分隔)' AFTER `import_user_ids`,
  ADD COLUMN `claim_role_ids` varchar(500) DEFAULT NULL COMMENT '捞取角色ID(逗号分隔)' AFTER `claim_user_ids`;
