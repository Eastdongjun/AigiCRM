-- ============================================================
-- YCMN 权限审计修复批 1(2026-08-10)——数据侧修复, 执行前先备份!
--   备份: mysqldump -uroot -p --single-transaction ycmn > backup-ycmn-pre-permfix-<时间>.sql
-- ============================================================

-- 1. group_id 死区回填(P2-10): sys_dept / biz_customer 的 group_id/legal_entity_id 全 NULL,
--    集团边界功能形同虚设(0809 group_name 死区同型)。单集团部署回填默认集团 1。
UPDATE sys_dept SET group_id = 1 WHERE group_id IS NULL;
UPDATE biz_customer SET group_id = 1 WHERE group_id IS NULL;
-- legal_entity_id: 需确认主法人 ID(当前 sys_legal_entity 表数据), 单法人时回填, 多法人/不确定时留空不填
-- UPDATE sys_dept SET legal_entity_id = <主法人ID> WHERE legal_entity_id IS NULL;
-- UPDATE biz_customer SET legal_entity_id = <主法人ID> WHERE legal_entity_id IS NULL;

-- 2. 【待用户确认】P2-18 权限码配置漂移收紧 —— 销售员(06)用户/集团管理码解除
--    说明: 解除后销售员不再能管理用户/集团(与业务角色定位一致)
--    ⚠️ 执行前确认这是预期收紧; 如需保留请在角色管理页自行调整后跳过本段
-- DELETE FROM sys_role_menu WHERE role_id = 11 AND menu_id IN (
--     SELECT id FROM sys_menu WHERE permission IN (
--         'system:user:add','system:user:edit','system:user:delete',
--         'system:group:add','system:group:edit','system:group:delete','system:group:view'
--     )
-- );
-- DELETE FROM sys_role_menu WHERE role_id = 10 AND menu_id IN (
--     SELECT id FROM sys_menu WHERE permission IN ('system:user:delete')
-- );

-- 3. 验证(执行后运行)
-- SELECT r.role_code, COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id GROUP BY r.role_code;
-- SELECT COUNT(*) FROM sys_dept WHERE group_id IS NULL;   -- 期望 0
-- SELECT COUNT(*) FROM biz_customer WHERE group_id IS NULL; -- 期望 0
