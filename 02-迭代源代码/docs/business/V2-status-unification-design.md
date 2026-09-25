# V2 第一刀：客户状态归一设计（status / protection_status / sea / deleted → 三维正交）

> 定位：V2 重构第一刀的**纯设计文档**。本文不改动任何 Java/SQL 代码；所有引用均为当前工作区真实代码（`文件:行号`）与测试库真实查询结果。
> 核查日期：2026-09-25（v2：同日按评审意见修订，处置记录见 §6）。代码锚点根目录：`aigicrm-backend/src/main/java/`（下文简写为 `backend:`）。
> 姊妹文档：[customer-lifecycle.md](customer-lifecycle.md)（现状态机业务口径）、[metrics-definition.md](../metrics-definition.md)（统计口径）。

---

## 0. 结论摘要

当前客户的"状态"由**两个持久化字符串字段 + 一张关联池表 + 一个逻辑删标记**共同表达，且 `status` 字段一个字段承载了两种语义（生命周期/归属 + 保护风险）：

- `biz_customer.status`：`normal / warning / expired / sea / archived`（V1 基线 DDL 只声明了前四种，见 `V1__baseline.sql:188`；`archived` 是 2026-09-22 议题五.4 追加，DDL 注释未更新）
- `biz_customer.protection_status`：`active / expiring / expired`（`V1__baseline.sql:189`）
- `biz_customer.deleted`：逻辑删（`@TableLogic`，`BizCustomer.java:100-102`）
- `biz_sea_pool` 行（`status != 'claimed'` 即在池）：公海归属的**第二个真源**（`CustomerAccessService.java:100-113`）

实证的五类口径病（详见 §1.5）：

1. **双字段冗余**：到期任务同一行同时写 `status=expired + protection_status=expired`（`CustomerExpireTask.java:108-111`）、`status=warning + protection_status=expiring`（`CustomerExpireTask.java:126-129`）——同一信息两处存。
2. **非法值已发生**：测试库存在 `protection_status='normal'`（域外值，1 行，id=573）。
3. **双真源漂移**：归属"是否公海"同时由 `status='sea'` 和 `biz_sea_pool` 活跃行表达，两者已出现分叉（测试库客户 302：池行 `pending_approval` 但客户行 `normal/active/有负责人`，且池表存留历史双行，见 §1.4）。
4. **统计口径缺口**：按 `protection_status='expired'` 统计"超期客户"的指标（`CustomerService.java:766-771`）没有排除公海——测试库 29 个"超期"客户 **29/29 全部在公海**，名下真实超期为 0。
5. **保护期物化值与派生源脱钩**：手动释放入池无条件写 `protection_status=expired` 而不动 `expire_date`（`SeaService.java:196-198`），客户 596 实证（`expire_date=2027-08-30` 未到期但物化 expired）；另有 31 行 `expire_date IS NULL` 靠 DDL 默认值 active 计入指标。

目标：归一为**三维正交模型**（生命周期 × 归属 × 保护风险），三个维度各自单真源、可独立演化；分三阶段落地（只读派生 → 写路径收口 → 旧字段退役），每阶段可独立验证与回滚。

---

## 1. 现状盘点

### 1.1 字段与真源现状

| 载体 | 定义位置 | 取值域（DDL/代码实证） | 承载语义 |
| --- | --- | --- | --- |
| `biz_customer.status` | `backend:module/customer/entity/BizCustomer.java:89-90`；DDL `V1__baseline.sql:188` | normal / warning / expired / sea / archived（实测+代码；DDL 注释缺 archived） | **混合**：normal/archived=生命周期，sea=归属，warning/expired=保护风险 |
| `biz_customer.protection_status` | `BizCustomer.java:91-92`；DDL `V1__baseline.sql:189` | active / expiring / expired（**实测出现非法值 'normal'**） | 保护风险（物化值，与派生源 expire_date 可脱钩，见 §1.5-5） |
| `biz_customer.deleted` | `BizCustomer.java:100-102`（`@TableLogic`） | 0 / 1 | 逻辑删（生命周期终态） |
| `biz_sea_pool`（status != 'claimed' 的活跃行） | `backend:common/engine/CustomerAccessService.java:100-113`（`findActivePool`） | available / pending_approval / claimed | **公海归属第二真源**（权限/审批侧只认它） |

索引现状（测试库 `SHOW INDEX` 实测）：

- `biz_customer`：`status` 有 `idx_status`；**`protection_status` 无索引**（列表筛选 `CustomerService.java:177-181` 全表扫该列）。
- `biz_sea_pool`：`idx_customer_id`、`idx_status`，另有 `active_flag` **虚拟生成列**（`case when status<>'claimed' then customer_id else NULL end`）+ 唯一键 `uk_sea_pool_active(tenant_id, active_flag)`（定义见 `V2026.09.22.1__sea_pool_active_unique.sql`，实测 `SHOW COLUMNS` 确认 VIRTUAL GENERATED）——即"每租户每客户至多一条活跃池行"已有 schema 级保证，且活跃行可用唯一键点查。

### 1.2 写入点清单（`biz_customer.status` / `protection_status` / `deleted` 的全部写入）

> v2 修订：原 W5 补第二个 API 入口；新增 W13/W14 两处**整实体回写**路径（评审指出原清单遗漏；这两处不调用 setStatus/setProtectionStatus，但整实体 `updateById` 会把加载时快照的 status/protection_status 原样落回，且是保护维度派生源 expire_date 的变更路径——直接决定 §3 阶段二架构锁的形态）。

| # | 位置 | 写入内容 | 触发场景与语义 |
| --- | --- | --- | --- |
| W1 | `backend:module/customer/service/CustomerService.java:368-369` | `status=normal; protection_status=active` | 创建客户固定初值。Excel 导入同路径（`CustomerImportListener.java:52` → `create`） |
| W2 | `backend:module/customer/service/CustomerService.java:511-512` | 回填库内值 | 编辑接口对**公海池内**客户的防改守卫（见 W5 的缺口） |
| W3 | `backend:module/customer/service/CustomerService.java:614` | `status=archived` | 归档（议题五.4） |
| W4 | `backend:module/customer/service/CustomerService.java:628` | `status=normal` | 从归档恢复 |
| W5 | `backend:module/customer/service/CustomerService.java:524`（`updateById(customer)` 透传） | 请求体携带的任意 `status` / `protection_status` | **写泄漏，两个 API 入口**：① `PUT /customer/{id}` 直收全实体（`CustomerController.java:99-100`）；② `PUT /customer/{id}/full` 组合端点（`CustomerController.java:166-174` → `CustomerService.java:1395` `updateWithIndicator` → `update()` 同一漏点）。两处均仅池内客户被拦（`CustomerService.java:504-512`）；非池客户任何 `customer:edit` 持有者可经编辑接口直写两字段 |
| W6 | `backend:module/sea/service/SeaService.java:170-174` | `status=sea; protection_status=expired` | `addToSea` 幂等分支（已有活跃池行时只补客户行状态） |
| W7 | `backend:module/sea/service/SeaService.java:196-198` | `status=sea; protection_status=expired`（**无条件写 expired，不动 expire_date**） | `addToSea` 正常入池。四个调用方：手动释放（`CustomerController.java:352-372`，仅校验负责人与池配置，**不拦 archived**）、离职交接（`SysUserService.java:419-428`，查询仅 `ne status='sea'`，**archived 客户会通过**）、到期任务（`CustomerExpireTask.java:117-118`）、公海导入（`SeaService.java:298-322`）。`addToSea` 自身（`SeaService.java:159-198`）**无归档守卫** → `archived+活跃池行` 是现网代码可制造的组合（裁决点 D8） |
| W8 | `backend:module/sea/service/SeaService.java:248-254` | `status=normal; protection_status=active`（并重置注册/到期/调整日） | 捞取审批通过 `completeClaim` |
| W9 | `backend:task/CustomerExpireTask.java:108-111` | `protection_status=expired; status=expired` | 到期（**双字段冗余写**） |
| W10 | `backend:task/CustomerExpireTask.java:126-129` | `protection_status=expiring; status=warning` | 临期 ≤30 天（**双字段冗余写**） |
| W11 | `backend:module/approval/service/ApprovalService.java:688-690` | `protection_status=active; status=normal` | 延期审批通过。**不检查客户是否在池**：客户在公海（池行 `pending_approval`/`available`）时延期被批准，客户行被拉回 normal/active 而池行仍在——即 §1.4 客户 302 漂移的成因路径之一 |
| W12 | `backend:module/customer/service/CustomerService.java:566`（`deleteById`）、`backend:module/sea/service/SeaService.java:289`（`deletePool`→`deleteById`） | `deleted=1` | 逻辑删；`status` 原样保留（组合表 `sea+deleted=1` 88 行即实证） |
| W13 | `backend:module/customer/service/CustomerService.java:1340-1353`（`refreshLastInvoiceDate`，整实体 `updateById` 于 1352） | 整实体回写：`last_invoice_date` + `calculateExpireDates` 重算后的 `expire_date`（status/protectionStatus 随加载快照原样落回） | 开票新增（1220）/确认（1257）/作废（1314）后驱动保护期重算——**保护维度派生源 expire_date 的变更路径**，状态迁移服务的意图清单必须覆盖（`recalibrateExpireFromInvoice`） |
| W14 | `backend:module/approval/service/ApprovalService.java:724-728`（transfer 审批执行，整实体 `updateById` 于 728） | 整实体回写：`owner_id`、`transfer_date`、`dept_id`（status/protectionStatus 原样落回） | 转移审批通过——归属维度变更路径，意图清单对应 `transferOwner` |

### 1.3 读取点清单

**后端（Java）**——`notIn(status,'sea','archived')` 口径共 **31 处**（v2 修订：按评审逐处清点修正；CustomerService 14 处 + StatisticsQueryService 11 处 + ReminderService 3 处 + CustomerExpireTask 3 处）：

| # | 位置 | 读法 | 语义 |
| --- | --- | --- | --- |
| R1 | `CustomerService.java:108-110, 165-173`（同口径 `254-263`） | `ne archived` / `notIn(sea,archived)`(166,172) / `eq,in status` | 列表过滤；`status=include_archived` 特殊语义（165-166） |
| R2 | `CustomerService.java:177-181` | `eq/in protection_status` | 保护状态筛选（无索引列，见 §1.1；`expire_date IS NULL` 的 31 行靠 DDL 默认 active 命中该筛选） |
| R3 | `CustomerService.java:271-279`（notIn 于 272,279） | `notIn(sea,archived)` | 列表 scope 计数 |
| R4 | `CustomerService.java:505-507` | 比对 `existing.status/protectionStatus` 与请求值 | 池内编辑防改守卫的判据 |
| R5 | `CustomerService.java:569-572` | 拼接删除留痕日志 | 审计快照（删除是终态须可追溯） |
| R6 | `CustomerService.java:737, 760, 777, 794, 817, 852, 860, 866` | `notIn(sea,archived)` | 工作台统计 / 有效客户统一口径（B5） |
| R7 | `CustomerService.java:742-747（active）, 749-754（expiring）, 766-771（expired）, 835-843（expiring TopN）` | `eq protection_status=…`，**均无 `notIn(sea,archived)`** | 保护期统计。缺口实证见 §1.4：`expired` 计数 29/29 为公海客户；`active` 计数会把归档客户与 `expire_date IS NULL` 行（DDL 默认 active）算入 |
| R8 | `backend:module/statistics/service/StatisticsQueryService.java:629, 656, 669, 681, 701, 708, 717, 726, 735, 747, 756` | `notIn(sea,archived)` | 报表 B5 统一口径（11 处复制粘贴） |
| R9 | `StatisticsQueryService.java:663（active）, 709（active）, 718（expiring）` | `eq protection_status`，**663 无 `notIn(sea,archived)`** | 办事处对比/核心客户指标，与 R7 同类缺口 |
| R10 | `backend:module/reminder/service/ReminderService.java:119, 213, 269` | `notIn(sea,archived)` | 提醒口径 |
| R11 | `backend:task/CustomerExpireTask.java:94, 215, 231`（v2 修正：原误写 227，第三处在 231） | `notIn(sea,archived)` | 到期检查 / 开票超期 / 从未开票 |
| R12 | `CustomerExpireTask.java:108, 114, 126` | 读 `protectionStatus/status` 防重写 | 到期任务幂等判据 |
| R13 | `backend:module/system/service/SysUserService.java:419-423` | `ne status='sea'` | 离职释放名单（**只排 sea 不排 archived**，与其他处 `notIn` 口径不一致；配合 W7 构成 archived 入池通道） |
| R14 | `backend:common/engine/CustomerAccessService.java:100-113` | 查 `biz_sea_pool` 活跃行（**不读 status**） | 池内判定的真源=池表 → 与 `status='sea'` 双真源 |
| R15 | `ApprovalService.java:208-240` | 池表 `available/pending_approval` + 原子锁 | claim 提交（池表侧状态机，本设计不动它） |
| R16 | `backend:module/customer/controller/CustomerController.java:49, 55`（page 参数）、`527-545`（export，status 透传）、`166-174`（`PUT /{id}/full`，全实体透传） | `status/protectionStatus` 查询参数/请求体透传 | API 入口 |

**前端 PC（aigicrm-ui）**

| 位置 | 用法 |
| --- | --- |
| `aigicrm-ui/src/utils/customer-status.js:7-13` | status→中文标签唯一字典（normal/warning/expired/sea/archived 五值） |
| `aigicrm-ui/src/utils/list-filter.js:38, 45-46` | 客户端过滤 `status`（38/45）与 `protectionStatus`（46），两域并存 |
| `aigicrm-ui/src/composables/useCustomerList.js:157-159, 185-188` | 查询参数组装；`include_archived` 特殊值（188） |
| `aigicrm-ui/src/views/customer/MainTable.vue:20, 64, 79, 107` | 状态筛选下拉、状态列、`archived` 行样式/恢复按钮 |

**小程序（aigicrm-miniprogram）**

| 位置 | 用法 |
| --- | --- |
| `aigicrm-miniprogram/pages/customer/index.js:11-21`、`pages/customer/detail/index.js:11-21` | **第三套状态字典**：含 PC 已不输出的 `protected/active/available` 旧键，**缺 `archived`**（归档客户显示原始英文串） |
| `pages/customer/index.js:48`、`249-250` | protectionStatus 独立透传（2026-09-21 曾因误传 `status=active` 恒空，注释在案） |

**Excel 导入/导出**：`BizCustomer.java:89-92` 的 `@ExcelProperty("状态")/("保护状态")`——导出模板含两列（`CustomerController.java:527-545`）。

### 1.4 测试库真实组合分布（只读查询，2026-09-25；v2 补评审新增证据）

命令（经 `ssh aigicrm-test` → `docker exec shared-mysql`，口令仅经容器环境变量引用）：

```
SELECT status, protection_status, deleted, COUNT(*) AS cnt
FROM biz_customer GROUP BY status, protection_status, deleted ORDER BY cnt DESC;
```

结果（`aigicrm` 库，共 597 行）：

| status | protection_status | deleted | cnt | 现状语义判读 |
| --- | --- | --- | --- | --- |
| normal | active | 0 | 269（其中 238 行有 expire_date，**31 行 expire_date IS NULL**） | 名下 + 保护期内（NULL 行靠 DDL 默认值 active 计入指标） |
| normal | active | 1 | 202 | 名下时刻被逻辑删（status 保留原值） |
| sea | expired | 1 | 88 | 在公海期间被删除（`deletePool` 路径） |
| sea | expired | 0 | 29 | 在公海（**含手动提前释放行，protection 物化值与 expire_date 脱钩，见下）） |
| warning | expiring | 1 | 4 | 临期被删 |
| warning | expiring | 0 | 4 | 名下临期（**status/protection 冗余并存**） |
| normal | **normal** | 1 | 1 | **非法 protection_status 值**（id=573，SX-202608-001） |

附加漂移与反例检查（同库只读，v2 新增三组）：

```
-- ① 归属双真源一致性（deleted=0）
sea_status_no_pool   （status='sea' 但无活跃池行）        = 0
pool_not_sea_status  （有活跃池行但 status≠'sea'）        = 1  ← 客户302 NM-202607-044

-- ② 客户 302 明细：expire_date=2027-07-23（远未到期）；biz_sea_pool 两行：
--    id=103 pending_approval 入池 2026-07-14（claim_date=NULL，陈旧锁行）
--    id=137 claimed       入池 2026-07-24，捞取于 2026-07-28 12:31（completeClaim 已把客户行置 normal/active）
--    → 漂移机制：07-14 锁行未被清理（B2 幂等修复 2026-09-22 之前可重复入池），
--      07-24 二次入池后捞取成功，客户行回 normal/active 而 07-14 活跃行残留（uk 允许：唯一约束只限"每客户一条非 claimed 行"）

-- ③ 手动提前释放反例（v2 评审证据）：客户 596 SX-202609-002
--    status=sea, protection_status=expired, expire_date=2027-08-30, 池行 available(enter_reason='移动端释放')
--    → 按派生源 expire_date 应为 active；物化 expired 与派生冲突（W7 无条件写 expired 所致）

-- ④ expire_date IS NULL：deleted=0 共 31 行（全部 normal/active）→ DDL 默认 active 维持指标口径

-- ⑤ 极值与池表
archived 行数 = 0        status='expired' 行数（未入海到期）= 0        biz_sea_pool 共 59 行
protection_status='expired' 且 deleted=0 = 29；其中真·名下超期（status NOT IN sea/archived）= 0
biz_sea_pool 分布: available=27, claimed=29, pending_approval=3
```

### 1.5 现状问题小结（每条均有 §1.2-1.4 锚点）

1. **一字段两义**：`status` 同时编码生命周期（normal/archived）、归属（sea）、保护风险（warning/expired），任何"有效客户"判断都要写成三值排除 `notIn(sea,archived)`，全库复制粘贴 **31 处**（R1/R3/R6/R8/R10/R11），漏写即口径事故（R7/R9/R13 已实证漏写）。
2. **双字段冗余（仅到期路径对称）**：W9/W10 双写且 `status=warning/expired` ↔ `protection_status=expiring/expired` 在**到期任务路径**上一一映射；但 **W7 手动释放路径只写 `protection_status=expired` 不写 `status=expired` 也不动 expire_date**，映射断裂（客户 596 实证：物化 expired / 派生 active）。
3. **非法值无护栏**：`protection_status='normal'` 已真实出现（1.4 表）；DDL/代码均无 CHECK/枚举校验，写入全靠散装字符串字面量。
4. **归属双真源**：`status='sea'`（客户行）与 `biz_sea_pool` 活跃行（R14 真源）并存，W11 可造出分叉（客户 302 实证，且池表残留历史双行——多活跃行脏数据正是 uk_sea_pool_active 收编（`V2026.09.22.1__sea_pool_active_unique.sql`）前的存量）；池行有 `pending_approval` 中间态而客户行没有对应表达——"审批锁中"只能查池表。
5. **保护维度物化值与派生源脱钩**：两套事实并存——(a) 手动提前释放写 expired 但 expire_date 在未来（客户 596）；(b) `expire_date IS NULL` 31 行物化 active 纯靠 DDL 默认值，并无保护期事实。任何"以 expire_date 派生"的切换都会改变这两类行的口径（K2 公告必须覆盖）。
6. **统计口径缺口**：R7/R9 的 protection_status 计数不排 sea/archived：`expired` 指标 100% 由公海客户构成（29/29）；`active` 计数会计入归档客户（潜伏）与 NULL 保护期行。
7. **前端三套字典**：PC `customer-status.js`、小程序 `customer/index.js`、`detail/index.js` 各自维护映射且值域不一致（小程序缺 archived、多历史键）。
8. **归档与公海可非法叠加**：手动释放（W7）与离职交接（R13）都不拦 archived 客户，`addToSea` 亦无守卫——`archived+活跃池行` 是现网可制造组合（裁决点 D8），非理论形态。

---

## 2. 目标模型：三维正交

### 2.1 维度定义

| 维度 | 取值 | 唯一真源（True Source） | 说明 |
| --- | --- | --- | --- |
| **生命周期 lifecycle** | `active`（有效）/ `archived`（归档）/ `deleted`（已删） | `biz_customer.status`（收窄为 normal/archived 两值）+ `deleted` | 归档/删除是管理动作，非派生；`deleted=1` 即终态，其余两维不再派生 |
| **归属 ownership** | `owned`（销售名下）/ `sea`（公海，含 `pending_approval` 审批锁） | **`biz_sea_pool` 活跃行**（`status != 'claimed'`；可经 `active_flag` 生成列 + `uk_sea_pool_active` 点查，见 §1.1） | 客户行上的 `status='sea'` 降级为池表状态的**投影缓存**；审批锁中=池 `pending_approval` |
| **保护风险 protection** | `active` / `expiring` / `expired` / **`unset`（未设保护期，expire_date IS NULL）** | **`expire_date` 派生**（对照 `sys_config: customer.warning.days`，默认 30；`CustomerExpireTask.java:88-89` 同公式） | 纯函数：`expire_date IS NULL → unset`；`today > expire_date → expired`；`expire_date - today ≤ warningDays → expiring`；否则 `active`。`protection_status` 降级为物化缓存。`unset` 在统计/筛选中的映射是裁决点 **D7**（倾向：计入 active，与现状 DDL 默认值口径连续，避免 31 行静默掉出指标） |

正交性约束（不变量）：

- 任意维度组合**句法上合法**且互不暗示。特别说明（v2 修正原稿错误前提）：**现状下 `archived + sea` 可被现网代码制造**——手动释放（`CustomerController.java:352-372`）与离职交接（`SysUserService.java:419-428`，查询仅 `ne 'sea'`）都不拦归档客户，`addToSea` 自身无守卫（W7）。目标模型下该组合按优先级 lifecycle > ownership 仲裁（archived 展示、池行待清理）并打 `driftWarning`；**入口端是否直接拒绝归档客户入池由裁决点 D8 决定**（倾向：拒绝，见 §4.2）。
- `deleted=1` 时三维视图直接返回 `deleted/—/—`，不参与任何统计、提醒、到期检查（与 B5 裁决一致）。

### 2.2 旧组合 → 新口径兼容映射表

覆盖 §1.4 观测到的全部组合 + 代码路径可产生的形态。**映射按"派生真源"解释旧值，旧字段不再信任自身**（v2 修订：M3 按 expire_date 拆分；新增 M1a/M12 覆盖 NULL 保护期与手动提前释放）：

| # | 旧：status | 旧：protection_status | 旧：deleted | 池活跃行 | expire_date | → lifecycle | → ownership | → protection | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M1 | normal | active | 0 | 无 | 有 | active | owned | 按 expire_date 派生（238 行） | 健康组合 |
| **M1a** | normal | active | 0 | 无 | **NULL** | active | owned | **unset**（31 行） | DDL 默认值兜底的"伪 active"；D7 决定指标映射（倾向=active） |
| M2 | normal | active | 1 | — | — | **deleted** | —（终态） | — | 202 行 |
| M3 | sea | expired | 0 | 有（available/pending_approval） | **任意** | active | **sea** | **按 expire_date 派生**：可为 expired（到期入池）/ active（手动提前释放，客户 596）/ expiring | v2 修正：原稿一律写 expired 与派生规则自相矛盾；29 行实测至少含 596 类派生 active 行 |
| M4 | sea | expired | 1 | — | — | **deleted** | — | — | 88 行 |
| M5 | warning | expiring | 0 | 无 | 有 | active | owned | expiring | 4 行；`status=warning` 冗余值丢弃 |
| M6 | warning | expiring | 1 | — | — | **deleted** | — | — | 4 行 |
| M7 | normal | **normal（非法）** | 1 | — | — | **deleted** | — | — | 1 行（id=573）；修复迁移见阶段三 T1 |
| M8 | expired | expired | 0 | 无 | 有（已过） | active | owned | expired | **无池办事处到期未入海**（`CustomerExpireTask.java:117-122` 保留分支）；名下到期是合法形态 |
| M9 | normal/**archived** | 任意 | 0 | 有（pending_approval/available） | 任意 | active（或 archived） | **sea**（锁中） | 按 expire_date 派生（NULL→unset） | 客户 302 漂移形态：ownership 永远跟池行；**archived+池行现网可制造**（W7/R13 无守卫），派生按 lifecycle 优先并告警 + D8 裁决入口行为 |
| M10 | archived | active/expiring/expired | 0 | 无 | 任意 | **archived** | owned | 冻结（不参与统计/提醒，B5） | 测试库暂 0 行，功能已上线（W3/W4） |
| M11 | normal | expired | 0 | 无 | 有 | active | owned | expired | 理论可由 W5 泄漏产生；派生口径自愈 |
| **M12** | 任意 | 任意 | 0/1 | 任意 | **NULL** | 同上各行 | 同上 | **unset** | expire_date 为 NULL 时 protection 恒为 unset，与旧物化值（多为 active）可能不一致——D7 裁决指标侧映射；T1 回填规则对其显式定义为"回填 unset 或维持 active"（随 D7） |

派生仲裁顺序（伪代码，阶段一只读实现；v2 增加 unset 分支）：

```
derive(customer c, poolRow p):
  if c.deleted == 1:            return (deleted,  —,      —)
  lifecycle = c.status == 'archived' ? archived : active
  ownership = (p != null && p.status != 'claimed') ? sea : owned   // 真源=池表(可经 active_flag 点查)
  protection = lifecycle == archived                    ? frozen
             : c.expire_date == null                    ? unset        // D7 决定统计映射(倾向计 active)
             : today > expire_date                      ? expired
             : expire_date <= today + warningDays       ? expiring
             : active
```

### 2.3 旧字段在新模型中的角色演变

| 字段 | 阶段一 | 阶段二 | 阶段三（终态） |
| --- | --- | --- | --- |
| `status` | 原样读写（不动） | 收口为单点写 | 只剩 `normal/archived`（lifecycle 真源）；`sea/warning/expired` 值经迁移 T3/T4 清除 |
| `protection_status` | 原样读写 | 收口为单点写（与 expire_date 派生对齐后物化） | 二选一（裁决点 D2）：保留物化缓存+补索引 / 删列纯派生 |
| `biz_sea_pool` | 保持不变（已是真源） | 保持 | 保持（ownership 真源） |
| `deleted` | 保持 | 保持 | 保持 |

---

## 3. 分阶段落地方案

总原则：**每个阶段独立可发布、可验证、可回滚；任何时刻生产只有一个写路径在主导。** 三阶段串行，阶段间需用户验收（见 §4 裁决点）。

### 阶段一：集中派生服务（只读统一口径，不改表、不改任何既有写路径）

**目标**：新组件 `CustomerStatusService`（建议落位 `backend:module/customer/service/`，命名可议）成为**读侧唯一口径**：输入 `BizCustomer` + 池活跃行，输出三维状态（§2.2 伪代码）。本阶段不改变任何表结构与既有查询行为，只做"影子口径"。

**改动面（设计，不实现）**：

1. 新增只读服务 + 三维值对象（`lifecycle/ownership/protection` 枚举，protection 含 `unset`）。
2. 新增**口径对照断言**（测试代码）：对 §1.4 的每条真实组合，断言派生结果与 §2.2 映射表逐行一致；特别断言三个反例行——客户 596（sea+物化 expired+expire_date 未来 → 派生 `sea/active`，**不与 M3 的"按 expire_date 派生"冲突**）、客户 302（池行 pending_approval + 客户行 normal → 派发 `active/sea/active` + driftWarning）、31 行 NULL（→ `unset`）；非法值（M7）断言"deleted 短路 + 不炸"。
3. 可选试点（低风险读路径先切）：`getExpiringByScope`（`CustomerService.java:835-843`）与 R7 的四个计数改调派生服务——同时修复 §1.5-6 缺口（`expired/expiring` 计数补 `notIn(sea,archived)` 语义）。**是否本阶段就修数字，是裁决点 D5**（D7 未决前，NULL 行先按现状=active 计入，保证试点只改"公海/归档混入"一类口径）。

**验证方法**：

- 单测：映射表 M1-M12 逐行断言（引用既有测试风格 `CustomerServiceTest.java:354-376`）。
- 影子 SQL（测试库只读，双口径 diff，期望 = §1.4 量化）：
  ```sql
  SELECT SUM(protection_status='expired' AND deleted=0)                      AS old_expired_metric,   -- 29
         SUM(protection_status='expired' AND deleted=0
             AND status NOT IN ('sea','archived'))                           AS new_expired_metric,   -- 0
         SUM(EXISTS(SELECT 1 FROM biz_sea_pool p WHERE p.customer_id=c.id
             AND p.status<>'claimed') <> (c.status='sea'))                   AS ownership_drift,      -- 1（客户302）
         SUM(expire_date IS NULL AND deleted=0)                              AS unset_rows            -- 31
  FROM biz_customer c;
  ```
- 全量回归：`mvn test`（不改变行为时全绿）。

**影响面**：零行为变更（试点切换除外）；无表变更；无 API 变更。

**回滚**：删除新服务即可（无人依赖）；试点读路径还原为原查询（一处 git revert）。

### 阶段二：写路径收口（散装 setStatus 收敛到单点 + 架构测试锁）

**目标**：`biz_customer.status/protection_status` 的写权限从 14 个写入点（W1-W14）收敛到**唯一状态迁移服务**（建议 `CustomerStatusTransitionService`，与阶段一派生服务同包）。业务代码只声明**意图**，由迁移服务保证三维一致。

**改动面（设计，不实现）**：

1. 迁移服务意图清单（v2 补全两维）：`assignOwner`（创建归属）、`transferOwner`（转移审批，承接 W14）、`enterSea`（入池/释放/离职/到期/公海导入五入口，**补归档守卫——archived 客户拒绝入池，随 D8 裁决**）、`claim`（捞取过户）、`approveExtend`（延期）、`markExpiring/markExpired`（到期物化）、`markArchived/restore`（归档/恢复）、`softDelete`（删除前把 status 归一为 normal 再置 deleted，终结 `sea+deleted` 组合，即 D6）、`recalibrateExpireFromInvoice`（**开票驱动的 expire_date 重算+protection 物化对齐，承接 W13**）。
2. **架构测试锁（v2 重设计）**：评审正确指出——仅禁 `setStatus/setProtectionStatus` 调用（复用 `ArchitectureTest.java:22-37` ArchUnit 模式）拦不住 W13/W14 的**整实体 `updateById`**（实体随加载快照把两列原样带回写）。锁必须升级到"写语句粒度"，三层：
   - **L1（静态，ArchUnit）**：除迁移服务（白名单）外，任何类不得调用 `BizCustomerMapper.updateById(...)` 与 `update(entity, wrapper)`（整实体写）；对 `LambdaUpdateWrapper` 仅禁止 `.set(BizCustomer::getStatus/setProtectionStatus)`。W5/W13/W14 三个整实体写点必须先改造为"选择性字段更新或委托迁移服务"，该规则才能全绿——这是本阶段的工程量主体，不是免费开关。
   - **L2（运行时兜底，防绕过）**：MyBatis-Plus `InnerInterceptor` 拦截 UPDATE 语句，若 SET 子句含 `status`/`protection_status` 且当前线程无迁移服务发放的 thread-local 令牌 → 抛异常并计数告警。静态锁漏网（反射/新 mapper/原生 SQL）由此兜住。
   - **L3（可选最后防线）**：MySQL BEFORE UPDATE 触发器（`NEW.status<>OLD.status OR NEW.protection_status<>OLD.protection_status` 且无会话变量授权时 SIGNAL 错误）。默认不上，仅当 L1/L2 上线后仍出现越权写时再加。
3. 漂移修复任务（一次性，测试库先验，顺序=先池表后客户行）：清池表残留活跃行（客户 302：作废 07-14 `pending_approval` 陈旧锁行，保留 07-24 `claimed` 历史，方向见 D3）；修 id=573 非法值与 596 类物化脱钩（protection 物化按派生对齐，属 T1 类前置小批）。
4. 堵 W5 双入口：`update()`（含 `updateWithIndicator` 路径，`CustomerService.java:1395`）对非池客户同样强制忽略请求体 status/protectionStatus，与池内守卫（`CustomerService.java:504-512`）对齐。**K3 的调用面抓取必须同时覆盖 `PUT /{id}` 与 `PUT /{id}/full` 两个入口。**

**验证方法**：

- 架构测试：L1 规则在收口完成当晚必须全绿（这是本阶段的验收门）；L2 用集成测试模拟"绕过迁移服务直写"断言抛异常。
- 单测/集成：每个迁移意图一条正反用例（如"公海客户被批准延期 → 按 D4 拒绝/清池后置 active"、"开票作废 → expire_date 回算且 protection 物化=派生值"、"归档客户手动释放 → 按 D8 拒绝"）。
- 测试库灰度：重跑 §1.4 组合统计，期望收敛为合法组合集 {M1, M1a, M2, M3, M4, M5, M6, M8, M10}（`sea` 值此时仍在——status 收窄是阶段三的事），`pool_not_sea_status=0`、非法 protection 值=0、物化≠派生 diff=0。
- 行为对照：捞取/释放/离职交接/到期任务/开票作废回算五条链路在灰度前后的 `biz_sea_pool` 与客户行终态快照 diff 为空。

**影响面**：全部写入路径（创建/编辑×2 入口/归档/恢复/释放/捞取/延期/转移/到期任务/开票回算/删除/导入）；API 契约不变（两个 PUT 端点请求体里带 status 的客户端从"可能生效"变为"恒忽略"——属收紧，K3 埋点先证调用面为空）。

**回滚**：git revert 收口 PR + 撤销 L1 白名单缩编（架构测试允许临时把某类加回豁免清单以恢复上线，债务显式登记 `docs/TECH_DEBT.md`）；L2 拦截器带开关（默认 on，事故回滚时可 off）。**不推荐**迁移服务内保留旧写法 feature flag 双写并行——双写正是本阶段要消灭的病根。

### 阶段三：旧字段退役

**目标**：`status` 收窄为 lifecycle 真源（normal/archived），`protection_status` 按裁决 D2 处理；前端字典统一；历史脏值清零。

**改动面（设计，不实现；v2 重排迁移步骤为 T1-T5，先池表后客户行，覆盖全部现存量——原 T1-T4 漏 29+88 行 `status='sea'` 存量，执行完过不了自己的验收门，属评审 Q3，已修）**：

1. 数据迁移（Flyway，顺序执行，全部可前向重放；每步先测试库验证）：
   - **T1 修 protection 物化值**（域外值 + 派生脱钩统一按"物化=派生"对齐）：`protection_status NOT IN (active,expiring,expired)` → 按 expire_date 派生回填（id=573 类）；**`expire_date IS NULL` → 按 D7 回填（倾向：维持 active）**；`protection_status <> 派生值`（596 类：物化 expired/派生 active）→ 回填派生值。行数预估：1（非法）+ 31（NULL）+ 596 类（`status='sea' AND expire_date>今天`，测试库实测见 §1.4-③）。
   - **T2 清池表残留活跃行**（先于客户行归一）：同客户活跃行因 uk 已保证 ≤1，但存在"陈旧锁行 vs 已捞取"历史残留（客户 302 型）→ 按 D3 清理陈旧 `pending_approval`（保留 claimed 历史）。
   - **T3 归一 `status='sea'` 存量**（v2 新增，原方案缺失）：ownership 真源已归池表，客户行 `sea` 值退役——
     - `status='sea' AND deleted=0`（实测 29 行）→ `status='normal'`（protection 已由 T1 对齐派生）；
     - `status='sea' AND deleted=1`（实测 88 行）→ `status='normal'`（即 D6 选 a 的执行步；**若用户改选 D6=b 保留历史痕迹，则本步只做 deleted=0 部分，验收集相应放行 (sea,*,1)——两分支见验收门**）。
   - **T4 归一冗余值**：`status IN ('warning','expired') AND deleted=0`（实测 4+0 行）→ `status='normal'`（保护语义已由 protection 承载）。
   - **T5（按 D2）**：a) 删 `protection_status` 列（需先过"只写不读"观察期，见回滚）；或 b) 保留为物化缓存 + 补 `idx_protection_status`。
2. **读点替换的 SQL 形态与代价（v2 新增，评审 Q3）**：31 处 `notIn(sea,archived)`（§1.3 清单）统一替换为派生服务谓词，落 SQL 即反连接：
   ```sql
   status = 'normal' AND deleted = 0
   AND NOT EXISTS (SELECT 1 FROM biz_sea_pool p
                   WHERE p.customer_id = c.id AND p.status <> 'claimed')
   -- 等价高效形态(点查唯一键): AND NOT EXISTS (SELECT 1 FROM biz_sea_pool p
   --     WHERE p.tenant_id = c.tenant_id AND p.active_flag = c.id)
   ```
   代价评估：`biz_sea_pool` 有 `idx_customer_id` 与 `uk_sea_pool_active(tenant_id, active_flag)`（§1.1 实测），反连接为**唯一键点查**，非全表扫；池表当前 59 行、增长上界=客户量（每客户至多一条活跃行），客户量百级时代价可忽略。写法上每个列表/统计/提醒查询多一个 EXISTS 子句是真实成本（31 处逐处替换+回归），因此**必须经派生服务谓词封装**（Java 侧一个 `CustomerPredicates.ownedActive()`），禁止 31 处再复制粘贴裸 SQL——这也是 K7 的读侧架构规则（阶段二可先行发布"新代码禁止裸写该条件"）。
3. 代码退役：到期任务简化（W9/W10 不再写 `status`）；R13 离职查询改派生口径（顺带修复"只排 sea 不排 archived"的不一致）；`status` 查询参数域收窄（`warning/expired/sea` 值返回 410/空集 + 发版公告）。
4. 前端统一：PC/小程序共用一套三维字典（后端 DTO 直接输出 `lifecycle/ownership/protection` 三字段 + 展示标签，前端不再自行映射）；`@ExcelProperty` 导出列同步（导入模板兼容期保留旧列名，映射到新口径）。

**验证方法（验收门，v2 重写为可执行的断言且与 D6/D7/D2 分支绑定）**：

```sql
-- ① status 收窄(D6=a 默认分支)
SELECT COUNT(*) FROM biz_customer WHERE deleted=0 AND status NOT IN ('normal','archived');      -- 期望 0
SELECT COUNT(*) FROM biz_customer WHERE deleted=1 AND status NOT IN ('normal');                 -- 期望 0(D6=a)
--   若用户改选 D6=b: 上两条放宽为 ... AND status<>'sea' 允许 (sea,*,1) 存量,但新产生必须为 0
-- ② protection 合法性(D2=b 分支;D2=a 已删列则跳过)
SELECT COUNT(*) FROM biz_customer WHERE deleted=0
  AND (protection_status IS NULL OR protection_status NOT IN ('active','expiring','expired'));  -- 期望 0
--   物化=派生 diff(D7=a 时 NULL/active 同计): 期望 0
SELECT COUNT(*) FROM biz_customer c WHERE c.deleted=0 AND c.protection_status <> <按 expire_date 派生>;
-- ③ 归属真源唯一: 每租户每客户活跃池行 ≤1(uk 保证) + 客户行不再表达 sea(①已覆盖)
-- ④ 读路径抽样回归: 列表四 scope、工作台六指标、提醒三口径、导出、小程序列表/详情
--    ——迁移前后结果 diff(M8/M1a 客户归属与指标口径不变)
-- ⑤ 性能: R2 筛选走新索引(D2=b)或 idx_expire_date 派生谓词;反连接走 uk 点查(EXPLAIN 验证)
```

**影响面**：表结构、Flyway 版本、API 查询参数域、Excel 模板、全端字典；是三阶段中唯一有破坏性契约变更的阶段，必须与小程序发版窗口协同。

**回滚**：T1-T4 为幂等 UPDATE，回滚脚本按迁移前备份表反向执行；T5 删列**必须**在"阶段三上线后稳定观察 ≥2 个到期任务周期（≥3 天）且读路径零回退"之后单独执行，删列前先"只写不读"观察期；API 域收窄保留一个版本的兼容映射（旧值→新语义空集+告警日志）。

---

## 4. 风险清单与裁决点

### 4.1 风险清单

| # | 风险 | 触发点 | 缓解 |
| --- | --- | --- | --- |
| K1 | 到期任务与捞取/延期审批并发写同一客户行（凌晨 2 点任务 vs 审批随时） | 阶段二收口后仍有两入口 | 迁移服务内按 customerId 加事务级行锁（`SELECT … FOR UPDATE`）或乐观版本列；任务侧已有逐客户隔离（`CustomerExpireTask.java:98-101`）可承接 |
| K2 | 派生口径改变**已对外指标数字**，业务方质疑"数据丢了" | 阶段一试点 / 阶段三切换 | 切换前发布双口径对照表（§1.4 即模板）。**v2 补：除已量化的超期 29→0 外，还含两处——(a) `expire_date IS NULL` 31 行若 D7 选 b（unset 不计 active）将从"保护期内"指标静默掉出；(b) 596 类提前释放行物化 expired→派生 active 将从"超期"指标移出。三处变更都必须进 `metrics-definition.md` 变更记录与周报公告** |
| K3 | `PUT /{id}` 堵漏后，若有隐藏客户端依赖直改 status 的暗门行为 | 阶段二 W5 | 发版前在测试环境抓取编辑请求 payload 中含 status/protectionStatus 的调用（日志埋点）确认调用面为空；**v2 补：埋点必须覆盖 `PUT /{id}/full`（`CustomerController.java:166-174`）第二入口（评审 Q5）** |
| K4 | 池表作 ownership 真源后，池表自身脏数据放大影响 | 阶段二/三 | 阶段二 T2 前置清池表：uk_sea_pool_active 唯一键 2026-09-22 已收编（`V2026.09.22.1__sea_pool_active_unique.sql`），但其上线前的存量残留（客户 302：07-14 陈旧 pending_approval + 07-24 claimed 双行）需一次性裁决清理（随 D3） |
| K5 | 小程序与 PC 字典不同步导致同一客户两端显示不同状态（现状已存在：小程序缺 archived） | 阶段三 | 后端 DTO 直出三维+标签，前端删本地字典；两端各留一版兼容窗口 |
| K6 | `protection_status` 无索引，筛选全表扫 | 现状持续 | 阶段三 D2 裁决：保留列则建索引；删列则筛选走 `idx_expire_date` 派生 |
| K7 | 三阶段跨多个迭代周期，中途插入的需求继续在旧口径上加 `notIn(sea,archived)` 复制粘贴 | 全程 | 阶段二 L1 架构规则可先行发布"新代码禁止新增对这两列的直接读/裸条件"（读侧规则比写侧规则可更早落地），谓词统一走 `CustomerPredicates.ownedActive()` |
| K8 | 归档客户入池（M9 孤儿组合）在 lifecycle>ownership 仲裁下显示"归档但在池"，运营困惑 | 派生服务上线即有；**且现网代码可继续制造（W7/R13 无守卫）** | 派生结果带 `driftWarning` 标记；管理端出"状态异常客户"清单页面消化存量；**入口端按 D8 加守卫停止增量** |
| K9 | **v2 新增**：L1 架构锁要求 W13/W14 整实体 `updateById` 改造为选择性更新，改造面波及开票回算/转移审批两条核心链路 | 阶段二 | 改造本身即阶段二工程量主体；先行集成测试覆盖开票作废回算与转移审批的终态快照；若排期不允许，可降级为"L2 运行时拦截先上、L1 白名单分期缩编" |

### 4.2 裁决点（需用户拍板）

| # | 问题 | 选项 | 设计倾向 |
| --- | --- | --- | --- |
| **D1** | 名下到期客户（M8：办事处无公海池，到期不入海）在列表中如何呈现？ | a) 保持名下，protection=expired 标红；b) 视同公海（无池则不可见） | **a**——归属与风险正交，"没池子"不该让客户凭空消失；与 `CustomerExpireTask.java:120-122` 现行为一致 |
| **D2** | `protection_status` 终态？ | a) 删列，纯派生（expire_date+配置）；b) 保留为物化缓存+索引（省一次日期比较，接受缓存陈旧窗口） | **a**——量级（百级客户）派生零成本，消除一整个冗余真源；若未来量级上万再议 b |
| **D3** | 阶段二修复客户 302 类漂移（陈旧 `pending_approval` 池行 + 客户行 normal/active）时，以哪侧为准？ | a) 客户行为准→作废池行（客户回到名下）；b) 池行为准→客户行 ownership 仍判 sea | **b（仅限 ownership）**——池表是 ownership 真源且带完整入池事实（enter_reason）；**v2 修正：protection 不再"写回 expired"（原稿 D3-b 会写出与派生源冲突的物化值，302 的 expire_date=2027-07-23 派生=active），一律按派生取值**。子裁决：陈旧行处置——302 存在 07-14 pending_approval（claim_date=NULL）与 07-24 claimed 双行，按"活跃行"定义虽只有一条非 claimed 行，但其为陈旧锁行；**倾向作废 07-14 行**（捞取事实以 07-24 行为准），302 最终判 `active/owned/active` 无漂移。同类存量逐行人工清单确认 |
| **D4** | 延期审批（W11）执行时客户已在池：拒绝还是自动清池？ | a) 拒绝（"客户已入公海，请先捞取"）；b) 自动作废池行+完成延期 | **a**——审批动作作用于提交时快照，池行可能已被他人锁定；fail-fast 与 `ApprovalService.java:243-245` 转移校验前移的既有原则一致 |
| **D5** | 阶段一是否顺手修复 R7/R9 统计缺口（超期指标 29→0）？ | a) 是，读侧试点即修；b) 否，数字冻结到阶段三一起变 | **a**——缺口有实证（§1.4），读侧修复零回滚成本；但需先在周报口径公告（随 K2） |
| **D6** | `sea+deleted=1`（88 行）历史组合：阶段三 T3 已把它纳入归一步骤，确认归一为 normal+deleted？ | a) 归一（终态字段干净）；b) 保留（历史痕迹，验收集放行 (sea,*,1) 存量） | **a**——deleted=1 后 status 无读取方（所有查询先 deleted=0），归一无风险且让组合统计可作长期护栏；默认计划按 a 执行，选 b 则跳过 T3 后半步 |
| **D7** | **v2 新增**：`expire_date IS NULL`（31 行）的 protection 口径？ | a) unset 视同 active 计入指标/筛选（与 DDL 默认值现状连续）；b) unset 独立域值，指标单列"未设保护期" | **a**——保持指标连续，避免 31 行静默掉出"保护期内"；b 会改变现有指标构成，若选 b 必须随 K2 公告并同步 `metrics-definition.md` |
| **D8** | **v2 新增**：归档客户入池（手动释放/离职交接）拒绝还是放行？现状两入口均放行（W7/R13），M9 孤儿组合可继续制造 | a) `enterSea` 意图拒绝（"归档客户不入公海，请先恢复"）；b) 放行并接受 archived+sea 组合由派生仲裁 | **a**——归档语义=退出经营（B5：不参与统计/提醒/到期检查），入池与其互斥；离职交接时归档客户保持名下随交接或单独移交，产品语义更清晰 |

---

## 5. 附录：证据与复现命令

```bash
# 组合分布（§1.4 主表）
ssh aigicrm-test
docker exec -it shared-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --default-character-set=utf8mb4 aigicrm -e \
  "SELECT status, protection_status, deleted, COUNT(*) FROM biz_customer GROUP BY 1,2,3 ORDER BY 4 DESC;"'

# 归属双真源漂移 + 指标缺口量化（§1.4 附加）
SELECT 'sea_status_no_pool' AS drift, COUNT(*) FROM biz_customer c
 WHERE c.deleted=0 AND c.status='sea' AND NOT EXISTS
   (SELECT 1 FROM biz_sea_pool p WHERE p.customer_id=c.id AND p.status<>'claimed')
 UNION ALL
SELECT 'pool_not_sea_status', COUNT(*) FROM biz_customer c
 WHERE c.deleted=0 AND c.status<>'sea' AND EXISTS
   (SELECT 1 FROM biz_sea_pool p WHERE p.customer_id=c.id AND p.status<>'claimed');

# v2 新增：手动提前释放反例 / NULL 保护期 / 池表残留双行（§1.4 ②③④）
SELECT id, status, protection_status, deleted, expire_date FROM biz_customer WHERE id IN (302, 596);
SELECT p.id, p.customer_id, p.status, p.enter_date, p.enter_reason, p.claim_date
  FROM biz_sea_pool p WHERE p.customer_id IN (302, 596) ORDER BY p.customer_id, p.enter_date;
SELECT COUNT(*) FROM biz_customer WHERE deleted=0 AND expire_date IS NULL;   -- 31

# 索引现状
SHOW INDEX FROM biz_customer WHERE Column_name IN ('status','protection_status');
SHOW INDEX FROM biz_sea_pool;          -- idx_customer_id / idx_status / uk_sea_pool_active
SHOW COLUMNS FROM biz_sea_pool LIKE 'active_flag';   -- VIRTUAL GENERATED
```

代码锚点速查：写入点 W1-W14 见 §1.2；读取点 R1-R16 + 前端/小程序/Excel 见 §1.3；DDL `V1__baseline.sql:188-189`；池真源 `CustomerAccessService.java:100-113`；架构测试基建 `ArchitectureTest.java:22-37`；既有筛选行为测试 `CustomerServiceTest.java:354-376`。

> 本文档为纯设计产物：除本文件外未改动任何源码/SQL/配置；所有数据库操作均为只读 SELECT/SHOW。

---

## 6. 评审意见处置（v2）

评审 7 条意见逐条核实（代码走读 + 测试库只读复跑）后**全部成立、全部采纳**，无驳回项。处置明细：

| # | 意见 | 核实结果 | 处置 |
| --- | --- | --- | --- |
| Q3 | 阶段三 T1-T4 漏 `status='sea'` 存量（29 alive + 88 deleted），执行完过不了自己的验收门；31 处 notIn 读点替换 SQL 形态未给出 | 成立：测试库复跑确认 29/88 行仍在；原 T2 只覆盖 0 行的场景、T3 只覆盖 warning/expired | 迁移重排为 T1-T5（T3 新增专治 sea 存量，D6 两分支显式绑定验收门）；§3 阶段三新增反连接 SQL 形态（NOT EXISTS + `uk_sea_pool_active/idx_customer_id` 点查）与 31 处替换的代价评估（§3 阶段三-2） |
| Q2a | M3 断言 sea→protection=expired 与派生真源冲突；实测客户 596（手动释放，expire_date=2027-08-30） | 成立：`SeaService.java:196-198` 无条件写 expired 不动 expire_date；596 行复跑确认 | M3 改为"按 expire_date 派生（可为 expired/active/expiring）"；§1.5-2 收窄"一一映射"论断为仅到期路径成立；阶段一断言用例补 596 反例行 |
| Q2b | expire_date IS NULL（31 行）无映射行、击穿阶段三验收、T1 无回填规则、指标静默掉 31 行 | 成立：复跑确认 31 行（全部 normal/active，含在 M1 的 269 内） | 派生域新增 `unset`（§2.1/derive()）；映射表新增 M1a/M12；新增裁决点 D7（倾向=active 保口径连续）；T1 补 NULL 回填规则；K2 公告范围补 31 行与 596 类两处 |
| Q2c/D3 | D3-b"写回 sea/expired"与派生真源自相矛盾（302 expire_date=2027-07-23）；302 池表双行（pending_approval+claimed）未裁决取哪行 | 成立：302 池行复跑确认（07-14 pending_approval claim_date=NULL + 07-24 claimed 07-28 捞取） | D3 重写：修复只动 ownership（池行为准），protection 一律派生不写回；子裁决明确作废陈旧 07-14 锁行、以 07-24 捞取事实为准，302 终态 `active/owned/active`；K4 补 302 实证 |
| Q1a | 写入点漏 W13（`CustomerService.java:1340-1353` refreshLastInvoiceDate 整实体回写）与 W14（`ApprovalService.java:728` transfer 整实体回写）；setter 级 ArchUnit 锁拦不住整实体 updateById；W5 漏 `PUT /{id}/full` 第二入口 | 成立：四处代码逐一走读确认（1352/728 整实体 updateById；166-174 → 1395 → update()） | §1.2 新增 W13/W14、W5 补双入口；意图清单补 `recalibrateExpireFromInvoice/transferOwner`；架构锁重设计为三层（L1 ArchUnit 写语句粒度 / L2 MP 拦截器运行时令牌 / L3 可选触发器），并新增 K9 说明 W13/W14 改造是阶段二工程量主体；K3 埋点范围补 `/full` |
| Q1b | §2.1"入池入口已排除归档"前提错误：手动释放与离职交接均不拦 archived | 成立：`CustomerController.java:352-372`、`SysUserService.java:419-428`（查询仅 ne sea）、`SeaService.java:159-198` 均无归档守卫 | §2.1 不变量段重写为"现网可制造"；§1.5 新增第 8 条；新增裁决点 D8（enterSea 拒绝归档客户）；M9 备注同步 |
| Q1c | R11 行号 227 应为 231 | 成立：`grep -n notIn CustomerExpireTask.java` = 94/215/231 | R11 已修正为 94/215/231；§1.3 读点总数由"20+"修正为 31 处（评审清单逐处核对一致） |
