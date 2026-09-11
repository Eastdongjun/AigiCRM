-- ============================================================================
-- V2026.09.11.1  V2–V11 历史缺口补齐：6 张「生产有、仓库链不产出」的表
-- ============================================================================
-- 背景：生产库的迁移历史含 V2–V11，其文件已归档在 sql/migration/archive/（且版本号有重复），
--       仓库无法原样收编；仓库的 V1__baseline.sql 是 2026-08-11 重建的快照，**不含**这 6 张表。
--       结果：全新库（开发/测试）缺这 6 张表，其中 sys_token_blacklist 是运行时代码真的会查的表
--       （JwtTokenProvider.java 的 SELECT/INSERT/DELETE）。
--
-- 实证：用 Flyway 9.22.3 在全新 MySQL 8.0 上重放仓库链 → 52 张表；
--       与生产 dump 的表集合做差集，production∖chain 恰为下列 6 张，chain∖production 仅
--       biz_customer_file / biz_incentive_read（仓库新工作尚未上生产）。无其他差异。
--
-- 取材：DDL 逐字取自只读生产备份 dump
--       08-备份索引/09-生产环境备份/database/ycmn-production.sql
--       sha256 = 5005547abbd84eea9876f3e2863456621a6d7ec0ef8d24417a93930444fe7d18
--
-- 与原 dump 的**全部**差异（仅此三处，均可核对）：
--   ① `DROP TABLE IF EXISTS` 已删除 —— 迁移不得删表
--   ② `CREATE TABLE` → `CREATE TABLE IF NOT EXISTS` —— 生产已存在这些表，必须幂等，
--      否则本迁移应用到生产时会直接失败
--   ③ 去掉 `AUTO_INCREMENT=N` —— 本迁移不播种任何数据，自增起点交回默认值 1
--
-- 不含任何数据（INSERT）：生产数据属业务数据，不入库到迁移。
--
-- 与生产的一致性：下列 6 段 DDL 与 dump **逐字节一致**（已用 sha256 机械核对），除上述三处
--   结构性变换外未做任何改动 —— 没有"顺手美化"任何注释或格式。
--
-- 已知残留缺陷（**本迁移刻意不修，且原样保留**）：sys_token_blacklist.expires_at 的注释在生产是
--   cp1252 双重编码乱码，并含 C1 控制字符（U+008F / U+0090）。本迁移原样保留它，以换取"与生产
--   逐字节一致"这条可机械验证的性质。同类乱码注释：仓库 V1__baseline.sql 有 63 处、
--   生产有 58 处（涉及 31 张表）——这是从 dump 继承的系统性缺陷，应作为**独立的一次性整备迁移**
--   统一修，而不是在本结构迁移里只修一处。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `perm_scope_field` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `scope_id` bigint NOT NULL COMMENT '所属范围配置 → role_perm_scope.id',
  `field_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实体字段名(驼峰)，如 revenue / invoiceAmount',
  `visible` tinyint NOT NULL DEFAULT '0' COMMENT '0=对该范围隐藏 1=显式可见',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_field` (`scope_id`,`field_name`),
  KEY `idx_field` (`field_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字段级可见性(V11，建表留位；运行时接入待收敛切换后)';

CREATE TABLE IF NOT EXISTS `perm_scope_org` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `scope_id` bigint NOT NULL COMMENT '所属范围配置 → role_perm_scope.id',
  `org_id` bigint NOT NULL COMMENT '授权组织 → sys_dept.id',
  `include_children` tinyint NOT NULL DEFAULT '1' COMMENT '是否含下级组织',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_org` (`scope_id`,`org_id`),
  KEY `idx_org` (`org_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='scope=custom 时的授权组织清单(V11，取代 sys_role_org/sys_user_org)';

CREATE TABLE IF NOT EXISTS `perm_scope_shadow_diff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(40) COLLATE utf8mb4_general_ci NOT NULL COMMENT '一次比对批次',
  `user_id` bigint NOT NULL,
  `module` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `action` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `legacy_count` int NOT NULL COMMENT '旧模型解析出的可见主体数',
  `new_count` int NOT NULL COMMENT '新模型解析出的可见主体数',
  `only_legacy` text COLLATE utf8mb4_general_ci COMMENT '仅旧模型可见(切换后会丢可见性→业务停摆)',
  `only_new` text COLLATE utf8mb4_general_ci COMMENT '仅新模型可见(切换后会多可见性→数据泄露)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_run` (`run_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据范围新旧模型影子比对结果(V11)；只有全量无差异才允许切换 ycmn.scope.use-unified=true';

CREATE TABLE IF NOT EXISTS `biz_sea_config_region` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `config_id` bigint NOT NULL COMMENT '公海池配置ID → biz_sea_config.id',
  `region_id` bigint NOT NULL COMMENT '办事处/大区部门ID → sys_dept.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_region` (`config_id`,`region_id`),
  KEY `idx_region` (`region_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公海池-办事处关联(V10，取代 biz_sea_config.region_ids 的 CSV 存储)';

CREATE TABLE IF NOT EXISTS `biz_sea_config_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团ID',
  `config_id` bigint NOT NULL COMMENT '公海池配置 → biz_sea_config.id',
  `perm_type` varchar(10) COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限类型: VIEW/EDIT/DELETE/IMPORT/CLAIM',
  `target_type` varchar(10) COLLATE utf8mb4_general_ci NOT NULL COMMENT '主体类型: DEPT/USER',
  `target_id` bigint NOT NULL COMMENT 'DEPT→sys_dept.id / USER→sys_user.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_perm_target` (`config_id`,`perm_type`,`target_type`,`target_id`),
  KEY `idx_lookup` (`perm_type`,`target_type`,`target_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公海池权限主体(V11，取代 biz_sea_config 的 10 个 CSV 列)';

CREATE TABLE IF NOT EXISTS `sys_token_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `jti` varchar(64) NOT NULL COMMENT 'JWT jti',
  `user_id` bigint NOT NULL,
  `expires_at` datetime NOT NULL COMMENT 'token è¿‡æœŸæ—¶åˆ»(è¡Œå¯æ¸…ç†)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jti` (`jti`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='token撤销黑名单(DB兜底)';

