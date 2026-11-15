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
