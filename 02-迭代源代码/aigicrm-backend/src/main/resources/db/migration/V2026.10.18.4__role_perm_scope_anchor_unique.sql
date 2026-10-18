-- ============================================================
-- V2026.10.18.4__role_perm_scope_anchor_unique.sql
-- 【审查 perm-5 修复 2026-10-18】角色-模块-动作范围锚点重复。
--
-- 背景:
--   旧唯一键 uk_assignee_ma(assignee_type, assignee_key, module, action, anchor)
--   把 anchor 纳入唯一性: 同一主体/模块/动作先存 OWNER、后改 ORG 时, upsert 不命中旧行,
--   会新增第二条记录。读取侧 anchorOf/查询均取第一条且无 ORDER BY, UI 以
--   (module, action) 建 map 后后写覆盖 → 同一配置可能按不同锚点解释, 口径漂移。
--
-- 变更(全部幂等, 可重复执行):
--   1. 按 (assignee_type, assignee_key, module, action) 去重, 保留最小 id(最早)的一条;
--      不按业务 id 定位(INV-MIG-001), 纯 join 自比较;
--   2. 新增四列唯一键 uk_role_perm_scope_key, 由数据库保证同一键只有一条;
--      旧 uk_assignee_ma 保留(其列是本键超集, 不再产生新语义, 删除属可选清理)。
--
-- 注: role_perm_scope 无软删列, 无需 deleted 参与唯一键组合。
--
-- 验证(迁移后应为 0 行; 仅注释, Flyway 不执行):
--   SELECT assignee_type, assignee_key, module, action, COUNT(*) c
--     FROM role_perm_scope
--    GROUP BY assignee_type, assignee_key, module, action HAVING c > 1;
-- ============================================================

-- 1. 去重: 同一 (assignee_type, assignee_key, module, action) 仅保留最小 id
DELETE r1
  FROM role_perm_scope r1
  JOIN role_perm_scope r2
    ON r1.assignee_type = r2.assignee_type
   AND r1.assignee_key = r2.assignee_key
   AND r1.module = r2.module
   AND r1.action = r2.action
   AND r1.id > r2.id;

-- 2. 唯一索引(幂等: information_schema 已存在则跳过)
SET @has_uk := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'role_perm_scope'
      AND index_name = 'uk_role_perm_scope_key');
SET @ddl := IF(@has_uk = 0,
    'ALTER TABLE role_perm_scope ADD UNIQUE KEY uk_role_perm_scope_key (assignee_type, assignee_key, module, action)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
