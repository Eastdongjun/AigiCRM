-- 激励查询按用户记录已读状态；PC 与微信小程序共用
CREATE TABLE IF NOT EXISTS biz_incentive_read (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_incentive_read_user_record (user_id, record_id),
  KEY idx_incentive_read_record (record_id),
  CONSTRAINT fk_incentive_read_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_incentive_read_record FOREIGN KEY (record_id) REFERENCES biz_incentive_record(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='激励查询用户阅读状态';

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission, sort_order, status, visible)
SELECT id, '激励查询阅读统计', 3, 'incentive:read-stats', 99, 1, 1
FROM sys_menu
WHERE permission = 'incentive:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'incentive:read-stats')
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, read_stats.id
FROM sys_role_menu rm
JOIN sys_menu incentive_view ON incentive_view.id = rm.menu_id
JOIN sys_menu read_stats ON read_stats.permission = 'incentive:read-stats'
WHERE incentive_view.permission = 'incentive:view'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing
    WHERE existing.role_id = rm.role_id
      AND existing.menu_id = read_stats.id
  );

UPDATE sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role_menu rm ON rm.role_id = ur.role_id
JOIN sys_menu read_stats ON read_stats.id = rm.menu_id
SET u.perm_version = COALESCE(u.perm_version, 0) + 1
WHERE read_stats.permission = 'incentive:read-stats';
