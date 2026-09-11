-- 修复测试库 润滑类 分类 parent_id 错配（2026-08-07）
-- 背景: 润滑类(id=44, code=trial_lubricant) 本应是产品试点(trial,id=4)的子分类，
--       但测试库被设为 parent_id=0(顶层)，导致出现第5个顶层分类。
--       生产库正确(parent_id=4)。对齐生产。
-- 无记录引用 category_id=44(已验证)，改动安全。
-- 执行: mysql --default-character-set=utf8mb4 aigicrm < fix-test-category-lubricant-parent.sql

UPDATE biz_incentive_category SET parent_id = 4 WHERE id = 44 AND parent_id = 0;
