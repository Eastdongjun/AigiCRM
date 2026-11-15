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

1. **小程序 UI 自动化 BLOCKED**：开发者工具 automator 重启后不再监听 9420–9423，无法完成页面级实测；`wx.request` 直连 `/api/fabushi/posts` 已返回 200 和种子数据，但页面 UI 未验证。
2. **PC 浏览器实测未做**：只验证构建产物/菜单/接口；未做浏览器登录后三页点击回归。
3. **源码与测试工作树仍分叉**：正式源码树缺已部署的 V15.1–V15.5 权限迁移/后端修复；测试工作树缺小程序内容流页面。合并评审仍需完成。
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

### 4. MP 验收状态
- 开发者工具 automator 无法启动/监听端口，**BLOCKED**；
- 已验证直连 `/api/fabushi/posts` 返回 200 和种子数据；
- 页面代码语法通过，但未做真实点击验收，不能宣称 MP UI PASS。

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
- 仍未完成：小程序页面级 UI 验收（BLOCKED）、PC 权限中心 9 码逐项勾选/撤权回归。明细见 `docs/tree-convergence-20261116.md`。
