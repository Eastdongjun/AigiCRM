# 迁移后复跑报告（测试库）

> 2026-11-18 ｜ 测试库 `aigicrm_prod_20261016` @ 111.229.37.220 ｜ **生产未触碰**
> 迁移执行：Flyway 9.22.3-alpine，12 个 pending 全部应用成功 → `2026.11.19.2`
> 迁移前备份：`~/aigicrm-db-backup-20261118-pre-migration.sql`（12M，md5 `5b619e53a74eae9ade68a84086d73011`）

## 一、迁移结果

- Flyway history 最新 `2026.11.19.2`，12 条 success=1。
- 新表全部在位：`biz_file_grant`、`biz_file_link`、`biz_lifecycle_request/item`、`biz_upload_session`、`biz_recycle_bin`、`biz_recycle_export`。
- `biz_customer.name_norm/dup_exempt/...`、`biz_file.md5`、`biz_incentive_record.deleted...`、`biz_reminder.deleted...` 等列已存在。
- 仅 VALUES() 弃用告警，无失败、无 checksum mismatch。

## 二、原 500 模块复跑（admin，GET）

| 模块 | 端点 | 结果 |
|---|---|---|
| 客户 | `/api/customer/list` | **200**，total 294 |
| 文件 | `/api/file/list?scope=public/private/fabushi/incentive` | **200**，61 / 1 / 17 / 2550 |
| 文件 | `/api/file/grants` | **200**，178 |
| 文件 | `/api/file/reconciliation` | **200**，2632/2632，**missing 0**，0.0% |
| 激励 | `/api/incentive/records` | **200**，534 |
| 激励 | `/api/incentive/customer-records` | **200**，117 |
| 统计 | `/api/statistics/dashboard` | **200** |
| 统计 | `/api/statistics/customer-rankings` | **200** |
| 提醒 | `/api/reminder/list` | **200**，158 |
| 公海 | `/api/sea/list` | **200**，32 |
| 审批 | `/api/approval/list` | **200**，55 |
| 任务 | `/api/task/list` | **200**，0 |
| 法布施 | posts / books | **200**，1 / 2 |
| 生命周期 | `/api/system/lifecycle/requests` | **200**，0 |
| 回收站 | customers/users/tasks/reminders/incentives/files | **200**，297/41/0/0/0/13 |
| 回收导出 | `/api/system/recycle/exports` | **200**，0 |
| 积分 | `/api/score/grid?period=2026` | **200** |

回收站 API 计数与 DB deleted=1 计数逐项一致：客户 297、用户 41、任务 0、提醒 0、激励 0、文件 13。

## 三、客户条目级（ID 集合）复跑

- **28 用户全量**：oracle 最终集合 vs 列表全量 ID。
- **27/28 完全一致**：extra=0、missing=0、owner_id/dept_id/status 字段不一致=0。
- **uid 41 TY005**：oracle=17、API=17（一致），但**配置 office 集合 121 中少看 104** → DEF-1 确认，P0 业务缺陷。
- 文件：`evidence/customer-idset-compare.tsv`、`customer-idset-summary.md`。

## 四、新发现问题

### DEF-9（P1）文件列表隐藏 3 个公开文件（admin）
- DB `biz_file` deleted=0 & scope=public = **64**；`GET /api/file/list?scope=public` 只返回 **61**。
- 缺失 ID：**33、68、928**。
  - 33/68：`folder_id=0`，无 id=0 文件夹（历史异常）；uploader=1。
  - 928：`folder_id=NULL`，uploader=62（TY200）。
- admin 的 file view 范围是 all（role_perm_scope id 172）。代码 `FileService.pageFiles` 的逐文件过滤在 `upSet == null`（全部上传人）时，仍因 `orgAllowed/grantCovered` 非空而进入限制分支（`FileService.java:324`），把“无夹 + 他人上传”的公开文件排除；33/68 则被 folder_id=0 的前置条件排除。
- 影响：管理员/全量文件权限用户看不到部分公开文件；需修 filter 语义或修 folder 0 数据。

### 信息项
- **文件 md5 未回填**：`biz_file` deleted=0 共 2632 条，md5 全为 NULL。迁移只加列；若上传去重/校验依赖 md5，存量文件需回填脚本。
- **H4 测试环境卷缺失不再复现**：reconcile 2632/2632、missing 0（与 10-12 报告不同，卷/数据已恢复或口径变化）。

## 五、迁移后 SQL 完整性抽查

| 检查 | 结果 |
|---|---|
| `biz_file_grant` 主体/文件夹孤儿 | 0 / 0 |
| `biz_file_grant` 严格重复（全字段键） | 0（按宽键的 27 组差异在 scope_mode/org/level/source，非真重复） |
| `biz_customer.name_norm` 空值 | 0 |
| 同名组 dup_exempt 标记异常 | 0 |
| `active_name` 重复 | 0（唯一索引生效） |
| 激励/提醒 deleted=1 | 0 / 0 |
| 任务 deleted=1 且 deleted_at 空 | 0 |
| 生命周期/上传会话/回收表 | 空（0 行） |

## 六、结论与下一步

1. **原 500 模块全部恢复 200**；回收站/生命周期/回收导出新模块可用。
2. **客户条目级 28 用户完成**：仅 TY005 配置-生效差 104（DEF-1 P0）。
3. **新发现 DEF-9（P1）**：文件公开列表隐藏 3 条（33/68/928）；修复前不要把这 3 条当“已核”。
4. 待修复/确认：DEF-1 收窄逻辑；DEF-9 文件过滤/ folder 0；DEF-3 进度写入校验；DEF-6/7/8 数据卫生；md5 回填策略。
5. 生产仍零写入；迁移仅测试库，生产等用户命令。
