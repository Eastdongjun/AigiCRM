-- ============================================================
-- V20__add_sys_user_column_pref.sql
-- 用户表格列显示偏好(按人记在服务器, 换浏览器/电脑仍生效)
--
-- 背景(2026-08-17 用户需求):
--   激励上传表格列较多(13列), 不同岗位关注点不同(如销售不关心修改人/时间),
--   需要"列设置"自定义呈现列, 且偏好按人持久化到服务器。
--
-- 设计:
--   - page_key 标识页面(如 incentive-record), 便于后续推广到其他表格页面
--   - columns 存 JSON 数组(如 ["customer","industry","files"]), 与前端列 key 对齐
--   - (user_id, page_key) 唯一: 每人每页一份偏好
--   - 无记录 = 使用前端默认列(全部显示), 不需要初始化数据
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_user_column_pref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `page_key` varchar(50) NOT NULL COMMENT '页面标识(如 incentive-record)',
  `columns` json DEFAULT NULL COMMENT '显示列 key 数组(JSON), null=使用默认',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_page` (`user_id`, `page_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表格列显示偏好';
