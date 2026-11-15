# 法布施实现与测试环境部署记录（2026-11-16）

## 已完成

### 数据库（测试环境）
- 迁移由 `V2026.11.15.1` 改名 `V2026.11.16.1`，避开 Flyway out-of-order。
- 新增 `V2026.11.16.2` 补法布施菜单组绑定。
- 测试库已到 **2026.11.16.2**；12 张 `biz_fabushi_*` 表、9 个权限码、16 条角色菜单绑定。
- 迁移前备份：`/tmp/pre-fabushi-20261116-002509.sql.gz`（测试服）。

### 后端
- 法布施后端已合并进测试工作树并部署测试环境，jar md5 `977744f834925ef35379ab022b3d492a`。
- 接口冒烟：
  - admin：books/posts/stats/personal/pending/team 全 200；
  - TY003（角色 06）：books/posts/stats/personal 200，`books/pending`、`stats/team` 403。
- 测试：`mvn test` **1085 tests, 0 failures**（含 FabushiStatsServiceTest 5 条 + 权限矩阵）。
- 权限字典已从测试库重新生成：104 条权限码（fabushi 9 条已登记）。

### PC
- 法布施 3 个页面/API/路由已合并进测试工作树并构建。
- 测试前端静态资源已部署（index.html md5 `fb891e6f83687a256dd17feefbe5797d`），fabushi JS 200。
- admin `/auth/info` 菜单已包含：法布施 / 书目管理 / 内容治理 / 统计看板。

### 小程序
- 新增内容流 `pages/fabushi/feed/index`、详情 `pages/fabushi/detail/index`，补 `getFabushiComments` API。
- 工作台新增「内容流」入口；发布页/书目页保留。
- 权限：`fabushi:view`、`fabushi:create`；admin 全局旁路。

## 未完成/阻塞

1. **小程序 UI 自动化（2026-11-16 01:25 已解除）**：`cli auto --auto-port 9424` 启动成功，automator `ws://127.0.0.1:9424` 可连接；工作台/内容流/详情/评论/木鱼/已读全部 PASS。证据：`test-results/fabushi/mp-evidence-20261116.json`。撤 `fabushi:view+create` 后应用中心入口与发布按钮消失、恢复后回归 PASS：`mp-revoke-evidence-20261116.json`。
2. **PC 权限中心 9 码回归（2026-11-16 已解除）**：在权限中心「法布施」组逐个勾选 9 码（每次 PUT 200，依赖置灰生效），全部勾选后再逐项恢复原始 2 码状态 PASS。证据：`test-results/fabushi/pc-perm-center-evidence-20261116.json`。
3. **源码与测试工作树分叉（2026-11-16 已解除）**：已收敛到集成分支 `integration/perm-fabushi-20261116`，后端 1,099 tests / 0 failures；详见 `docs/tree-convergence-20261116.md`。
4. 一期未做：附件图/音/视上传、共读活动 UI（表/权限码已预留）。

## 证据

- `test-results/fabushi/evidence-20261116.json`
- 权限字典：`docs/permission/permission-dict.md`（104 条）
- 迁移文件：`aigicrm-backend/src/main/resources/db/migration/V2026.11.16.1__fabushi_module.sql`、`V2026.11.16.2__fabushi_group_binding.sql`

## P0 安全修复（2026-11-16）

### 1. 法布施上传 scope
- `FileService.upload` scope 白名单加入 `fabushi`，保留 `fabushi:create` 二次校验。
- 测试环境实测 `POST /api/file/upload` + `scope=fabushi` 返回 **200**，file id=2686。
- 部署注意：测试环境 `/app/uploads/fabushi` 原先不存在且 `/app/uploads` 对 appuser 不可写；
  已创建目录并 chown 10001:10001。生产部署必须预建/授权该目录，否则上传仍会失败。

### 2. 密码策略
- 新增 `PasswordPolicy`：8–30 位，且至少含一个字母和一个数字。
- `ForgotPasswordResetRequest` 从 min=6 改为 min=8。
- `AuthService.resetPassword`、`SysUserService.changePassword` 统一调用 `PasswordPolicy.validate`。
- 前端忘记密码/顶栏改密/MP 改密文案同步。
- 新增 `PasswordPolicyTest`（3 tests）；测试工作树全量 **1,088 tests, 0 failures**。

### 3. PC 真实页面验收
Playwright + 测试环境真实登录态：
- `/fabushi/book` 标题「书目管理 - AIGICRM」，菜单含法布施/书目管理/内容治理/统计看板；
- `/fabushi/content`、`/fabushi/stats` 均正常加载；
- 截图：`test-results/fabushi/pc-fabushi-{book,content,stats}.png`。

### 4. MP 验收状态（2026-11-16 01:25 PASS）
- 开发者工具 `cli auto --auto-port 9424` 启动成功，automator 连接 `ws://127.0.0.1:9424`。
- 工作台：3 个入口（内容流/书目浏览/发布），个人统计 `myPosts=1/myComments=1/myReadCount=1`。
- 内容流：1 条种子内容（id=1），tab/分页加载正常；详情：正文/评论/木鱼计数正确。
- 木鱼：`liked 1→0→1`、计数同步；评论：新增 1 条并回显；已读：回内容流 `readByMe=true`。
- 撤权：临时隔离角色移除 `fabushi:view+create` 后，应用中心「法布施」组消失、工作台发布入口消失（3→2）、`canCreate=false`；恢复后全部回归。
- 验收中发现并修复 2 个小程序运行时缺陷：① 法布施 5 页 `require` 相对路径多一级 `../`（页面 JS 加载失败）；② 5 页误用 `res.data`，而 `utils/request.js` 已解包为 `body.data`（列表恒空）。修复后 `node --check` 92/92。
- 证据：`test-results/fabushi/mp-evidence-20261116.json`、`mp-revoke-evidence-20261116.json`、`mp-{workbench,feed,detail,detail-commented,revoked}.png`。

## 权限中心接入核查（2026-11-16）

### 已确认接入
- 菜单：`法布施` 组 + 3 页面 + 6 按钮，共 9 个 `fabushi:*` 码，status/visible=1。
- 资源/动作：`sys_resource` 1 行（module_code=fabushi）+ `sys_action` 9 行（view/create/book_view/book_manage/content_view/content_manage/stats_view/stats_export/activity_manage）。
- 角色绑定：admin 全 9 码；销售 06/行政 05 view+create；销售总监 0 view+create+stats:view。
- 数据范围：`role_perm_scope` 已从 incentive stats_view 拷贝 4 行（admin/0/05/06，all）。
- 权限中心元数据：新增 16.3 迁移补齐 `sys_permission_meta` 9 行；`fabushi:stats:view` 显示 DIRECT `fabushi/stats_view`。
- 依赖规则：create→view、book:manage→book:view、content:manage→content:view、stats:export→stats:view、activity:manage→view。
- 负例：只授 `fabushi:create` 不授 `fabushi:view` → **400 FIELD_PERM_REQUIRED**。
- 正例：授组+view+create → 200；TY003 `/auth/info` 返回两码，撤权后恢复。
- 权限中心 UI：`/system/config` 内「权限中心」页签可打开，截图 `pc-perm-center.png`。

### 结论
法布施已纳入统一权限中心，且菜单/动作/角色/范围/元数据/依赖完整；此前缺的是 `sys_permission_meta` 9 行，已用 16.3 补齐。

## 两树收敛（2026-11-16）

- 正式源码树 `feature/perm-kernel` 与测试部署树 `main` 已收敛到集成分支 `integration/perm-fabushi-20261116`。
- 法布施后端 12 表/9 码/3 迁移两树内容一致；本次补的是测试树缺失的小程序内容流/详情/权限刷新，以及源码树缺失的 V15.1–V15.5 权限底座。
- `FileService` 三方合并后同时保留：法布施 `fabushi:create` 上传门禁 + `fabushi:view` 下载旁路 + 11-14 版本字节清理修复（`FileService.java:335/358/1740`）。
- 权限中心范围目标已补 `fabushi:stats:view → (fabushi, stats_view)`（`PermCenterService.java:69`、`PermScopeManager.vue:193`），双源对账脚本 PASS。
- 收敛验证：后端 1,099 tests / 0 failures；前端 324 tests；UI build；小程序 92 JS 语法；权限码生成物 90 码独立真值 PASS；API 清单 264 接口新鲜度 PASS。
- 已补齐：小程序页面级 UI 验收 PASS、PC 权限中心 9 码逐项勾选/撤权回归 PASS；明细与证据见 `docs/tree-convergence-20261116.md`。

## 门禁与矩阵复跑（2026-11-16 02:00）

- 完整门禁 `bash scripts/gate.sh`：**PASS=9 FAIL=0**（后端/前端/小程序/构建/文档/不变量/全量契约/vue-tsc）。
- 4,704 格 API 矩阵：24 码 × 28 账号 × 7 范围 = **4,704 行，PASS 4,312 + DATA_GUARD 392 + FAIL 0**；矩阵克隆角色已恢复并清理。证据：`test-results/mp-matrix/api_matrix_final.json`。
- 复跑同时修复 3 个收敛引入的运行时缺陷（历史版本 `versions` 未声明、文件夹下载开关 `toggleDownloadable` 未实现、个人回收站分页读取错层）与 8 处契约漂移，详见 `docs/tree-convergence-20261116.md` 第 7 节。

## PC 端员工社区补齐（2026-11-16）

> 用户纠偏：PC 不能只有管理端，PC 与小程序应功能一致，只是页面形态不同。

### 新增 PC 员工端

- `/fabushi/feed`：内容流（心得/诵读/正能量）、我的统计、发布弹窗、进入详情
- `/fabushi/detail/:id`：正文、评论、木鱼、已读（进详情自动标记）
- `/fabushi/library`：已通过审核书目、分类/搜索、电子书下载
- 管理三页保留：`/fabushi/book`、`/fabushi/content`、`/fabushi/stats`

### 权限与菜单

- 新增迁移 `V2026.11.16.6__fabushi_pc_community_menus.sql`
- 新页面权限码复用 `fabushi:view`，未新增权限码
- 页面绑定从现有 `fabushi:view` 绑定复制（不硬编码角色）
- 默认可见：
  - admin：内容流/书目浏览/书目管理/内容治理/统计看板
  - 销售总监 0 / 行政 05 / 销售员 06：内容流/书目浏览
- PC 与 MP 共用同一套接口与权限，仅布局不同。

### 验收

- PC feed/detail/library/发布/评论/木鱼/已读实测 PASS
- `vue-tsc` 通过；`npm test` 324；`npm run build` 通过
- 角色菜单核对：admin 5 项、0/05/06 仅社区 2 项

### UI 设计令牌复核（2026-11-16）

用户指出：首版法布施 PC 页只加载了 `ui-design-pro` 技能，未真正消费 ZCode 设计码，页面存在 `#fff/#eef0f3/#2563eb` 硬编码。

修正：
- 移除页面自建 `page-container`，统一使用 layout 的 `.page-shell`（白色整体容器/16px 圆角/`--shadow-card`）。
- 颜色全部改为项目令牌：`--text-primary/secondary/muted/placeholder`、`--border-light`、`--primary-color`、`--el-fill-color-lighter`、`--el-bg-color`。
- 圆角/阴影改为 `--radius-md`、`--shadow-card-hover`。
- 列表卡改为浅灰瓦片，hover 变白抬升；书目卡 `align-items:start`，固定 140px 高，不再被 grid 拉伸。
- 验证：feed 双栏 1036+280、feed 卡 132px、book 卡 140×323、page-shell 16px、无横向溢出、分页在容器内；截图存 `test-results/menu-dict/fabushi-*-tokens.png`。

## PC 阅读原型与页面重做（2026-11-16）

### 原型

- 原型文件：[docs/prototypes/fabushi_pc_reader_proto_v1.html](prototypes/fabushi_pc_reader_proto_v1.html)
- 原型长图：[docs/prototypes/fabushi_pc_reader_proto_v1.png](prototypes/fabushi_pc_reader_proto_v1.png)
- 依据：**MP v4 行为**（A1/A2/A3/A4/B1/B4/C1/C4/C5）+ **PC v5 视觉**（深蓝侧栏/胶囊分类/筛选卡/主从布局）
- 5 屏：R1 内容流、R2 内容详情、R3 书目浏览、R4 在线阅读 PDF、R5 发布内容

### 页面落地

- `/fabushi/feed`：胶囊分类（心得/诵读/正能量）+ 筛选卡 + 双栏内容流 + 个人统计 + 发布弹窗
- `/fabushi/detail/:id`：正文/木鱼/评论 + 右侧作者/关联书目/阅读状态
- `/fabushi/library`：左类别栏 + 右书目卡 + 在线阅读/写心得
- `/fabushi/read/:bookId`：深色阅读壳 + PDF 在线阅读 + 下载兜底
- 全部使用 ZCode 设计令牌：`--hero-gradient`、`--text-*`、`--border-light`、`--radius-md`、`--shadow-card-hover`、`--el-fill-color-lighter`

### 实测

- PC feed：3 个胶囊 tab、双栏 1052+268、无横向溢出
- PC detail：右侧栏、5 条评论、已读状态
- PC library：左类别栏 220、书目卡、在线阅读入口
- PC reader：`/fabushi/read/2` iframe 加载 PDF，HTTP 200 `application/pdf`
- 发布弹窗：3 个类型胶囊
- 空库重放与 gate 另行记录

## v6/v5 全屏对齐（2026-11-16）

按用户「全做 要完整」与 ZCode 交接单，一期范围全部落地：

- PC：分享广场（原内容流）改名；30 天 NEW；书目 10 类全显；P6 书目管理改为胶囊分类+左书目/右治理表；P7 内容治理改为四分段+详情+下架/恢复；P9 统计看板接成员榜/类别热度/每日趋势/周月切换。
- 后端：`GET /fabushi/books/{id}`、`PUT /fabushi/posts/{id}/restore`、`GET /fabushi/stats/dashboard`；书目列表支持 `status=all`（仅 book:manage），非 approved 查询收口到 book:manage。
- MP：A1 工作台重构（hero+四格+五页签+搜索+推荐+动态）；A4 书目详情弹层（书卡/统计/作品/二期结构树标注）；B3 发布成功木鱼动画态；C5 个人四格并补 myComments。
- 权限：不新增码；恢复走 content:manage，看板走 stats:view，导出仍标二期。
- 二期未提前实现：附件图/音/视、fb_section 结构树、PDF 进度/续读、书架、阅读记录、提醒、统计导出、共读活动、MP PDF 阅读器。

## 真实权限矩阵与运行时修复（2026-11-16）

### 测试环境

- 地址：`http://111.229.37.220`
- Flyway：`2026.11.16.7`
- 后端：本次 jar；前端：本次 build
- 测试库已备份：`/data/aigicrm/backup/app-pre-full-fabushi-20261116-0500.jar`、`frontend-pre-full-fabushi-20261116-0500.tgz`

### 运行时发现并修复

1. 统计看板接口漏注册：源码有 DashboardService 但 Controller 未加 `/dashboard`，运行 404；已补并在测试环境验证 200。
2. 看板空 Map NPE：无书目时 `Map.of().get(null)` 抛 NPE；改 `Collections.emptyMap()`，周/月均 200。
3. 书目 `status=all` 未生效：服务仍按 status='all' 查询返回 0；改为 manage 时不加状态条件，非 manage 强制 approved。
4. 恢复接口清空留痕：`updateById` 默认忽略 null，改用 LambdaUpdateWrapper 显式 set null。
5. 部署后复测：`/books?status=all` 返回 3 条；`/books/3` 200；`/posts/2/restore` 200 且 offlineReason 清空；`/stats/dashboard?period=week|month` 200。

### 22,260 条真实 HTTP 权限矩阵

- 28 个启用用户 × 265 个端点 × 3 认证态（有效 token / 无 token / 伪造 token）
- 通过 21,216；异常 1,044
- 无权限却 2xx（permission_leak）：0
- 5xx：0
- 有权限却 401/403（非资源形状）：0
- 1,044 分类：
  - 790 条 403：`{id}=999999` 的资源/数据范围守卫，不是权限码缺失
  - 250 条 400：缺参数/请求形状在方法鉴权前返回，未返回业务数据；登记为“鉴权前 400”体验优化
  - 4 条 403：组织管理员空 body 无 orgId，触发“只能在管理组织内创建用户”守卫
- 法布施定向权限冒烟：admin/0/05/06 × 15 场景 = 60 例，全过
  - 0/05/06 无团队统计码 → 403；admin/0 → 200
  - 0/05/06 无 book:manage → 书目待审 403
  - 有 view 的 0/05/06 可读 approved 书目/内容

证据：
- `test-results/permission-matrix/result.json`
- `test-results/permission-matrix/summary.md`
- `test-results/permission-matrix/fabushi-role-smoke.json`

## 二期阅读链（2026-11-16，Round 2）

已实现：
- 后端 `/api/fabushi/shelf`、`/progress/{bookId}`、`/records`、`/settings`，均为本人维度 + `fabushi:view`
- 实体/Mapper：shelf、reading_progress、user_setting、section、activity、activity_join
- PC `/fabushi/shelf`：书架/阅读记录/提醒设置三页签；书目卡片“加入书架”
- PC 阅读器：读取/保存页码进度，iframe 以 `#page=N` 续读
- MP A1：书架/记录/统计页签接真实接口；阅读提醒设置页 `/pages/fabushi/settings/index`
- MP 书目详情：加入书架、系统 PDF 阅读（openDocument）并保存起始进度

已验证：
- 后端单测 1103 全绿
- PC 书架 1 条、记录 1 条、设置保存/读取 200
- 阅读进度 lastPage=8 保存/读取成功
- MP dev-verify 通过（50 页）
- 权限矩阵：新接口沿用 `fabushi:view`，role smoke 待加入

未完成（后续批）：附件图/音/视、章节树、MP PDF 页码回传、统计导出、共读活动、MP 团队统计页。

## 二期附件/章节/活动/导出（2026-11-16，Round 3）

- 附件：`POST/DELETE /posts/{id}/attachments`；文件必须 scope=fabushi；PC 发布/详情、MP 发布/详情已接
- 章节：`GET/POST/PUT/DELETE /books/{bookId}/sections`；P6 章节弹窗、MP A4 章节树已接
- 活动：列表/参与/发起/结束；PC 我的阅读活动页签、MP A1 活动卡已接
- 导出：`GET /stats/export` CSV（`fabushi:stats:export`）；PC 统计按钮已接
- 权限码生成物已补 `fabushi:activity:manage` / `fabushi:stats:export`
- 实测：附件图挂 post1 详情返回；章节树 1 条；活动创建/参与；CSV 200；PC 详情/章节/活动/导出 PASS
- API 288；MP 50 页；gate PASS=9；后端 1103
