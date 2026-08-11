-- V7: 角色-模块-动作 数据范围(权限体系重构 2026-08-10)
-- 核心模型: 每个权限 = 功能权限(谁能做, 沿用 sys_menu/sys_role_menu RBAC) × 数据范围(对谁的数据)
-- 本表存"对谁的数据"维度: 每角色×每模块×每动作 一个范围级别
-- 范围级别复用 DataScopeType: all/dept_and_children/dept/region/office/org/team/self
-- 语义: 未配置某(角色,模块,动作) → 查看沿用角色 data_scope; 编辑/删除/分享默认 self(仅本人, 兼容现状)
CREATE TABLE IF NOT EXISTS role_perm_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码(sys_role.role_code)',
    module VARCHAR(50) NOT NULL COMMENT '模块(如 customer/sea/file/incentive/approval/statistics/score/indicator/system)',
    action VARCHAR(50) NOT NULL COMMENT '动作(如 view/add/edit/delete/share/export/import/claim/approve/reject/config)',
    scope_level VARCHAR(20) NOT NULL DEFAULT 'self' COMMENT '数据范围: all/dept_and_children/dept/region/office/org/team/self',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_module_action (role_code, module, action)
) COMMENT '角色-模块-动作 数据范围';
