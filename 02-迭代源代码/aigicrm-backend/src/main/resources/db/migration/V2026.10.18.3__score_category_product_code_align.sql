-- ============================================================
-- V2026.10.18.3__score_category_product_code_align.sql
-- 【审计 data-5 修复 2026-10-18】积分大类/产品字典码拼写对齐。
--
-- 背景:
--   score_category 存在错拼值 `gongyiiucheng`(多一个 i), 而 score_product 与之联动的
--   产品行使用正确值 `gongyiliucheng`。前端 entry.vue 按 dictValue 做「大类 → 产品」联动
--   (score_product.dict_value = score_category.dict_value), 错拼会导致该大类下产品过滤
--   为空、导出大类名回退显示原始码; 历史积分项 biz_score_item.category 也存了错拼码,
--   字典修正后其展示名将查不到。
--
-- 变更(全部幂等, 可重复执行):
--   1. sys_dict_data: score_category / score_product 的 dict_value
--      `gongyiiucheng` → `gongyiliucheng`(score_product 通常已是正确值, 本语句对其为无操作);
--   2. biz_score_item.category: 历史积分项引用的错拼码同步为正确码
--      (仅改代码值, 积分项/得分/周期数据不动)。
--
-- 定位口径: 全程按 sys_dict_type.dict_code + sys_dict_data.dict_value 锚定,
--          零硬编码行 id(INV-MIG-001); 不新增/删除任何字典行。
--
-- 生效日期: 2026-10-18(与 finance.md / customer-lifecycle.md 的延期规则收敛同日)。
--
-- 回滚(仅备注, 不可机械执行): 本迁移不保留原错拼行 id, 且转换后无法区分"原错拼行"与
--   "原有正确值行"; 若整体改回会把正确行一并写错。确需回滚时按业务确认的字典行人工处理。
-- ============================================================

-- 1. 字典值对齐(score_category 错拼值修正; score_product 幂等归一)
UPDATE sys_dict_data d
JOIN sys_dict_type t ON t.id = d.dict_type_id
SET d.dict_value = 'gongyiliucheng'
WHERE t.dict_code IN ('score_category', 'score_product')
  AND d.dict_value = 'gongyiiucheng';

-- 2. 历史积分项大类码同步(否则字典修好后, 旧积分项大类名回退为原始码)
UPDATE biz_score_item
SET category = 'gongyiliucheng'
WHERE category = 'gongyiiucheng';

-- 验证(迁移后应为 0 行; 仅注释, Flyway 不执行):
--   SELECT d.id, t.dict_code, d.dict_value
--     FROM sys_dict_data d JOIN sys_dict_type t ON t.id = d.dict_type_id
--    WHERE t.dict_code IN ('score_category', 'score_product')
--      AND d.dict_value = 'gongyiiucheng';
--   SELECT COUNT(*) FROM biz_score_item WHERE category = 'gongyiiucheng';
