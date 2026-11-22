# 模块逐查进度（2026-11-18）

> 生产零写入；测试服只读为主。测试服 111.229.37.220，检查期间 23:22 发生过一次后端重启（非本检查触发）。

## 已完成

### 客户（第一轮，计数级）
- 28 启用用户 × 7 页签 = **196 项计数全部与独立 oracle 一致**。
- **P0 DEF-1**：TY005 勾选 office 应见 121，实际 17（auto_share 收窄，少看 104）；用户裁决 office=全办事处，当前为缺陷。
- 条目级（ID 集合）比对挂起：`/api/customer/list` 500（缺 `name_norm`）。
- 待跟踪：`data_scope_override=team` 对 uid 47/54 未生效；`auto_share.admin=true` 死配置。

### 法布施（可用端点）
- 权限抽查：4 用户（admin/NO001/TY002/NM100）× 9 个只读端点，实际 200/403 与 `sys_role_menu` 绑定一致；无越权 2xx。
- 统计对账：team/personal/monthly/dashboard 的 posts/likes/comments/read 与 SQL 一致（仅统计 published/deleted=0）；`memberCount=-1` 为“全部”哨兵，PC/MP 均已处理，非缺陷。
- 数据卫生：评论/点赞/已读/书架/章节/附件无孤儿；无重复；progress 页码合法；章节树无自环/跨书。
- **P2 DEF-3**：`biz_fabushi_reading_progress` 有 42 行 `book_id=999999` 孤儿（权限矩阵探针写入），证明进度写入不校验书存在。
- 书目 API total 2 / pending 1 与 DB（approved 2、pending 1，已排除 deleted=1）一致。

### 文件（可用部分）
- `/api/file/folders`、`/api/file/recycle/files` 200；`/api/file/list` 500（缺 `md5`，V2026.11.17.1）。
- DB 记录：file_scope = fabushi 17 / incentive 2550 / private 1 / public 64（deleted=0）。
- grants/reconciliation 500（缺 `biz_file_grant` / `md5`），深度检查挂起。

### 任务
- `/api/task/list` 200，当前 0 条；数据面暂无可用样本。

## 环境阻断（DEF-2，P0）
- 测试库 Flyway `2026.11.16.9`，代码/jar 已含 `2026.11.17.1`–`2026.11.19.2` 对象。
- 500 模块：客户列表、文件 list/grants/reconciliation、激励 records/customer、统计 dashboard/rank、提醒 list、公海 list、审批 list。
- 根因：缺 `name_norm`、`biz_file_grant`、`md5`、`biz_incentive_record.deleted`、`biz_reminder.deleted` 等。
- 用户决定：测试库暂不修，先查其他模块。

## 安全（DEF-4，P1，测试环境）
- 默认 profile 的 MyBatis `StdOutImpl` 打印全量 SQL 行，测试服日志含 admin bcrypt 哈希与手机号（值不记录）。
- `application-prod.yml` 已用 `Slf4jImpl`；`docker-compose.prod.yml` 默认 `SPRING_PROFILES_ACTIVE=prod`。生产运行时是否带 prod profile 仍需实证（未触碰生产）。

## 下一步
1. 对 500 模块做 **SQL-only** 完整性扫描（孤儿/重复/脏值/状态机/跨表一致性），不依赖 API。
2. 文件、任务可用端点继续；文件 list/grants 等挂起。
3. 条目级与端点级比对等测试库迁移窗口；生产零写入。
