# 批次申报 · UI-STATS-REDESIGN-001 法布施统计看板重设计（PC 12 项 + MP 6 项）

> 申报方：实施/协调岗（Claude，接替 ZCode 限额中断的干活窗口）
> 申报时间：2026-11-19 07:50 UTC+8
> **HEAD：`8d2b4344`**（基线 `45e55eb3` 已 SCAN_DONE）
> 工作区状态：**源码已冻结**（`git status -- 02-迭代源代码` 无 M/D；仅历史 xlsx/docs 未跟踪噪音）

---

## 一、改动摘要（11 文件，+703/−60）

| 文件 | 改动 |
|---|---|
| `FabushiDashboardService.java` | +192：周期已读人数/周期覆盖率/每日一读达成率/书目阅读 rollup/类别已读聚合；月度扩 `bookRanking`/`categoryReads` |
| `FabushiStatsService.java` | +72：personal 扩 `myReadDays`/`myStreakDays`/`myTopCategory`；`DICT_FABUSHI_CATEGORY` 共享常量 |
| `FabushiStatsController.java` | +50：周报/月报 CSV 扩口径（周期已读人数/达成率/书目区块/类别区块） |
| `FabushiBookService.java` | ±4：`fabushi_category` 两处存量字面量改共享常量（字典红线收口） |
| `FabushiStatsRedesignTest.java` | 新增 306 行 / 7 用例（多用户×多书×多日、断日/跨月 streak、达成率分母、feed 排除、可见集收口、月报扩字段） |
| `FabushiMonthlyReportTest` / `FabushiStatsServiceTest` | 构造器同步（新增 section/book/setting/dict 依赖） |
| `aigicrm-ui/src/views/fabushi/stats.vue` | +104：KPI 四卡/成员榜列序/书目榜/类别口径/月报分组/我的六格 |
| `aigicrm-miniprogram/pages/fabushi/{index.js,wxml,wxss}` | M1 数据格（连续天数/最常读类别）+ M4 书目榜 Top5 + M6 发布降位 |

## 二、口径实现（对照批规格 §二，QC 按此复算）

1. **periodReadUsers**：周期内 read_status distinct user，可见集内存收口（visible=null 全量档）。
2. **书目 rollup**：read→post（仅 PUBLISHED 且有 sectionId）→section.book_id→book(title,categoryCode)；feed/无章节链/未发布帖排除；行=bookId/title/category/categoryName/readUsers(distinct)/readCount/latestReadAt(max)；人次 desc Top10。
3. **categoryReads**：rollup 按 categoryCode 再聚合 readCount desc；类名运行时从字典 `fabushi_category` 解析（缺行回退 code）。
4. **dailyGoalRate**：分母=周期起至今日（含）逐日，「提醒开启∧freq 匹配当日（daily=恒真/workday=周一~五/weekend=周六日）」的人日；分子=其中当日有 ≥1 条 read 的人日；无设置者不入分母；分母 0 → `null`（页面 —）。
5. **myStreakDays**：distinct 阅读日自今日（今日无读自昨日）逐日回溯至首个断日，跨月不限窗口。
6. **myTopCategory**：本人全量 read→同链路→类别累计人次 top1，字典 label；无数据 null。
7. **月报**：`monthly()` 新增同一 rollup（任意月口径）——规格预估零改动，实测需扩两字段，**如实申报**。

## 三、PC 12 项对账

| # | 元素 | 状态 |
|---|---|---|
| 1 | KPI 四卡（今日/周期已读人数、周期覆盖率、每日一读达成率） | ✅ |
| 2 | KPI 行撤出发布/木鱼/评论 | ✅ |
| 3 | 成员榜列序（名次/成员/部门/已读天数/已读人次/发布/获木鱼/活跃度），activityPct 未动 | ✅ |
| 4 | 书目阅读榜（五列/Top10/.table-scroll/纯展示） | ✅ |
| 5 | 阅读类别分布换已读人次（仅书目帖，feed 不计） | ✅ |
| 6 | 每日一读趋势保留 | ✅（未改） |
| 7 | 部门进度保留 | ✅（未改） |
| 8 | 月度报告分阅读组/互动组（环比保留） | ✅ |
| 9 | 我的法布施 6 格（累计天数/连续天数/最常读类别/阅读人次+我的内容/获木鱼） | ✅ |
| 10 | 筛选栏保留 | ✅（未改） |
| 11 | CSV 周报+月报扩口径（头指标/书目区块/类别区块，页面同源） | ✅ |
| 12 | 新表空态沿用 .empty | ✅ |

## 四、MP 6 项对账

| # | 元素 | 状态 |
|---|---|---|
| M1 | 数据格：累计阅读(人次)/连续阅读天数/**最常读类别**/我的内容 | ✅（见偏差①） |
| M2 | 团队阅读·本周保留 | ✅ |
| M3 | 部门已读排行保留 | ✅ |
| M4 | 书目阅读榜·本周 Top5（书名+类别+人次） | ✅ |
| M5 | 月度保留 | ✅ |
| M6 | 发布类仅保留"我的内容"一格，不另设互动区 | ✅ |

## 五、门禁实测（实施方本地，QC 须自跑复验）

| 门禁 | 命令 | 结果 |
|---|---|---|
| 后端 | `mvn test jacoco:report` | **1227 tests / 0 fail / 0 error / 0 skip**；`target/site/jacoco/jacoco.xml` 已重生成 |
| 契约 | `bash scripts/dev-verify.sh contracts` | **exit 0；148 PASS / 0 FAIL / 1 SKIP**（文件权限 e2e 默认不跑） |
| 前端 | `npm test` | **340/340** |
| typecheck | `bash scripts/check-ui-typecheck.sh` | **536/536 持平**（不升） |
| S1192 自检 | 变更 Java 文件字面量 grep | 主代码无新增 ≥3 重复字面量；`readUsers` 补常量 |
| diff 卫生 | `git diff --check` | 干净 |

**预期 QC 复扫指标**：gate `AIGICRM-P0-5` OK；new blocker/critical/major/bugs/vuln = 0；critical/blocker/vulnerabilities = 0；S1068 计数不变；S3776 三族不增；new S1192 = 0。

## 六、如实申报（不扩大口径）

1. **MP M1 标签偏差**：现有两格数据（`myReadCount`/`myPosts`）均为**累计口径**（无时间过滤）。规格 M1 文字写"本月已读/本月发布（保留）"与数据语义不符；实现按真实语义标为"累计阅读(人次)/我的内容"，请 QC 裁决（若要求按规格字样，只需改标签，不改数据）。
2. **月报后端非零改动**：规格 §二预估 monthly 零改动，CSV 任意月书目/类别区块需要数据源，实测扩 `bookRanking`/`categoryReads` 两字段（同一 rollup）。
3. **MP 书目榜行**：在"书名+人次"基础上复用现有 `rank-dept` 位显示类别名（只读展示，不影响 Top5 判定）。
4. **构造器同步**：`FabushiStatsService`/`FabushiDashboardService` 新增 final 依赖，两处既有测试构造同步（无断言变更）。

## 七、QC 验收清单（规格 §八）

1. PC 12 项 + MP 6 项逐项对账；
2. 口径独立复算：测试库 SQL 直查 类别人次/书目榜/达成率/streak 与接口返回对账；
3. 截图复验（Chrome channel + --no-proxy-server）：布局落地 + `45e55eb3` 五子项零回归；
4. CSV 抽查：导出数字 vs 页面数字一致；
5. 全门禁 QC 自跑（不采信本文件数值）；
6. 字典跟随实测（测试服）：加临时类别→统计出现→改名跟随→停用消失→还原字典。

## 八、后续（QC SCAN_DONE 后）

推送窗口（用户已预授权：统计页做完重新推送）：PC dist + 后端 jar（若含聚合改动）+ MP `cli upload`/体验版二维码交用户真机复验；窗口纪律照旧（回滚锚点先行、测试服先行、L5 终验）。
