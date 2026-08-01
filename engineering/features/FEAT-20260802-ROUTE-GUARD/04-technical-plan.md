# 技术实施计划

## 已确认影响范围

- router/index.js（10 个 meta.permission）
- layout/index.vue（onMounted 权限检查）

## 架构与关键决策

- 在 layout onMounted 的 fetchUserInfo 后检查（store 已加载，无时序误判）；不用全局 beforeEach（避免 store 未初始化问题）

## 数据、API 与事件契约

- 无 API 变化

## 权限与安全边界

- 前端拦截为体验层；接口 403 保留

## 实施顺序

1. meta.permission → 2. layout 检查 → 3. 构建部署 → 4. 三场景验证

## 测试策略

- AC-001~003：销售员/管理员/业务页验证

## 迁移、发布与兼容

- 无

## 可观测性

- 跳转断言

## 回滚

- 容器 /tmp/html-rollback2/

## 风险与盲区

- /system/config 对销售员放行（有 view 权限，只读）——符合权限语义
