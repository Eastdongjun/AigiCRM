# 法布施业务完整性清单（2026-11-17）

> 用途：把「法布施」从页面入口到接口、再到验证证据逐条对齐，避免出现
> 「接口/单测都绿、真实业务操作却 404」这类完整性缺口。
> 关联：[功能清单 manifest](unified-feature-manifest.md)、[P2/P3 发布清单](fabushi-p2p3-release-notes-20261116.md)、[审计报告](audit-permission-matrix-20261116.md)。
>
> 术语更新（2026-11-25）：第一～五节是 11-17 当时的记录，正文里的模块名称已按现行名称更新（“内容治理”→“内容管理”，“分享广场”→“认知广场”，菜单改名见迁移 `V2026.11.23.8`），其余内容没有改。第六节的状态已按 2026-11-24 的代码更新。

## 一、业务流与验证状态

状态口径：✅ 运行态已验（UI/接口实测）；◐ 仅接口矩阵或单测覆盖；○ 未验证。

| # | 业务流 | 入口 | 关键接口 | 状态 | 证据 / 缺口 |
|---:|---|---|---|---|---|
| 1 | 书目新增/编辑 | PC 书目管理 `/fabushi/book` | `POST/PUT /fabushi/books` | ✅ | **2026-11-17 UI 回归**：新增书A/B/C 三本实测（弹窗四字段/门类页签 2→3/左侧列表联动/待审核计数 0→1，toast+截图+库内 id=5,6,7 三态一致）；编辑路径未单测 |
| 2 | 书目审核/驳回/停用 | PC 书目管理 | `PUT /books/{id}/approve|reject|disable` | ✅ | **2026-11-17 UI 回归**：通过（书B approved）/ 驳回+原因留痕（书A rejected+查看原因回显逐字）/ 停用（书C disabled）全链 toast+截图+库内一致 |
| 3 | 书目删除（软删） | PC 书目管理 | `DELETE /books/{id}` | ✅ | 四步实测：无权限 403 → admin 200 → 列表消失 → 重复删 404；T5f 书D 软删 deleted=1 复证 |
| 4 | **挂载电子书（上传→挂载）** | PC 书目管理「挂载电子书 PDF」 | `POST /file/upload` → `POST /books/{id}/pdf` | ✅ | **2026-11-17 修复双 /api 前缀后**：测试服上传 200、挂载 200；生产前端产物无 `/api/api/` |
| 5 | 章节树新增/删除 | PC 书目管理（章节弹窗） | `GET/POST/DELETE /books/{bookId}/sections` | ✅ | **2026-11-17 UI 回归**：书B 加章×2+节×1（序/部分/章/节四级下拉）+ 删节生效；库内 3 行 level_code=chapter/section，删除=软删 deleted=1 |
| 6 | 分享发布（含附件上传） | PC 认知广场 `/fabushi/feed`、MP 发布页 | `POST /fabushi/posts`、`POST /file/upload`、附件接口 | ✅（上传路径） | 上传路径同 #4 已修；正文发布 UI 未回归 |
| 7 | 评论/点赞/已读 | PC 详情、MP 详情 | `POST /posts/{id}/comments|like`、`POST /reads/mark` | ✅ | **2026-11-17 UI 回归（NO001）**：木鱼 toggle 双向 28→27（取消）→28（再赞）；评论 5→6 落库显示真名"马吉亮"；进详情自动标已读 |
| 8 | 内容下架/恢复 | PC 内容管理 `/fabushi/content` | `PUT /posts/{id}/offline|restore` | ◐ | 矩阵覆盖；UI 未回归（T5 交接单未点名，保持 ◐） |
| 9 | 书目浏览/详情 | PC `/fabushi/library`、MP A4 | `GET /fabushi/books*` | ✅ | P3 实测 + 矩阵 |
| 10 | 在线阅读（PC） | PC `/fabushi/read/:bookId` | `GET /file/download/{id}`、`GET/PUT /progress/{bookId}` | ✅ | Playwright 12/12（渲染/翻页回传/续读） |
| 11 | 在线阅读（MP） | MP web-view `/mp-fabushi-reader` | 同上 | ✅ | MP H5 5/5；**真机未验** |
| 12 | 我的觉醒/书架管理/阅读记录/提醒设置 | PC `/fabushi/shelf`、MP 觉醒/书架页签 | `/awaken/status`、`/shelf*`、`/records`、`/settings`、`/bookmarks*` | ◐ | 切片 6/7 单测+构建覆盖；觉醒聚合（Asia/Shanghai 日/月界）、书架三态/置顶/分组/批量、页码书签同页幂等、版式设置按书记忆均有单测；**真机/UI 回归未做** |
| 13 | ~~共读活动~~ | ~~PC 书架页签 / MP~~ | `/activities*` | ⛔ 已下线 | **2026-11-22 裁决 D22**：双端入口隐藏，接口冻结（`@Deprecated`），存量数据保留可查；权限码不回收 |
| 14 | 统计看板/团队/月度/导出 | PC `/fabushi/stats`、MP | `/stats/*` | ✅ | P3 实测 + 矩阵；D28 视觉升级（切片 7）零接口/口径改动 |
| 15 | 权限与菜单 | 权限中心 | 9 个 `fabushi:*` 码、6 子菜单 | ✅ | 矩阵 42,511 例 + UI 枚举；**2026-11-17 运行态复证**：NO001（销售总监）侧栏=员工 4 项（无书目管理/内容管理），admin=6 项全量 |

## 二、2026-11-17 修复项

- **挂载电子书/附件上传 404**：`book.vue`、`feed.vue` 写 `request.post('/api/file/upload')`，
  axios `baseURL=/api` → `/api/api/file/upload` → 404「请求的地址不存在」。
- 修复为 `'/file/upload'`；测试服上传/挂载 200；生产前端已更新。
- **门禁固化**：`scripts/check-ui-api-prefix.py`（含负例自证）已接入 `dev-verify.sh contracts`
  → `gate.sh [5b]` 每次必跑，同类双前缀直接变红。

## 三、仍存在的完整性缺口（如实披露）

1. **MP 真机 web-view**：仅 DevTools/HTTP 模拟验证，真机域名校验与性能未验（待体验版，1.0.72 已上传）；
2. **生产真实文件上传**：本次只验证了路由与路径（生产 `/api/file/upload` 返回 400 缺文件），未在生产写入测试文件；
3. **内容下架/恢复、书目编辑、书架/记录/提醒设置**：接口级矩阵覆盖，UI 未逐项回归（不在 11-17 T5 交接点名范围）；
4. **上传类型/大小边界**：用例已补（`scripts/test-fabushi-upload-limits.js`，2/2 实测），**但发现两个真实缺口待裁修复**（见第五节）。

## 四、门禁与清单的衔接

- 新增契约：`scripts/check-ui-api-prefix.py`（负例自证：违规必红、合法不误报）；
- 任何新增前端请求若再写 `request.*('/api/...')`，`gate.sh` 立即失败；
- **2026-11-17 新增**：`scripts/test-fabushi-upload-limits.js`（上传类型/大小边界用例，Node 原生 fetch，测试服可重复执行）；
- 本清单作为法布施业务的“完成定义”基线，新增业务流必须补行并给出验证证据。

## 五、2026-11-17 T5 补测发现的上传边界缺口 → ✅ 已修复（同日，待生产发布）

> 发现用例：`scripts/test-fabushi-upload-limits.js`；修复后同脚本转为验收用例（4/4 PASS，测试服实测）
> 用户令：「U1/U2 修复方案选型 按照建议修复」（2026-11-17）

| # | 缺口 | 修复（分层校验） | 文件 | 验证 |
|---|---|---|---|---|
| U1 | 挂 PDF 无类型校验（纯文本 fake.pdf 可上传+挂载） | ①**上传层魔数**：`.pdf` 扩展名上传须含 `%PDF-`（前 1024 字节内，PDF 规范 7.5.1），全站生效挡内容伪装；②**挂载层**：file_type 须=PDF（file_type=扩展名大写口径） | `FileService.java`（assertPdfMagic + uploadStorage 调用）、`FabushiBookService.java`（attachPdf） | 单测 6+7 例；负例自证（破坏校验→3 负例精准红）；测试服实测 fake.pdf 上传 400 |
| U2 | 「≤100MB」文案零实施 | ①**挂载层**：attachPdf 校验 file_size ≤ MAX_PDF_BYTES=100MB（只作用挂 PDF 场景，**不误伤 feed 音视频大附件**）；②**前端预检**：handlePdfChosen 提前拦 type/size（省 110MB 白传流量） | `FabushiBookService.java`、`book.vue` | 测试服实测 110MB 上传 200→挂载 400「PDF 不能超过 100MB(当前 110MB)」；真 PDF 全链 200 |

**修复后验收（测试服 111.229.37.220，后端 jar fcac2205，前端 index-Q-DEZwbc）**：
`4/4 PASS`（case1 假 PDF 上传 400 / case2 110MB 上传 200 / case2b 挂载 400 / case3 真 PDF 200+200）

**既有行为保持钉**（attachPdf 单测覆盖）：scope=fabushi 校验、fileId=null 400、404 均不回归。
**测试遗留**：书 D 已软删；fake/big110/real 等测试文件行留 fabushi scope（孤儿清理通道口径，测试环境）。
**待生产发布**：后端 jar + 前端 dist（与生产无关的 MP 无改动）；无 DB 迁移。

## 六、2026-11-23 需求变更（D1–D28）落地状态

> 依据：`Agent/05-领域/01-工程/10-项目/aigicrm/03-阶段/02-需求/REQUIREMENT-DELTA-20261122.md`（Q1–Q14 已确认）。
> 证据：切片 5/6/7 报告（`03-阶段/06-测试/02-证据/FABUSHI-SLICE{5,6,7}-20261123.md`）。

| 变更 | 状态 | 说明 |
|---|---|---|
| D1/D4/D5/D6 类型重构与命名排序 | ✅ 已落地 | `FabushiScopeType` 单源 + `V2026.11.23.1` 迁移；我的觉醒最前、认知广场第二 |
| D7 我的觉醒三板块 | ✅ 已落地 | `GET /fabushi/awaken/status`，当日/本月/累计；PC/MP 双端 |
| D8/D9/D10 三类上传 | ✅ 已落地 | 诵读（直接传/选书章节）、随施（免标题免书目）、再读一本（三级选书+章节自录） |
| D11 每日提醒触达 | ◐ **平台受限** | 设置/开关/时间可改；**微信小程序订阅消息真推送未实现**：缺 openid 与模板 ID，且类目授权受限。当前降级=应用内红点（`fabushiUnread`），按 Q8 如实标注，不伪造推送 |
| D12 图片编辑 | ◐ PC 已落地；MP 放大+裁剪+标注已写、未验证 | （2026-11-24 更正，原写“未做”；2026-11-25 补标注）PC 发布器：九宫格放大 / 裁剪 / 标注（`FabushiPublishDialog.vue`，标注只有红框）。MP：放大查看 + 裁剪 + 标注（划线 / 方框 / 圈，可选颜色与粗细，可撤销、清空；`pages/fabushi/image-editor`；node 层行为测试通过；开发者工具未登录、页面级冒烟未跑；**真机未验**，手工验收项见 `mobile-manual-qa-checklist.md` 的 P2b）。MP 标注的范围：原型 v7 标为“待裁（Q9）”，这里按已批准的上线计划正文补做 |
| D13 本人修改/删除 | ✅ 已落地 | `PUT/DELETE /fabushi/posts/{id}`，作者本人；软删留痕，治理链不受影响 |
| D14 复诵 | ✅ 已落地 | 独立 `biz_fabushi_recitation` 表 + 计数/列表；PC/MP 录音上传 |
| D15 认知广场三分区+按书浏览 | ✅ 已落地 | 三分区 + 再读一本按书下钻（`bookId` 服务端过滤） |
| D16 服务端搜索 | ✅ 已落地 | 用户名/书名/正文 LIKE，分页 |
| D17 电子书保留 | ✅ 回归不破坏 | PDF 逐页+进度+续读保留 |
| D18 类别字典重整 | ✅ 已落地 | `V2026.11.19.3` 已按客户 10 类改 label，value 不变，历史数据无损 |
| D19 阅读器版式设置 | ✅ 已落地 | （2026-11-24 更正，原写“批 1”）背景/缩放/对比度/裁边/单连续页/百分比 + 版式标记；重排、字号行距、书内搜索（11-23）；挂载前质检（`utils/pdf-inspect.js`，上传时提示无文本层/页面尺寸不一，可“仍然挂载”）；不做 OCR |
| D20 书架管理 | ✅ 已落地 | 续读大卡/封面网格/状态分组排序/批量/自建分组+10 类自动组 |
| D21 朋友圈式分享 | ✅ 已落地 | 信息流/九宫格/语音/操作行；发布器三通道 |
| D22 共读活动下线 | ✅ 已落地 | 双端入口隐藏；`FabushiActivityController` `@Deprecated`；manifest 标注已下线 |
| D23 阅读管理 | ✅ 已落地 | 想读/在读/已读完三态 + `finished_at`；阅读记录归入 |
| D24 阅读器体验扩充 | ◐ 书内搜索已落地；文本级划线未做 | （2026-11-24 更正）页码书签（存库）+ 百分比 + 书内搜索（有文本层才可用；影印版如实不可搜）；**文本级划线未做** |
| D25 PC 木鱼美化 | ✅ 已落地 | CSS 木鱼（渐变/敲击/波纹/+1），零后端改动 |
| D26 发布器多媒体 | ✅ 已落地 | 图片/拍摄/视频/说话/文件/位置，沿用现网附件能力 |
| D27 附件归档与权限显性化 | ✅ 回归核验 | 归档 `文件中心›法布施`；四层判定链与 `canViewFabushiFile` 未动 |
| D28 统计视觉升级 | ✅ 已落地 | PC hero/图标 KPI/奖牌/多彩类别；MP tile 渐变+趋势角标；零接口改动 |

**部署前置**（2026-11-24 更正）：`V2026.11.23.1` → `.2` → … → `.9`，共 **9 支**（原写 `.1` → `.4` 共 4 支的说法已过期；之后又加了 `.5` 补复诵表 `tenant_id`、`.6` 行业词表种子、`.7` 首启向导标记、`.8` “内容治理”改名“内容管理”、`.9` 统计看板移入“统计分析”组）。迁移**不随应用启动**，由 `scripts/deploy-db.sh` 在发布后端之前执行。新增表 3 张（recitation / shelf_group / bookmark），新增列 4 组（post 类型、shelf 四列、book layout_type）。无新增权限码。
