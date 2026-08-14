# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 王磊磊菜单 DOM 断言：学分管理组仅"学分排行"，无"学分录入" |
| AC-002 | TEST-002 | PASS | admin 菜单含岗位管理（有 position:view） |

## 静态与构建验证

- npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- 权限码与菜单项映射核对：score:entry→学分录入、score:ranking→学分排行、system:position:view→岗位管理

## UI 验证

- 菜单渲染断言（无截图——ui_scope=none）

## 回归验证

- 王磊磊其余菜单（客户/文件/激励/提醒/公海/审批）完整显示

## 未覆盖与风险

- 直接 URL 访问 /score/entry 仍 403（接口保护，预期行为）；生产未部署
