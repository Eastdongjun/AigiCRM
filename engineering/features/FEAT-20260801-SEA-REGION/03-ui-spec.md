# UI 与交互规格

## 页面任务与信息层级

- 公海池弹窗：名称 → 可见范围（组织树 + 已选标签）→ 其他设置
- 组织架构页：类型 tag（中文）+ 组织名

## 设计系统

- 沿用现有 Token；新增 .tree-card/.tree-card-header/.tree-card-tip 样式（边框卡片+提示）

## 组件与字段

- el-tree check-strictly：key d_<id>/u_<id>，disabled 仅总部
- 已选范围：el-tag 列表（部门 warning 色/人员 primary 色），可关闭删除、清空按钮
- org.vue 类型：el-tag type 1 danger/2 warning/3 primary/4 info + 中文文本

## 状态矩阵

- 树勾选 → 标签同步；移除标签 → 树取消勾选

## 响应式与可访问性

- 弹窗 700px 双栏（树 14/24 + 已选 10/24）

## 视觉验证

- 修改前基线：evidence/ui-baseline-1440x900.png
- 最终截图：evidence/ui-1440x900.png（组织架构）、evidence/ui-sea-pool.png（公海池弹窗）
- 结构化复核：evidence/ui-review.json
- 视觉评分: 90
- 最后一次视觉修改时间: 2026-08-01

## 不适用说明

（UI 任务，不适用本段）
