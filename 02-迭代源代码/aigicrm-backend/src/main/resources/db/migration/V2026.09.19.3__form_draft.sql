-- 【议题四裁决 2026-09-19】表单草稿:7 天保留/按账号+集团隔离/停用切集团立即清理/版本冲突检测
-- 对象:客户表单、激励上传等录入中断恢复;敏感内容允许入草稿(裁决,不脱敏)。

CREATE TABLE IF NOT EXISTS biz_form_draft (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '草稿归属用户',
    group_id    BIGINT       NOT NULL COMMENT '集团隔离(切集团即不可见)',
    draft_type  VARCHAR(50)  NOT NULL COMMENT '草稿类型(customer_form/incentive_upload 等)',
    draft_data  TEXT         NULL COMMENT '表单数据 JSON(敏感字段允许,裁决)',
    version     INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号(冲突检测)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_draft_user_group_type (user_id, group_id, draft_type),
    KEY idx_draft_updated (updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='表单草稿(7天保留,懒过期)';
