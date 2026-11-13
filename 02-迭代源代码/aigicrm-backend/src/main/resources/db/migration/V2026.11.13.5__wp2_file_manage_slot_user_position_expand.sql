-- V2026.11.13.5 USER/POSITION (file,manage) 槽扩全五码(文件权限专项复检 FILE-02 补齐)
--
-- 背景: V2026.11.13.3 的 USER/POSITION 槽码映射表只列了 (file|manage)→file:acl:manage 与
-- file:folder:manage 两码; 检查复检报告指出 USER/POSITION 侧缺 upload/edit/share 三码。
-- 行为影响评估: 新轨槽级聚合(grantsForSlot 合并槽内全部码 grant)下, 同槽 grant 范围一致,
-- 三码缺失不影响解析结果; 本迁移为**审计/对账完整性补齐**(消除"行分布差异")。
-- 幂等: NOT EXISTS 防重; 自然键锚定。
-- tenant=1: 单集团(锁定决策#6)。

SET @wp2_tenant = 1;

INSERT INTO sys_perm_grant
  (tenant_id, subject_type, subject_id, action_code, scope_type, anchor, effect)
SELECT 1, ps.assignee_type, ps.assignee_key, c.code,
  CASE COALESCE(ps.scope_level, 'self')
    WHEN 'all' THEN 'ALL'
    WHEN 'custom' THEN 'CUSTOM'
    WHEN 'region' THEN 'MY_REGION_SUB'
    WHEN 'office' THEN 'MY_OFFICE_SUB'
    WHEN 'dept' THEN 'MY_DEPT_SUB'
    WHEN 'dept_and_children' THEN 'MY_DEPT_SUB'
    WHEN 'org_and_children' THEN 'MY_DEPT_SUB'
    WHEN 'org' THEN 'MY_DEPT'
    WHEN 'team' THEN 'MY_TEAM'
    ELSE 'SELF'
  END,
  COALESCE(ps.anchor, 'OWNER'), 'ALLOW'
FROM role_perm_scope ps
JOIN (
  SELECT 'file:upload' AS code
  UNION ALL SELECT 'file:edit'
  UNION ALL SELECT 'file:share'
  UNION ALL SELECT 'file:acl:manage'
  UNION ALL SELECT 'file:folder:manage'
) c
WHERE ps.assignee_type IN ('USER', 'POSITION')
  AND ps.module = 'file' AND ps.action = 'manage'
  AND NOT EXISTS (
    SELECT 1 FROM sys_perm_grant g
    WHERE g.tenant_id = @wp2_tenant AND g.subject_type = ps.assignee_type
      AND g.subject_id = ps.assignee_key AND g.action_code = c.code);
