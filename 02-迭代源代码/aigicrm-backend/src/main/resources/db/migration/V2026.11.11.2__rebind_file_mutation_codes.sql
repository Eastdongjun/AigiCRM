-- 【T1 2026-11-11 审计整改】空库重建兜底: 5 个文件写权限码的角色重绑 + 状态激活
--
-- 背景(2026-11-11 只读审计 P0 推断, 证据链已复核; N1 契约自证同日修正事实口径):
--   V2026.09.11.3__restore_file_mutation_permission_bindings.sql(链第14位) 用"已持有 file:view
--   的角色"回绑 5 码, 但它排在唯一种子 V2026.09.11.6(链第17位)之前——空库重放执行到它时
--   sys_menu 为空(V1 纯 DDL 0 INSERT), JOIN 恒 0 行; 种子虽补建 5 码菜单行但无绑定, 且
--   file:edit 行 status=0。此后仅 V2026.11.05.10 单独恢复了 file:edit(空库上由种子
--   file:view 绑定传递生效)。结果: 空库重建/新环境初始化后, upload/delete/share/folder:manage
--   四码零绑定, 文件上传/删除/分享/建夹对全员(含 admin, admin 无隐式旁路——SysMenuMapper
--   纯 role_menu JOIN)403; file:edit 亦处 status=0 濒停用态。当前生产/测试库不受影响
--   (其绑定由历史在线变更形成)。
--
-- 语义(两种库皆幂等):
--   空库重放: 种子已建 5 码行 → 本迁移置 status=1 并给持 file:view 的角色补绑;
--   存量库:   绑定已存在 → NOT EXISTS 守卫使 INSERT 0 行, UPDATE 仅修 status(已 1 则 0 变更)。
-- 锚定纪律(INV-MIG-001): 全部按 permission 自然键定位, 零硬编码行 id(含 INSERT 列表——
-- id 交给 AUTO_INCREMENT; 2026-11-11 N2 复核修正: 首版 "SELECT 9047+n.i" 显式 id 既与本文件
-- 头部承诺自相矛盾, 又会在"某行缺失但 9047..9051 段已被占用"的库上主键冲突)。
-- 父级锚 permission='file:view' 行(种子该行是 menu_type=2「文件管理」; 首版误加 menu_type=3
-- 过滤 → 恒 miss → 恒落常量 9014, 空库恰好撞对、存量库则可能悬空)。锚定写法与
-- V2026.09.11.3:77 同款(生产验证过的先例), 无 file:view 时退化为 parent_id=0(顶层),
-- 此分支仅手工残库可触达, 正常链路上 .09.11.3/种子已先行建行, 本步 0 行生效。

-- ① 五码若缺行则补建(纵深防御: 正常空库重放时 .09.11.3 已建行, 此步只救手工残库)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`, `icon`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT `id` FROM `sys_menu` WHERE `permission` = 'file:view' ORDER BY `id` LIMIT 1), 0),
       CASE n.i WHEN 0 THEN '文件上传' WHEN 1 THEN '文件编辑' WHEN 2 THEN '文件删除'
                WHEN 3 THEN '文件分享' ELSE '文件夹管理' END,
       3, '', '',
       CASE n.i WHEN 0 THEN 'file:upload' WHEN 1 THEN 'file:edit' WHEN 2 THEN 'file:delete'
                WHEN 3 THEN 'file:share' ELSE 'file:folder:manage' END,
       '', 90 + n.i, 1, 1, NOW()
FROM (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) n
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` x
    WHERE x.`permission` = CASE n.i WHEN 0 THEN 'file:upload' WHEN 1 THEN 'file:edit' WHEN 2 THEN 'file:delete'
                                    WHEN 3 THEN 'file:share' ELSE 'file:folder:manage' END
      AND x.`menu_type` = 3);

-- ② 激活状态(种子把 file:edit 行置 0; 其余已 1 则无变更)
UPDATE `sys_menu` SET `status` = 1
WHERE `permission` IN ('file:upload','file:edit','file:delete','file:share','file:folder:manage')
  AND `menu_type` = 3 AND `status` <> 1;

-- ③ 给"当前持有 file:view 的角色"补绑 5 码(与 .09.11.3 原始意图一致; NOT EXISTS 幂等)
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, m.`id`
FROM `sys_role_menu` rm
JOIN `sys_menu` vm ON vm.`id` = rm.`menu_id` AND vm.`permission` = 'file:view'
JOIN `sys_menu` m ON m.`permission` IN ('file:upload','file:edit','file:delete','file:share','file:folder:manage')
                 AND m.`menu_type` = 3
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id`);

-- ④ 财务双码空库兜底(绑定覆盖率契约新发现的审计漏点):
--   V2026.09.23.4(finance:manage)/V2026.11.03.3(customer:finance:edit) 注册迁移均只有菜单行
--   无角色绑定——空库重放后财务写链路同样全员 403。财务敏感, 兜底只给 admin(上线后其他
--   角色由权限中心人工勾选), 与 incentive:read-ranking:view 的 admin 兜底模式一致。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
JOIN `sys_menu` m ON m.`permission` IN ('finance:manage','customer:finance:edit') AND m.`menu_type` = 3
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`);
