# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 样式断言：bg #fff、borderTop 3px #409eff、radius 12px、6 字段卡、标题行"筛选条件 清空筛选" |
| AC-002 | TEST-002 | PASS | 选行业化工 → "1 项已选"tag、清空激活、结果 4 客户 |

## 静态与构建验证

- npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- 筛选结果正确（化工 4 客户）；清空重置

## UI 验证

- 双视口截图；console 仅既有 logo 404（页面 6 个历史 error 为 file 页测试残留）

## 回归验证

- 主搜索框/查询/重置正常

## 未覆盖与风险

- 生产未部署
