-- 20260816 测试库菜单入口修复（幂等，与生产同模式）
UPDATE `sys_menu` SET `path`='/customer-group' WHERE `id`=11;
UPDATE `sys_menu` SET `path`='/sea-group' WHERE `id`=17;
UPDATE `sys_menu` SET `path`='/system/org-group' WHERE `id`=32;
UPDATE `sys_menu` SET `path`='/system/config-group' WHERE `id`=34;
UPDATE `sys_menu` SET `path`='/incentive-group' WHERE `id`=15;
UPDATE `sys_menu` SET `status`=1 WHERE `id` IN (115,118,119);
INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`menu_type`,`path`,`component`,`permission`,`icon`,`sort_order`,`visible`,`status`,`created_at`)
SELECT 116, 17, '公海池', 2, '/sea', 'sea/index', 'sea:view', 'Ship', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id`=116);
UPDATE `sys_menu` SET `menu_type`=2, `path`='/incentive' WHERE `id`=114;
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
 (1,115),(1,116),(1,118),(1,119),(9,115),(9,116),
 (10,115),(10,116),(10,118),(10,119),(11,115),(11,116);
