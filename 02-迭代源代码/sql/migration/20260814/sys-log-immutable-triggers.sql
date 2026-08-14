-- ============================================================
-- sys_log 审计不可变触发器（2026-08-14，Wave B）
-- 背景：RBAC 设计资产要求审计日志不可变（trg_iam_audit_no_upd/del 模式）；
--       YCMN sys_log 此前应用同库同账号可 UPDATE/DELETE，无法支撑事后追责。
-- 方案：BEFORE UPDATE/DELETE 触发器直接 SIGNAL 拒绝——对任何账号（含 root）生效，
--       只有显式 DROP TRIGGER 才能绕过（该动作本身留痕，属运维级操作）。
-- 幂等：先 DROP 再 CREATE（重复执行安全）。
-- ⚠️ 注意：单语句触发器（不带 BEGIN...END/DELIMITER）——生产实测 batch 模式
--   DELIMITER 脚本执行无报错但不落库，改用单语句写法后生效。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p ycmn < sql/migration/20260814/sys-log-immutable-triggers.sql
-- ============================================================

DROP TRIGGER IF EXISTS trg_sys_log_no_upd;
CREATE TRIGGER trg_sys_log_no_upd BEFORE UPDATE ON sys_log FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log 审计日志不可修改';

DROP TRIGGER IF EXISTS trg_sys_log_no_del;
CREATE TRIGGER trg_sys_log_no_del BEFORE DELETE ON sys_log FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_log 审计日志不可删除';

-- 验证：
--   UPDATE sys_log SET ip='x' WHERE id=1;   -- 应报 ERROR 1644
--   DELETE FROM sys_log WHERE id=1;         -- 应报 ERROR 1644
