-- V2026.11.23.5 补 biz_fabushi_recitation 的租户隔离列（CODE-REVIEW-20261123 R1-01）
--
-- 缺陷：V2026.11.23.2 建表时漏了 tenant_id，而同批 .3/.4 两张表都有。
-- 后果两条路都不通：
--   a) 不登记白名单 → 写入不被插件补租户（靠 DEFAULT 兜）、查询不被租户过滤，属隐式契约；
--   b) 直接登记白名单 → 生产 TENANT_ENABLE=true，启动自检 MybatisPlusConfig#verifyTenantColumns
--      发现缺列直接 fail-fast，**生产起不来**。
-- 所以必须先补列，再登记。
--
-- 单集团前提（2026-11-23 确认）：系统只服务一个集团，tenant_id 恒为 1。
--   ⇒ 本列 DEFAULT '1' 对存量行即为正确值，**无需数据回填**；
--   ⇒ 不单独建 tenant_id 索引（选择性为 0，与既有 12 张 biz_fabushi_* 表范式一致）。
--
-- 为何新增本文件而不回改 V2026.11.23.2：迁移只追加、不改写已应用文件，
--   否则已执行环境的 Flyway checksum 会漂移（迁移契约 [5] 冻结校验同理）。
--   另：本表尚未上生产（生产落后 33 张表），此刻补列零回填成本，是最便宜的时机。
ALTER TABLE `biz_fabushi_recitation`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '集团隔离' AFTER `user_id`;
