-- ============================================================================
-- V2026.09.11.2  修正数据范围相关列的**注释**（不动任何数据、不动列定义）
-- ============================================================================
-- 背景（本次全景蓝图抽取时发现的口径不一致）：
--   1) sys_role.data_scope 的注释写的是 all/dept_and_children/dept/self，
--      而代码侧权威枚举 DataScopeType 实际是 7 个值：
--          all 全部 / custom 自定义 / region 本大区及以下 / office 本办事处及以下 /
--          dept 本部门及以下 / team 本人及下级 / self 仅本人
--      → 注释漏了 custom/region/office/team 四个真实值。
--   2) role_perm_scope.scope_level 的注释同样陈旧，且原文是 cp1252 双重编码乱码。
--   3) dept_and_children 是**兼容值**（OrgScopeResolver 与 dept 同语义处理），
--      但 DataScopeType.fromCode 不认它 → 会静默降级 SELF（详见 V2026.09.11.2 同期代码修复）。
--
-- 本迁移只做一件事：**用 ALTER … MODIFY COLUMN 改 COMMENT**。
--   - 列类型 / 排序规则 / NULL 约束 / 默认值 与 V1__baseline.sql 完全一致，逐字保持
--   - 不触碰任何数据行；不改任何其他列；不改索引
--   - 生产与全新库均可安全应用（两处表都由 V1 建立）
--
-- ⚠️ 为什么不直接改 V1__baseline.sql：V1 已应用到生产，改一个字节会让
--    生产 flyway_schema_history 的 checksum 失配（见 FROZEN-SHA256.txt）。
-- ============================================================================

-- sys_role.data_scope：补齐 7 个真实取值，并标注兼容值
ALTER TABLE `sys_role`
  MODIFY COLUMN `data_scope` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'self'
  COMMENT '数据范围 all全部 custom自定义 region本大区及以下 office本办事处及以下 dept本部门及以下 team本人及下级 self仅本人（兼容值 dept_and_children 等同 dept；org/org_and_children 已废弃）';

-- role_perm_scope.scope_level：同上（原文为乱码，一并修正为可读中文）
ALTER TABLE `role_perm_scope`
  MODIFY COLUMN `scope_level` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'self'
  COMMENT '数据范围 all全部 custom自定义 region本大区及以下 office本办事处及以下 dept本部门及以下 team本人及下级 self仅本人（兼容值 dept_and_children 等同 dept；org/org_and_children 已废弃）';
