-- 目标库：执行前先 USE <测试库或新快照>；生产应用须用户窗口授权，并把 START TRANSACTION/ROLLBACK 改为 COMMIT。
-- AIGICRM role 0（销售总监）功能权限收窄 — 2026-11-18（B16）
-- 用户裁决：应收窄。先测试库/新快照演练；生产应用须窗口授权。
-- 注意：sys_perm_grant.subject_id = 角色码 '0'（不是 sys_role.id=10）；sys_role_menu.role_id=10。
-- 保留：system:dept:view / system:position:view / system:role:view / system:user:view。
-- 待业务确认（本脚本不动）：incentive:person-rank:export、sea:config:edit。

-- ---------- 0. 收窄前计数 ----------
SELECT 'before_grants_total' AS k, COUNT(*) AS v
  FROM sys_perm_grant WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id='0';
SELECT 'before_grants_system' AS k, COUNT(*) AS v
  FROM sys_perm_grant WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id='0'
    AND action_code LIKE 'system:%';
SELECT 'before_menu_total' AS k, COUNT(*) AS v
  FROM sys_role_menu WHERE role_id=10;

-- ---------- 1. 演练事务（生产执行时去掉 START TRANSACTION/ROLLBACK，改 COMMIT） ----------
START TRANSACTION;

-- 1a. 移除 23 条 system 权限：sys_role_menu（按 menu.permission）
DELETE FROM sys_role_menu
 WHERE role_id=10
   AND menu_id IN (
     SELECT id FROM sys_menu WHERE permission IN (
       'system:config:edit','system:config:view',
       'system:dept:edit',
       'system:group:add','system:group:delete','system:group:edit','system:group:view',
       'system:log:view','system:menu:view','system:org:admin',
       'system:position:add','system:position:delete','system:position:edit',
       'system:role:add','system:role:delete','system:role:edit',
       'system:rule:add','system:rule:delete','system:rule:edit','system:rule:view',
       'system:user:add','system:user:delete','system:user:edit'
     )
   );

-- 1b. 移除 23 条 system 权限：sys_perm_grant（按 action_code）
DELETE FROM sys_perm_grant
 WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id='0'
   AND action_code IN (
     'system:config:edit','system:config:view',
     'system:dept:edit',
     'system:group:add','system:group:delete','system:group:edit','system:group:view',
     'system:log:view','system:menu:view','system:org:admin',
     'system:position:add','system:position:delete','system:position:edit',
     'system:role:add','system:role:delete','system:role:edit',
     'system:rule:add','system:rule:delete','system:rule:edit','system:rule:view',
     'system:user:add','system:user:delete','system:user:edit'
   );

-- 1c. legacy/ops 清理（明确 legacy；pending 两项不动）
DELETE FROM sys_role_menu
 WHERE role_id=10
   AND menu_id IN (
     SELECT id FROM sys_menu WHERE permission IN (
       'finance:view','finance:manage',
       'incentive:view-all','incentive:view-shared',
       'file:reconcile'
     )
   );
DELETE FROM sys_perm_grant
 WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id='0'
   AND action_code IN ('finance:view','finance:manage','file:reconcile');

-- ---------- 2. 收窄后计数（事务内） ----------
SELECT 'after_grants_total' AS k, COUNT(*) AS v
  FROM sys_perm_grant WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id='0';
SELECT 'after_grants_system' AS k, COUNT(*) AS v
  FROM sys_perm_grant WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id='0'
    AND action_code LIKE 'system:%';
SELECT 'after_menu_total' AS k, COUNT(*) AS v
  FROM sys_role_menu WHERE role_id=10;
SELECT 'remaining_system_grants' AS k, GROUP_CONCAT(action_code ORDER BY action_code) AS v
  FROM sys_perm_grant WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id='0'
    AND action_code LIKE 'system:%';

-- 演练：回滚。生产执行改 COMMIT。
ROLLBACK;
