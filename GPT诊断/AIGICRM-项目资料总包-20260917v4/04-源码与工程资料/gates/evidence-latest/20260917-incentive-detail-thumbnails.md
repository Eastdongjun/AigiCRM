# 20260917 incentive detail thumbnails （详情页自动缩略图）

## Goal

用户要求：打开激励详情页即能看到图片预览（缩略图），不用逐个点「打开」。

## Design

复用后端现成的缩略图能力，无需后端改动：

- `GET /api/file/download/{id}?thumbnail=1&token=<download-token>`
- 短时下载 token 由 `GET /api/file/download-token` 签发（1h，scope=download，
  专为 `<image>` 无法带 header 设计，见 JwtAuthFilter）。
- 仅图片类型（`file.type === 'image'`）加载缩略图；视频/文档保持图标入口。
- demo 模式不加载（演示附件无后端）。失败走共享 `showError`，页面主体不受影响。
- 未新增 `wx.downloadFile` 调用：缩略图走 `<image>` 渲染层加载，
  自动机精确计数（detail downloadFile===3 / previewImage===1 / openDocument===1 /
  saveFile===1）不受影响。

## Changes （本轮新增）

- `pages/incentive/detail/index.js`：引入 `getDownloadToken`；`setRecord` 后调
  `loadThumbnails()`：有图片附件时取一次 token，为每个图片拼 `thumbUrl` 后
  `setDetailData({fileGroups})` 回流（经 `buildFileGroupsView` 透传，`...file` 保留字段）。
- `pages/incentive/detail/index.wxml`：有 `thumbUrl` 时渲染 `<image mode="aspectFill"
  bindtap="previewAttachment">`（点图即全屏预览），否则回退原图标。
- `pages/incentive/detail/index.wxss`：新增 `.attachment-thumb` 76rpx 圆角。
- 契约字符串、绑定、handler、class 名（`attachment-row` 等）全部保留。

## Verification

- `node --check`：通过。
- `git diff --check`（detail 目录）：通过。
- `check-mobile-incentive-contract.sh`：PASS。
- `check-mobile-feedback-contract.sh`：PASS（缩略图失败走 showError）。
- `dev-verify.sh miniprogram`：全部 PASS（46 pages；71 JS, 48 JSON, 82 WXML/WXSS）。
- 端到端：admin 取 download-token → 无 header 请求
  `/api/file/download/1095?thumbnail=1&token=…` → 200，1173B，image/png（原图 2780B）。
- 开发者工具 CLI preview（`pages/incentive/detail/index?id=336`）：构建成功 801.8 KB。

## Status

详情页自动缩略图完成。验证记录 id=336（含安装前/中两张图）是最佳目检对象。
同方案可复用到列表页/时间轴页（未做，等用户确认后再铺）。
