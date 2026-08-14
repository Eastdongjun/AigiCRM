# UI 与交互规格

## 页面任务与信息层级

- 上传弹窗：拖拽区（主）+ 待传列表（次）+ 底部操作（开始上传/取消）

## 设计系统

- 拖拽区虚线边框 #c0c4cc、hover 蓝色 #409eff + 浅蓝底 #f5f9ff；图标 44px；tip 灰字 12px
- 待传项：灰底圆角条 #f0f2f5，文件名左/大小右

## 组件与字段

- 自定义 div 拖拽区 + 隐藏 input[type=file]（multiple）
- 状态：pendingFiles（File[]）、uploading（bool）

## 状态矩阵

- 拖拽区 hover 蓝；上传中按钮 loading；空待传禁用

## 响应式与可访问性

- 弹窗 500px；待传列表 max-height 160px 滚动

## 视觉验证

- 修改前基线：evidence/ui-baseline-1440x900.png
- 最终截图：evidence/ui-1440x900.png
- 结构化复核：evidence/ui-review.json
- 视觉评分: 88
- 最后一次视觉修改时间: 2026-08-01

## 不适用说明

（UI 任务，不适用本段）
