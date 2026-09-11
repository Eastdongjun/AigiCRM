CREATE TABLE IF NOT EXISTS biz_customer_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL DEFAULT 'attachment',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_file_relation (customer_id, file_id, relation_type, deleted),
    KEY idx_customer_file_customer (customer_id, deleted),
    KEY idx_customer_file_file (file_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户与文件关联';
