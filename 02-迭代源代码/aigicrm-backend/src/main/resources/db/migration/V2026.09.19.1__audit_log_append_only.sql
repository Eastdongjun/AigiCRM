-- 【BS-R 2026-09-19】安全审计日志(append-only,防抵赖)
-- 背景:第二轮深度验证 STRIDE-R 盲区——越权者事后可靠删日志抵赖;sys_log 为业务操作日志,
-- 无任何防改删约束;安全事件(登录失败/匿名探测/403 拒绝/权限与集团变更)此前只落文件。
-- 设计:独立 sys_audit_log,数据库触发器拒绝一切 UPDATE/DELETE(应用账号/误操作同拦),
-- 仅允许 INSERT;写入由 SecurityAuditService 旁路完成,失败降级不阻断业务。

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_type     VARCHAR(50)  NOT NULL COMMENT 'LOGIN_FAILED/LOGIN_SUCCESS/ANON_PROBE/ACCESS_DENIED/PERMISSION_CHANGE/GROUP_SWITCH/SECURITY_ALERT/USER_ADMIN',
    actor_id       BIGINT       NULL COMMENT '操作者用户 id(匿名事件为 NULL)',
    actor_username VARCHAR(64)  NULL COMMENT '操作者/目标用户名(匿名探测记探测的用户名)',
    detail         VARCHAR(1000) NULL COMMENT '结构化 k=v 明文(uri/action/roleId/from-to 等)',
    ip             VARCHAR(64)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_event_time (event_type, created_at),
    KEY idx_audit_actor_time (actor_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='安全审计日志(append-only)';

-- append-only 约束:改/删一律拒绝(含应用自身)
DROP TRIGGER IF EXISTS trg_audit_log_no_update;
CREATE TRIGGER trg_audit_log_no_update BEFORE UPDATE ON sys_audit_log
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is append-only: UPDATE rejected';

DROP TRIGGER IF EXISTS trg_audit_log_no_delete;
CREATE TRIGGER trg_audit_log_no_delete BEFORE DELETE ON sys_audit_log
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is append-only: DELETE rejected';

-- 已知边界(记录,不掩盖):DROP TABLE / TRUNCATE 无法用触发器拦截——生产依赖应用账号
-- 无 DDL 权限(N1-B 最小权限决策)+ DBA 操作留痕;本迁移与触发器定义随库入版本管理。
