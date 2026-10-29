-- ============================================================
-- 【2026-10-29 产品目录归树批(用户裁决: 按树规划+唯一入口=系统设置→激励类别管理)】
-- 背景: 五大类产品目录(涂层类/防滑板类/机封类/密封类/润滑类 → 16 小类)此前硬编码在
--       双端 4 份常量里(PC product-catalog.js / MP 上传页 / MP 两列表页), 后台无法维护。
-- 处置: 归入分类树 = 产品试点(trial)分支。树的二级本就是五大类(涂层类 trial_coating /
--       楼梯板类 trial_stair / 机封类 trial_seal / 密封类 trial_gasket / 润滑类 trial_lubricant),
--       本迁移: ①"楼梯板类"正名"防滑板类"(生产 83 条记录 related_products 一直用"防滑板类";
--       记录表只存 category_id、名称查询时 join, 改名零数据连带)
--       ②把 16 个小类 seed 成对应二级节点下的第三级(status=1, score=0, code 空)。
-- 幂等: 改名带旧名条件; seed 用 NOT EXISTS 跳过同名, 重复执行无副作用。父节点按 category_code
--       定位, 不硬编码 id(迁移纪律)。全部节点 tenant_id 沿用父节点值。
-- ============================================================

-- ① 正名(旧名条件保护: 已改过或手工改名则跳过)
UPDATE biz_incentive_category
SET category_name = '防滑板类', updated_at = NOW()
WHERE category_code = 'trial_stair' AND category_name = '楼梯板类';

-- ② 第三级小类 seed(按父节点编码 + 子名定位, 同名已存在则跳过)
INSERT INTO biz_incentive_category (parent_id, category_name, category_code, score, sort_order, status, tenant_id)
SELECT p.id, v.child_name, NULL, 0, v.sort_order, 1, p.tenant_id
FROM biz_incentive_category p
JOIN (
  SELECT 'trial_coating' AS code, '防腐'      AS child_name, 1 AS sort_order UNION ALL
  SELECT 'trial_coating', '加固', 2 UNION ALL
  SELECT 'trial_coating', '钢结构', 3 UNION ALL
  SELECT 'trial_coating', '煤气管道', 4 UNION ALL
  SELECT 'trial_coating', '其他', 5 UNION ALL
  SELECT 'trial_stair',  '公共区', 1 UNION ALL
  SELECT 'trial_stair',  '装置区', 2 UNION ALL
  SELECT 'trial_seal',   '水泵', 1 UNION ALL
  SELECT 'trial_seal',   '渣泵', 2 UNION ALL
  SELECT 'trial_seal',   '介质泵', 3 UNION ALL
  SELECT 'trial_gasket', '静密封', 1 UNION ALL
  SELECT 'trial_gasket', '动密封', 2 UNION ALL
  SELECT 'trial_gasket', '橡胶密封', 3 UNION ALL
  SELECT 'trial_lubricant', '润滑油', 1 UNION ALL
  SELECT 'trial_lubricant', '润滑脂', 2 UNION ALL
  SELECT 'trial_lubricant', '润滑剂', 3
) v ON v.code = p.category_code
WHERE NOT EXISTS (
  SELECT 1 FROM biz_incentive_category e
  WHERE e.parent_id = p.id AND e.category_name = v.child_name
);
