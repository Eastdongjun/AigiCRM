# 法布施业务完整性清单（2026-11-17）

> 用途：把「法布施」从页面入口到接口、再到验证证据逐条对齐，避免出现
> 「接口/单测都绿、真实业务操作却 404」这类完整性缺口。
> 关联：[功能清单 manifest](unified-feature-manifest.md)、[P2/P3 发布清单](fabushi-p2p3-release-notes-20261116.md)、[审计报告](audit-permission-matrix-20261116.md)。

## 一、业务流与验证状态

状态口径：✅ 运行态已验（UI/接口实测）；◐ 仅接口矩阵或单测覆盖；○ 未验证。

| # | 业务流 | 入口 | 关键接口 | 状态 | 证据 / 缺口 |
|---:|---|---|---|---|---|
| 1 | 书目新增/编辑 | PC 书目管理 `/fabushi/book` | `POST/PUT /fabushi/books` | ◐ | 权限矩阵覆盖；UI 编辑未逐项回归 |
| 2 | 书目审核/驳回/停用 | PC 书目管理 | `PUT /books/{id}/approve|reject|disable` | ◐ | 矩阵覆盖；UI 未回归 |
| 3 | 书目删除（软删） | PC 书目管理 | `DELETE /books/{id}` | ✅ | 四步实测：无权限 403 → admin 200 → 列表消失 → 重复删 404 |
| 4 | **挂载电子书（上传→挂载）** | PC 书目管理「挂载电子书 PDF」 | `POST /file/upload` → `POST /books/{id}/pdf` | ✅ | **2026-11-17 修复双 /api 前缀后**：测试服上传 200、挂载 200；生产前端产物无 `/api/api/` |
| 5 | 章节树新增/删除 | PC 书目管理（章节弹窗） | `GET/POST/DELETE /books/{bookId}/sections` | ◐ | 矩阵覆盖；UI 未回归 |
| 6 | 分享发布（含附件上传） | PC 分享广场 `/fabushi/feed`、MP 发布页 | `POST /fabushi/posts`、`POST /file/upload`、附件接口 | ✅（上传路径） | 上传路径同 #4 已修；正文发布 UI 未回归 |
| 7 | 评论/点赞/已读 | PC 详情、MP 详情 | `POST /posts/{id}/comments|like`、`POST /reads/mark` | ◐ | 矩阵覆盖 |
| 8 | 内容下架/恢复 | PC 内容治理 `/fabushi/content` | `PUT /posts/{id}/offline|restore` | ◐ | 矩阵覆盖 |
| 9 | 书目浏览/详情 | PC `/fabushi/library`、MP A4 | `GET /fabushi/books*` | ✅ | P3 实测 + 矩阵 |
| 10 | 在线阅读（PC） | PC `/fabushi/read/:bookId` | `GET /file/download/{id}`、`GET/PUT /progress/{bookId}` | ✅ | Playwright 12/12（渲染/翻页回传/续读） |
| 11 | 在线阅读（MP） | MP web-view `/mp-fabushi-reader` | 同上 | ✅ | MP H5 5/5；**真机未验** |
| 12 | 书架/阅读记录/提醒设置 | PC `/fabushi/shelf`、MP C1-C3 | `/shelf`、`/records`、`/settings` | ◐ | 矩阵覆盖 |
| 13 | 共读活动 | PC 书架页签 / MP | `/activities*` | ◐ | 矩阵覆盖 |
| 14 | 统计看板/团队/月度/导出 | PC `/fabushi/stats`、MP | `/stats/*` | ✅ | P3 实测 + 矩阵 |
| 15 | 权限与菜单 | 权限中心 | 9 个 `fabushi:*` 码、6 子菜单 | ✅ | 矩阵 42,511 例 + UI 枚举 |

## 二、2026-11-17 修复项

- **挂载电子书/附件上传 404**：`book.vue`、`feed.vue` 写 `request.post('/api/file/upload')`，
  axios `baseURL=/api` → `/api/api/file/upload` → 404「请求的地址不存在」。
- 修复为 `'/file/upload'`；测试服上传/挂载 200；生产前端已更新。
- **门禁固化**：`scripts/check-ui-api-prefix.py`（含负例自证）已接入 `dev-verify.sh contracts`
  → `gate.sh [5b]` 每次必跑，同类双前缀直接变红。

## 三、仍存在的完整性缺口（如实披露）

1. **MP 真机 web-view**：仅 DevTools/HTTP 模拟验证，真机域名校验与性能未验（待 upload 1.0.71）；
2. **生产真实文件上传**：本次只验证了路由与路径（生产 `/api/file/upload` 返回 400 缺文件），未在生产写入测试文件；
3. **书目新增/审核/驳回/停用、章节、评论/点赞、共读活动**：接口级矩阵覆盖，UI 未逐项回归；
4. **上传文件类型/大小边界**：`≤100MB` 提示与后端限制未做专项用例。

## 四、门禁与清单的衔接

- 新增契约：`scripts/check-ui-api-prefix.py`（负例自证：违规必红、合法不误报）；
- 任何新增前端请求若再写 `request.*('/api/...')`，`gate.sh` 立即失败；
- 本清单作为法布施业务的“完成定义”基线，新增业务流必须补行并给出验证证据。
