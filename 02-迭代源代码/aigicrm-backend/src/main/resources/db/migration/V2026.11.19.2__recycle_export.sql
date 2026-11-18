-- ============================================================
-- 【A3 2026-11-18】回收站 ZIP 导出（用户拍板 #1/#2/#3）
--   上限: 10 万行/类型、总量 20GB（超限 400 提示缩小范围）
--   功能码 system:recycle:export → ADMIN_ONLY（admin 绑定）
--   下载 7 天过期（410）；创建/下载/删除全审计
-- 定位口径: 按 permission/role_code 锚定，零硬编码行 id（INV-MIG-001 同款）
-- 回滚(需人工确认后单独执行):
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--     WHERE m.permission='system:recycle:export';
--   DELETE FROM sys_menu WHERE permission='system:recycle:export';
--   DELETE FROM sys_permission_meta WHERE code='system:recycle:export';
--   DROP TABLE biz_recycle_export;  -- 导出文件需另行人工清理
-- ============================================================

-- 1. 导出台账表（仓库只存路径不存字节；文件落 EXPORT_DIR/{tenant}/{export_no}.zip）
CREATE TABLE IF NOT EXISTS biz_recycle_export (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  export_no VARCHAR(40) NOT NULL,
  requested_by BIGINT NOT NULL,
  types_json JSON NOT NULL COMMENT '["customer","user","file","task","incentive","reminder"]',
  filter_json JSON NULL COMMENT 'deleted_at 范围/关键字',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/ready/failed/expired',
  file_path VARCHAR(500) NULL,
  file_size BIGINT NULL,
  checksum_sha256 CHAR(64) NULL,
  expires_at DATETIME NOT NULL,
  downloaded_at DATETIME NULL,
  error_msg VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_recycle_export_no (tenant_id, export_no),
  KEY idx_recycle_export_expire (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回收站 ZIP 导出台账';

-- 2. 功能码注册: 系统管理→回收站 页面下的按钮(menu_type=3), 挂到既有回收站页面锚点
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, sort_order, visible, status, created_at)
SELECT p.id, '导出回收站', 3, NULL, NULL, 'system:recycle:export', 30, 1, 1, NOW()
FROM sys_menu p
WHERE p.permission = 'system:recycle:view' AND p.menu_type = 2
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.permission = 'system:recycle:export');

-- 3. admin 角色绑定（拍板 #2: ADMIN_ONLY——其他角色不绑, 授权走权限中心亦受 adminOnly 收口）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m
WHERE r.role_code = 'admin' AND m.permission = 'system:recycle:export' AND m.status = 1;

-- 4. 注册表 admin_only=1（列口径对齐 V2026.11.16.3 模板；ON DUPLICATE 防重复注册）
INSERT INTO `sys_permission_meta`
  (`code`, `name`, `type`, `module`, `parent_code`, `admin_only`, `requires_org_admin`, `deprecated`, `no_endpoint`, `depends_on`)
VALUES
  ('system:recycle:export','导出回收站','BUTTON','system','system:recycle:view',1,0,0,0,'system:recycle:view')
ON DUPLICATE KEY UPDATE `admin_only`=1, `name`=VALUES(`name`);
