-- B1 S6.3：行业词表默认种子（可配置）
-- 先确保字典类型存在；再仅补缺失值，已有生产/客户自定义值不覆盖。
INSERT INTO sys_dict_type (dict_name, dict_code, module_code, status, built_in, remark)
SELECT '行业', 'industry', 'customer', 1, 1, 'B1 行业词表'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code='industry');

INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, status)
SELECT t.id, v.label, v.label, v.ord, 1
FROM sys_dict_type t
JOIN (
    SELECT '冶金' AS label, 1 AS ord
    UNION ALL SELECT '煤矿', 2
    UNION ALL SELECT '化工', 3
    UNION ALL SELECT '电力', 4
    UNION ALL SELECT '有色金属', 5
    UNION ALL SELECT '机械', 6
    UNION ALL SELECT '工程公司', 7
    UNION ALL SELECT '维修厂', 8
    UNION ALL SELECT '其他终端客户', 9
    UNION ALL SELECT '新行业', 10
) v
WHERE t.dict_code = 'industry'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_data d
      WHERE d.dict_type_id = t.id AND d.dict_value = v.label
  );
