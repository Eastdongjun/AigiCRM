-- 【B3 2026-09-22 schema 漂移收编】uk_sea_pool_active 此前仅在个别运行库手工存在(V10 时代),
-- 迁移链缺失导致 Clean Room 新库与运行库行为不一致(GPT 报告 DB-P1-003)。
-- 语义: active_flag 虚拟生成列(未捞取 status<>'claimed' 时=customer_id, 已捞取=NULL),
--       唯一键 (tenant_id, active_flag) = 每客户每租户至多一条未捞取公海记录(NULL 不参与唯一)。
-- 与本地运行库 SHOW CREATE TABLE 逐字对齐后收编; 幂等: 列/键存在即跳过。

SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'biz_sea_pool' AND column_name = 'active_flag');
SET @ddl1 := IF(@has_col = 0,
    'ALTER TABLE biz_sea_pool ADD COLUMN active_flag bigint GENERATED ALWAYS AS (case when status <> ''claimed'' then customer_id else NULL end) VIRTUAL COMMENT ''未捞取行=customer_id,已捞取=NULL,配合唯一键保一行(V10收编)''',
    'SELECT 1');
PREPARE s1 FROM @ddl1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @has_uk := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'biz_sea_pool' AND index_name = 'uk_sea_pool_active');
SET @ddl2 := IF(@has_uk = 0,
    'ALTER TABLE biz_sea_pool ADD UNIQUE KEY uk_sea_pool_active (tenant_id, active_flag)',
    'SELECT 1');
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;
