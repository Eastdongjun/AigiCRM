-- ============================================================
-- 迁移: 客户新增 region_id(所属大区, dept_type=2)
-- 背景: 「新增客户/客户列表」的"所属办事处"改为"所属大区"。
--       officeId(办事处,dept_type=3)保持不变(公海路由/编号/统计仍用),
--       新增独立 region_id 供选择与展示,零破坏现有功能。
-- 用法: mysql ... --default-character-set=utf8mb4 aigicrm < migration-customer-region.sql
-- ============================================================

-- 1) 加列 + 索引(重复执行前请自行确认列是否已存在)
ALTER TABLE biz_customer
    ADD COLUMN region_id BIGINT COMMENT '所属大区ID(dept_type=2,新增/编辑页选择)' AFTER office_id;
ALTER TABLE biz_customer
    ADD INDEX idx_region_id (region_id);

-- 2) 回填历史客户: 由 office_id 向上取最近的 dept_type=2 大区
--    (本地组织: 办事处 type=3 的 parent 即为大区 type=2)
UPDATE biz_customer bc
JOIN sys_dept o ON o.id = bc.office_id
LEFT JOIN sys_dept p ON p.id = o.parent_id
SET bc.region_id = CASE
    WHEN o.dept_type = 2 THEN o.id
    WHEN p.dept_type = 2 THEN p.id
    ELSE NULL END
WHERE bc.office_id IS NOT NULL;

-- 注: 生产(运筹虎年)组织层级与本地不同(顶层是 type=3 办事处),
--     若无真正的 dept_type=2 大区数据, region_id 回填结果会为 NULL,
--     需按生产实际组织树单独确认大区数据后再回填。
