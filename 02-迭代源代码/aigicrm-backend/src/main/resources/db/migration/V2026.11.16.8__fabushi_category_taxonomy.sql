-- 法布施书目类型体系重构(2026-11-16 用户裁定: "弟子规/了凡四训是书名不是类型")
-- 旧 fabushi_category 10 项 = 具体书名占位(弟子规/了凡四训/论语/道德经/孝经/感应篇/十善业/群书治要) + 2 模糊兜底(国外类/实用类),
-- 书目增多分类即爆炸。新 6 项 = 内容门类:
--   jiaxun 家训家风(了凡四训/弟子规/朱子家训系) | ruxue 儒学经典(论语/孝经/四书系)
--   daoxue 道学经典(道德经/庄子系)             | foxue 佛学善书(感应篇/十善业系)
--   wenxuan 治要文选(群书治要系)               | qita 其他经典(兜底)
-- 回滚: 反向恢复旧字典行与书目 code(旧 value 清单见下方映射, 可按本文件逆写)。

-- 1) 存量书目按映射迁到新类型(按旧 value 定位, 禁硬编码行 id)
UPDATE biz_fabushi_book SET category_code = 'jiaxun'  WHERE category_code IN ('dizigui', 'liaofan');
UPDATE biz_fabushi_book SET category_code = 'ruxue'   WHERE category_code IN ('lunyu', 'xiaojing');
UPDATE biz_fabushi_book SET category_code = 'daoxue'  WHERE category_code = 'daodejing';
UPDATE biz_fabushi_book SET category_code = 'foxue'   WHERE category_code IN ('ganyingpian', 'shishanye');
UPDATE biz_fabushi_book SET category_code = 'wenxuan' WHERE category_code = 'qunshuzhiyao';
UPDATE biz_fabushi_book SET category_code = 'qita'    WHERE category_code IN ('foreign', 'practical');

-- 2) 删除旧书名占位字典(整类重建, 按 dict_code 定位)
DELETE dd FROM sys_dict_data dd
JOIN sys_dict_type t ON t.id = dd.dict_type_id
WHERE t.dict_code = 'fabushi_category';

-- 3) 新类型种子(6 门类; 幂等: NOT EXISTS 防重)
INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, status)
SELECT t.id, x.label, x.value, x.sort, 1
FROM sys_dict_type t
JOIN (
  SELECT '家训家风' AS label, 'jiaxun' AS value, 1 AS sort UNION ALL
  SELECT '儒学经典', 'ruxue', 2 UNION ALL
  SELECT '道学经典', 'daoxue', 3 UNION ALL
  SELECT '佛学善书', 'foxue', 4 UNION ALL
  SELECT '治要文选', 'wenxuan', 5 UNION ALL
  SELECT '其他经典', 'qita', 6
) x
WHERE t.dict_code = 'fabushi_category'
  AND NOT EXISTS (
    SELECT 1 FROM sys_dict_data d
    WHERE d.dict_type_id = t.id AND d.dict_value = x.value
  );

-- 4) 类型备注收口(不再"名称待甲方确认")
UPDATE sys_dict_type
SET remark = '法布施书目分类(内容门类: 家训家风/儒学经典/道学经典/佛学善书/治要文选/其他经典)'
WHERE dict_code = 'fabushi_category';
