-- Local-only fixture for predictable statistics checks.
-- Never apply this file to a shared, test, or production database.

INSERT INTO `biz_customer` (`id`, `tenant_id`, `customer_code`, `customer_name`, `owner_id`, `status`, `deleted`, `created_at`, `updated_at`)
VALUES (990001, 1, 'LOCAL-STAT-01', '本地统计客户一', NULL, 'normal', 0, CURRENT_DATE, CURRENT_TIMESTAMP),
       (990002, 1, 'LOCAL-STAT-02', '本地统计客户二', NULL, 'normal', 0, CURRENT_DATE, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE `customer_name` = VALUES(`customer_name`), `status` = 'normal', `deleted` = 0, `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `biz_customer_indicator` (`id`, `customer_id`, `indicator_02`, `indicator_03`, `indicator_04`, `indicator_05`, `indicator_06`, `is_core`, `relationship_score`, `tenant_id`)
VALUES (990001, 990001, 3, 3, 3, 2, 2, 1, 13, 1), (990002, 990002, 1, 1, 1, 1, 1, 0, 5, 1)
ON DUPLICATE KEY UPDATE `indicator_02` = VALUES(`indicator_02`), `indicator_03` = VALUES(`indicator_03`), `indicator_04` = VALUES(`indicator_04`), `indicator_05` = VALUES(`indicator_05`), `indicator_06` = VALUES(`indicator_06`), `is_core` = VALUES(`is_core`), `relationship_score` = VALUES(`relationship_score`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `biz_invoice` (`id`, `customer_id`, `invoice_date`, `amount`, `invoice_no`, `deleted`, `created_at`, `updated_at`, `tenant_id`)
VALUES (990001, 990001, CURRENT_DATE, 120.00, 'LOCAL-STAT-INV-01', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1), (990002, 990002, CURRENT_DATE, 20.00, 'LOCAL-STAT-INV-02', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
ON DUPLICATE KEY UPDATE `amount` = VALUES(`amount`), `invoice_date` = VALUES(`invoice_date`), `deleted` = 0, `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `biz_payment` (`id`, `customer_id`, `payment_date`, `payment_amount`, `payment_method`, `deleted`, `created_at`, `updated_at`, `tenant_id`)
VALUES (990001, 990001, CURRENT_DATE, 120.00, 'local-fixture', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1), (990002, 990002, CURRENT_DATE, 20.00, 'local-fixture', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
ON DUPLICATE KEY UPDATE `payment_amount` = VALUES(`payment_amount`), `payment_date` = VALUES(`payment_date`), `deleted` = 0, `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `biz_file` (`id`, `tenant_id`, `file_name`, `file_size`, `file_type`, `oss_key`, `file_scope`, `uploader_id`, `created_at`, `updated_at`, `deleted`)
VALUES (990001, 1, 'local-statistics-01.txt', 1, 'txt', 'local/statistics/01.txt', 'public', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0), (990002, 1, 'local-statistics-02.txt', 1, 'txt', 'local/statistics/02.txt', 'public', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE `file_name` = VALUES(`file_name`), `file_scope` = 'public', `deleted` = 0, `updated_at` = CURRENT_TIMESTAMP;
