# MP-DEADAPI-001 — 残留已删除端点包装（P0）

- 位置：`aigicrm-miniprogram/api/customer.js:45-48,87-90`
- 修复：删除 addInvoice/deleteInvoice/addPayment/deletePayment 包装与导出；grep 零残留
- 验收：后端无这些端点；MP 构建/契约绿
- 归属：实施方（协调方 0b）；QC 复验
- 状态：OPEN（2026-11-18）
