-- ============================================================
-- 【2026-10-29 字典对账门禁批】停用重复孤儿字典 core_indicator_02
-- 背景: 系统字典引用全景审计发现 core_indicator_02(核心指标02-高层客情, 建于 2026-06-12)
--       与 relationship_depth(客情关系深度) 内容完全重复(商务少/纯商务/感情商务/多样性),
--       且双端界面(PC utils/customer-fields.js / MP pages/customer/shared.js)自始只消费
--       relationship_depth, core_indicator_02 全库零引用 —— 属"建了没人用"的重复字典。
-- 处置: 停用(status=0, 不物理删除, 可逆); 同批 core_indicator_03..06 已接线启用(指标选项字典驱动)。
-- 幂等: 按 dict_code 定位, 重复执行无副作用。
-- ============================================================

UPDATE sys_dict_type
SET status = 0,
    remark = CONCAT(IFNULL(remark, ''), ' | 2026-10-29 停用: 与 relationship_depth 内容重复, 界面只消费后者')
WHERE dict_code = 'core_indicator_02';

UPDATE sys_dict_data
SET status = 0
WHERE dict_type_id IN (SELECT id FROM sys_dict_type WHERE dict_code = 'core_indicator_02');
