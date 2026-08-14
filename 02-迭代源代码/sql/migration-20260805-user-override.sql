-- ============================================================
-- 迁移: sys_user 新增 data_scope_override（用户级数据范围覆盖）
-- 背景: 统一权限体系 §7 层2 —— 用户级数据范围覆盖角色级默认。
--       sys_user.manager_id（直属上级，已有列）在 Task 8 之前已存在并透传保存，
--       本迁移仅补 data_scope_override 一列。
-- 取值: 空/NULL = 跟随角色聚合结果；非空 = 合法 DataScopeType.code
--       （all / custom / region / office / dept / team / self）
-- 执行: mysql -h host -P port -u root -p --default-character-set=utf8mb4 <库名> < migration-20260805-user-override.sql
-- 幂等: MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，重复执行前请先确认列是否已存在
--       （SHOW COLUMNS FROM sys_user LIKE 'data_scope_override'）
-- ============================================================

ALTER TABLE sys_user
    ADD COLUMN data_scope_override VARCHAR(20) NULL COMMENT '用户级数据范围覆盖(空=跟随角色；取值见 DataScopeType.code)' AFTER manager_id;

-- ==================== 回滚 SQL（需要时手动执行） ====================
-- ALTER TABLE sys_user DROP COLUMN data_scope_override;
-- ==================================================================
