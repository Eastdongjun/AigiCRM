-- ============================================================
-- V2026.09.19.9__task_permission_role_binding.sql
-- 任务模块权限码绑定角色（修复：PR-10 上线后全员 403，含系统管理员）
-- ============================================================
-- 背景（2026-09-19 本地活体验证实测）：
--   V2026.09.19.8 登记了 task:view / task:manage 两个按钮级权限点（menu 9065/9066），
--   但**未做任何 sys_role_menu 角色绑定**。TaskController 全部端点挂
--   @PreAuthorize("hasAuthority('task:view'/'task:manage'") ⇒ 码无角色持有 ⇒
--   GET /api/task/list 对 admin / 销售总监(0) / 销售员(06) 一律 403：
--   任务模块（页面/工作台卡片/统计）对所有人不可用。
--   与 V2026.09.13.1 修的"码在用、字典没登记"是同一族缺陷的镜像——"码登记了、没人持有"。
--
-- 本迁移做什么（幂等；只做绑定，不新增权限点）：
--   1) task:view + task:manage → 系统管理员(admin) + 销售总监(0) + 销售员(06)。
--      · task:manage 门禁创建/完成/转交；数据边界由 TaskService 按经办人/创建人另行收口，
--        销售员拿到 manage 只能操作本人相关任务，不会越权他人任务。
--      · 行政(05) 暂不绑定：任务流转以销售侧为主，需要时由管理员在权限矩阵显式勾选。
--   2) 9065/9066 visible 0→1：在角色权限树/权限矩阵中可见可勾选（对齐
--      V2026.09.13.1 的 visible=1 口径；visible 只影响树展示，不影响侧边栏——按钮码 path=NULL）。
--
-- 回滚（需人工确认后单独执行）：
--   DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
--     WHERE m.permission IN ('task:view','task:manage');
--   UPDATE sys_menu SET visible = 0 WHERE permission IN ('task:view','task:manage');
--   注意：回滚后任务模块恢复为全员 403（本迁移修复的原始缺陷）。
--
-- 运行提示：本迁移第 3 节已递增 perm_version 触发缓存自愈（ JwtAuthFilter 装载时
-- 比对版本不一致即重建权限集），无需手工清 Redis；等价于应用内
-- RolePermScopeService.updateScopes 的 bumpAllPermVersions() 兜底路径。
-- ============================================================

-- ---------- 1) 角色绑定（幂等） ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.`id`, m.`id`
FROM `sys_role` r
    JOIN `sys_menu` m ON m.`permission` IN ('task:view', 'task:manage')
WHERE r.`role_code` IN ('admin', '0', '06')
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id`
);

-- ---------- 2) 权限树可见 ----------
UPDATE `sys_menu` SET `visible` = 1 WHERE `permission` IN ('task:view', 'task:manage') AND `visible` = 0;

-- ---------- 3) 权限缓存自愈（关键） ----------
-- 权限上下文缓存 PermissionContext 存外部 Redis（TTL 10 分钟）且**跨应用重启存活**；
-- 其新鲜度判定 = 缓存内 perm_version 与 sys_user.perm_version 比对。
-- 本迁移直插 sys_role_menu 不经过应用层 evictAll()，若不递增版本号，
-- 已登录用户的权限集最长残留到 TTL 过期（实测：迁移后重启应用仍 403，递增版本后立即 200）。
UPDATE `sys_user` SET `perm_version` = `perm_version` + 1;
