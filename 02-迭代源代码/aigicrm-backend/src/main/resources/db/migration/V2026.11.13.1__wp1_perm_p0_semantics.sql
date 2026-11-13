-- V2026.11.13.1 WP1 P0 语义止血批(权限升级施工规格书 WP1.4 + WP1.5, 锁定决策#7/#8)
--
-- ① 上级/负责人开关两键显式 false(锁定决策#8: 默认关):
--    后端 OrgScopeResolver 缺失行已 fail-closed 默认 false(2026-09-21 收敛),
--    但存量行若显式 'true' 仍生效; 本迁移不翻动在用环境的显式选择——仅把"缺失行"补成显式 false
--    (消除前端缺行归一 'true' 的口径差, BasicSettings.vue 同批改缺行默认 false)。
--    已存在的行为: 显式 true 的行保持 true(在用环境若依赖开关, 由管理员在系统设置显式关闭, 不静默翻转)。
-- ② (statistics, view) 数据范围档退役(锁定决策#7: statistics:view 只做入口码):
--    后端消费面已同批移除(CONSUMED_AXIS), 统计项改按来源动作 (customer, view) 过滤
--    (CustomerVisibilityService 单一源); 存量配置行删除, 删除前逐行落 sys_rule_version 审计留痕。
--    注: V2026.11.12.2 退役的是入口码 dashboard:view(菜单行), 与本迁移的数据范围档互不重叠。
-- 幂等性: ① NOT EXISTS 防重插 + UPDATE 天然幂等; ② DELETE 幂等(重复执行删 0 行)。
-- 自然键锚定: config_key / (module,action) 组合, 无硬编码行 id(INV-MIG-001)。

-- ① 上级可见下属链: 缺行补显式 false
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'perm.superior.see.subordinate', 'false', '上级可见下属链(仅查看类动作生效; admin-only 修改)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'perm.superior.see.subordinate');

-- ① 负责人可见组织: 缺行补显式 false
INSERT INTO sys_config (config_key, config_value, description)
SELECT 'perm.org.leader.see.org', 'false', '组织负责人可见本组织子树(仅查看类动作生效; admin-only 修改)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'perm.org.leader.see.org');

-- ② 删除前审计留痕(旧行逐条记 "old→退役"), 失败不阻断(旁路降级与运行时审计同口径)
-- ⚠️ 比较两侧显式 COLLATE utf8mb4_general_ci: sys_rule_version 建表只钉 CHARSET 未钉 COLLATE
-- (V2026.09.19.5), MySQL 8 环境默认 0900_ai_ci 与 5.7/连接侧 general_ci 混用报
-- "Illegal mix of collations"(空库重放实测抓出); 双侧显式钉死, 跨版本可移植。
INSERT INTO sys_rule_version (rule_key, old_value, new_value, remark, operator_name)
SELECT CONCAT('perm_scope:', assignee_type, ':', assignee_key, ':statistics:view'),
       CONCAT(scope_level, '/', anchor),
       '已退役(锁定决策#7: statistics:view 只做入口)',
       'WP1.5 统计口径统一: (statistics,view) 数据范围档退役',
       'flyway:V2026.11.13.1'
FROM role_perm_scope
WHERE module = 'statistics' AND action = 'view'
  AND NOT EXISTS (
        SELECT 1 FROM sys_rule_version rv
        WHERE rv.rule_key COLLATE utf8mb4_general_ci
                  = CONCAT('perm_scope:', assignee_type, ':', assignee_key, ':statistics:view') COLLATE utf8mb4_general_ci
          AND rv.new_value COLLATE utf8mb4_general_ci
                  = '已退役(锁定决策#7: statistics:view 只做入口)' COLLATE utf8mb4_general_ci);

DELETE FROM role_perm_scope
WHERE module = 'statistics' AND action = 'view';
