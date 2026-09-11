# 技术实施计划

## 已确认影响范围

- aigicrm-ui/src/views/system/config.vue：字典管理 Tab 模板（69-113 行区）+ dict-* 样式 + 图标 import（Files/Collection/PriceTag）

## 架构与关键决策

- 决策：纯前端视觉重构，零功能改动；卡片列表替代表格（信息密度低、更直观）
- 决策：操作按钮悬浮显示（hover/active 时显现）减少视觉噪音；关键操作（编辑/删除）仍在卡片内

## 数据、API 与事件契约

- 无 API 变化；handleTypeSelect/loadDictData 等函数原样复用

## 权限与安全边界

- 无；内置类型删除按钮 v-if="!row.builtIn" 保持

## 实施顺序

1. 模板重构 → 2. 样式重写 → 3. import 补图标 → 4. 构建部署 → 5. 浏览器验证（选中/加载/编辑弹窗/双视口截图）

## 测试策略

- AC-001：卡片列表 DOM 断言 + 选中态；AC-002：右侧头部/数据/空态；AC-003：编辑弹窗回归

## 迁移、发布与兼容

- 无；前端 hash 化部署

## 可观测性

- 基线+最终截图、ui-review.json

## 回滚

- 容器 /tmp/html-rollback2/

## 风险与盲区

- 低；原 el-table 的 current-change 逻辑已由点击事件替代（handleTypeSelect 复用）
