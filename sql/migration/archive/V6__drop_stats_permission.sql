-- V6: 清理 V4/V5 引入的 biz_stats_permission(2026-08-10 权限体系回退)
-- 背景: 权限体系重新设计, 原"公司级统计可见"按模块拆分方案(61fd97c/8139cff)已回退代码;
--       V4/V5 已在生产/测试库运行, 此表成为孤儿——删除以保持库与代码一致。
-- 幂等: 全新环境(V4/V5 已被回退移除)该表不存在, DROP IF EXISTS 为 no-op。
DROP TABLE IF EXISTS biz_stats_permission;
