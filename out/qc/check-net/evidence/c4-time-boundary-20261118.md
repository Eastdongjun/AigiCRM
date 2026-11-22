# C4 时间边界 — 静态审查（2026-11-18，HEAD ed5f0cc8）

> 代码级审查；运行时（时间旅行/跨天等待）判定 **BLOCKED**（单窗不可行）。

## 一、时区链（PASS）

- JDBC `serverTimezone=Asia/Shanghai`、Jackson `time-zone: Asia/Shanghai`、容器 TZ 实测 UTC+8 —— 全链一致。
- 定时任务 `@Scheduled(cron)` 无显式 `zone` → 依赖 JVM 默认（=容器 Asia/Shanghai）——**P3 建议显式声明**，非缺陷。

## 二、提醒域日界（PASS）

- `ReminderService`：`today.atStartOfDay()`（:394）做当日起点；`deadline = today.plusDays(warningDays)`（:337/454/509）；当日已处理过滤 `processedAt >= todayStart`（:411）——**跨天正确重置、不重复提醒**。
- 物化幂等（`markExpiring`，S8 修复：POST 显式触发、GET 不写库）——跨天重复跑无害。
- `LocalDate.now()` 多处取值：单请求内通常一致；若请求恰跨 00:00 可能有毫秒级不一致——**理论边界，无实际影响**（提醒按日粒度）。

## 三、统计月界（PASS）

- `today.withDayOfMonth(1)`（`StatisticsQueryService:186/196`）——月首对齐正确。
- `monthlyCustomerCounts`/`monthlyFileCounts` 按年 12 月数组（:1004-1005）——边界无 off-by-one。

## 四、激励 period（B4 口径）

- `period` 为**字符串直接匹配**（`eq(BizIncentiveRecord::getPeriod, period)`，`IncentiveService:380/1097`）——格式一致性依赖上传侧；**属 B4 口径（period 字典/跨月规则）**，非本项缺陷。

## 五、结论

- **C4 静态 = PASS**（时区/日界/月界实现正确；period 归 B4）。
- **运行时 = BLOCKED**（需时间旅行或造数后跨天等待；单窗不可行）；已备替代方案：单元级时钟注入（仅文件域有 `LongSupplier`，其余域无）。
