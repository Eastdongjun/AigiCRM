-- ============================================================
-- V2026.09.11.3__restore_file_mutation_permission_bindings.sql
-- 恢复 5 个文件写操作权限码的启用状态与角色绑定(V26 的空转修复)
--
-- ============================================================
-- 背景(实测证据, 2026-09-11 复核):
--
--   ① V22__cleanup_dead_menu_permissions_by_code.sql:25-52 把这 5 个码
--      UPDATE sys_menu SET status=0, 并在 :54-81 删除其全部 sys_role_menu 绑定。
--      V22:11 给出的决策前提是"均前后端零引用或已废弃"——**该前提不成立**:
--        · 后端 FileController 5 个写端点真实校验这些码(见下"受影响端点");
--        · 小程序 4 处真实把关(pages/file/index.js:60,61;
--          pages/files/index.js:38; pages/files/detail/index.js:14;
--          pages/files/folder-form/index.js:10)。
--      ⇒ V22 是**误伤**: 它把活着的功能关掉了。
--
--   ② V26__split_file_mutation_permissions.sql 的意图正是恢复这 5 个码
--      ("split file mutations from file:view while preserving every existing
--        file:view role"), 但其 INSERT 全部带
--      AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission='file:upload')
--      ——而行已存在(status=0) ⇒ **5 条 INSERT 全部被跳过**;
--      随后 :38-46 的绑定继承 JOIN 'file:view' × 待恢复码 也落空
--      ⇒ **V26 净效果恒为 0 行, 在任何库上都不生效**。
--
--   ③ 生产真值(只读 dump sha256 001d926d…6c1cd3, 2026-09-05 快照):
--        id=47  file:upload         status=0  绑定 0
--        id=48  file:delete         status=0  绑定 0
--        id=81  file:edit           status=0  绑定 0
--        id=111 file:share          status=0  绑定 0
--        id=120 file:folder:manage  status=0  绑定 0
--        id=14  file:view           status=1  绑定 4(admin/05/0/06)
--      全库 66 码中 status=1 的仅 54 个。
--      ⇒ 生产上这 5 个写端点对**所有人(含 admin)403**。
--
--   ④ 本机全新库重放(Flyway 9.22.3 同生产镜像 tag)独立复现同一结论:
--      sys_menu 仅 1 行, 这 5 个码一行都不存在。
--
-- ============================================================
-- 本迁移做什么(三件事, 全部幂等):
--   1) 把 5 个码 status 置 1(不存在时先补建菜单行, 父级锚定 file:view);
--   2) 给"当前持有 file:view 的角色"重建这 5 个码的绑定 ——
--      与 V26 的原始意图一致(保留 file:view 的每一个既有角色);
--   3) 不删除任何行、不改任何已部署迁移的字节、不触碰任何数据行。
--
-- ============================================================
-- 受影响端点(后端 FileController, 供复核):
--   :58  POST /upload              file:upload
--   :67  POST /folders             file:folder:manage
--   :74  PUT  /folders/{id}        file:folder:manage
--   :81  DELETE /folders/{id}      file:folder:manage
--   :88  DELETE /{id}              file:delete
--   :96  POST /{id}/replace        file:edit
--   :209 POST /{id}/share          file:share
--
-- ============================================================
-- 幂等性说明:
--   · step 1 的 INSERT 带 NOT EXISTS(permission=…) 守卫;
--   · step 1 的 UPDATE 带 status=0 守卫(已启用不重更新);
--   · step 2 的 INSERT 带 NOT EXISTS(role_id, menu_id) 守卫,
--     与 sys_role_menu 的 UNIQUE KEY uk_role_menu(role_id, menu_id) 双保险。
--   · 重复执行: 退出码 0, 行数不变。
--
-- 安全降级:
--   · 某库若无 file:view 行(GAP-2 全新库即如此), step 1 的 parent_id 落
--     默认 11, step 2 无 file:view 角色可比 ⇒ 绑定为空(不报错)。
--     此时应由种子迁移先行补齐字典, 再重放本迁移。
--   · 生产/测试库 file:view(14) 存在且绑 4 角色 ⇒ 本迁移按预期生效。
--
-- 回滚: 见文件尾部(把 5 个码改回 status=0 并删除本次新增的绑定)。
-- ============================================================

-- ------------------------------------------------------------
-- 1) 确保 5 个码的菜单行存在(缺则补建), 并置 status=1
--    父级锚定 file:view 所在层级; 无 file:view 时退化为常量 11(与 V29 同口径)
-- ------------------------------------------------------------
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `permission`, `sort_order`, `visible`, `status`, `created_at`)
SELECT COALESCE((SELECT id FROM `sys_menu` WHERE `permission` = 'file:view' ORDER BY id LIMIT 1), 11),
       t.menu_name, 3, t.permission, t.sort_order, 1, 1, NOW()
FROM (
    SELECT '文件上传'     AS menu_name, 'file:upload'        AS permission, 91 AS sort_order
    UNION ALL SELECT '文件编辑',        'file:edit',          92
    UNION ALL SELECT '文件删除',        'file:delete',        93
    UNION ALL SELECT '文件分享',        'file:share',         94
    UNION ALL SELECT '文件夹管理',      'file:folder:manage', 95
) t
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`permission` = t.`permission`);

-- 已存在但被停用的行: 置回启用(不改 name/sort_order, 保持既有行的原貌)
UPDATE `sys_menu` m
    JOIN (
        SELECT 'file:upload' AS permission
        UNION ALL SELECT 'file:edit'
        UNION ALL SELECT 'file:delete'
        UNION ALL SELECT 'file:share'
        UNION ALL SELECT 'file:folder:manage'
    ) t ON t.permission = m.`permission`
SET m.`status` = 1
WHERE m.`status` = 0;

-- ------------------------------------------------------------
-- 2) 为"当前持有 file:view 的角色"重建 5 个码的绑定
--    (= V26:38-46 的原意图, 此处真正执行)
-- ------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, m.id
FROM `sys_role_menu` rm
    JOIN `sys_menu` view_menu ON view_menu.`id` = rm.`menu_id`
                             AND view_menu.`permission` = 'file:view'
    JOIN `sys_menu` m ON m.`permission` IN ('file:upload', 'file:edit', 'file:delete',
                                            'file:share', 'file:folder:manage')
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id`
);

-- ------------------------------------------------------------
-- 3) 自检: 本迁移的净效果必须非零(否则说明前置条件不满足)
--    仅打印, 不产生副作用 —— 由契约脚本断言, 迁移本身不因它失败
--    复核查询(手工执行):
--      SELECT m.permission, m.status, COUNT(rm.role_id)
--      FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id = m.id
--      WHERE m.permission IN ('file:upload','file:edit','file:delete',
--                             'file:share','file:folder:manage')
--      GROUP BY m.permission, m.status;
--    预期(生产/测试): 5 行全部 status=1 且绑定数 ≥ 1
-- ------------------------------------------------------------

-- ==================== 回滚(按需手工执行) ====================
-- 执行前先备份:
--   mysqldump --single-transaction <DB> sys_menu sys_role_menu > menu_backup_$(date +%F).sql
--
-- 回滚 SQL(恢复到 V22 之后的状态 —— 即本迁移执行前的生产状态):
--   DELETE rm FROM `sys_role_menu` rm
--     JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
--     WHERE m.`permission` IN ('file:upload','file:edit','file:delete',
--                              'file:share','file:folder:manage');
--   UPDATE `sys_menu` SET `status` = 0
--     WHERE `permission` IN ('file:upload','file:edit','file:delete',
--                            'file:share','file:folder:manage');
--   -- 若 step 1 曾补建菜单行, 一并删除(生产不存在此类行, 通常无需执行):
--   DELETE FROM `sys_menu`
--     WHERE `permission` IN ('file:upload','file:edit','file:delete',
--                            'file:share','file:folder:manage')
--       AND `id` NOT IN (47, 48, 81, 111, 120);
-- ============================================================
