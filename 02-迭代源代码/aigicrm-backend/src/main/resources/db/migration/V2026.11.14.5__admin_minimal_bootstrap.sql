-- V2026.11.14.5 admin 最小权限集 bootstrap(全系统收口第 1 批)
--
-- 产品裁决(2026-11-14): admin 不再是"隐式全通"; 默认只保留"能进入权限中心并给别人授权"的
-- 最小集, 其余功能码必须由权限中心显式勾选。数据范围按 sys_role.data_scope 生效(后续批次)。
--
-- 为什么保留这 7 码: 如果连它们也删, admin 登录后无法进入权限中心/角色/用户页, 会把自己锁死。
-- 这不是业务权限, 是"权限中心操作面"。
--
-- 回滚: sys_role_menu_admin_bak_20261114 保存了裁剪前 admin 的 role_id/menu_id;
--      需要恢复时 INSERT ... SELECT 回 sys_role_menu 即可(见文件末尾注释)。
--
-- 只增/只裁剪 admin 自己的行; 不碰其他角色; 目录行(permission IS NULL)保留, 保证菜单树父链可用。

-- ① 备份 admin 当前菜单绑定(一次性; 表存在则不重复)
CREATE TABLE IF NOT EXISTS sys_role_menu_admin_bak_20261114 AS
SELECT rm.role_id, rm.menu_id, NOW() AS backed_at
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
WHERE r.role_code = 'admin';

-- ② 裁剪: 只保留 bootstrap 7 码; 目录行(permission NULL)保留
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_code = 'admin'
  AND m.permission IS NOT NULL
  AND m.permission NOT IN (
    'system:config:view',
    'system:config:edit',
    'system:role:view',
    'system:role:edit',
    'system:menu:view',
    'system:user:view',
    'system:user:edit'
  );

-- ③ 确保 bootstrap 7 码在 admin 名下(幂等; 覆盖历史缺失)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission IN (
    'system:config:view',
    'system:config:edit',
    'system:role:view',
    'system:role:edit',
    'system:menu:view',
    'system:user:view',
    'system:user:edit'
)
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ④ 清掉 admin 的旧内核 grant(admin 默认不再持有 file/客户等数据槽授权;
--    后续在权限中心勾选码时由 RolePermScopeService 双写重新生成)。
DELETE g
FROM sys_perm_grant g
JOIN sys_role r ON r.role_code = g.subject_id
WHERE g.subject_type = 'ROLE' AND r.role_code = 'admin';

-- 回滚示例(需要时人工执行, 不在本迁移内自动执行):
-- INSERT INTO sys_role_menu(role_id, menu_id)
-- SELECT role_id, menu_id FROM sys_role_menu_admin_bak_20261114
-- WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm
--                   WHERE rm.role_id = sys_role_menu_admin_bak_20261114.role_id
--                     AND rm.menu_id = sys_role_menu_admin_bak_20261114.menu_id);
