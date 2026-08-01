# 技术实施计划

## 已确认影响范围

- index.vue handleFileSelected（安装槽分支 1 行）

## 架构与关键决策

- 与 track 分支对齐：保留全部已有文件再拼接

## 数据、API 与事件契约

- 无 API 变化

## 权限与安全边界

- 无

## 实施顺序

1. 改 handleFileSelected → 2. 构建部署 → 3. 两次选择实测

## 测试策略

- AC-001：两次 fileChooser 注入验证共存

## 迁移、发布与兼容

- 无

## 可观测性

- 槽位 DOM 断言

## 回滚

- 容器 /tmp/html-rollback2/

## 风险与盲区

- 低
