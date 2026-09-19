-- 【PR-24 2026-09-19】规则版本化:记录业务规则参数(保护期天数/门槛值等)的每次变更
-- 总册 PR-24:"规则变更保存版本、发布人、影响范围及结果"
-- 方案(runs/20260919-pr24-batch1):sys_config 已承载规则参数(PR-08 配置化);
-- 本表 append-only 记录每次变更的旧值→新值,可追溯"数字为什么变了"。

CREATE TABLE IF NOT EXISTS sys_rule_version (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rule_key    VARCHAR(100) NOT NULL COMMENT '规则键(=sys_config.config_key,如 customer.protection.days)',
    old_value   VARCHAR(500) NULL COMMENT '变更前值',
    new_value   VARCHAR(500) NOT NULL COMMENT '变更后值',
    operator_id BIGINT       NULL COMMENT '操作者',
    operator_name VARCHAR(64) NULL,
    remark      VARCHAR(500) NULL COMMENT '变更说明(可选)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_rule_key_time (rule_key, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='规则版本(append-only,防抵赖同 sys_audit_log)';

-- append-only 触发器(与 sys_audit_log 同模式)
DROP TRIGGER IF EXISTS trg_rule_version_no_update;
CREATE TRIGGER trg_rule_version_no_update BEFORE UPDATE ON sys_rule_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_rule_version is append-only: UPDATE rejected';

DROP TRIGGER IF EXISTS trg_rule_version_no_delete;
CREATE TRIGGER trg_rule_version_no_delete BEFORE DELETE ON sys_rule_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_rule_version is append-only: DELETE rejected';
