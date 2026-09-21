-- 【G2 权限审计批次2 2026-09-21】biz_task 进租户隔离前置:
-- 1) tenant_id 列存在性守卫(幂等, 已存在则跳过; 启动自检 verifyTenantColumns 依赖此列);
-- 2) 历史行回填集团(当前系统单集团=1; 多集团化前需按归属重算)。
SET @has_col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'biz_task' AND column_name = 'tenant_id');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE biz_task ADD COLUMN tenant_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE biz_task SET tenant_id = 1 WHERE tenant_id IS NULL;
