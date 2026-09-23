-- ============================================================
-- V2026.09.23.4__dev_type_split_four_categories.sql
-- 开发类型字典: 「新建改建扩建大修」连写项拆分为四个独立分类
-- ============================================================
-- 背景(2026-09-23 用户需求):
--   "新建改建扩建大修 在开发类型里面, 客户列表里面也要新增本分类及对应的数据"
--
--   现状(生产实测): sys_dict_data dev_type 共 4 项 ——
--     id 7  成熟创新(48 客户) / id 8 突破开拓(203) / id 9 铺垫宣传(182)
--     id 91 「新建改建扩建大修」八字连写单项(5 客户)
--   连写项无法单独筛选统计, 拆为 新建/改建/扩建/大修 四个独立分类。
--
-- 本迁移做什么(幂等, 按 dict_label 定位不硬编码 id):
--   1) dev_type 新增四项: 新建(5)/改建(6)/扩建(7)/大修(8)
--   2) 旧连写项 id 91 → status=0 停用(新表单/筛选不再出现;
--      存量 5 客户字段值保留, 详情页仍显示原文本, 由业务侧逐步改到细分项)
--
-- 关联前端(同批提交): PC 客户列表 Tab(useCustomerList)新增四个分类页签,
--   各页签按 devType 精确匹配展示对应客户; 小程序/PC 表单与激励查询的
--   开发类型选项均走字典接口自动获得新分类。
--
-- 回滚(需人工确认后单独执行):
--   UPDATE sys_dict_data SET status=1 WHERE dict_label='新建改建扩建大修';
--   DELETE FROM sys_dict_data WHERE dict_label IN ('新建','改建','扩建','大修')
--     AND dict_type_id=(SELECT id FROM sys_dict_type WHERE dict_code='dev_type');
-- ============================================================

-- 1) 新增四项(幂等)
INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, status)
SELECT t.id, x.label, x.label, x.sort, 1
FROM sys_dict_type t
JOIN (
    SELECT '新建' AS label, 5 AS sort
    UNION ALL SELECT '改建', 6
    UNION ALL SELECT '扩建', 7
    UNION ALL SELECT '大修', 8
) x
WHERE t.dict_code = 'dev_type'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_data d
      WHERE d.dict_type_id = t.id AND d.dict_label = x.label
  );

-- 2) 停用连写旧项(幂等)
UPDATE sys_dict_data
SET status = 0, updated_at = NOW()
WHERE dict_label = '新建改建扩建大修'
  AND dict_type_id = (SELECT id FROM sys_dict_type WHERE dict_code = 'dev_type')
  AND status = 1;

-- 复核查询(手工执行, 预期 7 行: 3 旧启用 + 4 新启用, 连写项 status=0):
--   SELECT d.id, d.dict_label, d.sort_order, d.status
--   FROM sys_dict_data d JOIN sys_dict_type t ON d.dict_type_id=t.id
--   WHERE t.dict_code='dev_type' ORDER BY d.sort_order;
