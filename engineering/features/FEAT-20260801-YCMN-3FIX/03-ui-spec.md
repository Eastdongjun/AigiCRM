# UI 与交互规格

## 页面任务与信息层级

- 任务：激励上传/追加弹窗内选择安装文件（单槽或三槽），点击"+"卡片弹出系统文件选择框。
- 层级：基本信息（客户/行业/产品）→ 安装详情 → 安装文件（核心操作区）→ 跟踪记录 → 共享 → 底部操作按钮。
- 修复点：弹窗整体高度受控（96vh 内），header/footer 固定，body 独立滚动，文件槽位首屏可见，卡片不被底部按钮覆盖。

## 设计系统

- 沿用现有：卡片 88x100px、虚线边框 #dcdfe6、圆角 10px、hover #409eff 描边；弹窗 780px 宽 top 2vh。
- 新增/调整：`.upload-dlg :deep(.el-dialog)` 高度约束（height:96vh; max-height:96vh; display:flex; flex-direction:column; margin-top:2vh）；`.dlg-body` 移除 max-height 62vh 双滚动，由 el-dialog__body 统一滚动。

## 组件与字段

- 组件：el-dialog（弹窗）、隐藏 input[type=file]（保持 opacity:0 方案）、file-card 卡片。
- 校验：不变（文件槽位至少 1 个才能提交的 canSubmit 逻辑不动）。
- 交互：点击"+" → pickFile → input.click()；user activation 下浏览器弹出文件选择框。

## 状态矩阵

- 卡片 default：虚线边框；hover：蓝色描边；has：实线绿边（已有文件）；add：虚线 + "+" 图标。
- 弹窗：打开时无滚动条跳动（body 滚动条只在需要时出现）。

## 响应式与可访问性

- 1440x900：弹窗高 ≤96vh，文件槽位首屏可见。
- 1280x720：同上，body 内部滚动可用。
- 键盘/焦点：保持原有（未新增交互元素）。

## 视觉验证

- 修改前基线：`evidence/ui-baseline-1440x900.png`（修复前弹窗，卡片被底部按钮覆盖）
- 最终截图：`evidence/ui-1440x900.png`
- 最终截图：`evidence/ui-1280x720.png`
- 结构化复核：`evidence/ui-review.json`
- 视觉评分: 90
- 最后一次视觉修改时间: 2026-08-01 17:40

## 不适用说明

（UI 任务，无此项）
