-- ============================================================
-- V19__drop_backup_tables.sql
--
-- 【需求】生产 8 张历史备份表(*_bak_*)已于 2026-08-16 归档 NAS 后手删；
--         V1 基线快照(2026-08-11 生产 schema)仍含其 CREATE TABLE,
--         任何空库按 V1→V18 重建都会复活这些表——本迁移把删除版本化。
-- 【方法】DROP TABLE IF EXISTS, 幂等可重放:
--         已删环境(生产)执行无副作用, 未删环境(测试服/新建库)执行即落净。
-- 【业务规则】仅删除 8 张历史快照表, 不动任何主表/视图/数据;
--         归档副本永久保留于 NAS backup/archive/bak-tables-20260816/
--         (每表独立 .sql, 共 8 个文件, 不受 daily 14 天轮转影响)。
-- 【注意】执行前提: 目标环境这 8 表已 dump 归档; 生产已归档完毕可安全执行。
-- ============================================================

DROP TABLE IF EXISTS `biz_customer_bak_0624`;
DROP TABLE IF EXISTS `biz_sea_config_bak_0624`;
DROP TABLE IF EXISTS `biz_sea_config_bak_0629`;
DROP TABLE IF EXISTS `biz_sea_pool_bak_0624`;
DROP TABLE IF EXISTS `biz_sea_pool_bak_poolid_0721`;
DROP TABLE IF EXISTS `sys_role_bak_20260808`;
DROP TABLE IF EXISTS `sys_role_org_bak_20260808`;
DROP TABLE IF EXISTS `sys_user_bak_wll_0721`;
