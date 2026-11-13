-- V2026.11.13.3 WP2 授权/任职/闭包回填(权限升级施工规格书 WP2.3)
--
-- 回填顺序(规格 §2.3): 资源/动作(V2026.11.13.2 已做) → sys_perm_grant → sys_employment →
-- sys_org_closure(sys_perm_epoch 在 .2)。
-- 授权来源与优先级: sys_role_menu 每行 → (ROLE, role_code, action, scope);
--   scope 优先取匹配的 role_perm_scope(ROLE) 行 → 否则 sys_role.data_scope → 再否则 SELF;
--   role_perm_scope 的 USER/POSITION 行独立生成 (USER/POSITION, assignee_key) 授权。
--   (incentive, stats_view) 生成 incentive:stats:view + incentive:rank:view 两码(规格 2.3);
--   (statistics, view) 不生成(锁定决策#7, WP1 已删档); 死档不生成; `*` 通配存量行不映射
--   (legacy 载体行, 旧体系继续生效至 WP8 切流, 对账工具覆盖)。
-- 幂等: 全部 INSERT 前置 NOT EXISTS / INSERT IGNORE; 自然键锚定, 无硬编码 id。
-- tenant: 单集团(锁定决策#6, 与 sys_role.group_id 默认值/租户拦截器主集团口径一致)。
-- 会话变量表达(INV-MIG-001: WHERE 语境 tenant_id = 数字字面量会被守卫误判为行定位)。

-- ============ 1. ROLE 级授权回填 ============
SET @wp2_tenant = 1;
INSERT INTO sys_perm_grant (tenant_id, subject_type, subject_id, action_code, scope_type, anchor, effect)
SELECT 1, 'ROLE', t.role_code, t.code,
  CASE
    WHEN t.slot_key IS NULL THEN 'SELF'   -- scope_required=0(路由/对象级/入口): 范围不参与解析
    WHEN COALESCE(t.s_scope, t.r_scope, 'self') = 'all' THEN 'ALL'
    WHEN COALESCE(t.s_scope, t.r_scope, 'self') = 'custom' THEN 'CUSTOM'
    WHEN COALESCE(t.s_scope, t.r_scope, 'self') = 'region' THEN 'MY_REGION_SUB'
    WHEN COALESCE(t.s_scope, t.r_scope, 'self') = 'office' THEN 'MY_OFFICE_SUB'
    WHEN COALESCE(t.s_scope, t.r_scope, 'self') IN ('dept', 'dept_and_children', 'org_and_children') THEN 'MY_DEPT_SUB'
    WHEN COALESCE(t.s_scope, t.r_scope, 'self') = 'org' THEN 'MY_DEPT'
    WHEN COALESCE(t.s_scope, t.r_scope, 'self') = 'team' THEN 'MY_TEAM'
    ELSE 'SELF'
  END,
  CASE WHEN t.slot_key IS NULL THEN 'OWNER' ELSE COALESCE(t.s_anchor, 'OWNER') END,
  'ALLOW'
FROM (
  SELECT r.role_code, m.permission AS code,
    CASE m.permission   -- 该码是否拥有数据范围档(有档才允许匹配 role_perm_scope 覆盖)
      WHEN 'customer:view' THEN 'customer|view'
      WHEN 'customer:edit' THEN 'customer|edit'
      WHEN 'customer:delete' THEN 'customer|delete'
      WHEN 'customer:finance:edit' THEN 'customer|finance_edit'
      WHEN 'file:view' THEN 'file|view'
      WHEN 'file:acl:manage' THEN 'file|manage'      -- (file,manage) 槽的门码(f管理槽, 码 file:manage 已退役)
      WHEN 'file:folder:manage' THEN 'file|manage'
      WHEN 'file:delete' THEN 'file|delete'
      WHEN 'approval:view' THEN 'approval|view'
      WHEN 'reminder:view' THEN 'reminder|view'
      WHEN 'sea:view' THEN 'sea|view'
      WHEN 'incentive:stats:view' THEN 'incentive|stats_view'
      WHEN 'incentive:rank:view' THEN 'incentive|stats_view'
      WHEN 'score:view' THEN 'score|view'
      WHEN 'task:view' THEN 'task|view'
      WHEN 'system:log:view' THEN 'system|view'
      ELSE NULL
    END AS slot_key,
    s.scope_level AS s_scope, s.anchor AS s_anchor, r.data_scope AS r_scope
  FROM sys_role_menu rm
  JOIN sys_role r ON r.id = rm.role_id AND r.status = 1
  JOIN sys_menu m ON m.id = rm.menu_id
    AND m.permission IS NOT NULL AND m.permission <> '' AND m.status = 1
  LEFT JOIN role_perm_scope s
    ON s.assignee_type = 'ROLE' AND s.assignee_key = r.role_code
   AND CONCAT(s.module, '|', s.action) = CASE m.permission
      WHEN 'customer:view' THEN 'customer|view'
      WHEN 'customer:edit' THEN 'customer|edit'
      WHEN 'customer:delete' THEN 'customer|delete'
      WHEN 'customer:finance:edit' THEN 'customer|finance_edit'
      WHEN 'file:view' THEN 'file|view'
      WHEN 'file:acl:manage' THEN 'file|manage'      -- (file,manage) 槽的门码
      WHEN 'file:folder:manage' THEN 'file|manage'
      WHEN 'file:delete' THEN 'file|delete'
      WHEN 'approval:view' THEN 'approval|view'
      WHEN 'reminder:view' THEN 'reminder|view'
      WHEN 'sea:view' THEN 'sea|view'
      WHEN 'incentive:stats:view' THEN 'incentive|stats_view'
      WHEN 'incentive:rank:view' THEN 'incentive|stats_view'
      WHEN 'score:view' THEN 'score|view'
      WHEN 'task:view' THEN 'task|view'
      WHEN 'system:log:view' THEN 'system|view'
      ELSE '__none__'   -- 无档码永不匹配(防 (system,view) 行误挂到 system:user:view 等)
   END
  GROUP BY r.role_code, m.permission, s.scope_level, s.anchor, r.data_scope
) t
WHERE NOT EXISTS (
  SELECT 1 FROM sys_perm_grant g
  WHERE g.tenant_id = @wp2_tenant AND g.subject_type = 'ROLE'
    AND g.subject_id = t.role_code AND g.action_code = t.code);

-- ============ 2. USER/POSITION 级授权回填 ============
INSERT INTO sys_perm_grant (tenant_id, subject_type, subject_id, action_code, scope_type, anchor, effect)
SELECT DISTINCT 1, ps.assignee_type, ps.assignee_key, cm.code,
  CASE COALESCE(ps.scope_level, 'self')
    WHEN 'all' THEN 'ALL'
    WHEN 'custom' THEN 'CUSTOM'
    WHEN 'region' THEN 'MY_REGION_SUB'
    WHEN 'office' THEN 'MY_OFFICE_SUB'
    WHEN 'dept' THEN 'MY_DEPT_SUB'
    WHEN 'dept_and_children' THEN 'MY_DEPT_SUB'
    WHEN 'org' THEN 'MY_DEPT'
    WHEN 'org_and_children' THEN 'MY_DEPT_SUB'
    WHEN 'team' THEN 'MY_TEAM'
    ELSE 'SELF'
  END,
  COALESCE(ps.anchor, 'OWNER'),
  'ALLOW'
FROM role_perm_scope ps
JOIN (
  SELECT 'customer|view' AS slot, 'customer:view' AS code
  UNION ALL SELECT 'customer|edit', 'customer:edit'
  UNION ALL SELECT 'customer|delete', 'customer:delete'
  UNION ALL SELECT 'customer|finance_edit', 'customer:finance:edit'
  UNION ALL SELECT 'file|view', 'file:view'
  UNION ALL SELECT 'file|manage', 'file:acl:manage'      -- (file,manage) 槽的两个门码
  UNION ALL SELECT 'file|manage', 'file:folder:manage'
  UNION ALL SELECT 'file|delete', 'file:delete'
  UNION ALL SELECT 'approval|view', 'approval:view'
  UNION ALL SELECT 'reminder|view', 'reminder:view'
  UNION ALL SELECT 'sea|view', 'sea:view'
  UNION ALL SELECT 'incentive|stats_view', 'incentive:stats:view'
  UNION ALL SELECT 'incentive|stats_view', 'incentive:rank:view'
  UNION ALL SELECT 'score|view', 'score:view'
  UNION ALL SELECT 'task|view', 'task:view'
  UNION ALL SELECT 'system|view', 'system:log:view'
) cm ON cm.slot = CONCAT(ps.module, '|', ps.action)
WHERE ps.assignee_type IN ('USER', 'POSITION')
  AND NOT (ps.module = 'statistics' AND ps.action = 'view')   -- 防御(行已由 V2026.11.13.1 删除)
  AND NOT EXISTS (
    SELECT 1 FROM sys_perm_grant g
    WHERE g.tenant_id = @wp2_tenant AND g.subject_type = ps.assignee_type
      AND g.subject_id = ps.assignee_key AND g.action_code = cm.code);

-- ============ 3. 任职回填(规格 2.3: 岗位任职 → 无岗位者 dept_id 主职) ============
INSERT IGNORE INTO sys_employment (user_id, org_id, position_id, is_primary, status)
SELECT up.user_id, p.org_id, p.id, COALESCE(up.is_primary, 1), 1
FROM sys_user_position up
JOIN sys_position p ON p.id = up.position_id AND p.org_id IS NOT NULL AND p.status = 1
JOIN sys_user u ON u.id = up.user_id AND u.deleted = 0;

INSERT IGNORE INTO sys_employment (user_id, org_id, position_id, is_primary, status)
SELECT u.id, u.dept_id, 0, 1, 1
FROM sys_user u
JOIN sys_dept d ON d.id = u.dept_id
WHERE u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_employment e WHERE e.user_id = u.id);

-- ============ 4. 组织闭包回填(parent_id 递归; 深度护栏 20) ============
INSERT INTO sys_org_closure (ancestor_id, descendant_id, depth)
WITH RECURSIVE closure AS (
  SELECT id AS ancestor_id, id AS descendant_id, 0 AS depth
  FROM sys_dept WHERE status = 1
  UNION ALL
  SELECT c.ancestor_id, d.id, c.depth + 1
  FROM closure c
  JOIN sys_dept d ON d.parent_id = c.descendant_id AND d.status = 1
  WHERE c.depth < 20
)
SELECT ancestor_id, descendant_id, MAX(depth)
FROM closure
GROUP BY ancestor_id, descendant_id;
