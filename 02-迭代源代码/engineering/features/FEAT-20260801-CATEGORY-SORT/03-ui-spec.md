# UI 与交互规格

## 页面任务与信息层级

- 页面任务：激励上传页四区（顶层类别）Tab 与分组的顺序调整为 产品试点→两谈两做方案→交流拜访→外出参观
- 无样式/形态变化，仅顺序变化；默认选中 Tab 变为"产品试点"（parentCategories[0]）

## 设计系统

- 不变：沿用现有 cat-tab/分组样式 Token，无新增

## 组件与字段

- index.vue 顶部 Tab（v-for="cat in parentCategories"，:6）：顺序随 getCategoryTree 返回序自动更新
- 上传弹窗"选择子项上传文件"分组（:44-63）：顺序随 tree 更新
- config.vue 类别管理一级卡片分组（:155）：顺序随 tree 更新
- 字段：无新增/修改

## 状态矩阵

- 不变：default/active 沿用现有样式

## 响应式与可访问性

- 不变：无新增元素

## 视觉验证

- 修改前基线：`evidence/ui-baseline-1440x900.png`
- 最终截图：`evidence/ui-1440x900.png`
- 最终截图：`evidence/ui-1280x720.png`
- 结构化复核：`evidence/ui-review.json`
- 视觉评分: 90（无样式改动，顺序符合用户指定）
- 最后一次视觉修改时间: 2026-08-01

## 不适用说明

（UI 任务，不适用本段）
