# UI 与交互规格

## 页面任务与信息层级

- 唯一任务：维护字典类型与数据。左侧类型（第一层级）→ 右侧数据（第二层级）
- 阅读顺序：类型卡片（名称→编码→操作）→ 数据表格（标签→值→排序→状态→操作）

## 设计系统

- 白卡片 #fff / 圆角 10px / 阴影 0 1px 3px rgba(0,0,0,.04)；主色 #409eff
- 选中态：蓝边+浅蓝底 #ecf5ff；悬浮：浅蓝 #f5f9ff；操作按钮悬浮浮现（opacity 0→1）
- 编码等宽字体 ui-monospace；图标 Collection（类型）/PriceTag（数据）/Files（头部）

## 组件与字段

- 左：dict-type-item 卡片（名称粗体 13px + 内置 tag + 编码 11px 灰 + 操作行）
- 右：el-table stripe + 头部（名称+条数 tag success + 新增按钮）；el-empty 空态

## 状态矩阵

- 卡片 hover：边框 #b3d3f8 + 浅蓝底；active：蓝边+浅蓝底+图标深蓝；操作行默认透明、hover/active 显现

## 响应式与可访问性

- 1440x900 / 1280x720 双视口验证；左栏固定 380px，右栏自适应

## 视觉验证

- 修改前基线：evidence/ui-baseline-1440x900.png
- 最终截图：evidence/ui-1440x900.png、ui-1280x720.png
- 结构化复核：evidence/ui-review.json
- 视觉评分: 90
- 最后一次视觉修改时间: 2026-08-01

## 不适用说明

（UI 任务，不适用本段）
