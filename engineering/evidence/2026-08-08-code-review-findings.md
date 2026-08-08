# 代码问题核查处置记录（2026-08-08）

> 来源：Claude 代码审查（对照 7-31 双审报告 + TECH_DEBT.md）。本文件记录新发现 N1-N3 的处置状态。

## 处置总览

| 项 | 发现 | 决策 | 状态 |
|---|---|---|---|
| N1-A | 生产 MySQL root 密码明文硬编码，已入 git 历史 | 密码移出 git 文件，改经 `~/.ycmn-deploy.env` 注入 | ✅ 完成，commit `084ac65` |
| N1-B | root 密码泄露 + 应用用 root 连库 | 最小权限账号 + 轮换 root（**执行前单独授权**） | 📋 规划完成，见 `docs/superpowers/plans/2026-08-08-db-password-rotation.md` |
| N2 | 租户隔离默认关，无环境开启 | **单集团部署，不启用**（用户确认） | ✅ 决策记录于设计记忆 |
| N3 | 从未开票客户 NULL→9999 天超期误报 | 独立 `invoice_never` 提醒，不进超期 | ✅ 代码完成，commit `9a92e9c`（部署待确认） |

## N3 补充事实（供后续 UI 接入）

- `invoice_overdue` / `invoice_never` 提醒**当前在 UI 无展示位**：提醒页只展示分享+保护期到期；dashboard 的 `overdueDays>300` extra 因 expiringPage 不返回该字段为死代码。
- 生产 249 启用客户中 40 个从未开票（`invoice_never` 量级 ~40 条）。
- **待办（未做，需确认）**：将 invoice 两类提醒接入提醒页（`_type:'invoice'` 标签 + 提示文案 + processReminder 已读），否则提醒只落库不展示。
- **数据清理（未做，需确认）**：历史 `biz_reminder` 中 `invoice_overdue` 且 `overdue_days>=9999` 的存量行需迁移为 `invoice_never`/overdue_days=0，避免与新提醒并存。

## 7-31 报告核对

- B1/B3/B5/H1-H6/H8/H9/H11/M9/M12：已修复（见审查报告第三节，已逐一验证）
- B2/B6、H7/H10/H12、M1-M3/M5/M6/M8：未逐条深挖，建议下轮核查
- M7/M10/M11：技术债保留，按 TECH_DEBT.md 节奏推进
