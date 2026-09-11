# 功能受理

## 用户目标
客户详情页「四项激励」Tab 下点击「安装前/安装中/安装后」阶段文件,可正常打开/下载文件,不再弹出 400 错误页。

## 当前行为
- 2026-08-01 测试环境(111.229.37.220)实测:
  - 点击「安装前」(filePreId='[149,165]')→ 打开 `/api/file/download/[149,165]` → HTTP 400
  - 点击「安装中」(filePreId='[152]')→ 打开 `/api/file/download/[152]` → HTTP 400
- 根因:customer/detail.vue:477 previewIncentiveFile() 直接拼接 getFiles() 产出的原始 id;getFiles() 未做 parseId(JSON 数组字符串 → 单个数字)
- 对照:incentive/search.vue、incentive/customer.vue 均含 parseId() 且正常

## 范围
- aigicrm-ui/src/views/customer/detail.vue:增加 parseId() 并在 getFiles() 中对 file_pre/mid/post_id 与 file_id 应用
- 前端构建、部署测试环境(111.229.37.220)、浏览器回归验证

## 不在范围
- 后端与数据库变更
- 生产环境部署(需用户另行授权)
- 多文件阶段一次下载全部文件(沿用既有约定:取第一个)

## 来源与事实
- 用户反馈(2026-08-01)
- 浏览器实测:3 个 400 标签页 URL 均为 `/api/file/download/[id数组]`
- 源码对比:search.vue:191-198、customer.vue:90-96 的 parseId 用法

## 约束
- parseId 实现与 search.vue/customer.vue 完全一致,不做新设计
- 部署前端不执行 `rm -rf dist`(bind mount 会断开;用构建覆盖)
- 不把任何凭据写入命令参数

## 初始风险
- 低:部署后浏览器缓存旧前端,需硬刷新验证
- 低:多文件阶段仅打开第一个文件为既有约定,若用户期望全部文件需另议
- 无不可逆操作
