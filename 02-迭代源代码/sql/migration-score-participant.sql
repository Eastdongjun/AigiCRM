-- 积分录入：周期参与同事名单表
-- 目的：持久化某周期表格里显示的"同事"列，保证保存后重新打开时列数量/顺序与保存时一致
-- 首次打开（无名单）时前端默认显示全体启用用户；保存后按本表还原
CREATE TABLE IF NOT EXISTS biz_score_participant (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    period     VARCHAR(20)  NOT NULL COMMENT '周期，如 2026上半年',
    user_id    BIGINT       NOT NULL COMMENT '参与同事的用户ID',
    sort_order INT          DEFAULT 0 COMMENT '列顺序',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分录入周期参与同事名单';
