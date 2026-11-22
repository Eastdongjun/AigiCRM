# MP-PERMS-001 — normalizeFile 未透传行级 perms（P0）

- 位置：`aigicrm-miniprogram/pages/files/index.js:804-825`
- 修复：返回对象补 perms（Array.isArray(item.perms)?item.perms:String(item.perms
- 验收：|'').split(',').filter(Boolean)）；视图/按钮按行级 perms 判定|MP 文件页权限按钮与 PC 一致；Node/契约绿
- 归属：实施方（协调方 0b）；QC 复验
- 状态：OPEN（2026-11-18）
