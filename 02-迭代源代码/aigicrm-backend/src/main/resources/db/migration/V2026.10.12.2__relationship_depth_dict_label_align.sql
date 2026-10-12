-- 【#10 客户小调整 2026-10-12】客情·商务关系深度选项文案切字典
-- 背景: 页面硬编码旧文案(陌生/知道/熟悉/支持), 而后台字典 relationship_depth 早已是
-- 客户口径新文案(商务少/纯商务/感情商务/多样性)。本次前后端切消费字典, 迁移负责:
-- 1) 幂等确保 relationship_depth 四行 = 新文案(测试库与生产库对齐, 缺行则补);
-- 2) core_indicator_02(核心指标02-高层客情) 为同字段的另一套历史字典, label 同步为
--    新文案, 避免后台两套字典显示不一致误导管理员; value(0-3)不动, 分值/统计/排行不受影响。

-- 1) relationship_depth 对齐(存在则改, 不存在则插)
UPDATE sys_dict_data d
JOIN sys_dict_type t ON t.id = d.dict_type_id AND t.dict_code = 'relationship_depth'
SET d.dict_label = CASE d.dict_value
    WHEN '0' THEN '商务少' WHEN '1' THEN '纯商务' WHEN '2' THEN '感情商务' WHEN '3' THEN '多样性'
    ELSE d.dict_label END
WHERE d.dict_label <> CASE d.dict_value
    WHEN '0' THEN '商务少' WHEN '1' THEN '纯商务' WHEN '2' THEN '感情商务' WHEN '3' THEN '多样性'
    ELSE d.dict_label END;

INSERT INTO sys_dict_data (dict_type_id, dict_value, dict_label, sort_order, status)
SELECT t.id, v.val, v.lbl, v.sort, 1
FROM sys_dict_type t
JOIN (SELECT '0' val, '商务少' lbl, 1 sort UNION ALL SELECT '1', '纯商务', 2
      UNION ALL SELECT '2', '感情商务', 3 UNION ALL SELECT '3', '多样性', 4) v
WHERE t.dict_code = 'relationship_depth'
  AND NOT EXISTS (
    SELECT 1 FROM sys_dict_data d WHERE d.dict_type_id = t.id AND d.dict_value = v.val
  );

-- 2) core_indicator_02 同步新文案(仅改 label, value/排序不动)
UPDATE sys_dict_data d
JOIN sys_dict_type t ON t.id = d.dict_type_id AND t.dict_code = 'core_indicator_02'
SET d.dict_label = CASE d.dict_value
    WHEN '0' THEN '商务少' WHEN '1' THEN '纯商务' WHEN '2' THEN '感情商务' WHEN '3' THEN '多样性'
    ELSE d.dict_label END
WHERE d.dict_label <> CASE d.dict_value
    WHEN '0' THEN '商务少' WHEN '1' THEN '纯商务' WHEN '2' THEN '感情商务' WHEN '3' THEN '多样性'
    ELSE d.dict_label END;
