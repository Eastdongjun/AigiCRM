-- ============================================================
-- 系统级数据范围开关（2026-08-07，FEAT：上级/负责人默认看下级）
-- 两个开关控制"直接上级默认看递归下属链 / 组织负责人默认看本组织整棵子树"。
-- 后端 getBooleanValue 对缺失键默认返回 true（默认开启），此处显式插入 'true'
-- 便于运维可见、前端设置页显示一致。回滚：两行值改 'false' 或删行即可（秒级生效）。
-- 执行需带库名：mysql -uroot -p aigicrm < perm-scope-switches.sql
-- ============================================================

INSERT INTO sys_config (config_key, config_value, description)
VALUES ('perm.superior.see.subordinate', 'true', '直接上级默认可见递归下属链数据(最多5层)'),
       ('perm.org.leader.see.org', 'true', '组织负责人默认可见本组织整棵子树数据')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- 朱莉莉(TY005, id=41)专项：数据范围 office → 看太原办销售部8人(含梁慧栋/测试4条激励记录)
-- 已确认不动 leader_id，用 data_scope_override。回滚：UPDATE sys_user SET data_scope_override=NULL WHERE id=41;
UPDATE sys_user SET data_scope_override = 'office' WHERE id = 41;
