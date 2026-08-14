# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 安装前槽两次选择：第二次后 upload-t… 与 ui-1440x… 共存（修复前只剩后者） |

## 静态与构建验证

- npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- 提交链路（file/upload ×2 → records 创建 file_pre_id=[id1,id2]）此前已验证

## UI 验证

- 槽位卡片计数正常（2 文件 + 添加卡）

## 回归验证

- 一次多选（2 文件同时选）仍正常

## 未覆盖与风险

- 生产未部署
