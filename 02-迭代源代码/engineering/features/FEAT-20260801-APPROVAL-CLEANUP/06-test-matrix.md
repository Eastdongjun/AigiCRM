# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | SQL 回读：biz_approval_config 仅剩 id=2 捞取客户审批/claim（DELETE 后 SELECT 输出） |
| AC-002 | TEST-002 | PASS | 浏览器系统设置→审批配置：表格仅一行"捞取客户审批 claim 指定人员 非会签 11 0"（DOM 快照） |

## 静态与构建验证

- 无代码变更，不适用

## 功能与集成验证

- 引用检查：biz_approval_flow 4 条（claim/transfer/extend/storage）无乱码 flow_type 引用

## UI 验证

- 页面快照确认无乱码条目（Console 1 error 为既有 logo.jpg 404）

## 回归验证

- "捞取客户审批/claim" 配置完整保留（approver [11]）

## 未覆盖与风险

- 生产未清理（未要求）
