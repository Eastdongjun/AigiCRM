# UI 与交互规格

## 页面任务与信息层级

- 页面任务：激励查询页搜索（客户/关键字 + 高级筛选：产品小类、同事姓名、行业、开发类型、日期、组织）
- 主搜索框为第一层级入口；"高级筛选"按钮展开次层级筛选区
- 本次仅改主搜索框 placeholder 文案，其余不变

## 设计系统

- 不变：沿用现有 el-input/el-select/bar/adv-grid Token

## 组件与字段

- 主搜索框：placeholder="搜索客户名称 / 关键字（如：煤气管道）..."（REQ-001）
- 产品小类：大类 el-select + 小类 el-select（禁用联动），选项硬编码 productCats（REQ-002）
- 同事姓名：el-select filterable allow-create（REQ-003）
- 主搜索框数据：customerName + keyword 双参数（REQ-004）

## 状态矩阵

- 大类未选：小类 disabled；大类切换：小类清空

## 响应式与可访问性

- 双视口 1440x900 / 1280x720 验证；高级筛选区 adv-grid 三列自适应

## 视觉验证

- 修改前基线：`evidence/ui-baseline-1440x900.png`
- 最终截图：`evidence/ui-1440x900.png`
- 最终截图：`evidence/ui-1280x720.png`
- 结构化复核：`evidence/ui-review.json`
- 视觉评分: 90（仅文案改动，布局无变化）
- 最后一次视觉修改时间: 2026-08-01

## 不适用说明

（UI 任务，不适用本段）
