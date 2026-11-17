# 法布施业务完整性清单（2026-11-17）

> 用途：把「法布施」从页面入口到接口、再到验证证据逐条对齐，避免出现
> 「接口/单测都绿、真实业务操作却 404」这类完整性缺口。
> 关联：[功能清单 manifest](unified-feature-manifest.md)、[P2/P3 发布清单](fabushi-p2p3-release-notes-20261116.md)、[审计报告](audit-permission-matrix-20261116.md)。

## 一、业务流与验证状态

状态口径：✅ 运行态已验（UI/接口实测）；◐ 仅接口矩阵或单测覆盖；○ 未验证。

| # | 业务流 | 入口 | 关键接口 | 状态 | 证据 / 缺口 |
|---:|---|---|---|---|---|
| 1 | 书目新增/编辑 | PC 书目管理 `/fabushi/book` | `POST/PUT /fabushi/books` | ✅ | **2026-11-17 UI 回归**：新增书A/B/C 三本实测（弹窗四字段/门类页签 2→3/左侧列表联动/待审核计数 0→1，toast+截图+库内 id=5,6,7 三态一致）；编辑路径未单测 |
| 2 | 书目审核/驳回/停用 | PC 书目管理 | `PUT /books/{id}/approve|reject|disable` | ✅ | **2026-11-17 UI 回归**：通过（书B approved）/ 驳回+原因留痕（书A rejected+查看原因回显逐字）/ 停用（书C disabled）全链 toast+截图+库内一致 |
| 3 | 书目删除（软删） | PC 书目管理 | `DELETE /books/{id}` | ✅ | 四步实测：无权限 403 → admin 200 → 列表消失 → 重复删 404；T5f 书D 软删 deleted=1 复证 |
| 4 | **挂载电子书（上传→挂载）** | PC 书目管理「挂载电子书 PDF」 | `POST /file/upload` → `POST /books/{id}/pdf` | ✅ | **2026-11-17 修复双 /api 前缀后**：测试服上传 200、挂载 200；生产前端产物无 `/api/api/` |
| 5 | 章节树新增/删除 | PC 书目管理（章节弹窗） | `GET/POST/DELETE /books/{bookId}/sections` | ✅ | **2026-11-17 UI 回归**：书B 加章×2+节×1（序/部分/章/节四级下拉）+ 删节生效；库内 3 行 level_code=chapter/section，删除=软删 deleted=1 |
| 6 | 分享发布（含附件上传） | PC 分享广场 `/fabushi/feed`、MP 发布页 | `POST /fabushi/posts`、`POST /file/upload`、附件接口 | ✅（上传路径） | 上传路径同 #4 已修；正文发布 UI 未回归 |
| 7 | 评论/点赞/已读 | PC 详情、MP 详情 | `POST /posts/{id}/comments|like`、`POST /reads/mark` | ✅ | **2026-11-17 UI 回归（NO001）**：木鱼 toggle 双向 28→27（取消）→28（再赞）；评论 5→6 落库显示真名"马吉亮"；进详情自动标已读 |
| 8 | 内容下架/恢复 | PC 内容治理 `/fabushi/content` | `PUT /posts/{id}/offline|restore` | ◐ | 矩阵覆盖；UI 未回归（T5 交接单未点名，保持 ◐） |
| 9 | 书目浏览/详情 | PC `/fabushi/library`、MP A4 | `GET /fabushi/books*` | ✅ | P3 实测 + 矩阵 |
| 10 | 在线阅读（PC） | PC `/fabushi/read/:bookId` | `GET /file/download/{id}`、`GET/PUT /progress/{bookId}` | ✅ | Playwright 12/12（渲染/翻页回传/续读） |
| 11 | 在线阅读（MP） | MP web-view `/mp-fabushi-reader` | 同上 | ✅ | MP H5 5/5；**真机未验** |
| 12 | 书架/阅读记录/提醒设置 | PC `/fabushi/shelf`、MP C1-C3 | `/shelf`、`/records`、`/settings` | ◐ | 矩阵覆盖 |
| 13 | 共读活动 | PC 书架页签 / MP | `/activities*` | ✅ | **2026-11-17 UI 回归**：admin 发起（bookId=6 解析书名正确）+ admin 参与（0→1 人）+ NO001 参与（1→2 人）；「发起活动」按钮仅 admin 可见=权限门禁正确；库内 activity + 2 join 行一致 |
| 14 | 统计看板/团队/月度/导出 | PC `/fabushi/stats`、MP | `/stats/*` | ✅ | P3 实测 + 矩阵 |
| 15 | 权限与菜单 | 权限中心 | 9 个 `fabushi:*` 码、6 子菜单 | ✅ | 矩阵 42,511 例 + UI 枚举；**2026-11-17 运行态复证**：NO001（销售总监）侧栏=员工 4 项（无书目管理/内容治理），admin=6 项全量 |

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
