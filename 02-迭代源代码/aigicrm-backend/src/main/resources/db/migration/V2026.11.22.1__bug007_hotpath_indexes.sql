-- 【BUG-20261122-007】热路径关联列补最左索引(全系统测试矩阵 10 域 SC-04 定位, 2026-11-22)
-- 背景: verify-db-schema-review.sh 系统体检发现三处热路径表关联列无最左索引——
--   sys_user_role.role_id: 角色解绑/按角色清理路径(uk_user_role 最左是 user_id,
--     user_id 查询已覆盖, role_id 反向查询全表扫);
--   biz_approval.customer_id: 在途防重查询(ensureExpiring 同款 pending/processedToday
--     计数按 customer_id 过滤)与审批列表关联;
--   biz_customer_log.user_id: 用户操作时间线查询(现仅 idx_customer_id)。
-- 当前表规模小(千行级)性能影响甚微, 属结构性债清偿; 索引名为项目既有命名风格(idx_<列>)。
CREATE INDEX idx_role_id ON sys_user_role (role_id);
CREATE INDEX idx_customer_id ON biz_approval (customer_id);
CREATE INDEX idx_user_id ON biz_customer_log (user_id);
