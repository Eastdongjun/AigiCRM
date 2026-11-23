-- B1 可选演示数据：仅在显式 --seed-demo 且空库/无同名演示客户时导入。
-- 只写 biz_customer + sys_config，不写真实客户；tenant/group 固定 1（单集团安装）。
INSERT INTO biz_customer
  (tenant_id, customer_code, customer_name, industry, dev_type, owner_id, dept_id, status, protection_status, group_id)
SELECT 1, 'DEMO001', '演示客户-制造业A', '制造业', '成熟', 1, 1, 'normal', 'active', 1
WHERE NOT EXISTS (SELECT 1 FROM biz_customer WHERE customer_code='DEMO001');

INSERT INTO biz_customer
  (tenant_id, customer_code, customer_name, industry, dev_type, owner_id, dept_id, status, protection_status, group_id)
SELECT 1, 'DEMO002', '演示客户-互联网B', '互联网', '成熟创新', 1, 1, 'normal', 'active', 1
WHERE NOT EXISTS (SELECT 1 FROM biz_customer WHERE customer_code='DEMO002');

INSERT INTO biz_customer
  (tenant_id, customer_code, customer_name, industry, dev_type, owner_id, dept_id, status, protection_status, group_id)
SELECT 1, 'DEMO003', '演示客户-新能源C', '新能源', '突破', 1, 1, 'normal', 'active', 1
WHERE NOT EXISTS (SELECT 1 FROM biz_customer WHERE customer_code='DEMO003');

INSERT INTO sys_config (config_key, config_value, description)
VALUES ('onboarding.demoSeeded', 'true', 'B1 安装器演示数据标记')
ON DUPLICATE KEY UPDATE config_value='true';
