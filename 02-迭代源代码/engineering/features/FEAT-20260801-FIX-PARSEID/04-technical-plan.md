# 技术方案

## 已确认影响范围
- 仅前端文件:ycmn-ui/src/views/customer/detail.vue(474 行 getFiles + 477 行 previewIncentiveFile)
- 附带工程缺陷修复:ycmn-ui/vitest.setup.js(localStorage stub)
- 后端、数据库、其他页面零影响

## 架构与关键决策
- 决策:parseId 实现与 incentive/search.vue:191、incentive/customer.vue:90 完全一致,不做新设计
- JSON 数组字符串 '[152]' → 152;'[149,165]' → 149(取第一个,既有约定)

## 数据、API 与事件契约
- 数据:file_pre_id/file_mid_id/file_post_id/file_id 为 JSON 数组字符串
- API:/api/file/download/{id}?token={jwt},id 必须为数字

## 权限与安全边界
- 下载鉴权不变(token 参数);无新增接口、无权限变更

## 实施顺序
1. detail.vue 加 parseId + getFiles 应用 → 2. npm run build → 3. 部署测试环境(docker cp)→ 4. 浏览器验证

## 迁移、发布与兼容
- 无数据库迁移;前端构建产物向后兼容

## 可观测性
- 浏览器控制台 0 错误;下载 URL 正确性由点击行为验证

## 回滚
- 旧 commit 9abe6fa 重新构建部署;或从生产镜像取回旧前端

## 风险与盲区
- 生产未部署(待授权);多文件仅取第一个(既有约定);截图工具会话内曾故障(已恢复)

## 测试策略
- npm test(vitest 8 用例)
- 浏览器:单/多文件点击 200;回归 3 页
