-- V9 回滚(2026-08-10)——无损 DDL 逆操作, 无数据销毁
-- 注意: 若已有集团2+数据, 回滚将丢失 tenant_id 维度; 单集团回滚无数据影响

ALTER TABLE biz_approval_config     DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_approval_flow       DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_approval_node       DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_contact             DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_customer_indicator  DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_customer_log        DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_customer_transfer   DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_file_permission     DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_file_share          DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_folder_acl          DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_incentive_category  DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_incentive_permission DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_invoice             DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_payment             DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_score_entry         DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_score_item          DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_score_participant   DROP KEY idx_tenant, DROP COLUMN tenant_id;
ALTER TABLE biz_sea_config          DROP KEY idx_tenant, DROP COLUMN tenant_id;

ALTER TABLE role_perm_scope DROP KEY uk_assignee_ma;
ALTER TABLE role_perm_scope ADD UNIQUE KEY uk_role_module_action (role_code, module, action);
ALTER TABLE role_perm_scope DROP COLUMN anchor, DROP COLUMN assignee_key, DROP COLUMN assignee_type;
