-- ============================================================
-- 学分改名批(2026-11-01 用户令: "积分管理，改成学分管理")
-- 范围 = score 模块(菜单三枚 + 字典名称三枚); dict_code/权限码/路由/接口一律不动,
-- 业务引用零影响。四项服务(激励)模块的"分值"不在本批射程。
-- 幂等: 全部 WHERE 锚定旧名, 重跑零效果。
-- ============================================================

-- 菜单: 锚定 menu_name+permission, 兼容两套历史行(id 60/61/83 与 2026.09.11.6 seed 的 9060/9061)
UPDATE `sys_menu` SET `menu_name` = '学分管理' WHERE `menu_name` = '积分管理' AND `permission` = 'score:manage';
UPDATE `sys_menu` SET `menu_name` = '学分录入' WHERE `menu_name` = '积分录入' AND `permission` = 'score:entry';
UPDATE `sys_menu` SET `menu_name` = '学分排行' WHERE `menu_name` = '积分排行' AND `permission` IN ('score:view', 'score:ranking');

-- 字典名称(仅展示名 dict_name; dict_code 不动)
UPDATE `sys_dict_type` SET `dict_name` = '学分大类' WHERE `dict_code` = 'score_category' AND `dict_name` = '积分大类';
UPDATE `sys_dict_type` SET `dict_name` = '学分产品' WHERE `dict_code` = 'score_product' AND `dict_name` = '积分产品';
UPDATE `sys_dict_type` SET `dict_name` = '学分区间' WHERE `dict_code` = 'score_range' AND `dict_name` = '积分区间';
