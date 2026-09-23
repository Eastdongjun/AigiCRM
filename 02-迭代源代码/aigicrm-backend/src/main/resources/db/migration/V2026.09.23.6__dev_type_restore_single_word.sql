-- ============================================================
-- V2026.09.23.6__dev_type_restore_single_word.sql
-- 撤销 23.5 的拆分: 「新建改建扩建大修」是一个完整分类词, 不拆分
-- ============================================================
-- 背景(2026-09-23 用户纠正):
--   "新建改建扩建大修 是一个词 不要断句" —— 23.5 把该连写项误解为
--   需要拆分的四个分类(新建/改建/扩建/大修)。实际需求是:
--   「新建改建扩建大修」作为一个完整分类保留, 客户列表加本分类页签。
--
-- 本迁移做什么(幂等, 按 dict_label 定位):
--   1) 恢复连写项 status=1(启用)
--   2) 删除四个拆分项(生产实测零客户引用, 删除安全; 若有引用则
--      先把引用客户归并回连写项再删, 归并逻辑幂等)
--
-- 前端同批: 客户列表 Tab 由四个拆分页签改为一个「新建改建扩建大修」页签。
-- ============================================================

-- 0) 若有客户已被打到拆分值, 归并回连写项(幂等, 防数据丢失)
UPDATE biz_customer SET dev_type = '新建改建扩建大修'
WHERE dev_type IN ('新建', '改建', '扩建', '大修');

-- 1) 恢复连写项
UPDATE sys_dict_data
SET status = 1, updated_at = NOW()
WHERE dict_label = '新建改建扩建大修'
  AND dict_type_id = (SELECT id FROM sys_dict_type WHERE dict_code = 'dev_type')
  AND status = 0;

-- 2) 删除四个拆分项
DELETE FROM sys_dict_data
WHERE dict_label IN ('新建', '改建', '扩建', '大修')
  AND dict_type_id = (SELECT id FROM sys_dict_type WHERE dict_code = 'dev_type');

-- 复核(预期 4 行启用: 成熟创新/突破开拓/铺垫宣传/新建改建扩建大修):
--   SELECT d.dict_label, d.status FROM sys_dict_data d
--     JOIN sys_dict_type t ON d.dict_type_id=t.id
--   WHERE t.dict_code='dev_type' ORDER BY d.sort_order;
