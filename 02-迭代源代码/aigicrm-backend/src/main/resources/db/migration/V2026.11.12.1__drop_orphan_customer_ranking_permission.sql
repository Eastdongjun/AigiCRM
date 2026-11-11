-- 【四待裁项裁决A 2026-11-12】孤儿码 ranking:customer:view 退役删行
--
-- 背景(底座评估 design.md 已知问题#3, 2026-11-12 用户拍板 A):
--   V2026.09.11.4 曾为该孤儿码补登记 sys_menu 行(menu_type=3, parent_id=20 父级已删的悬空按钮行),
--   但 PC/MP 前端始终零消费(词表对账/前端 grep 双证), 唯一后端消费点是 CustomerController:155
--   三码 OR 通道(score:view ∨ ranking:customer:view ∨ statistics:view)。
-- 硬标准核验(生产只读 2026-11-12):
--   ① 三端零引用: PC/MP 源码 grep 零命中(MP 仅生成物常量与 e2e 夹具, 非消费);
--   ② 绑定非零但兜底实存: 该码仅销售员绑定, 而销售员同时持 score:view(menu 61)与
--      statistics:view(menu 108/129)——删码后 rankings 端点走双码兜底, 可见集零变化。
-- 同步变更: CustomerController:155 三码 OR 收窄为双码(同批提交)。
-- 幂等性: DELETE 天然幂等; 按 permission 自然键锚定, 无硬编码 id(INV-MIG-001)。
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `id` FROM `sys_menu` WHERE `permission` = 'ranking:customer:view');

DELETE FROM `sys_menu` WHERE `permission` = 'ranking:customer:view' AND `menu_type` = 3;
