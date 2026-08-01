# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 销售员 /statistics → /dashboard + 提示 |
| AC-002 | TEST-002 | PASS | NO001 /statistics → 正常进入 |
| AC-003 | TEST-003 | PASS | 销售员 /customer → 正常 |

## 静态与构建验证

- npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- /system/config 对销售员放行（有 system:config:view，只读）

## UI 验证

- 跳转+提示正常

## 回归验证

- 菜单正常角色跳转无影响

## 未覆盖与风险

- 生产未部署
