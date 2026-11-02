-- ============================================================
-- 客户生命线事件表(2026-11-02 用户裁决 D:
--   "客户的这些日期需要做记录和追踪, 后期能看到这个客户的完整生命线")
-- 只增不改的追加日志表: 关键日期事件由各业务路径写入,
-- PC 客户详情"生命线"区块按时间倒序展示。
-- 事件类型(本批埋点, 不留未消费的预留类型):
--   created        建档
--   invoice_adv    开票推进最近开票日(逐笔/阶段录入, 日期前移)
--   invoice_manual 人工维护最近开票日(customer:invoice-date:edit 持码者)
--   extend         延期审批批准(累计延期天数)
--   released       释放进公海(手动/到期自动/导入/离职统一入池口)
--   claimed        公海捞取成功(新负责人+短保护期)
--   transferred    转移审批过户
-- ============================================================

CREATE TABLE IF NOT EXISTS `biz_customer_event` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `tenant_id`   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户(插件维护,业务代码不读写)',
  `customer_id` BIGINT       NOT NULL COMMENT '客户',
  `event_type`  VARCHAR(40)  NOT NULL COMMENT '事件类型',
  `event_date`  DATE         NOT NULL COMMENT '业务日期',
  `detail`      VARCHAR(300) NULL COMMENT '事件说明',
  `old_value`   VARCHAR(100) NULL COMMENT '变更前值',
  `new_value`   VARCHAR(100) NULL COMMENT '变更后值',
  `created_by`  BIGINT       NULL COMMENT '操作人(系统自动事件为NULL)',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_ce_customer` (`customer_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '客户生命线事件(只增不改)';
