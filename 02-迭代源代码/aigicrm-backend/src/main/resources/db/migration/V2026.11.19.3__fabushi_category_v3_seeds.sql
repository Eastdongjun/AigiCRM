-- 法布施书目类别 v3 + spec 种子(2026-11-19 spec v2; 拍板①④)
-- 拍板①: 10 大类照搬原文, "团队必读书目"也是类别(一书一类, 跨类以重复建条目解决);
--         原 must_read 标志位建议作废——不加列。
-- code 登记: tuanduibidu=团队必读书目(全拼, 承 16.8 拼音风格; 如改短码需同步本文件与字典行)。
-- 存量重映射(拍板): 旧五门类(jiaxun/ruxue/daoxue/foxue/wenxuan)全部存量→国学建议书目,
--         其中《了凡四训》按 title 特判→团队必读; qita 行随字典重建但 value 复用(存量不断链)。
-- 种子(拍板④): 按 spec 原文清单逐条——title 原文原样; author 仅录原文显式给出者;
--         版本/页数原文未给=留空待界面补全; 省略号与"等等"不扩; 健康类空《》不作条目;
--         其他类空类只建字典行; 「中国四大名著」按原文作集合单条(书表无 remark 列,
--         拆分待后续界面维护, 本注释即为备注)。
-- 权威源: out/法布施-spec-v2-原文存档-20261119-01claude.md(正文+附录 A, 有出入以正文为准)。
-- 回滚(逆写): 字典按 V2026.11.16.8 六门类种子重建; 书目 tuanduibidu/guoxue 存量行按 16.8
--         规则人工归位(了凡四训/弟子规→jiaxun, 论语/孝经→ruxue, 道德经→daoxue,
--         感应篇/十善业→foxue, 群书治要→wenxuan); 本批种子行可按 created_by=1 且
--         approved_at>=本迁移执行时刻定位删除。
-- 对账(测试/生产窗口执行, 三查):
--   ① SELECT category_code, COUNT(*) FROM biz_fabushi_book WHERE deleted=0 GROUP BY category_code;
--     -- 旧五码(jiaxun/ruxue/daoxue/foxue/wenxuan)应 0 行
--   ② SELECT COUNT(*) FROM sys_dict_data dd JOIN sys_dict_type t ON t.id=dd.dict_type_id
--     WHERE t.dict_code='fabushi_category';  -- 应=10
--   ③ SELECT b.title FROM biz_fabushi_book b WHERE b.deleted=0 AND b.category_code NOT IN
--     (SELECT d.dict_value FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id
--      WHERE t.dict_code='fabushi_category');  -- 孤儿 code 应 0 行
-- 幂等: UPDATE 天然幂等; 字典 DELETE+INSERT(NOT EXISTS)与种子 NOT EXISTS(title+category)重跑安全。

-- 1) 存量书目重映射(先按旧五门类归国学, 再《了凡四训》特判归团队必读)
UPDATE biz_fabushi_book SET category_code = 'guoxue'
WHERE category_code IN ('jiaxun', 'ruxue', 'daoxue', 'foxue', 'wenxuan');
UPDATE biz_fabushi_book SET category_code = 'tuanduibidu'
WHERE title = '了凡四训' AND category_code = 'guoxue';

-- 2) 类别字典整类重建(16.8 同模式; 顺序=spec 原文 10 大类顺序, qita 复用 value)
DELETE dd FROM sys_dict_data dd
JOIN sys_dict_type t ON t.id = dd.dict_type_id
WHERE t.dict_code = 'fabushi_category';

INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, sort_order, status)
SELECT t.id, x.label, x.value, x.sort, 1
FROM sys_dict_type t
JOIN (
  SELECT '团队必读书目' AS label, 'tuanduibidu' AS value, 1 AS sort UNION ALL
  SELECT '国学建议书目', 'guoxue', 2 UNION ALL
  SELECT '小说类', 'xiaoshuo', 3 UNION ALL
  SELECT '管理类', 'guanli', 4 UNION ALL
  SELECT '实用类', 'shiyong', 5 UNION ALL
  SELECT '诗歌类', 'shige', 6 UNION ALL
  SELECT '传记类', 'zhuanji', 7 UNION ALL
  SELECT '国外类', 'guowai', 8 UNION ALL
  SELECT '健康类', 'jiankang', 9 UNION ALL
  SELECT '其他类', 'qita', 10
) x
WHERE t.dict_code = 'fabushi_category'
  AND NOT EXISTS (
    SELECT 1 FROM sys_dict_data d
    WHERE d.dict_type_id = t.id AND d.dict_value = x.value
  );

-- 3) 种子书目 39 条(spec 原文; author 仅录原文显式者; status=approved 直接入库;
--    created_by/approved_by=1(admin 系统种子); NOT EXISTS 按 title+category 防重——
--    与存量同名经典(大学/论语/中庸/道德经/庄子等, 步骤 1 已改码)不重复插入)
INSERT INTO biz_fabushi_book (category_code, title, author, status, created_by, approved_by, approved_at)
SELECT x.cat, x.title, x.author, 'approved', 1, 1, NOW()
FROM (
  SELECT 'tuanduibidu' AS cat, '了凡四训' AS title, NULL AS author UNION ALL
  SELECT 'tuanduibidu', '活法', NULL UNION ALL
  SELECT 'tuanduibidu', '认知觉醒', NULL UNION ALL
  SELECT 'tuanduibidu', '长安的荔枝', NULL UNION ALL
  SELECT 'guoxue', '大学', NULL UNION ALL
  SELECT 'guoxue', '易经', NULL UNION ALL
  SELECT 'guoxue', '心经', NULL UNION ALL
  SELECT 'guoxue', '论语', NULL UNION ALL
  SELECT 'guoxue', '中庸', NULL UNION ALL
  SELECT 'guoxue', '道德经', NULL UNION ALL
  SELECT 'guoxue', '庄子', NULL UNION ALL
  SELECT 'guoxue', '金刚经', NULL UNION ALL
  SELECT 'guoxue', '六祖坛经', NULL UNION ALL
  SELECT 'xiaoshuo', '遥远的救世主', NULL UNION ALL
  SELECT 'xiaoshuo', '平凡的世界', NULL UNION ALL
  SELECT 'xiaoshuo', '明朝那些事儿', NULL UNION ALL
  SELECT 'xiaoshuo', '曾国藩传', '唐浩明' UNION ALL
  SELECT 'xiaoshuo', '中国四大名著', NULL UNION ALL
  SELECT 'guanli', '目标', '艾利高德拉特' UNION ALL
  SELECT 'guanli', '创新与企业家精神', '彼得德鲁克' UNION ALL
  SELECT 'guanli', '竞争三部曲', '迈克尔.彼特' UNION ALL
  SELECT 'guanli', '营销管理', '菲利普.科特勒' UNION ALL
  SELECT 'guanli', '第五项修炼：学习型组织的艺术与实践', '彼得.圣吉' UNION ALL
  SELECT 'shiyong', '毛选', NULL UNION ALL
  SELECT 'shiyong', '鲁迅', NULL UNION ALL
  SELECT 'shiyong', '博弈论', NULL UNION ALL
  SELECT 'shiyong', '逻辑学', NULL UNION ALL
  SELECT 'shiyong', '心理学', NULL UNION ALL
  SELECT 'shige', '诗经', NULL UNION ALL
  SELECT 'shige', '唐诗', NULL UNION ALL
  SELECT 'shige', '宋词', NULL UNION ALL
  SELECT 'zhuanji', '苏东坡传', NULL UNION ALL
  SELECT 'zhuanji', '史蒂夫.乔布斯', NULL UNION ALL
  SELECT 'guowai', '苏菲的世界', NULL UNION ALL
  SELECT 'guowai', '申辩篇', NULL UNION ALL
  SELECT 'guowai', '理想国', NULL UNION ALL
  SELECT 'guowai', '百年孤独', NULL UNION ALL
  SELECT 'guowai', '悲惨世界', NULL UNION ALL
  SELECT 'jiankang', '黄帝内经', NULL
) x
WHERE NOT EXISTS (
  SELECT 1 FROM biz_fabushi_book b
  WHERE b.title = x.title AND b.category_code = x.cat AND b.deleted = 0
);

-- 4) 类型备注收口
UPDATE sys_dict_type
SET remark = '法布施书目分类(spec v2 2026-11-19 十大类: 团队必读/国学建议/小说/管理/实用/诗歌/传记/国外/健康/其他)'
WHERE dict_code = 'fabushi_category';
