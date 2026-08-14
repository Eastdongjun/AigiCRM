-- V8: 客户转移记录表(修复 P1-04 2026-08-10)
-- 背景: SysUserService.markLeave/transferCustomers 执行 INSERT INTO biz_customer_transfer,
-- 但迁移链(V1-V7)从未建此表 → 离职带转移/手动转移功能在事务内抛 "Table doesn't exist",
-- 整个事务回滚(含已完成的客户入池/分享清理)。本迁移按代码 INSERT 列清单补建。
CREATE TABLE IF NOT EXISTS biz_customer_transfer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL COMMENT '转移源用户(离职/原负责人)',
    to_user_id BIGINT NOT NULL COMMENT '接收用户',
    transfer_type VARCHAR(20) NOT NULL COMMENT 'leave=离职转移 / manual=手动转移',
    customer_count INT NOT NULL DEFAULT 0 COMMENT '转移客户数',
    operator_id BIGINT NOT NULL COMMENT '操作人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_transfer_from (from_user_id),
    KEY idx_transfer_to (to_user_id)
) COMMENT '客户转移记录(离职/手动)';
