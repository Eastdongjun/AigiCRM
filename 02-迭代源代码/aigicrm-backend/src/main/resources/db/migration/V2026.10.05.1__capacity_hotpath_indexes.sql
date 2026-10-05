-- 【容量加固 2026-10-05】热路径索引
-- 依据：容量评估（dwfrun-cfeed7cb）数据层专项实证——
--   1) 客户列表默认 ORDER BY created_at DESC 无索引（filesort），且 deptId IN 子树筛选无索引；
--      PC 端一次拉 500 行（useCustomerList.js size:500），当前 597 行无感、表增长后每次列表全表扫。
--   2) 工作台「团队动态」每刷新跑 6 个 ORDER BY created_at DESC LIMIT 的无索引 filesort：
--      biz_customer_log / biz_file / biz_approval / biz_incentive_record 均无 created_at 索引，
--      这些表只增不减（sys_log 已有 idx_created_at，不含在内）。
-- 行为影响：纯加索引，不改数据不改语义；表行数当前千级，线上执行瞬时完成。

CREATE INDEX idx_biz_customer_created_at ON biz_customer (created_at);
CREATE INDEX idx_biz_customer_dept_id ON biz_customer (dept_id);
CREATE INDEX idx_biz_customer_log_created_at ON biz_customer_log (created_at);
CREATE INDEX idx_biz_file_created_at ON biz_file (created_at);
CREATE INDEX idx_biz_approval_created_at ON biz_approval (created_at);
CREATE INDEX idx_biz_incentive_record_created_at ON biz_incentive_record (created_at);
