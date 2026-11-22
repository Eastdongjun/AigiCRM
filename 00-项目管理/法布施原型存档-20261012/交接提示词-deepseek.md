# 法布施模块 · DeepSeek 开发交接提示词

日期：2026-11-16 ｜ 移交方：ZCode（产品/UI 原型） ｜ 接收方：DeepSeek（开发执行）
仓库：`/Users/dongjun/东来/项目/02-已上线/01-AIGICRM`（Git 仓库已公开，提交前按公开标准自审）

---

## 0. 一句话任务

按已定稿的原型 v6/v5 完成「法布施」模块的开发对齐与二期排期落地：PC 与小程序功能一致（只是形态不同），一期口径以 `unified-feature-manifest.md` 的 `fabushi.*` 行为准，二期能力严格按本文第六节范围，不得提前实现或伪装成已上线。

## 1. 项目位置与环境

| 项 | 值 |
|---|---|
| 模块代码根 | `02-迭代源代码/`（`aigicrm-ui` PC Vue3+Element Plus ｜ `aigicrm-miniprogram` 原生小程序 ｜ 后端 Spring Boot `com.aigicrm.fabushi` 包） |
| 当前分支 | `feature/perm-kernel`（一期代码已实现，全绿，部署/push/MP upload 等用户明令，**不要擅自 push/部署**） |
| 数据字典 | `02-迭代源代码/docs/unified-feature-manifest.md` 的 `fabushi.*` 8 行 = 权威口径 |
| 权威文档 | 需求说明书 v1.1（`00-项目管理/法布施模块需求说明书-20261012.md`）＞ 技术设计方案 v2.0（`法布施模块技术设计方案-20261113.md`）＞ 原型 v6/v5 |

## 2. 原型文件（本目录）

| 文件 | 用途 |
|---|---|
| `fabushi_pc_proto_v6.html` | PC 9 屏交互原型（P1-P5 员工端 + P6-P9 管理端），开发时逐屏对照 DOM 结构/文案/权限标注 |
| `fabushi_mp_proto_v5.html` | 小程序 14 屏交互原型（A1-A4 / B1-B4 / C1-C6） |
| `fabushi_pc_proto_v6.png` / `fabushi_mp_proto_v5.png` | 整板视觉基准（评审对照用） |
| `fabushi_proto_v6_notes.md` | **先读**：屏索引、权限码-屏对照、一期/二期标注、与旧版差异、未实现清单 |
| `fabushi_pc_proto_v5.html` / `fabushi_mp_proto_v4.html` | 旧版存档，**只读勿改勿覆盖** |

## 3. 当前代码状态（先核对再动手，防止重复造轮子）

一期主体**已实现**（分支 `feature/perm-kernel`，manifest 状态=已实现）：

- **PC 7 条路由**（`aigicrm-ui/src/router/index.js:122-161`）：`/fabushi/feed`（内容流）、`/fabushi/detail/:id`、`/fabushi/library`（书目浏览）、`/fabushi/read/:bookId`（在线阅读，浏览器内建 PDF）、`/fabushi/book`（书目管理）、`/fabushi/content`（内容治理）、`/fabushi/stats`（统计看板五联卡+个人统计）；对应 `views/fabushi/` 7 个 vue。
- **MP 5 页**（`app.json`）：`pages/fabushi/index`（工作台，tabBar 页）、`book`、`upload`、`feed`、`detail`；底栏五项=首页/四项服务/法布施/应用/我的。
- 后端 `fabushi.*` 权限码 9 个已接入权限内核（WP7），迁移已落库。
- **未实现**（原型挂「二期」chip 的全部能力 + 三期）：见第 5 节。

你的第一步动作：拉通现状盘点（`git log --oneline -20`、manifest fabushi 行、`views/fabushi/` 与 `pages/fabushi/` 清单），输出「已有 vs 待建」对账表后再写代码。

## 4. 本期范围

**一期收尾（小改动）**：
1. PC 菜单第一项更名「内容流」→**「分享广场」**（2026-11-16 用户拍板）。同步三处：① `sys_menu` 菜单行名（侧栏由后端下发，走新 Flyway 迁移改行，禁硬编码 id、按 path=自然键定位）；② `router/index.js` meta.title 与 detail 页 parentTitle；③ 原型/manifest 行文同步。
2. 原型与实现的对齐走查：对照 v6/v5 原型逐屏核对文案、状态标签（待审核/通过/驳回/停用、发布中/已下架）、已读未读与 NEW 规则（30 天内显 NEW）、木鱼=点赞可切换、已读=用户+内容唯一重复浏览只刷新时间。差异列清单送用户确认后修。

**二期（待用户分批下令，先出方案不动工）**：附件图/音/视（fb_post_attachment，图 1-5 页/次、音 1-10 段×≤60s、视 1-5 段，fb_ 独立配额）· 结构化章节树（fb_section，序/部分/章/节）· PDF 进度记忆/续读（fb_reading_progress，PC 换掉 iframe 内建阅读、MP 新建 reader 页）· 书架（fb_shelf）· 阅读记录日历（fb_read_status 可视化）· 阅读提醒（fb_user_setting + 订阅消息授权降级应用内）· 统计升级（周榜 ISO 周、部门共读进度、月度报告、导出 Excel=fabushi:stats:export）· 共读活动（fb_activity/fb_activity_join，fabushi:activity:manage）。

**三期**：重点描述 · 语音跟读 · 成就徽章 · 提醒全量。

## 5. 权限与数据口径（不可偏离）

**9 个权限码**：`fabushi:view`（浏览/详情/已读/个人统计/法布施空间文件下载）、`fabushi:create`（发布/评论/木鱼/提交书目/上传）、`fabushi:book:view`（PC 书目管理页）、`fabushi:book:manage`（通过/驳回/停用/挂PDF/待审队列）、`fabushi:content:view`（PC 内容治理页）、`fabushi:content:manage`（下架，必填原因+全量留痕）、`fabushi:stats:view`（团队统计，独立数据范围档 fabushi/stats_view）、`fabushi:stats:export`（导出，二期）、`fabushi:activity:manage`（共读活动，二期）。

**角色默认**：admin=全部；销售总监0=view/create/stats:view；行政05/销售员06=view/create。PC 管理三页默认仅 admin；员工侧栏不出现无权限菜单。

**数据模型**（技术方案 v2.0 §2，以分支内已落库迁移为准，增量改动出新迁移文件、禁改历史迁移）：6 个字典类型（fabushi_category 10 预置类/……）+ fb_book / fb_section / fb_post（统一内容表，scope_type=heart/recitation/znl）/ fb_post_attachment / fb_comment / fb_read_status / fb_user_setting / fb_shelf / fb_reading_progress / fb_activity / fb_activity_join。**类别走字典管理，无独立 fb_category 表**。书目状态 pending/approved/rejected/disabled；内容 published/offline。

**硬口径**：木鱼=点赞（同一资产两用，无独立点赞按钮）；已读=用户+内容唯一；30 天内新内容显 NEW；员工提交书目→待审核→admin 通过才进书目浏览；下架后前端不可见、后台留痕可恢复；阅读时长/续读率/点赞人列表后端无字段，任何页面不得编造。

## 6. 菜单口径

- PC 顶层七组：首页→业务核心→流程协同→学分管理→**法布施**（分享广场/书目浏览/书目管理/内容治理[待审角标]/统计看板）→统计分析→系统管理。
- MP 底栏：首页/四项服务/法布施/应用/我的（A1=tabBar 页）；应用中心顺序：客户经营→文件管理→四项激励→法布施→学分管理→统计分析→流程协同。
- 权限即时生效口径：勾选即生效/取消即收回，同 token 双端立即验证（USER 级勾选双端立马生效是硬验收标准）。

## 7. 工程与提交纪律

1. Flyway 迁移：新增文件按 `V2026.11.XX.x__xxx.sql` 命名；禁硬编码 ID，一律自然键；utf8mb4。
2. 权限码改动需同步：FROZEN 登记册 + labelMap 中文名 + MatrixTest 断言 + manifest 行（状态/路由/权限码列）。
3. 前端样式引用 `aigicrm-ui/src/style.css` `:root` 设计令牌与 `tokens-and-patterns.md` 已验证模式，不散落硬编码色值；图标 inline SVG 禁 emoji。
4. 零侵入：不动客户/激励/文件/权限内核等其他模块代码；不动旧原型存档。
5. 不连接生产/测试数据库做写操作；不擅自 push/部署/MP upload（等用户明令）；提交信息与契约门禁照仓库 AGENTS.md，提交前 mvn + dev-verify + contracts 跑绿。
6. **仓库已公开**：提交前按公开标准自审——内部管理文档、口令/IP、真实人员信息不入库（原型中的姓名均为演示数据，若需入库请用户确认）。

## 8. 验收清单（对齐需求书 §七 + 原型）

- [ ] 书目体系：10 类字典落库；非 admin 不能改类别；待审核书目仅管理端可见。
- [ ] 内容链：三类发布（文字版）→ feed 展示 → 详情/评论/木鱼/已读 → 治理下架留痕，全链与原型 P1/P2/P5/P7、A2/A4/B2/B4 一致。
- [ ] 在线阅读：PC 有 PDF 书目才出「在线阅读」；下载走 fabushi:view；进度记忆不得假装已实现。
- [ ] 权限：9 码矩阵 403/200 对号；员工侧栏不见管理三页；USER 级勾选即时生效双端实证。
- [ ] 统计：五联卡/成员榜/类别热度/趋势数字与底表可对账；导出按钮标二期不接假接口。
- [ ] 走查：对照两张 PNG 逐屏核对布局/文案/状态色，二期 chip 不缺不冒充。

---

*交接完。先盘点、再对齐、后开发；每一步的产出先给用户过目再落码。*
