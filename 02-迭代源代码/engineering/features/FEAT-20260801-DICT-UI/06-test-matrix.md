# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | DOM 断言：dict-type-item 11 项；点击"开发类型"后 active 高亮（class 含 active） |
| AC-002 | TEST-002 | PASS | 选中后右侧头部"开发类型 · 数据列表 3 项"+3 行数据；未选时显示"从左侧选择字典类型查看数据"空态 |
| AC-003 | TEST-003 | PASS | 卡片"编辑"按钮点击 → "编辑字典类型"弹窗正常打开（名称/编码/备注字段） |

## 静态与构建验证

- npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- 数据加载（loadDictData）原逻辑复用，选中即加载；新增按钮禁用态随 currentType

## UI 验证

- 双视口截图 1440x900/1280x720；console 仅既有 logo.jpg 404

## 回归验证

- 编辑弹窗字段回显正常；内置类型无删除按钮（v-if 保持）

## 未覆盖与风险

- 生产未部署
