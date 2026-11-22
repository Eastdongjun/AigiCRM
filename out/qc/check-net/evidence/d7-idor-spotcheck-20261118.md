# D7 安全 — IDOR 对象级权限抽查（2026-11-18，HEAD 080a95f7）

> 代码级抽查；未做全遍历（端到端全遍历需窗口+账号矩阵）。

## 一、抽查结果（4/4 有对象级/范围校验）

| 端点 | 校验 | 结果 |
|---|---|---|
| `GET /api/customer/{id}` | `accessService.assertCanView(id)`（`CustomerController:166`） | **PASS** |
| `GET /api/approval/{id}` | `approvalService.detail(id, userId)` → `canViewApproval(approval, userId)`（`ApprovalService:230`） | **PASS** |
| `GET /api/file/{id}/versions/{v}/download` 及下载路径 | `@PreAuthorize('file:download')` + `checkFile download`（`FileController:141-144`，注释明示） | **PASS** |
| 旧 `GET /api/customer/rankings`（历史越权面） | **已删除**（P0-2 2026-11-12 外部审计 HIGH 退役；双端零引用） | **PASS（已消除）** |

## 二、现行排行入口

- `/statistics/customer-rankings`：`applyDataScope` 收口，PC（`api/statistics.js:28`）与 MP（`api/statistics.js:28`、`pages/ranking/index.js`）均调用此端点——**无越权面**。

## 三、边界（诚实）

- 本抽查只覆盖 4 个代表端点；**未做全端点 IDOR 遍历**（需窗口+多账号，属 W3 后续）。
- 上传路径穿越：已由 `sanitizeFilename`（去掉目录/`..`/控制字符）+ 白名单（`ALLOWED_UPLOAD_EXTS`）覆盖（前期已核）。
