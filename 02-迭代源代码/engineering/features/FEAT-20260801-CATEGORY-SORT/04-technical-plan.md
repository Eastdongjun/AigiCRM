# 技术实施计划

## 已确认影响范围

- 数据库（测试服 111.229.37.220，MySQL 容器 shared-mysql，库 aigicrm）：表 `biz_incentive_category`，仅顶层 4 行 sort_order
- 前端/后端代码：**零改动**（getCategoryTree 已按 sort_order 升序返回，IncentiveService.java:81-85）
- 消费方联动：incentive/index.vue（Tab/默认选中/弹窗分组）、system/config.vue（分组）、incentive/customer.vue、overview.vue

## 架构与关键决策

- 决策：改数据（sort_order），不改代码。原因：排序本就由 sort_order 单一驱动，改数据一处生效全站一致、可逆、无构建风险；替代方案（代码硬编码 code 权重排序）被拒——会引入第二套排序源，与现有 sort_order 管理界面（config.vue 有排序列可编辑）冲突

## 数据、API 与事件契约

- 变更：`UPDATE biz_incentive_category SET sort_order=1 WHERE id=4 (trial)`、`2 WHERE id=3 (plan)`、`3 WHERE id=2 (exchange)`、`4 WHERE id=1 (visit)`（用 id 精确匹配，不用 code）
- 幂等：可重复执行；并发：无人写该表排序（config.vue 编辑走独立表单）
- API 不变：getCategoryTree 返回序即新序

## 权限与安全边界

- 仅测试服；改前备份整表（SELECT 导出），可一条 SQL 还原

## 实施顺序

1. SSH 测试服备份 `biz_incentive_category` 排序前快照到功能包 evidence/
2. 执行 4 条 UPDATE（按 id）
3. 回读验证：getCategoryTree API 顺序 + SQL 回读

## 测试策略

- AC-001：SQL 回读（id+sort_order）
- AC-002/AC-003：浏览器实测（上传页 Tab 顺序/默认选中、上传弹窗分组、config.vue 分组）

## 迁移、发布与兼容

- 无代码发布；测试服即时生效；生产同步需用户授权（同 SQL 一条即可）

## 可观测性

- 证据：功能包 evidence/ 下备份 SQL + 回读输出 + 浏览器截图

## 回滚

- `UPDATE` 反向赋值（visit=1/exchange=2/plan=3/trial=4）或执行备份 SQL；验证 getCategoryTree 顺序复原

## 风险与盲区

- 低风险：数据变更 4 行，消费方单一接口；未覆盖生产（需授权）
