# 模块可用性探测（测试服）

> 生成：2026-11-18 ｜ 方式：admin token，仅 GET，只读 ｜ 测试服 111.229.37.220 ｜ 生产未触碰

## 结论

测试服 jar 与测试库 Flyway 不一致（库 `2026.11.16.9`，代码已含 `2026.11.17.1`–`2026.11.19.2` 的对象），导致**大部分数据面接口 500**。仅少模块可查。

## 探测结果

| 模块 | 代表端点 | HTTP | 根因（后台日志） |
|---|---|---|---|
| 客户 | `/api/customer/list` | **500** | `Unknown column 'name_norm'`（V2026.11.17.1 未应用） |
| 文件 | `/api/file/list` | 200 | — |
| 文件 | `/api/file/folders` | 200 | — |
| 文件 | `/api/file/recycle/files` | 200 | — |
| 文件 | `/api/file/grants` | **500** | `Table 'biz_file_grant' doesn't exist` |
| 文件 | `/api/file/reconciliation` | **500** | `Unknown column 'md5'` |
| 激励 | `/api/incentive/records` | **500** | `Unknown column 'deleted'`（biz_incentive_record 审计列） |
| 激励 | `/api/incentive/customer-records` | **500** | `name_norm`（客户实体） |
| 统计 | `/api/statistics/dashboard` | **500** | `name_norm` |
| 统计 | `/api/statistics/customer-rankings` | **500** | `name_norm` |
| 提醒 | `/api/reminder/list` | **500** | `Unknown column 'deleted'`（biz_reminder 回收列） |
| 任务 | `/api/task/list` | 200 | — |
| 法布施 | `/api/fabushi/posts` | 200 | — |
| 法布施 | `/api/fabushi/books` | 200 | — |
| 公海 | `/api/sea/list` | **500** | `name_norm` |
| 审批 | `/api/approval/list` | **500** | `name_norm` |
| 积分 | `/api/score/grid` | 400 | 缺 `period` 参数（非缺陷） |

## 影响

- 客户：计数级已完成（28×7 全对）；条目级挂起（列表 500）。
- 文件：仅 list/folders/recycle 可查；grants/reconciliation 挂起。
- 激励、统计、提醒、公海、审批：整链数据面挂起。
- 可继续：文件（可用部分）、任务、法布施。

## 处置

按用户 2026-11-18 决定：**测试库暂不修，先查其他模块**；等后续统一迁移窗口再补条目级与挂起模块。生产零写入。
