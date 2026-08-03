# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | bash scripts/check-frontend-quality.sh → 0 违规,退出 0 |
| AC-002 | TEST-002 | PASS | 注入 size:9999 临时文件 → 检出违规,退出非 0 |
| AC-003 | TEST-003 | PASS | bash scripts/check-prod-perf.sh https://www.aigicrm.com → gzip/no-cache/4安全头/耗时全 PASS |

## 静态与构建验证

- bash -n 两个脚本语法通过;前端 npm run build 成功(commit 3be1329)

## 功能与集成验证

- pre-delivery-check.sh 完整运行:[5] 前端质量检查生效并 PASS
- 门禁首跑抓到 config.vue:600、incentive/index.vue:633 两处现存 size:9999(已修复为 500)

## UI 验证

不适用(ui_scope=none):工程门禁脚本,无界面变化。

## 回归验证

- 前端修复(size:500)后构建部署,首页 200、登录 200

## 未覆盖与风险

- 扫描规则保守,可能漏网(按新问题补充规则)
- 浏览器 E2E 未纳入(现有 smoke-test.sh API 冒烟保留)
