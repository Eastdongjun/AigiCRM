-- ============================================================
-- 收口 sys_dept/sys_org 双表 —— 删除 sys_org 兼容视图
--
-- 前提：后端代码已全部切换到 sys_dept（实体/SQL/CTE 已无 sys_org 引用）并部署成功。
-- 历史脚本（migration-20260802 / migration-permission-upgrade-mysql8）不得修改，
-- 其 CREATE VIEW sys_org 语句保留作为回滚入口。
--
-- 数据安全：sys_org 只是 sys_dept 的别名视图，无独立数据。删视图不删任何数据。
--
-- 执行前必须校验 TABLE_TYPE：
--   SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = 'aigicrm' AND TABLE_NAME = 'sys_org';
--   -> 期望 TABLE_TYPE='VIEW' 才执行下面的 DROP VIEW。
--   -> 若为 'BASE TABLE'（旧库物理表残留），DROP VIEW 会报 "is not VIEW"，此时禁止执行，需人工确认。
-- ============================================================

DROP VIEW IF EXISTS `sys_org`;
