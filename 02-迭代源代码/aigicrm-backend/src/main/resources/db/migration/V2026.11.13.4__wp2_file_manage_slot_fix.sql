-- V2026.11.13.4 文件模块 manage 槽修复(外部文件权限专项检查 P0, HEAD 8fa20bf4 基础上)
--
-- 问题(检查报告 F10/P0 核实属实):
--   V2026.11.13.2 的 scope_required CASE 含死码条件 'file:manage'(该码已退役未注册),
--   实际存在的 file:acl:manage / file:folder:manage 落 ELSE=0; file:upload/edit/share 未
--   映射到 (file,manage) 槽(V2026.11.13.3 仅映射 acl/folder:manage 两码, grant scope=SELF)。
--   后果: perm.read.file=new 时 matrixActionOf('upload'/'rename'/'replace'/'share'/'edit')
--   → (file,manage) 槽 → scope_required=0 → ScopeDecision.all() → FolderPermissionService
--   矩阵兜底恒 true → 任意公共夹上传/改名/替换/分享全组织放开(越权)。
-- 修复:
--   ① 五码 scope_required=1(upload/edit/share 的矩阵槽历史上就是 (file,manage),
--     见 FolderPermissionService.matrixActionOf; acl/folder:manage 同组);
--   ② 五码 grant scope 重算 = 角色 (file,manage) 槽配置 → 角色 data_scope → SELF
--     (与 V2026.11.13.3 回填同优先级; 修复"五码 grant=SELF"的错误回填)。
-- 幂等: UPDATE 天然幂等; 自然键锚定(uk_code)。
-- 注意: file:delete/file:view 不在修复范围(各自独立槽, 回填已正确)。

-- ① scope_required=1
UPDATE sys_action SET scope_required = 1
WHERE code IN ('file:acl:manage', 'file:folder:manage', 'file:upload', 'file:edit', 'file:share');

-- ② 五码 grant scope 重算(ROLE 行; USER/POSITION 行若有则按其原值保留——回填仅有 ROLE 行)
UPDATE sys_perm_grant g
JOIN sys_role r ON r.role_code = g.subject_id AND g.subject_type = 'ROLE'
LEFT JOIN role_perm_scope s
  ON s.assignee_type = 'ROLE' AND s.assignee_key = r.role_code
 AND s.module = 'file' AND s.action = 'manage'
SET g.scope_type = CASE COALESCE(s.scope_level, r.data_scope, 'self')
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
  g.anchor = COALESCE(s.anchor, 'OWNER')
WHERE g.action_code IN ('file:acl:manage', 'file:folder:manage', 'file:upload', 'file:edit', 'file:share');
