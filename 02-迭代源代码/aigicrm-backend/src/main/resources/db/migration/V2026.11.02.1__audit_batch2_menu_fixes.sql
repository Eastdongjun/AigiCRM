-- ============================================================
-- 审计批2 数据修正(2026-11-01):
-- 1) 学分改名漏网: seed 菜单"积分查看"(score:view)不在 V2026.11.01.1 三个 UPDATE 覆盖内
-- 2) 死权限码退役: system:dept:add / system:dept:delete 两枚按钮码前后端均无消费
--    (dept.vue 全部写操作只判 system:dept:edit), 保留会误导管理员以为可细分授权
-- 幂等: 均锚定旧值。
-- ============================================================

UPDATE `sys_menu` SET `menu_name` = '学分查看' WHERE `menu_name` = '积分查看' AND `permission` = 'score:view';

DELETE FROM `sys_menu` WHERE `permission` IN ('system:dept:add', 'system:dept:delete') AND `menu_type` = 3;
DELETE FROM `sys_role_menu` WHERE `menu_id` NOT IN (SELECT `id` FROM `sys_menu`);
