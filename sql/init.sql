SET NAMES utf8mb4;
-- ============================================
-- 运筹马年 - 市场资源管理系统 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS ycmn DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE ycmn;

-- ============================================
-- 1. 系统管理模块
-- ============================================

-- 用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号',
    password VARCHAR(200) NOT NULL COMMENT '密码(BCrypt)',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    avatar VARCHAR(500) COMMENT '头像URL',
    dept_id BIGINT COMMENT '所属部门ID',
    office_id BIGINT COMMENT '所属办事处ID',
    storage_quota BIGINT DEFAULT 10737418240 COMMENT '私人存储空间配额(字节,默认10GB)',
    storage_used BIGINT DEFAULT 0 COMMENT '已用存储空间(字节)',
    status TINYINT DEFAULT 1 COMMENT '状态 0禁用 1正常',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_dept_id (dept_id),
    INDEX idx_office_id (office_id)
) COMMENT '用户表';

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(200) COMMENT '描述',
    data_scope VARCHAR(20) DEFAULT 'self' COMMENT '数据范围 all全部 dept_and_children本部门及下级 dept本部门 self仅自己',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '角色表';

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
) COMMENT '用户角色关联表';

-- 菜单权限表
CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
    menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    menu_type TINYINT COMMENT '类型 1目录 2菜单 3按钮',
    path VARCHAR(200) COMMENT '路由路径',
    component VARCHAR(200) COMMENT '组件路径',
    permission VARCHAR(100) COMMENT '权限标识',
    icon VARCHAR(100) COMMENT '图标',
    sort_order INT DEFAULT 0,
    visible TINYINT DEFAULT 1,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '菜单权限表';

-- 角色菜单关联表
CREATE TABLE sys_role_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_menu (role_id, menu_id)
) COMMENT '角色菜单关联表';

-- 组织架构(部门/办事处)
CREATE TABLE sys_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0 COMMENT '父部门ID',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    dept_code VARCHAR(10) DEFAULT NULL COMMENT '部门编码(大区拼音缩写)',
    dept_type TINYINT COMMENT '类型 1总部 2大区 3办事处',
    leader_id BIGINT COMMENT '负责人ID',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '组织架构表';

-- 字典类型表
CREATE TABLE sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
    dict_code VARCHAR(100) NOT NULL UNIQUE COMMENT '字典编码',
    status TINYINT DEFAULT 1,
    remark VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '字典类型表';

-- 字典数据表
CREATE TABLE sys_dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type_id BIGINT NOT NULL COMMENT '字典类型ID',
    dict_label VARCHAR(100) NOT NULL COMMENT '显示标签',
    dict_value VARCHAR(100) NOT NULL COMMENT '数据值',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '字典数据表';

-- 操作日志表
CREATE TABLE sys_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '操作人',
    username VARCHAR(50) COMMENT '操作人账号',
    module VARCHAR(50) COMMENT '模块',
    action VARCHAR(50) COMMENT '操作类型',
    target_type VARCHAR(50) COMMENT '目标类型',
    target_id BIGINT COMMENT '目标ID',
    detail VARCHAR(1000) COMMENT '详情',
    ip VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) COMMENT '操作日志表';

-- 系统设置表
CREATE TABLE sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500),
    description VARCHAR(200),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '系统设置表';

-- ============================================
-- 2. 客户管理模块
-- ============================================

-- 客户表
CREATE TABLE biz_customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_code VARCHAR(20) NOT NULL UNIQUE COMMENT '客户编号(C001)',
    customer_name VARCHAR(200) NOT NULL COMMENT '订货客户全称',
    invoice_name VARCHAR(200) COMMENT '开票名称',
    industry VARCHAR(50) COMMENT '行业',
    dev_type VARCHAR(50) COMMENT '开发类型(成熟/成熟创新/突破)',
    group_name VARCHAR(200) COMMENT '所属集团',
    branch_company VARCHAR(200) COMMENT '所属分公司',
    unit_name VARCHAR(200) COMMENT '所属单位',
    capacity VARCHAR(1024) COMMENT '产能(万吨)',
    province VARCHAR(50) COMMENT '省',
    city VARCHAR(50) COMMENT '市',
    district VARCHAR(50) COMMENT '区/县',
    address VARCHAR(500) COMMENT '厂址',
    has_terminal TINYINT DEFAULT 0 COMMENT '是否终端 0否 1是',
    register_date DATE COMMENT '注册日',
    last_invoice_date DATE COMMENT '最近开票日',
    expire_date DATE COMMENT '到期日(360天)',
    adjust_date DATE COMMENT '调整日(180天)',
    transfer_date DATE COMMENT '转移日',
    owner_id BIGINT COMMENT '第一负责人ID',
    manager_id BIGINT COMMENT '管理上级ID',
    office_id BIGINT COMMENT '所属办事处ID(dept_type=3,系统派生,公海路由/统计用)',
    region_id BIGINT COMMENT '所属大区ID(dept_type=2,新增/编辑页选择)',
    remark TEXT COMMENT '备注',
    status VARCHAR(20) DEFAULT 'normal' COMMENT '状态: normal/warning/expired/sea',
    protection_status VARCHAR(20) DEFAULT 'active' COMMENT '保护状态: active/expiring/expired',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_owner_id (owner_id),
    INDEX idx_office_id (office_id),
    INDEX idx_region_id (region_id),
    INDEX idx_expire_date (expire_date),
    INDEX idx_status (status),
    INDEX idx_industry (industry)
) COMMENT '客户表';

-- 客户跟进日志
CREATE TABLE biz_customer_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL COMMENT '记录人',
    log_type VARCHAR(50) COMMENT '类型(终端考察/技术交流/产品试点报告/其他)',
    content TEXT NOT NULL COMMENT '跟进内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_id (customer_id)
) COMMENT '客户跟进日志';

-- ============================================
-- 3. 公海管理模块
-- ============================================

CREATE TABLE biz_sea_pool (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    pool_id BIGINT COMMENT '所属公海池配置ID',
    original_owner_id BIGINT COMMENT '原负责人ID',
    enter_reason VARCHAR(50) COMMENT '进入原因: protection_expired/abandoned/resignation',
    enter_date DATETIME COMMENT '进入时间',
    claim_user_id BIGINT COMMENT '捞取人ID',
    claim_date DATETIME COMMENT '捞取时间',
    status VARCHAR(20) DEFAULT 'available' COMMENT '状态: available/pending_approval/claimed',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status)
) COMMENT '公海池';

-- 公海池配置表
CREATE TABLE biz_sea_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pool_name VARCHAR(100) NOT NULL COMMENT '公海池名称',
    region_ids VARCHAR(500) COMMENT '派生办事处ID缓存(逗号分隔dept_type=3),由可见部门/人员派生,用于释放路由',
    view_role_ids VARCHAR(500) COMMENT '已废弃,保留列',
    visible_dept_ids VARCHAR(500) COMMENT '可见部门ID(逗号分隔,dept_type=4)',
    visible_user_ids VARCHAR(500) COMMENT '可见人员ID(逗号分隔)',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '公海池配置表';

-- ============================================
-- 4. 审批管理模块
-- ============================================

-- 审批流程定义
CREATE TABLE biz_approval_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flow_type VARCHAR(50) NOT NULL COMMENT '类型: claim/transfer/extend/storage',
    flow_name VARCHAR(100) COMMENT '流程名称',
    approver_role_id BIGINT COMMENT '审批角色ID',
    sort_order INT DEFAULT 0 COMMENT '审批顺序(多级审批)',
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '审批流程定义';

-- 审批记录
CREATE TABLE biz_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    approval_code VARCHAR(30) NOT NULL UNIQUE COMMENT '审批编号(P2024-001)',
    approval_type VARCHAR(50) NOT NULL COMMENT '类型: claim/transfer/extend/storage',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    customer_id BIGINT COMMENT '关联客户ID',
    current_node VARCHAR(100) COMMENT '当前审批节点',
    action_after_approve VARCHAR(200) COMMENT '通过后动作描述',
    extra_data JSON COMMENT '扩展数据',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/reviewing/approved/rejected',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_applicant_id (applicant_id),
    INDEX idx_status (status),
    INDEX idx_approval_type (approval_type)
) COMMENT '审批记录';

-- 审批节点记录
CREATE TABLE biz_approval_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    approval_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL COMMENT '审批人ID',
    node_order INT COMMENT '节点顺序',
    action VARCHAR(20) COMMENT '操作: approve/reject',
    comment VARCHAR(500) COMMENT '审批意见',
    operated_at DATETIME COMMENT '操作时间',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态',
    INDEX idx_approval_id (approval_id),
    INDEX idx_approver_id (approver_id)
) COMMENT '审批节点记录';

-- ============================================
-- 5. 文件管理模块
-- ============================================

-- 文件目录表
CREATE TABLE biz_file_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0 COMMENT '父目录ID',
    folder_name VARCHAR(100) NOT NULL COMMENT '目录名称',
    folder_type VARCHAR(20) COMMENT '类型: public/private/incentive',
    owner_id BIGINT COMMENT '所属用户ID(私人目录)',
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id),
    INDEX idx_owner_id (owner_id)
) COMMENT '文件目录表';

-- 文件表
CREATE TABLE biz_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    folder_id BIGINT COMMENT '所属目录ID',
    file_name VARCHAR(200) NOT NULL COMMENT '文件名',
    file_size BIGINT COMMENT '文件大小(字节)',
    file_type VARCHAR(20) COMMENT '文件类型',
    oss_key VARCHAR(500) NOT NULL COMMENT 'OSS对象Key',
    oss_url VARCHAR(1000) COMMENT 'OSS访问URL',
    file_scope VARCHAR(20) DEFAULT 'private' COMMENT '范围: public/private/incentive',
    uploader_id BIGINT NOT NULL COMMENT '上传者ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_folder_id (folder_id),
    INDEX idx_uploader_id (uploader_id),
    INDEX idx_file_scope (file_scope)
) COMMENT '文件表';

-- ============================================
-- 6. 提醒管理模块
-- ============================================

CREATE TABLE biz_reminder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    reminder_type VARCHAR(50) COMMENT '类型: invoice_overdue/expiring',
    owner_id BIGINT COMMENT '负责人ID',
    office_id BIGINT COMMENT '办事处ID',
    overdue_days INT COMMENT '已超天数',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/processed',
    processed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_id (customer_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_status (status)
) COMMENT '提醒记录表';

-- ============================================
-- 7. 四项激励Plus模块
-- ============================================

-- 激励分类配置
CREATE TABLE biz_incentive_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0 COMMENT '父分类ID(0为四项大类)',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    category_code VARCHAR(50) COMMENT '分类编码',
    score INT DEFAULT 0 COMMENT '分值',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '激励分类配置';

-- 激励上传记录
CREATE TABLE biz_incentive_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    customer_id BIGINT COMMENT '关联客户ID',
    period VARCHAR(20) COMMENT '周期(如2026上半年)',
    industry VARCHAR(50) COMMENT '行业(取字典 industry)',
    related_products VARCHAR(1000) COMMENT '相关产品(多行文本)',
    file_id BIGINT COMMENT '关联文件ID(旧版单文件,历史记录用)',
    pre_install_question TEXT COMMENT '安装前问题',
    file_pre_id BIGINT COMMENT '安装前文件ID',
    file_mid_id BIGINT COMMENT '安装中文件ID',
    file_post_id BIGINT COMMENT '安装后文件ID',
    remark VARCHAR(500) COMMENT '备注',
    score INT DEFAULT 0 COMMENT '得分(子类分值×文件数)',
    shared TINYINT DEFAULT 0 COMMENT '是否共享给团队 0否1是',
    status VARCHAR(20) DEFAULT 'uploaded' COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_category_id (category_id),
    INDEX idx_period (period)
) COMMENT '激励上传记录';

-- 联系人表
CREATE TABLE IF NOT EXISTS biz_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    name VARCHAR(50) COMMENT '姓名',
    position VARCHAR(50) COMMENT '职位',
    phone VARCHAR(30) COMMENT '电话',
    remark VARCHAR(200) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_customer_id (customer_id)
) COMMENT '客户联系人';

-- 回款记录表
CREATE TABLE IF NOT EXISTS biz_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    payment_date DATE COMMENT '回款日期',
    payment_amount DECIMAL(12,2) COMMENT '回款金额(万元)',
    payment_method VARCHAR(50) COMMENT '回款方式',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_customer_id (customer_id)
) COMMENT '回款记录';

-- ============================================
-- 初始数据
-- ============================================

-- 默认角色
INSERT INTO sys_role (role_name, role_code, description, data_scope, sort_order) VALUES
('系统管理员', 'admin', '拥有全部权限', 'all', 1),
('销售经理', 'sales_manager', '管理销售团队和客户审批', 'dept', 2),
('销售工程师', 'sales_engineer', '管理个人客户', 'self', 3),
('技术人员', 'technician', '技术支持', 'self', 4);

-- 默认组织架构
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, dept_type, sort_order) VALUES
(1, 0, '总部', NULL, 1, 1),
(2, 1, '内蒙大区', 'NM', 2, 1),
(3, 1, '山西大区', 'SX', 2, 2),
(4, 2, '内蒙办', NULL, 3, 1),
(5, 2, '鄂尔多斯办', NULL, 3, 2),
(6, 3, '太原办', NULL, 3, 1),
(7, 3, '西安办', NULL, 3, 2),
(8, 1, '技术部', NULL, 2, 3),
(9, 4, '内蒙办销售部', NULL, 4, 1),
(10, 6, '太原办销售部', NULL, 4, 1);

-- 管理员账号 (密码: admin123, BCrypt加密)
INSERT INTO sys_user (username, password, real_name, phone, dept_id, office_id, status) VALUES
('admin', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '吴哲', '13800000000', 1, 1, 1),
('wangleilei', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '王磊磊', '13800000001', 9, 4, 1),
('zhangsan', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '张三', '13800000002', 9, 4, 1),
('liming', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '李明', '13800000003', 10, 6, 1),
('zhaosi', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '赵四', '13800000004', 9, 4, 1),
('lijia', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '李佳', '13800000005', 10, 6, 1),
('zhaomin', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '赵敏', '13800000006', 9, 4, 1),
('mawenjie', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '马文杰', '13800000007', 9, 4, 1),
('zhangw', '$2a$10$SvbnuTsrmn4.prfpPhePqOn1cakD4IPrMy1S6QaLuT3/avIzxjieO', '张工', '13800000008', 8, 8, 1);

-- 用户角色分配
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), -- admin -> 系统管理员
(2, 3), -- 王磊磊 -> 销售工程师
(3, 3), -- 张三 -> 销售工程师
(4, 2), -- 李明 -> 销售经理
(5, 3), -- 赵四 -> 销售工程师
(6, 3), -- 李佳 -> 销售工程师
(7, 3), -- 赵敏 -> 销售工程师
(8, 3), -- 马文杰 -> 销售工程师
(9, 4); -- 张工 -> 技术人员

-- 菜单数据
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, visible) VALUES
-- 首页
(1, 0, '首页', 1, '', '', '', 'HomeFilled', 0, 1),
(2, 1, '工作台', 2, '/dashboard', 'dashboard/index', 'dashboard:view', 'Monitor', 1, 1),
-- 业务核心
(10, 0, '业务核心', 1, '', '', '', 'Briefcase', 1, 1),
(11, 10, '客户管理', 2, '/customer', 'customer/list', 'customer:view', 'User', 1, 1),
(12, 10, '新增客户', 2, '/customer/add', 'customer/add', 'customer:add', 'Plus', 2, 1),
(13, 10, '客户详情', 2, '/customer/detail/:id', 'customer/detail', 'customer:view', 'Document', 3, 0),
(14, 10, '文件管理', 2, '/file', 'file/index', 'file:view', 'Folder', 4, 1),
(15, 10, '四项激励Plus版', 2, '/incentive', 'incentive/index', 'incentive:view', 'Trophy', 5, 1),
(16, 10, '提醒管理', 2, '/reminder', 'reminder/index', 'reminder:view', 'Bell', 6, 1),
(17, 10, '公海管理', 2, '/sea', 'sea/index', 'sea:view', 'Ship', 7, 1),
(18, 10, '审批管理', 2, '/approval', 'approval/index', 'approval:view', 'CircleCheck', 8, 1),
-- 考核与分析
(20, 0, '考核与分析', 1, '', '', '', 'DataAnalysis', 2, 1),
(21, 20, '统计分析', 2, '/statistics', 'statistics/index', 'statistics:view', 'TrendCharts', 1, 1),
(22, 20, '积分榜', 2, '/scoreboard', 'statistics/scoreboard', 'statistics:scoreboard', 'Medal', 2, 1),
-- 系统管理
(30, 0, '系统管理', 1, '', '', '', 'Setting', 3, 1),
(31, 30, '人员管理', 2, '/system/user', 'system/user', 'system:user:view', 'UserFilled', 1, 1),
(32, 30, '组织架构', 2, '/system/dept', 'system/dept', 'system:dept:view', 'OfficeBuilding', 2, 1),
(33, 30, '角色管理', 2, '/system/role', 'system/role', 'system:role:view', 'Key', 3, 1),
(34, 30, '系统设置', 2, '/system/config', 'system/config', 'system:config:view', 'Tools', 4, 1),
(35, 30, '日志管理', 2, '/system/log', 'system/log', 'system:log:view', 'Notebook', 5, 1),
-- 按钮权限
(40, 11, '客户新增', 3, '', '', 'customer:add', '', 1, 1),
(41, 11, '客户编辑', 3, '', '', 'customer:edit', '', 2, 1),
(42, 11, '客户删除', 3, '', '', 'customer:delete', '', 3, 1),
(43, 11, '客户导出', 3, '', '', 'customer:export', '', 4, 1),
(44, 17, '公海捞取', 3, '', '', 'sea:claim', '', 1, 1),
(53, 17, '公海编辑', 3, '', '', 'sea:pool:edit', '', 2, 1),
(54, 17, '公海删除', 3, '', '', 'sea:pool:delete', '', 3, 1),
(55, 17, '公海导入', 3, '', '', 'sea:pool:import', '', 4, 1),
(45, 18, '审批通过', 3, '', '', 'approval:approve', '', 1, 1),
(46, 18, '审批驳回', 3, '', '', 'approval:reject', '', 2, 1),
(47, 14, '文件上传', 3, '', '', 'file:upload', '', 1, 1),
(48, 14, '文件删除', 3, '', '', 'file:delete', '', 2, 1),
(49, 31, '用户新增', 3, '', '', 'system:user:add', '', 1, 1),
(50, 31, '用户编辑', 3, '', '', 'system:user:edit', '', 2, 1),
(51, 33, '角色新增', 3, '', '', 'system:role:add', '', 1, 1),
(52, 33, '角色编辑', 3, '', '', 'system:role:edit', '', 2, 1);

-- 角色菜单分配 (管理员拥有全部)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 销售经理权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 1),(2, 2),(2, 10),(2, 11),(2, 12),(2, 13),(2, 14),(2, 15),(2, 16),(2, 17),(2, 18),
(2, 20),(2, 21),(2, 22),(2, 40),(2, 41),(2, 42),(2, 43),(2, 44),(2, 45),(2, 46),(2, 47),(2, 48);

-- 销售工程师权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 1),(3, 2),(3, 10),(3, 11),(3, 12),(3, 13),(3, 14),(3, 15),(3, 16),(3, 17),(3, 18),
(3, 20),(3, 21),(3, 22),(3, 40),(3, 41),(3, 43),(3, 44),(3, 47);

-- 技术人员权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(4, 1),(4, 2),(4, 10),(4, 11),(4, 13),(4, 14),(4, 15),(4, 20),(4, 21),(4, 22),(4, 47);

-- 审批流程定义
INSERT INTO biz_approval_flow (flow_type, flow_name, approver_role_id, sort_order) VALUES
('claim', '客户捞取审批', 2, 1),
('transfer', '客户转移审批', 2, 1),
('extend', '保护期延期审批', 2, 1),
('storage', '存储空间申请', 1, 1);

-- 四项激励分类（管理员可维护）
INSERT INTO biz_incentive_category (id, parent_id, category_name, category_code, score, sort_order) VALUES
-- 四大类
(1, 0, '外出参观', 'visit', 0, 1),
(2, 0, '交流拜访', 'exchange', 0, 2),
(3, 0, '两谈两做方案', 'plan', 0, 3),
(4, 0, '产品试点', 'trial', 0, 4),
-- 外出参观子类
(10, 1, '矿区参观', 'visit_mine', 2, 1),
(11, 1, '工厂参观', 'visit_factory', 2, 2),
(12, 1, '展会参观', 'visit_expo', 1, 3),
-- 交流拜访子类
(20, 2, '技术交流会', 'exchange_tech', 2, 1),
(21, 2, '商务拜访', 'exchange_biz', 1, 2),
(22, 2, '客户回访', 'exchange_revisit', 1, 3),
-- 两谈两做方案子类
(30, 3, '技术方案', 'plan_tech', 3, 1),
(31, 3, '商务方案', 'plan_biz', 2, 2),
(32, 3, '合作方案', 'plan_coop', 2, 3),
-- 产品试点子类
(40, 4, '涂层类', 'trial_coating', 1, 1),
(41, 4, '楼梯板类', 'trial_stair', 1, 2),
(42, 4, '机封类', 'trial_seal', 1, 3),
(43, 4, '密封类', 'trial_gasket', 1, 4),
(44, 4, '润滑类', 'trial_lubricant', 1, 5);

-- 公海池配置 (visible_dept_ids=可见部门; region_ids=由可见部门/人员派生的办事处,用于释放路由)
INSERT INTO biz_sea_config (pool_name, visible_dept_ids, visible_user_ids, region_ids, sort_order) VALUES
('内蒙办公海', '9', NULL, '4', 1),
('太原办公海', '10', NULL, '6', 2);

-- 公共文件目录
INSERT INTO biz_file_folder (id, parent_id, folder_name, folder_type, owner_id, sort_order) VALUES
(1, 0, '客户资料', 'public', NULL, 1),
(2, 0, '合同文件', 'public', NULL, 2),
(3, 0, '技术方案', 'public', NULL, 3),
(4, 0, '市场资料', 'public', NULL, 4);

-- 系统配置
INSERT INTO sys_config (config_key, config_value, description) VALUES
('system.name', '运筹马年CRM', '系统名称'),
('system.logo', 'logo.png', '系统Logo'),
('customer.protection.days', '360', '客户保护期'),
('customer.adjust.days', '180', '客户调整期天数'),
('customer.warning.days', '30', '提醒提前'),
('invoice.overdue.days', '365', '开票超期天数'),
('user.default.storage', '10737418240', '用户默认存储空间(字节,10GB)'),
('system.subtitle', '市场资源管理系统', '系统副标题'),
('customer.code.rule', 'region_date_seq', '编号规则');

-- 字典类型
INSERT INTO sys_dict_type (id, dict_name, dict_code, remark) VALUES
(1, '行业类型', 'industry', '客户所属行业'),
(2, '开发类型', 'dev_type', '客户开发类型');

-- 字典数据
INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, sort_order) VALUES
(1, '煤矿', '煤矿', 1),
(1, '化工', '化工', 2),
(1, '电力', '电力', 3),
(1, '洗煤厂', '洗煤厂', 4),
(1, '有色', '有色', 5),
(1, '其他', '其他', 6),
(2, '成熟', '成熟', 1),
(2, '成熟创新', '成熟创新', 2),
(2, '突破', '突破', 3);

-- 示例客户数据
INSERT INTO biz_customer (customer_code, customer_name, invoice_name, industry, dev_type, group_name, branch_company, unit_name, capacity, province, city, district, address, has_terminal, register_date, last_invoice_date, expire_date, adjust_date, owner_id, office_id, status, protection_status) VALUES
('C001', '陕煤榆林化学(含二期)', '陕煤榆林化学有限公司', '化工', '成熟', '陕煤集团', '陕煤榆林化学', '榆林化学工厂', NULL, '陕西', '榆林', '神木', '榆神工业区', 1, '2025-08-17', '2026-01-15', '2026-06-12', NULL, 2, 4, 'warning', 'expiring'),
('C002', '鄂尔多斯煤制油', '鄂尔多斯煤制油公司', '化工', '成熟', '国家能源', '煤制油公司', NULL, NULL, '内蒙古', '鄂尔多斯', NULL, NULL, 1, '2025-09-20', '2026-03-10', '2026-07-04', NULL, 2, 4, 'normal', 'active'),
('C003', '神东哈拉沟煤矿', '神东哈拉沟煤矿', '煤矿', '成熟', '国家能源', '神东煤炭', NULL, '1207万', '陕西', '榆林', '神木', NULL, 1, '2025-11-15', '2025-12-20', '2026-09-10', NULL, 2, 4, 'normal', 'active'),
('C004', '神东大柳塔煤矿', '神东大柳塔煤矿', '煤矿', '成熟', '国家能源', '神东煤炭', NULL, '498万', '陕西', '榆林', '神木', NULL, 1, '2025-12-01', '2026-01-15', '2026-09-27', NULL, 3, 4, 'normal', 'active'),
('C005', '汇能蒙南电厂', '汇能蒙南电厂', '电力', '突破', '汇能集团', '蒙南电厂', NULL, NULL, '内蒙古', '鄂尔多斯', NULL, NULL, 1, '2025-06-10', NULL, '2026-04-05', NULL, 2, 5, 'expired', 'expired'),
('C006', '陕西有色天瑞硅材料', '天瑞硅材料有限公司', '有色', '突破', '陕西有色', '天瑞硅材料', NULL, NULL, '陕西', '榆林', NULL, NULL, 1, '2025-10-05', '2025-12-20', '2026-07-31', NULL, 4, 4, 'normal', 'active'),
('C007', '神东寸草塔煤矿', '神东寸草塔煤矿', '煤矿', '突破', '国家能源', '神东煤炭', NULL, '240万', '内蒙古', '鄂尔多斯', NULL, NULL, 0, '2025-07-20', NULL, '2026-05-15', NULL, 4, 5, 'expired', 'expired'),
('C008', '陕西榆神能源', '榆神能源有限公司', '电力', '成熟', '榆神能源', NULL, NULL, NULL, '陕西', '榆林', NULL, NULL, 1, '2025-11-28', '2026-02-15', '2026-09-23', NULL, 5, 4, 'normal', 'active'),
('C009', '鄂尔多斯煤制油二期', '煤制油二期项目', '化工', '突破', '国家能源', '煤制油公司', NULL, NULL, '内蒙古', '鄂尔多斯', NULL, NULL, 0, '2026-01-10', NULL, '2026-11-05', NULL, 3, 6, 'normal', 'active'),
('C010', '神东补连塔煤矿', '补连塔煤矿', '煤矿', '成熟', '国家能源', '神东煤炭', NULL, '2148万', '内蒙古', '鄂尔多斯', NULL, NULL, 1, '2025-08-30', '2025-11-15', '2026-06-25', NULL, 2, 4, 'warning', 'expiring'),
('C011', '陕西煤业化工集团', '陕煤化集团总部', '化工', '成熟', '陕煤集团', NULL, NULL, NULL, '陕西', '西安', NULL, NULL, 1, '2025-12-15', '2026-04-20', '2026-10-11', NULL, 5, 7, 'normal', 'active'),
('C012', '山西焦煤集团', '山西焦煤集团', '煤矿', '突破', '山西焦煤', NULL, NULL, NULL, '山西', '太原', NULL, NULL, 1, '2026-02-01', NULL, '2026-11-27', NULL, 4, 6, 'normal', 'active'),
('C013', '中煤平朔集团', '中煤平朔集团', '煤矿', '突破', '中煤集团', '平朔公司', NULL, NULL, '山西', '朔州', NULL, NULL, 1, '2026-03-01', NULL, '2026-12-25', NULL, 3, 6, 'normal', 'active'),
('C014', '神东榆家梁煤矿', '榆家梁煤矿', '煤矿', '成熟', '国家能源', '神东煤炭', NULL, '1630万', '陕西', '榆林', '神木', NULL, 1, '2025-10-20', '2026-02-28', '2026-08-16', NULL, 2, 4, 'normal', 'active'),
('C015', '神东保德煤矿', '保德煤矿', '煤矿', '成熟', '国家能源', '神东煤炭', NULL, '1400万', '山西', '忻州', '保德', NULL, 1, '2025-09-05', '2025-12-10', '2026-07-01', NULL, 8, 4, 'normal', 'active');

-- 回填客户所属大区(region_id): 由 office_id 向上取最近的 dept_type=2 大区
UPDATE biz_customer bc
JOIN sys_dept o ON o.id = bc.office_id
LEFT JOIN sys_dept p ON p.id = o.parent_id
SET bc.region_id = CASE
    WHEN o.dept_type = 2 THEN o.id
    WHEN p.dept_type = 2 THEN p.id
    ELSE NULL END
WHERE bc.office_id IS NOT NULL;
