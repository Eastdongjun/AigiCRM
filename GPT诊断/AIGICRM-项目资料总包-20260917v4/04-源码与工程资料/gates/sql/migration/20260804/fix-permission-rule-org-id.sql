-- 修复：sys_permission_rule 中 id=1/2 两条规则的 target_column 配置错误
--
-- 背景：这两条规则("仅看本组织及下级客户"/"仅看本组织客户")的 target_entity='customer'，
-- target_column='org_id'，但 biz_customer 表实际没有 org_id 这一列（只有 owner_id/office_id/
-- region_id/group_id/legal_entity_id），一旦被绑定到任何角色/岗位，PermissionRuleEngine 会把
-- "org_id in (...)" / "org_id = ..." 拼进查询 biz_customer 的 SQL，直接报 Unknown column 错误。
--
-- 这类"按组织直接过滤客户表"的规则设计本身就有问题：客户的组织归属是通过 owner_id 关联到
-- 用户、再关联用户所属部门间接得出的，不是客户表自己的字段，无法用简单的 column_filter/
-- org_filter 规则类型实现，需要更深的引擎改造（子查询）才能支持。
--
-- 现阶段用户已确认组织范围统一走"部门(dept)"体系（见 CustomerService/IncentiveService 的
-- applyDataScope 修复），数据范围能力已经通过角色的 dataScope 字段（custom/org/org_and_children/
-- dept_and_children/dept/self/all）+ 组织矩阵(sys_role_org) 实现，不依赖这两条规则。
-- 因此这里先禁用这两条配置错误、且未被任何角色/岗位实际绑定的规则，避免它们将来被误绑定后
-- 直接导致查询报错；如果以后确实需要"按组织过滤客户"的规则能力，需要单独扩展
-- PermissionRuleEngine 支持子查询。
--
-- 执行前建议：
--   1) 确认没有角色/岗位已经绑定这两条规则：
--        SELECT * FROM sys_role_rule WHERE rule_id IN (1,2);
--        SELECT * FROM sys_position_rule WHERE rule_id IN (1,2);
--      如果已有绑定，先与业务确认后再执行本脚本（本脚本只禁用规则本身，不会自动解绑）。
--   2) 先在测试环境执行、验证系统管理 > 权限规则管理页面显示正常后，再在生产环境执行。

UPDATE sys_permission_rule
SET status = 0,
    description = CONCAT(COALESCE(description, ''), '（已于 2026-08-04 禁用：target_column=org_id 在 biz_customer 表不存在，绑定后会导致 SQL 报错，详见修复说明）')
WHERE id IN (1, 2)
  AND target_entity = 'customer'
  AND target_column = 'org_id';
