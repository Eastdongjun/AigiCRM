# 技术实施计划

## 已确认影响范围

- ycmn-ui/src/views/file/index.vue：上传弹窗模板 + script（自定义上传）

## 架构与关键决策

- 决策：弃用 el-upload 自动上传（其 on-success 在此环境不触发，getFile 拦截）；改用自定义 XHR（request.post 复用激励页验证过的模式）——完全可控、错误明细清晰
- 决策：folderId null 不携带（后端 Long 参数收到 "null" 字符串 400）

## 数据、API 与事件契约

- POST /api/file/upload：multipart（file + scope + [folderId]）

## 权限与安全边界

- 无；token 由 request 拦截器自动携带

## 实施顺序

1. 模板换自定义拖拽区 → 2. script 加 pendingFiles/uploadOne/startUploads → 3. 样式 → 4. 构建部署 → 5. 浏览器验证（含真实 fileChooser）

## 测试策略

- AC-001：请求参数断言（无 folderId）；AC-002：成功提示/关弹窗/列表刷新；AC-003：失败路径代码审查+成功路径实测；AC-004：多文件

## 迁移、发布与兼容

- 无；前端 hash 部署

## 可观测性

- 证据截图 + 请求断言

## 回滚

- 容器 /tmp/html-rollback2/

## 风险与盲区

- 低；多文件部分失败场景未浏览器实测（逻辑已覆盖）
