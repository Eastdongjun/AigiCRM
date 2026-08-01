# 技术实施计划

## 已确认影响范围

- 数据：测试服 ycmn.biz_approval_config id=1（flow_name="客户捞取"，flow_type="????"）
- 代码：零改动

## 架构与关键决策

- 直接 DELETE（条件含 flow_name 防误删），不软删——用户自加测试数据，无历史价值
- 引用检查已确认：biz_approval_flow 无该 flow_type 记录

## 数据、API 与事件契约

- DELETE FROM biz_approval_config WHERE id=1 AND flow_name='客户捞取'

## 权限与安全边界

- 仅测试服；无凭据

## 实施顺序

1. DELETE → 2. SELECT 回读 → 3. 浏览器验证审批配置页

## 测试策略

- AC-001：SQL 回读仅剩 1 条；AC-002：页面表格断言

## 迁移、发布与兼容

- 无代码发布

## 可观测性

- 回读输出 + 页面快照

## 回滚

- 重建 INSERT（id=1, 客户捞取/????, USER, ANY, [11]）

## 风险与盲区

- 无
