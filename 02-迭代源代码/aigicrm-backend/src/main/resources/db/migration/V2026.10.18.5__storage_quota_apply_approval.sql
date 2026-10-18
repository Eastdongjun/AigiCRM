-- ============================================================
-- V2026.10.18.5__storage_quota_apply_approval.sql
-- 【D1=A 2026-10-18 用户裁决】存储空间(配额)申请审批补全
--
-- 业务规则:
--   1. 申请人: 系统管理员(role_code='admin')始终可申请; 非管理员须由管理员在
--      「系统设置」勾选(file.storage.apply.users, JSON 数组, 元素 userId);
--   2. 审批人: 沿用 biz_approval_config 审批流配置; 本迁移预置 storage 流=
--      admin 角色(ANY 或签), 管理员可在审批流配置页调整;
--   3. 只允许调高配额, 不允许降配; 单用户上限默认 100GB(bytes), 系统设置可改;
--   4. 同一申请人同时只能有一张在途(reviewing/pending) storage 审批单;
--   5. 审批通过执行配额变更并写安全审计(userId/oldQuota/newQuota/approvalId/operator)。
--
-- 幂等:
--   sys_config 三键均按 config_key NOT EXISTS 守卫; biz_approval_config 仅在
--   不存在 active(storage) 配置时插入一条。全部可重复执行。
--
-- 定位口径:
--   全程按 config_key / flow_type / role_code 锚定, 零硬编码业务行 id(INV-MIG-001)。
--   旧版 biz_approval_flow 的 storage 行保留不动——提交侧优先读取
--   biz_approval_config(既有逻辑), 新配置天然优先。
--
-- 回滚(需人工确认后单独执行):
--   DELETE FROM biz_approval_config WHERE flow_type='storage' AND status=1;
--   DELETE FROM sys_config WHERE config_key IN
--     ('file.storage.apply.enabled','file.storage.apply.users','file.storage.max.quota');
-- ============================================================

-- 1. 非管理员申请开关(管理员不受此开关限制)
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'file.storage.apply.enabled', 'true', '存储空间申请开关(仅对非管理员生效;管理员始终可申请)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'file.storage.apply.enabled');

-- 2. 授权申请人名单(JSON 数组, 元素 userId; 仅对非管理员生效)
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'file.storage.apply.users', '[]', '存储空间申请授权人员ID列表(JSON数组,仅对非管理员生效)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'file.storage.apply.users');

-- 3. 单用户配额上限(bytes, 默认 100GB; 运行时非法值回退 100GB 并限制在 [1GB,1TB])
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'file.storage.max.quota', '107374182400', '单用户存储配额上限(bytes,默认100GB)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'file.storage.max.quota');

-- 4. 预置 storage 审批流: admin 角色或签(ANY), 串行阶段序 1。
--    仅当不存在 active storage 配置时插入, 避免覆盖管理员已调整的流程。
INSERT INTO biz_approval_config
    (flow_name, flow_type, approver_type, approver_ids, sign_mode, sort_order, status, tenant_id)
SELECT '存储空间申请', 'storage', 'ROLE', JSON_ARRAY(r.id), 'ANY', 1, 1, r.group_id
FROM sys_role r
WHERE r.role_code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM biz_approval_config c
                   WHERE c.flow_type = 'storage' AND c.status = 1)
ORDER BY r.group_id, r.id
LIMIT 1;
