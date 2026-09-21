-- 【GPT复审 P1-06 修复 2026-09-21】编码唯一性改为租户内唯一:
-- 原状: customer_code/approval_code 数据库全局唯一, 但取号逻辑按当前集团 MAX+1(G2 租户化后),
-- 两集团各生成 NM-001 时后者撞全局唯一键 —— "租户内生成、全局唯一"设计冲突。
-- 修复: 唯一约束改为 (tenant_id, code), 与取号口径对齐; 单集团存量数据无跨租户重复, 重建安全。
-- 幂等: 先查目标索引名是否存在再重建(information_schema 守卫)。

SET @cust_idx := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'biz_customer' AND index_name = 'uk_tenant_customer_code');
SET @ddl1 := IF(@cust_idx = 0,
    'ALTER TABLE biz_customer DROP INDEX customer_code, ADD UNIQUE KEY uk_tenant_customer_code (tenant_id, customer_code)',
    'SELECT 1');
PREPARE s1 FROM @ddl1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @appr_idx := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'biz_approval' AND index_name = 'uk_tenant_approval_code');
SET @ddl2 := IF(@appr_idx = 0,
    'ALTER TABLE biz_approval DROP INDEX approval_code, ADD UNIQUE KEY uk_tenant_approval_code (tenant_id, approval_code)',
    'SELECT 1');
PREPARE s2 FROM @ddl2; EXECUTE s2; DEALLOCATE PREPARE s2;
