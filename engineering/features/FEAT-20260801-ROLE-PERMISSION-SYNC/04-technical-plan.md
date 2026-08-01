# 技术实施计划

## 已确认影响范围

- sys_role_menu：role 9 + menu 84/99/18/45/46

## 架构与关键决策

- 按角色矩阵系统性补齐（页面可用性规则：菜单权限 ⊆ 页面接口权限）
- 幂等 INSERT；权限实时生效

## 数据、API 与事件契约

- INSERT sys_role_menu（NOT EXISTS 防重）

## 权限与安全边界

- 行政获审批处理能力（用户确认）；不授系统管理类（roles/menus 等）

## 实施顺序

1. 绑定 → 2. curl 验证 → 3. 浏览器 6 页验证

## 测试策略

- AC-001：接口状态；AC-002：页面 XHR 403 监控

## 迁移、发布与兼容

- 无代码发布

## 可观测性

- curl + 浏览器断言

## 回滚

- DELETE sys_role_menu WHERE role_id=9 AND menu_id IN (84,99,18,45,46)

## 风险与盲区

- 低
