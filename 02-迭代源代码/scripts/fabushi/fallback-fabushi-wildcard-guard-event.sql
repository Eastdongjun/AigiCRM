-- ============================================================
-- R2-FABUSHI-SCOPE-001 临时兜底（2026-11-22）
-- 目的：代码修复（社区型法布施文件按 fabushi:view 放行 + 写入面拒绝 USER/POSITION 通配）
--       上线前，若仍有人用 SQL 直写 USER/POSITION 级通配行（module='*' AND action='*'），
--       每 10 分钟自动补一行 fabushi/file_view=all，并递增受影响用户的 perm_version。
--
-- 为什么是 EVENT 而不是 TRIGGER：
--   MySQL 不允许触发器对本表（role_perm_scope）再 INSERT（"already used by statement"），
--   同表自动补行只能用定时 EVENT / 外部任务。
--
-- 生命周期（临时！）：
--   创建：mysql ... < scripts/fabushi/fallback-fabushi-wildcard-guard-event.sql
--   检查：SELECT EVENT_NAME, STATUS, INTERVAL_VALUE, INTERVAL_FIELD
--           FROM information_schema.EVENTS WHERE EVENT_NAME='ev_fabushi_wildcard_guard';
--   移除：DROP EVENT IF EXISTS ev_fabushi_wildcard_guard;  （新代码上线并验证后必须执行）
--
-- 约束：不硬编码账号；只按 role_perm_scope 事实行处理；显式配置（任意 scope_level）不覆盖。
-- ============================================================
DROP EVENT IF EXISTS ev_fabushi_wildcard_guard;

DELIMITER $$
CREATE EVENT ev_fabushi_wildcard_guard
ON SCHEDULE EVERY 10 MINUTE
COMMENT 'R2-FABUSHI-SCOPE-001 临时兜底: 通配主体自动补 fabushi/file_view=all, 代码上线后 DROP'
DO
BEGIN
  -- USER 级通配：补精确行
  INSERT INTO role_perm_scope (role_code, assignee_type, assignee_key, module, action, scope_level, anchor)
  SELECT DISTINCT '', w.assignee_type, w.assignee_key, 'fabushi', 'file_view', 'all', 'OWNER'
  FROM role_perm_scope w
  JOIN sys_user u ON u.id = CAST(w.assignee_key AS UNSIGNED)
  WHERE w.assignee_type = 'USER'
    AND w.module = '*' AND w.action = '*'
    AND u.deleted = 0
    AND NOT EXISTS (
      SELECT 1 FROM role_perm_scope e
      WHERE e.assignee_type = w.assignee_type
        AND e.assignee_key = w.assignee_key
        AND e.module = 'fabushi'
        AND e.action = 'file_view'
    );
  IF ROW_COUNT() > 0 THEN
    UPDATE sys_user u
    JOIN role_perm_scope w ON u.id = CAST(w.assignee_key AS UNSIGNED)
    SET u.perm_version = u.perm_version + 1
    WHERE w.assignee_type = 'USER' AND w.module = '*' AND w.action = '*';
  END IF;

  -- POSITION 级通配：补精确行
  INSERT INTO role_perm_scope (role_code, assignee_type, assignee_key, module, action, scope_level, anchor)
  SELECT DISTINCT '', w.assignee_type, w.assignee_key, 'fabushi', 'file_view', 'all', 'OWNER'
  FROM role_perm_scope w
  WHERE w.assignee_type = 'POSITION'
    AND w.module = '*' AND w.action = '*'
    AND NOT EXISTS (
      SELECT 1 FROM role_perm_scope e
      WHERE e.assignee_type = w.assignee_type
        AND e.assignee_key = w.assignee_key
        AND e.module = 'fabushi'
        AND e.action = 'file_view'
    );
  IF ROW_COUNT() > 0 THEN
    UPDATE sys_user u
    JOIN sys_user_position up ON up.user_id = u.id
    JOIN role_perm_scope w ON CAST(w.assignee_key AS UNSIGNED) = up.position_id
    SET u.perm_version = u.perm_version + 1
    WHERE w.assignee_type = 'POSITION' AND w.module = '*' AND w.action = '*';
  END IF;
END$$
DELIMITER ;
