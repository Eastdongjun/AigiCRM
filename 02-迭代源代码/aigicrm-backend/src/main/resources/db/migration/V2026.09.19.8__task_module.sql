-- 【PR-10 2026-09-19】任务模块:跟进记录与下一步任务衔接
-- 总册:"跟进支持本次结果、下一动作、责任人、计划时间及完成条件;
--        简单记录可不生成任务;明确承诺可形成待办;任务完成必须保留结果"
-- 口径:提醒已读 ≠ 任务完成(不混淆);任务入口嵌入现有页面(不加底部菜单)

CREATE TABLE IF NOT EXISTS biz_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id     BIGINT       NULL COMMENT '关联客户(可空=通用任务)',
    title           VARCHAR(200) NOT NULL COMMENT '任务标题(如"提交选型方案")',
    detail          VARCHAR(1000) NULL COMMENT '完成条件/详细说明',
    assignee_id     BIGINT       NOT NULL COMMENT '责任人',
    due_date        DATE         NULL COMMENT '计划完成时间',
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/in_progress/completed/cancelled',
    completed_at    DATETIME     NULL COMMENT '完成时间',
    completion_note VARCHAR(1000) NULL COMMENT '完成结果(必须记录,不是简单勾掉)',
    source_log_id   BIGINT       NULL COMMENT '来源跟进日志ID(点回去看上下文)',
    created_by      BIGINT       NOT NULL COMMENT '创建人',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    tenant_id       BIGINT       NULL COMMENT '集团隔离',
    KEY idx_task_assignee (assignee_id, status),
    KEY idx_task_customer (customer_id),
    KEY idx_task_due (due_date, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='任务(跟进衔接的待办事项)';

-- 权限码:任务查看/管理
INSERT INTO sys_menu (menu_name, menu_type, permission, sort_order, visible, status)
SELECT '任务查看', 3, 'task:view', 99, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'task:view');

INSERT INTO sys_menu (menu_name, menu_type, permission, sort_order, visible, status)
SELECT '任务管理', 3, 'task:manage', 99, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'task:manage');
