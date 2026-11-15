-- ============================================================
-- 数据字典模块分类（固定枚举）
-- 2026-11-16
--
-- 模块码与菜单/权限模块对齐：
--   customer / file / incentive / service / sea / workflow / score /
--   fabushi / statistics / system / common
-- 本次只回填已有 17 个类型；不删任何数据。
-- ============================================================

ALTER TABLE sys_dict_type
  ADD COLUMN module_code VARCHAR(50) NOT NULL DEFAULT 'common' AFTER dict_code;

CREATE INDEX idx_dict_type_module ON sys_dict_type (module_code);

UPDATE sys_dict_type SET module_code = 'customer'
WHERE dict_code IN (
  'dev_type', 'relationship_depth',
  'core_indicator_02', 'core_indicator_03', 'core_indicator_04',
  'core_indicator_05', 'core_indicator_06', 'industry'
);

UPDATE sys_dict_type SET module_code = 'score'
WHERE dict_code IN ('score_category', 'score_product', 'score_range');

UPDATE sys_dict_type SET module_code = 'fabushi'
WHERE dict_code IN (
  'fabushi_category', 'fabushi_remind_freq', 'fabushi_section_level',
  'fabushi_badge_type', 'fabushi_znl_tag', 'fabushi_edition_type'
);
