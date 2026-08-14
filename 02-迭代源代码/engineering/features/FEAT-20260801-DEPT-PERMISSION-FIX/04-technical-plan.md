# 技术实施计划

## 已确认影响范围

- 数据库 sys_role_menu：role 9/11 + menu 98（基础数据-部门，system:dept:view）

## 架构与关键决策

- 决策：数据修复（角色菜单绑定）而非代码改动——部门数据为业务基础数据，与"基础数据-部门"菜单（id=98）设计意图一致；总监角色已有该绑定（先例）
- 前端无需改动（hasPerm 自动生效，权限实时查库）

## 数据、API 与事件契约

- INSERT sys_role_menu（幂等：NOT EXISTS 防重）

## 权限与安全边界

- 仅授 view（只读），不授 dept:edit/add/delete

## 实施顺序

1. INSERT 绑定 → 2. curl 验证 403→200 → 3. 浏览器 9 页验证

## 测试策略

- AC-001：curl /system/depts；AC-002：浏览器页面 XHR 403 监控

## 迁移、发布与兼容

- 无代码发布；权限实时生效（无需重新登录）

## 可观测性

- curl 状态码 + 浏览器 403 拦截

## 回滚

- DELETE sys_role_menu WHERE role_id IN (9,11) AND menu_id=98

## 风险与盲区

- 低
