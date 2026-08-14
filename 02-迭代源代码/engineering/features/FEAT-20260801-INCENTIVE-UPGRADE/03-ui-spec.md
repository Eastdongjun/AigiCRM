# UI 与交互规格

## 页面任务与信息层级

- **激励上传页（index.vue）**：任务=按品类上传/追加激励文件。层级：四区 Tab → 子项列表（左）→ 记录表格（右）；弹窗：基本信息 → 安装详情 → 安装文件 → 跟踪记录(trial) → 共享
- **激励查询页（search.vue）**：任务=多维度检索客户激励。主搜索框 → 高级筛选 → 卡片网格 → 详情时间线
- **客户详情页（detail.vue）**：任务=查看客户全貌。激励 Tab 内记录按四区排序展示

## 设计系统

沿用现有体系：主色 #409eff、四区 Tab 色（visit #f59e0b / exchange #3b82f6 / plan #8b5cf6 / trial #10b981）、卡片/表格白底 #fff、边框 #e8ecf1、圆角 10-12px、字号 12-14px。不引入新 Token。

## 组件与字段

| 组件 | 字段/交互 | 校验 | 数据来源 |
|---|---|---|---|
| 上传弹窗-相关产品 | 两级 el-select（大类→小类），提交"大类-小类" | 大类必选才可选小类 | productCats 常量（交接文件选项表） |
| 上传弹窗-安装详情 | visit/exchange: "参观交流详情描述"；plan: "文件详情描述"；trial: "安装前问题"（均绑 preInstallQuestion）+ 安装备注 | 无 | 品类 code 分支 |
| 上传弹窗-安装文件 | visit/exchange/plan: 单槽（标签"现场照片"/"安装文件"）1~5，存 filePreId | 单槽 ≥1 才能提交 | pendingChild 品类 code |
| 上传弹窗-跟踪记录 | visit/exchange/plan 隐藏；trial 显示 | 无 | 品类 code |
| 查询-产品小类 | 高级筛选新增级联下拉（同 productCats 选项） | 无 | 需求1选项表 |
| 查询-同事姓名 | 高级筛选新增输入框（模糊） | 无 | /incentive/users |
| 查询-主搜索框 | 客户名或关键字（relatedProducts like） | 无 | 后端 keyword 参数 |

## 状态矩阵

- 小类下拉：大类未选 → disabled；选中大类 → 启用
- 单槽文件：0 个 → 提交禁用；5/5 → 添加按钮消失
- 卡片/表格：空 → "暂无数据"；加载沿用现有
- 详情"最近更新"：取 createdAt 最新（与排序解耦）

## 响应式与可访问性

- 1280px 视口下弹窗 95vw（现有 @media 规则）；卡片网格 4 列→自动收缩
- 现有键盘可达性不变；新增控件均为标准 el-select/el-input

## 视觉验证

- 修改前基线：`evidence/ui-baseline-1440x900.png` ✓（已保存）
- 最终截图：`evidence/ui-1440x900.png`（实现后保存）
- 最终截图：`evidence/ui-1280x720.png`（实现后保存）
- 结构化复核：`evidence/ui-review.json`（实现后执行）
- 视觉评分: 88（基线；最终以 record-ui-review 绑定实现后截图复核为准）
- 最后一次视觉修改时间: 2026-08-01（基线）

## 不适用说明

（本段删除：UI 任务）
