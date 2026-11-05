-- ============================================================
-- V2026.11.05.7__incentive_stats_scope_action_backfill.sql
-- 【2026-11-05 用户拍板 B"四项激励独立数据范围"】
-- 四项激励统计/排行页(incentive:stats:view/rank:view)的数据范围改读独立档位
-- (incentive, stats_view)——两码共用一动作(同一数据族: category-stats/scoreboard/ranking)。
-- 本迁移把存量 (statistics, view) 的范围配置按 assignee 逐行拷贝为 (incentive, stats_view):
--   存量"跟随统计分析档位"的行为在迁移后完全保持(同值), 此后管理员可在 权限中心
--   (系统设置→权限中心→功能与范围) 对 四项激励 独立勾选范围, 不再牵动统计分析。
-- 用户/岗位/角色三级 assignee 全拷贝(登录装载三级同序)。幂等: NOT EXISTS 守卫。
-- 缓存: 末尾 perm_version 全员 +1(直改库自愈)。
-- 回滚(人工): DELETE FROM role_perm_scope WHERE module='incentive' AND action='stats_view';
--   UPDATE sys_user SET perm_version = perm_version + 1;
-- ============================================================

-- 1) 逐行拷贝(statistics,view) → (incentive,stats_view)(含 role_code 列——该列 NOT NULL 无默认,
--    首版漏供致迁移失败[success=0 实证], 修正为按源行原样复制); NOT EXISTS 防重
INSERT INTO role_perm_scope (role_code, assignee_type, assignee_key, module, action, scope_level, anchor)
SELECT src.role_code, src.assignee_type, src.assignee_key, 'incentive', 'stats_view', src.scope_level, src.anchor
FROM role_perm_scope src
WHERE src.module = 'statistics' AND src.action = 'view'
  AND NOT EXISTS (
    SELECT 1 FROM role_perm_scope x
    WHERE x.assignee_type = src.assignee_type AND x.assignee_key = src.assignee_key
      AND x.module = 'incentive' AND x.action = 'stats_view'
  );

-- 2) 权限缓存自愈
UPDATE sys_user SET perm_version = perm_version + 1;
