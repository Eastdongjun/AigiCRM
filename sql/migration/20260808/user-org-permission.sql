-- ============================================================
-- 用户级自定义授权组织（2026-08-08）
-- 每个用户可单独配置 custom 数据范围可见组织（如多个行政各看不同部门）。
-- 用户 data_scope_override='custom' 时使用 sys_user_org，优先于角色级 sys_role_org。
-- 执行需带库名：mysql -uroot -p ycmn < user-org-permission.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user_org (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  org_id BIGINT NOT NULL,
  include_children TINYINT DEFAULT 0,
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户级自定义授权组织';
