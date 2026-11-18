# DEF-EXTRADATA-001 — 审批提交 extraData 空串未规范化导致 500（P2，输入边界）

- 发现：2026-11-18 W3 运行时窗口（QC `01-claude-1c`），测试服新库 `aigicrm_prod_20261118`。
- 位置：`POST /api/approval/submit`（`ApprovalService.submit`）→ `biz_approval.extra_data`（MySQL JSON 列）。
- 事实：请求体 `extraData: ""`（空字符串）时未规范化，直插 JSON 列 → MySQL `Invalid JSON text: "The document is empty."` → `DataIntegrityViolationException` → 500「数据访问异常，支持编号 S-…」。
- 证据：support id `S-20261119050155-bd561`，堆栈 `### Error updating database. Cause: MysqlDataTruncation: Invalid JSON text: "The document is empty." at position 0 in value for column 'biz_approval.extra_data'`。
- 影响：正常客户端（PC/MP）传对象或合法 JSON/null 不触发；但空串属可预期输入（例如 claim 类型本身不需要 extraData 的客户端实现）→ 用户侧看到 500 而非明确业务提示。**非安全/数据损坏问题**（事务回滚，无脏写）。
- 修复建议：`submit` 入口把 `extraData` 空串/空白规范化为 `"{}"`；或校验后返回 400「参数不合法」。
- 验证口径：`extraData:""` 提交 → 200（规范化）或 400（校验）；不再出现 500。
- 归属：实施方（0b）；QC 复验。
- 状态：**FIXED（2026-11-18，实施 0b，待 QC 复验）**——`submit` 入口空串/空白规范化为 `{}`；新增 `ApprovalServiceTest.submit_blankExtraData_normalizedToEmptyJson_no500`；后端 1195/0、契约 191 PASS/0 FAIL。
