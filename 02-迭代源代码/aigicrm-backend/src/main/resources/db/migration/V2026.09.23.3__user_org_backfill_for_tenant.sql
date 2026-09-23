-- ============================================================
-- V2026.09.23.3__user_org_backfill_for_tenant.sql
-- 回填用户默认组织(org_id), 修复非admin用户租户表数据不可见
-- ============================================================
-- 背景(2026-09-23 生产实测, 用户 NM005 报激励查询无数据):
--   集团归属推导链(UserDetailsServiceImpl): 岗位org → 岗位组织dept.group_id;
--   无岗位时 user.org_id → dept.group_id。生产 67 个正式用户 org_id 全 NULL
--   且无人配岗位(测试库 68/69 同) ⇒ groupIds 恒空。
--
--   MybatisPlusConfig 租户插件: admin 角色豁免(ignoreTable); 其余用户
--   groupIds 空 → resolveTenantId 返回 NullValue → SQL 生成 `tenant_id = NULL`
--   恒假(类注释明写的 fail-closed) ⇒ biz_incentive_record/biz_customer 等
--   26 张租户白名单表对非 admin 查询恒空。admin 的豁免掩盖了该问题,
--   NM005(销售员/06)是第一个深度使用的非 admin 用户从而暴露。
--   实证: 同角色(06)同部门(dept 6)临时用户探测 /api/incentive/* 全部
--   200+data:[](库内 377 条 tenant_id=1、shared=1 294 条)。
--
--   legacy 库从未维护 org_id(11 个部门全部 group_id=1, 数据侧健康),
--   故按部门归属回填: org_id := dept_id(部门有集团归属时);
--   无部门或部门无集团的兜底挂主集团顶级组织(id=1, group_id=1)。
--   org_id 语义=组织树节点(测试库 UATNOF 样本 org_id=1 印证)。
--
-- 本迁移做什么(幂等):
--   1) org_id 回填(仅补 NULL, 不动已有值)
--   2) sys_user.perm_version+1(触发权限上下文缓存重建; groupIds 装载于
--      SecurityUser/上下文, 已登录用户最长 TTL 10 分钟或重登后生效)
--
-- 回滚(需人工确认后单独执行):
--   UPDATE sys_user SET org_id = NULL WHERE ... (不提供自动回滚——回填是
--   数据完整性修复, 回滚将重新导致非 admin 全员租户数据不可见)
-- ============================================================

UPDATE `sys_user` u
LEFT JOIN `sys_dept` d ON d.`id` = u.`dept_id`
SET u.`org_id` = CASE
    WHEN d.`id` IS NOT NULL AND d.`group_id` IS NOT NULL THEN u.`dept_id`
    ELSE 1  -- 主集团顶级组织(sys_dept id=1, group_id=1)
END
WHERE u.`org_id` IS NULL;

UPDATE `sys_user` SET `perm_version` = `perm_version` + 1;

-- 复核查询(手工执行):
--   SELECT SUM(org_id IS NULL) AS still_null FROM sys_user;              -- 预期 0
--   SELECT u.username, u.org_id, d.group_id FROM sys_user u
--     LEFT JOIN sys_dept d ON d.id=u.org_id WHERE u.username='NM005';   -- 预期 org=6 group=1
