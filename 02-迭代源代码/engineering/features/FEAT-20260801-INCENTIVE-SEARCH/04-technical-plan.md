# 技术实施计划

## 已确认影响范围

- 前端 aigicrm-ui/src/views/incentive/search.vue：仅主搜索框 placeholder 文案（REQ-001）
- 测试服部署：完整重装 aigicrm-frontend 容器 /usr/share/nginx/html（消除新旧 chunk 混装残留）
- 后端：零改动（customer-records 已支持 customerName/keyword/salesperson/product 等全部参数）

## 架构与关键决策

- 决策：三项搜索功能已实现（c61acb0），本次仅补入口提示 + 完整部署
- 决策：完整重装容器 html（rm 旧 + docker cp 新），不用增量覆盖——消除 `._` 元数据和残留 chunk 的隐患
- 部署方式：本地 npm build → zip → scp → 服务器解压 → docker cp；容器无 bind mount，直接替换

## 数据、API 与事件契约

- 无数据变更；API 参数：customerName/keyword 双匹配（OR）、product=related_products LIKE、salesperson=realName LIKE → user_id IN

## 权限与安全边界

- 仅测试服；旧版备份 /tmp/html-rollback/（容器内）；无凭据写入

## 实施顺序

1. 修改 search.vue placeholder
2. npm run build → 验证新 chunk（index hash 变化 + search chunk 含新文案）
3. scp 打包上传 → 服务器解压 → 备份容器旧版 → 清空 → docker cp
4. 回读验证（index.html 引用新入口 + search chunk 含文案）
5. 浏览器实测 AC-001~004

## 测试策略

- AC-001：placeholder DOM 断言
- AC-002：产品小类选"涂层类→煤气管道"→ 1 客户
- AC-003：同事姓名"王磊磊"→ 2 客户均标注王磊磊
- AC-004：关键字"煤气管道"→ 1 客户；客户名"陕煤集团榆林化学"→ 该客户

## 迁移、发布与兼容

- 测试服即时生效；生产同步需授权（相同构建产物）

## 可观测性

- 证据：evidence/ 截图（1440x900/1280x720）、部署回读输出、gate-report

## 回滚

- 容器回滚：docker exec cp /tmp/html-rollback/. /usr/share/nginx/html/（备份在容器内）

## 风险与盲区

- 低；用户浏览器需强刷（旧 chunk 缓存）——HANDOFF 已知项
