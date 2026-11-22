-- 防复发契约（2026-11-22 R2-FABUSHI-SCOPE-001）：
-- USER/POSITION 级通配行（module='*' AND action='*'）按优先级 USER>POSITION>ROLE 整体覆盖
-- 角色模块级配置。若同一主体没有显式的 fabushi/file_view 行，法布施数据范围被通配收窄，
-- 历史上 7 个账号因此出现“书可见、PDF 下载 403”。
-- 本查询返回“有通配但无显式 fabushi/file_view”的主体；0 行才算通过。
-- 显式配置（任意 scope_level）视为管理员明确决定，不算遮蔽；只查缺失精确行的主体。
-- 用法：mysql -N -B "$DB_NAME" < scripts/check-user-scope-fabushi-file-view.sql
SELECT a.assignee_type,
       a.assignee_key,
       COALESCE(u.username, '(无匹配用户)') AS username,
       a.scope_level AS wildcard_scope
FROM role_perm_scope a
LEFT JOIN sys_user u ON u.id = CAST(a.assignee_key AS UNSIGNED)
WHERE a.assignee_type IN ('USER', 'POSITION')
  AND a.module = '*'
  AND a.action = '*'
  AND (a.assignee_type <> 'USER' OR u.deleted = 0)
  AND NOT EXISTS (
    SELECT 1 FROM role_perm_scope e
    WHERE e.assignee_type = a.assignee_type
      AND e.assignee_key = a.assignee_key
      AND e.module = 'fabushi'
      AND e.action = 'file_view'
  )
ORDER BY a.assignee_type, a.assignee_key;
