# SQL-only 全模块逐查 · 最终报告

> 2026-11-18 ｜ 测试库只读；不调 API；生产零写入
> 执行：`scripts/sql_only_checks.py`（100 项）+ `scripts/schema_drift.py`（12 个未应用迁移）
> 结果：**100 项：86 通过 / 4 发现 / 0 SQL 错误 / 10 信息项**

## 一、模块逐查状态

| 模块 | SQL-only 结果 | 发现 | API 现状 |
|---|---|---|---|
| 认证/用户 | 9 项通过 | 信息：83 条过期 token 黑名单；0 强制改密 | 正常（auth/info 200） |
| 组织 | 5 项通过 | 停用部门 40 缺闭包行，按停用语义排除 | 正常 |
| 权限 | 9 项：8 通过 | **PERM-01（P2）** 3 条临时角色残留范围行 | PermCenter preview 200 |
| 客户 | 16 项通过 | 信息：3 组同名活跃客户（迁移前存量） | 列表 500（缺 `name_norm`），计数正常 |
| 公海 | 4 项通过 | — | 列表 500（`name_norm`） |
| 审批 | 7 项通过 | 自审自批样本 0 | 列表 500（`name_norm`） |
| 文件 | 11 项：9 通过 | **FIL-01（P1）** 2 个文件 folder_id=0；**FIL-10（P2）** 3 组重复 oss_key | list 500（缺 `md5`）；grants/reconcile 缺表/列 |
| 激励 | 9 项通过 | 信息：91 组业务键重复（待业务确认） | 列表 500（缺 `deleted` 审计列） |
| 积分 | 5 项通过 | — | grid 需 period 参数 |
| 提醒 | 5 项通过 | 信息：24 条负 overdue_days（未到期天数语义） | 列表 500（缺 `deleted`） |
| 任务 | 3 项通过 | 当前 0 条 | 正常 |
| 表单草稿 | 3 项通过 | — | 未单测 |
| 法布施 | 11 项：10 通过 | **FAB-01（P2）** 42 条进度孤儿（book 999999） | 读接口正常；权限/统计已抽查通过 |
| 生命周期/交接 | **SCHEMA_MISSING** | 缺 `biz_lifecycle_request/item` | 未探测（表缺失） |
| 回收站/导出 | **SCHEMA_MISSING** | 缺 `biz_recycle_bin/export` | file.recycle 200（空） |
| 统计 | 1 项通过 | STA-01 全量客户数 294，与计数接口一致 | dashboard/rank 500（`name_norm`） |
| 系统管理 | sys_* 检查通过 | — | 正常 |
| 权限中心 | 与权限合并 | 见 PERM-01 | 正常 |

## 二、4 个发现（附样本）

### FIL-01（P1）文件引用不存在文件夹
- 文件 id **33、68**，`folder_id=0`，`file_scope=public`，上传人 1，创建于 2026-06-25 / 07-29。
- `biz_file_folder` 无 id=0 的文件夹；服务端把 `NULL` 当作根/无夹，`0` 不是合法 folder id，可能导致这 2 个文件不可见。
- 注：33/68 同时是 FIL-10 的重复 oss_key 一组，疑似历史重复上传。

### FIL-10（P2）活跃文件 oss_key 重复
- 3 组：文件 id **98,101**、**97,102**、**33,68**（每组 2 条，deleted=0）。
- 同一 OSS key 被多条 file 记录引用；需确认是历史重复上传还是去重/引用设计，避免统计、删除、配额按记录数重复计算。

### FAB-01（P2）法布施阅读进度孤儿
- `biz_fabushi_reading_progress` 中 **42 行 book_id=999999**，无对应书目。
- 由 2026-11-16 权限矩阵探针写入（28 用户 + uid 85/86）；证明 `PUT /api/fabushi/progress/{bookId}` **不校验书存在**。其余评论/点赞/书架/章节/附件无孤儿。

### PERM-01（P2）范围行 ROLE 主体不存在（临时角色残留）
- `role_perm_scope` id **276/277/278**，`assignee_key=fabu_audit_tmp`，对应角色已不存在；临时审计夹具残留。

## 三、信息项（不计缺陷，需登记/清理）

- **AUTH-08**：83 条已过期 token 黑名单残留，可清理。
- **CUS-04**：3 组活跃客户同名，属 `name_norm` 迁移前存量；V2026.11.17.1 的 grandfather 逻辑将处理。
- **INC-05**：91 组激励业务键 `(user,category,customer,period)` 重复，需业务确认是否允许多条。
- **REM-05**：24 条 `overdue_days<0`，代码语义为「距到期天数」（负=未到期），不是缺陷。
- **STA-01**：全量客户 294，与客户计数接口一致。
- **STA-02**：开票金额合计 192.54（测试库样本）。

## 四、Schema 漂移（DEF-2 证据）

12 个未应用迁移（`2026.11.17.1`–`2026.11.19.2`），缺 **7 张表**、约 **40 列**：

- 缺表：`biz_file_link`、`biz_file_grant`、`biz_recycle_bin`、`biz_lifecycle_request`、`biz_lifecycle_item`、`biz_upload_session`、`biz_recycle_export`
- 缺列：客户 7、sys_user 4、文件 3、文件版本 3、激励 4、任务 3、提醒 4（其余为 file_grant/lifecycle 表内列）
- 明细：`evidence/schema-drift-20261118.md`
- 这解释了客户列表、文件 list/grants、激励、统计、提醒、公海、审批的 500。

## 五、未完成/挂起

1. 客户条目级 ID 集合比对（列表 500）。
2. 500 模块的端点 200/403 与数据面验证（等迁移）。
3. 文件记录 ↔ 物理卷一致性（需 shell，非 SQL；H4 历史问题）。
4. 生命周期/回收站（表缺失）。
5. SQL 检查未覆盖的：性能、并发、灾备、真机、UI 渲染。

## 六、建议

1. 迁移窗口统一应用 12 个迁移后，重跑：客户 28×7、权限矩阵、文件 list/grants、激励/统计/提醒/公海/审批。
2. 清理：`fabu_audit_tmp` 范围行（276-278）、book 999999 进度行（42）、过期 token 黑名单（83）。
3. 修复：法布施进度写入校验书存在；确认 folder_id=0 与重复 oss_key 的历史口径。
4. 生产零写入不变；迁移与生产动作等用户命令。
