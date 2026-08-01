# 技术实施计划

## 已确认影响范围

- customer/list.vue:335

## 架构与关键决策

- 用现成 low-privilege 接口（customer/users/simple）替代 system/users——不改权限、不加权限

## 数据、API 与事件契约

- /api/customer/users/simple → data: [{id, realName}]

## 实施顺序

1. 改接口+适配 → 2. 构建部署 → 3. wangleilei 页面验证

## 测试策略

- AC-001：浏览器 XHR 403 监控

## 迁移、发布与兼容

- 无

## 回滚

- 容器 /tmp/html-rollback2/

## 风险与盲区

- 低
