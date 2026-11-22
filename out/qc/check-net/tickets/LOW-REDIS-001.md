# LOW-REDIS-001 — Redis 异常被报成「数据库异常」（P3，可诊断性）

- 位置：`aigicrm-backend/src/main/java/com/aigicrm/common/GlobalExceptionHandler.java:188-197`
- 事实：`handleDataAccess(DataAccessException e)` 对全部 `DataAccessException` 统一 `log.error("[{}] 数据库异常")`。Spring Data Redis 的 `RedisConnectionFailureException` 继承 `DataAccessException`，Redis 故障时会记成"数据库异常"，误导排障；返回文案也不区分。
- 触发面：权限快照缓存（`perm:ctx*`）、登录失败计数（`login_fail*`）等所有走 Redis 的读写在 Redis 故障时。
- 修复建议：handler 内先判 `e instanceof RedisConnectionFailureException`（含 cause 链），单独记「缓存（Redis）异常」并返回区分文案；或至少把 `e.getClass().getSimpleName()` 打进日志。
- 验收：测试服停 Redis（或 mock 抛 `RedisConnectionFailureException`）→ 日志出现"缓存/Redis 异常"而非"数据库异常"；接口 500 文案区分。
- 归属：实施方（协调方 0b）；QC 复验。
- 状态：OPEN（2026-11-18，W1 crosswalk 低危 7）
