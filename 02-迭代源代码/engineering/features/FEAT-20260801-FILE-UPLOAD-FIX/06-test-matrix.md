# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | curl：folderId=null→400、folderId=空→200、无 folderId→200；自定义上传请求不含 folderId |
| AC-002 | TEST-002 | PASS | 浏览器真实 fileChooser 上传：提示"上传成功 1 个文件"、弹窗 overlay display:none、列表新增 bu-upload-test.txt |
| AC-003 | TEST-003 | PASS | 失败路径：uploadOne 抛错→failedNames 收集→提示成功/失败明细（代码审查 + 部分成功实测） |
| AC-004 | TEST-004 | PASS | DataTransfer 注入多文件：待传列表显示 N 项、逐个上传成功 |

## 静态与构建验证

- npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- scope 随 Tab（public/private）传递正确；容量/统计刷新（loadStorage 调用）

## UI 验证

- 1440x900 截图；console 仅既有 logo.jpg 404

## 回归验证

- 原上传入口（上传按钮/拖拽/多选）交互保持

## 未覆盖与风险

- 多文件部分失败未浏览器实测；生产未部署
