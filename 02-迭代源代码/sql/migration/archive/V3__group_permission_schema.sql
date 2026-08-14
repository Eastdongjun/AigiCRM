-- ============================================================
-- V3: 集团制组织权限模型升级
-- 参考若依+JeecgBoot+泛微OA 多层级权限控制体系
-- 执行前请备份数据库！
-- ============================================================

-- ============================================================
-- 一、集团表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_group` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_name`  VARCHAR(100) NOT NULL COMMENT '集团名称',
    `group_code`  VARCHAR(50)  NOT NULL COMMENT '集团编码（唯一）',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '集团描述',
    `sort_order`  INT DEFAULT 0 COMMENT '排序',
    `status`      TINYINT DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集团表';

-- ============================================================
-- 二、法人公司表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_legal_entity` (
    `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `entity_name`  VARCHAR(100) NOT NULL COMMENT '法人公司名称',
    `entity_code`  VARCHAR(50)  NOT NULL COMMENT '法人公司编码（唯一）',
    `group_id`     BIGINT NOT NULL COMMENT '所属集团ID',
    `credit_code`  VARCHAR(50)  DEFAULT NULL COMMENT '统一社会信用代码',
    `description`  VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `sort_order`   INT DEFAULT 0 COMMENT '排序',
    `status`       TINYINT DEFAULT 1 COMMENT '状态 0=禁用 1=启用',
    `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_entity_code` (`entity_code`),
    INDEX `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法人公司表';

-- ============================================================
-- 三、组织表扩展 — 增加集团和法人公司归属
-- sys_org 是视图（基于 sys_dept），列加到基表 sys_dept，视图自动透传
-- ============================================================
ALTER TABLE `sys_dept`
    ADD COLUMN IF NOT EXISTS `group_id`         BIGINT DEFAULT NULL COMMENT '所属集团ID' AFTER `parent_id`,
    ADD COLUMN IF NOT EXISTS `legal_entity_id`  BIGINT DEFAULT NULL COMMENT '所属法人公司ID' AFTER `group_id`,
    ADD INDEX IF NOT EXISTS `idx_group_id` (`group_id`),
    ADD INDEX IF NOT EXISTS `idx_legal_entity_id` (`legal_entity_id`);

-- ============================================================
-- 四、客户表扩展 — 增加集团和法人公司外键
-- ============================================================
ALTER TABLE `biz_customer`
    ADD COLUMN IF NOT EXISTS `group_id`         BIGINT DEFAULT NULL COMMENT '所属集团ID' AFTER `region_id`,
    ADD COLUMN IF NOT EXISTS `legal_entity_id`  BIGINT DEFAULT NULL COMMENT '所属法人公司ID' AFTER `group_id`,
    ADD INDEX IF NOT EXISTS `idx_group_id` (`group_id`),
    ADD INDEX IF NOT EXISTS `idx_legal_entity_id` (`legal_entity_id`);

-- ============================================================
-- 五、初始化示例数据
-- ============================================================

-- 插入默认集团
INSERT IGNORE INTO `sys_group` (`id`, `group_name`, `group_code`, `description`, `sort_order`) VALUES
(1, '陕西青阳耀东科技集团', 'qyyd', '默认集团（系统初始化）', 0);

-- 插入默认法人公司
INSERT IGNORE INTO `sys_legal_entity` (`id`, `entity_name`, `entity_code`, `group_id`, `description`, `sort_order`) VALUES
(1, '陕西青阳耀东科技有限公司', 'qyyd-main', 1, '主法人公司', 0);

-- ============================================================
-- 六、数据回填（如果已有数据，建议手动执行以下逻辑）
-- ============================================================
-- 将现有组织全部归属到默认集团和法人公司
-- UPDATE sys_org SET group_id = 1, legal_entity_id = 1 WHERE group_id IS NULL;
-- 将现有客户通过 office_id 反查组织找到归属
-- UPDATE biz_customer c
--   JOIN sys_org o ON c.office_id = o.id
--   SET c.group_id = o.group_id, c.legal_entity_id = o.legal_entity_id
--   WHERE c.group_id IS NULL;
