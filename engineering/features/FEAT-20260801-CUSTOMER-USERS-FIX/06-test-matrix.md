# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | wangleilei 6 页（客户列表/新增/激励/公海/文件/提醒）0 个 403 |

## 静态与构建验证

- npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- simple 接口返回数组适配正确；负责人下拉正常

## UI 验证

- 页面正常（ui_scope=none）

## 回归验证

- admin/行政页面不受影响（simple 接口 customer:view 全角色有）

## 未覆盖与风险

- statistics/analysis 403 为预期（菜单无页面）；生产未部署
