-- V9: 权限架构底层重构(2026-08-10)——租户全表化 + role_perm_scope 三级可配 + 锚点
-- 执行顺序: 本脚本必须先于新 jar 上线(租户插件启动自检要求白名单表都有 tenant_id 列)
-- 全部 DDL 可逆, 回滚见 V9_rollback.sql

-- ============================================================
-- 1. 业务表 tenant_id 化(18 张加列; 8 张已有: customer/share/file/folder/incentive_record/sea_pool/approval/reminder)
--    排除: 5 张 *_bak_* 快照表 + biz_stats_permission(废弃空表)
--    DEFAULT 1 即完成单集团数据回填
-- ============================================================
ALTER TABLE biz_approval_config     ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_approval_flow       ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_approval_node       ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_contact             ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_customer_indicator  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_customer_log        ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_customer_transfer   ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_file_permission     ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_file_share          ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_folder_acl          ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_incentive_category  ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_incentive_permission ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_invoice             ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_payment             ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_score_entry         ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_score_item          ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_score_participant   ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);
ALTER TABLE biz_sea_config          ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '集团ID', ADD KEY idx_tenant(tenant_id);

-- ============================================================
-- 2. role_perm_scope 演进: 主体三级(角色/岗位/用户) + 锚点(按人/按组织)
-- ============================================================
ALTER TABLE role_perm_scope
    ADD COLUMN assignee_type VARCHAR(10) NOT NULL DEFAULT 'ROLE' COMMENT '主体类型: ROLE/POSITION/USER',
    ADD COLUMN assignee_key  VARCHAR(50) NOT NULL COMMENT '主体键: ROLE=角色编码 / POSITION=岗位ID / USER=用户ID',
    ADD COLUMN anchor        VARCHAR(10) NOT NULL DEFAULT 'OWNER' COMMENT '范围锚点: OWNER=按负责人 / ORG=按客户归属组织(dept_id)';
UPDATE role_perm_scope SET assignee_key = role_code WHERE assignee_type = 'ROLE' AND assignee_key = '';
ALTER TABLE role_perm_scope DROP KEY uk_role_module_action;
ALTER TABLE role_perm_scope ADD UNIQUE KEY uk_assignee_ma (assignee_type, assignee_key, module, action, anchor);
-- role_code 列保留(兼容 V7 查询/回滚窗口, 1 个发布周期后清理)

-- ============================================================
-- 3. 验证
-- ============================================================
SELECT CONCAT('tenant_cols=', COUNT(*)) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='ycmn' AND COLUMN_NAME='tenant_id' AND TABLE_NAME LIKE 'biz\_%' AND TABLE_NAME NOT LIKE '%\_bak\_%';
