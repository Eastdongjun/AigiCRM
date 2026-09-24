# 提醒体系

> 来源锚点:`ReminderService`(expiringPage/ensureExpiring/process/pendingCount)、`CustomerExpireTask`。核实日期 2026-09-24(当日完成双去重口径统一与 401 条积压清理,V2026.09.23.8)。

## 1. 提醒类型(六种)

| 类型 | 触发条件 | 生成方 |
| --- | --- | --- |
| `expiring` 到期提醒 | 客户 expire_date 进入 30 天窗口(未到期) | 凌晨任务 + admin 手动补生成 |
| `invoice_overdue` 开票超期 | 已开票但最近开票日距今 > 365 天 | 凌晨任务 |
| `invoice_never` 从未开票 | 从未有过开票记录 | 凌晨任务 |
| `share_to` 客户已分享给你 | 分享发生时 | 业务动作 |
| `share_by` 你分享了客户 | 分享发生时 | 业务动作 |
| `share_owner` 你的客户被分享 | 分享发生时 | 业务动作 |

## 2. 物化与去重(09-24 统一判据)

**统一判据:该客户存在 status=pending 的 expiring 提醒,或今日已有 processedAt 落在今天的记录 → 不新建。**

- 修复前双口径打架:凌晨任务"无 pending 才建" vs 页面补生成"今日没建过就建"→ 页面每触发一次多造一条,旧 pending 永不清,生产积压 401 条。
- 两个写入方(凌晨 `CustomerExpireTask` 与 admin 手动 `POST /reminder/ensure-expiring`)现在同一判据;`ensure-expiring` 为 **admin-only**(P1-09 收紧,PC/小程序前端均已对齐:非 admin 不触发)。
- 补生成仅对 admin 有意义,普通用户的数据新鲜度由凌晨任务兜底,不依赖任何人在页面上点。

## 3. 跟进语义

- `process(id)`:置 processed + processedAt=now;仅本人范围内提醒可处理(越权报"无权处理")。
- 到期列表行的 `reminderId` 指向该客户**当前 pending 的那条**(无则指向今日已处理那条);`todayFollowedUp` = 今日处理过(processedAt 口径)。
- 处理完当天不再重复物化;**次日**由凌晨任务或 admin 补生成再造一条新 pending,形成每日跟进循环,直到客户移出窗口或入海。

## 4. 角标

`pendingCount` = 数据范围内未跟进的 expiring 客户数 + 分享类待处理提醒数,驱动侧边栏角标。

## 5. 可见性

按 `reminder` 模块数据范围(resolveModuleUsers);非 all 范围用户只见范围内负责人的提醒;详情页按 ID 猜读有越权防护。

## 6. 历史积压清理(V2026.09.23.8,幂等)

每客户 expiring 只保留最新一条 pending;客户已删/已入海/已归档的死 pending 一并清除。生产 401→32,测试 375→29。

## 7. 提醒管理页取数结构(已知性能债,方案待令)

页面加载 = 6 个 GET(1 到期列表 + 3 分享 + 2 开票)分 4 段串行瀑布;每段内部还有逐行点查(N+1)。数据库无罪(全表 560 行、窗口内客户个位数)。优化方案(并发化+批量化)已诊断未实施。
