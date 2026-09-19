-- ============================================================
-- V2026.09.11.5__fix_customer_date_edit_role_binding.sql
-- 修正 customer:date:edit 的角色绑定：V29 用了不存在的 role_code
--
-- ============================================================
-- 问题(2026-09-11 复核, 生产只读 dump 实证):
--
--   V29__customer_protected_date_permission.sql:13 的绑定语句写的是:
--     WHERE r.role_code IN ('admin','back_office','sales_director',
--                           'director','region_manager','admin_staff')
--
--   而生产 sys_role 的真实 role_code 只有 4 个:
--     id=1  admin  系统管理员
--     id=9  05     行政
--     id=10 0      销售总监
--     id=11 06     销售员
--
--   ⇒ 这 6 个名字里**只有 'admin' 存在**, 其余 5 个在整条 Flyway 链里
--     仅出现在 V29 这一处(另见归档脚本 sql/migration/archive/
--     V2__permission_schema.sql、sql/migration/20260802/base_data_utf8.sql
--     与 scripts/seed-local-permission-fixture.sql —— 均为链外材料)。
--   ⇒ 该 INSERT ... SELECT 在生产只绑定 admin, 另外三个角色**静默漏绑**
--     (SELECT 0 行不报错, 也无告警)。
--
--   注: V29 已冻结(见 db/migration/FROZEN-SHA256.txt), 不得原地修改,
--       故以本迁移补偿。
--
-- ============================================================
-- 本迁移做什么(幂等):
--   把 customer:date:edit 绑定到 V29 业务规则所指向的三个角色:
--     0  (销售总监) —— 管理侧
--     06 (销售员)   —— V29 头注释"销售新增客户时可录入"的当事方
--     admin         —— V29 原本唯一命中的角色(保留)
--
--   **不绑定** 05(行政) —— V29 的 6 个名字里没有对应行政的角色,
--   该角色是否应有此权限属业务判断, 不擅自扩大授权面。
--
--   绑定基于**角色编码**而非硬编码 id, 与 V17 的既有惯例一致
--   ("菜单行按 permission 字符串锚定, 不写死 id, 防各环境 id 漂移")。
--
-- ============================================================
-- 安全说明:
--   本迁移**扩大授权**(给 0/06 两个角色新增一个权限点)。
--   但这是 V29 头注释已声明的设计意图:
--     "注册日/最近开票日录入后修改权限。
--      业务规则: 销售新增客户时可录入; 后续只有具备 customer:date:edit
--      的角色可修改。"
--   且管理员可随时在角色管理页取消勾选(sys_menu 该行为 status=1,
--   会出现在 SysMenuService.tree() 的配置树中)。
--
-- 幂等: NOT EXISTS(role_id, menu_id) 守卫 + sys_role_menu 的
--       UNIQUE KEY uk_role_menu(role_id, menu_id) 双保险。
-- 安全降级: 若某库无 customer:date:edit 行(未执行 V29), JOIN 落空不报错。
-- 回滚: 见文件尾部。
-- ============================================================

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
    JOIN `sys_menu` m ON m.`permission` = 'customer:date:edit'
WHERE r.`role_code` IN ('admin', '0', '06')
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x
      WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`
  );

-- ------------------------------------------------------------
-- 复核查询(手工执行):
--   SELECT r.id, r.role_code, r.role_name
--   FROM sys_role r
--     JOIN sys_role_menu rm ON rm.role_id = r.id
--     JOIN sys_menu m ON m.id = rm.menu_id
--   WHERE m.permission = 'customer:date:edit'
--   ORDER BY r.id;
--   预期: admin(1) / 0(销售总监) / 06(销售员) 三行
-- ------------------------------------------------------------

-- ==================== 回滚(按需手工执行) ====================
-- 执行前先备份:
--   mysqldump --single-transaction <DB> sys_role_menu > rm_backup_$(date +%F).sql
--
-- 回滚 SQL(仅解除本迁移新增的两个角色绑定, 保留 admin):
--   DELETE rm FROM `sys_role_menu` rm
--     JOIN `sys_role` r ON r.`id` = rm.`role_id`
--     JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
--     WHERE m.`permission` = 'customer:date:edit'
--       AND r.`role_code` IN ('0', '06');
-- ============================================================
