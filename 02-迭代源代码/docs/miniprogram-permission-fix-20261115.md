# 小程序权限生效修复记录（2026-11-15）

## 修复目标

PC 改权限/数据范围后，小程序不再只在“下一次请求”静默更新全局变量，而是：

1. 统一权限刷新入口，失败可重试；
2. 清理文件缓存，避免权限撤销后离线打开；
3. 通知当前页面重算入口/按钮/列表；
4. TabBar 按权限置灰；
5. 修正字段码父码、数据范围文案与 epoch 隔离。

## 代码改动

| 文件 | 改动 |
|---|---|
| `utils/permission-refresh.js`（新增） | 权限版本号、文件缓存清理、当前页 `onPermissionChanged` 通知、TabBar 刷新 |
| `api/auth.js` | `loadCurrentUser` 比较权限签名，变化时 markChanged；新增 `refreshPermissions(force)` 去重/3s 节流/成功返回 true |
| `utils/request.js` | epoch 为空也刷新；刷新成功才落 epoch；403 触发权限刷新；epoch 按用户+环境分键 |
| `app.js` | onShow 使用统一 refreshPermissions |
| `pages/app/index.js` | 应用中心 `computeApps()` 可在 onShow/onPermissionChanged 重算；admin 兜底 |
| `pages/home/index.js` | 权限版本变化绕过 30s 节流并重载 |
| `pages/files/index.js` | 文件页 `isAdmin() || file:view`；权限变化重新初始化；admin 按钮兜底 |
| `pages/customer/index.js`、`incentive/home/index.js`、`incentive/index.js`、`approval/index.js`、`reminder/index.js`、`sea/index.js`、`statistics/index.js`、`ranking/index.js` | 新增 `onPermissionChanged`，权限变化重载对应数据 |
| `pages/profile/index.js`、`profile/scope/index.js` | 范围标注“基础范围”，提示模块范围以实际列表为准；支持权限变化重载 |
| `pages/customer/form/index.js` | 日期/开票日字段同时校验父码 `customer:edit` |
| `custom-tab-bar/index.js/.wxml/.wxss` | 四项服务/文件 Tab 按权限置灰，点击提示无权限；admin/demo 放行 |
| `scripts/test-miniprogram-permission-refresh.js`（新增） | 版本递增/页面通知/缓存清理/TabBar 门禁单测 |
| `scripts/test-miniprogram-incentive-dial.js` | 同步已退役 UI 断言（非业务改动） |

## 验证

### DevTools 实测（正式源码目录）

撤 `file:view` 前后：

- 应用中心卡片：`[customer, files, incentiveQuery]` → `[customer, incentiveQuery]`
- 文件 Tab：`disabled=false` → `disabled=true`
- 无需重登；由下一次真实请求触发 epoch 刷新 + 页面重算

证据：`test-results/mp-matrix/permission_refresh_evidence.json`

### 单测

- `test-miniprogram-permission-refresh.js` PASS
- `test-miniprogram-request-failures.js` PASS
- 小程序单测 15/16 PASS（perf 脚本因 9423 端口/真实账号 BLOCKED）

### UI 回归

- `verify-mobile-shell.mjs` PASS
- `verify-file-center.mjs` PASS

## 仍未完成

- MP-P1 仍是“下一次请求/onShow”生效，不是实时推送。
- MP-P4 详情页仍主要靠后端 403，未全部接入 `onPermissionChanged`。
- MP-P10 后端尚未下发模块级生效范围摘要。
- MP-P12 下载 token 撤销窗口需后端逐请求校验复核。
- MP-P16 权限诊断页未实现。

## 阶段 1 追加（2026-11-15 晚）

### 已落地

- **详情页刷新**：客户详情、激励详情、审批详情、公海捞取详情新增 `onPermissionChanged`；失去查看权限时返回，否则重拉详情。
- **统一 admin 兜底**：文件页守卫/按钮已改为 `isAdmin() || hasPermission(...)`；应用中心 admin 全放行。
- **模块范围摘要**：后端 `/auth/info` 新增 `moduleScopes`（客户/激励/文件/公海/审批/提醒/统计），
  `RolePermScopeService.effectiveScopeCode` 按 USER>POSITION>ROLE、精确>通配、同层取最宽解析。
- **小程序展示**：我的页/数据范围页显示“基础范围 + 模块范围”，不再只显示基础范围。
- **下载 token**：后端下载端点已有 `@PreAuthorize('file:view')` + `canDownloadFile` 逐请求校验；
  撤权后旧下载 token 请求会被 403（代码复核；MP 本地 token 缓存后续可再清）。
- **字段码**：客户日期/开票日同时校验父码 `customer:edit`。

### 验证

- 后端测试：当前源码树 **1,063 tests, 0 failures**；测试部署树 **1,080 tests, 0 failures**。
- 小程序单测 **15/16 PASS**（perf 仍因 9423 端口/真实账号 BLOCKED）。
- DevTools：
  - 撤 `file:view` 后应用中心卡片 `[customer,files,incentiveQuery]` → `[customer,incentiveQuery]`，文件 Tab disabled；
  - 我的页模块范围显示：`客户 全部数据 · 激励 全部数据 · 文件 全部数据 · 公海 全部数据 · 审批 全部数据 · 提醒 全部数据 · 统计 全部数据`。
- 测试环境后端已部署 moduleScopes jar：md5 `a27811a476fcd00ebf895c44606ba83d`，备份 `/tmp/bak/app.jar.bak-modulescopes-20261115`。

### 证据

- `test-results/mp-matrix/permission_refresh_evidence.json`
- `test-results/mp-matrix/profile_scope_evidence.json`

## 阶段 2/3 追加（2026-11-15 晚）

- 新增**权限诊断页** `pages/profile/diagnostic/index`：显示当前用户、基础范围、权限版本、本地 epoch、7 模块范围、全部权限码，可复制权限码。
- 个人中心增加「权限诊断」入口。
- 补充剩余页面 `onPermissionChanged`：客户财务/联系人、激励上传、学分排行详情、统计详情、指标、文件夹表单；失去权限时返回。
- 应用中心/文件页 admin 兜底继续保留。
- 验证：
  - DevTools 诊断页实测 admin：**87 个权限码、7 个模块范围**；
  - 小程序语法 0 错误；单测 **15/16 PASS**（perf 仍 BLOCKED）；
  - 后端测试当前源码树 1,063、测试部署树 1,080 全通过。
- 证据：`test-results/mp-matrix/permission_diagnostic_evidence.json`。

## 阶段 3 追加（2026-11-15 晚）

- **admin 全局兜底**：`utils/auth.hasPermission` 对 admin 直接返回 true，所有页面入口/按钮与后端 admin 旁路一致；后端仍做二次校验。
- **剩余表单/子页守卫**：客户表单、财务表单、联系人表单/详情、客户分享、跟进日志、客户筛选、客户指标、文件选择器新增 `onPermissionChanged`；失权提示并返回。
- **验证**：
  - 新增 admin 兜底单测（admin 无权限码仍 hasPermission=true；普通用户仍按码）；
  - 小程序单测 **15/16 PASS**（perf 仍 BLOCKED）；
  - DevTools `verify-mobile-shell` / `verify-file-center` PASS；
  - 语法 0 错误。
- 说明：4,704 格 API 矩阵是后端权限码/范围矩阵，本轮仅改小程序显示层 + `/auth/info` 新增 moduleScopes 字段，不改变接口权限结果；矩阵基线仍为 FAIL=0。

## 阶段 3 收口（2026-11-15 深夜）

- 补齐 fabushi 工作台/书目/发布页的 `onPermissionChanged`：失 `fabushi:view/create` 时返回，否则重载。
- 业务页面（含详情/表单/子页）已全部接入权限刷新；仅 `profile/feedback`、`profile/wechat` 按登录态页面保留，不做业务权限门。
- **最终矩阵复跑（已重跑成功）**：首次执行连库失败、旧 JSON 曾误复制，已删除并清理测试角色；2026-11-16 00:00 重新跑完 24 码 × 28 账号 × 7 范围 = 4,704 格，**PASS 4,312 + DATA_GUARD 392，FAIL 0**。
- 证据：`test-results/mp-matrix/api_matrix_final.json`（mtime 2026-11-15 23:52）；单测 15/16 PASS（perf 仍 BLOCKED）。

## 两树收敛（2026-11-16）

- 小程序权限修复已从源码树整体移植到测试部署基线，集成分支 `integration/perm-fabushi-20261116`。
- 收敛后小程序前端 `node --check` 92/92 通过；源码树后端 `mvn test` **1,099 tests / 0 failures**；PC `npm test` **324/324**、`npm run build` exit 0。
- 小程序 UI DevTools 验收 **PASS**（2026-11-16 01:25）：automator 9424；法布施工作台/内容流/详情/评论/木鱼/已读 + 撤权/恢复入口消失均实测通过。证据 `test-results/fabushi/mp-evidence-20261116.json`、`mp-revoke-evidence-20261116.json`。
- 合并明细与证据见 `docs/tree-convergence-20261116.md`。

## 收敛后矩阵复跑（2026-11-16）

- 以收敛后后端 jar 重跑 24 码 × 28 账号 × 7 范围：**4,704 行，PASS 4,312 + DATA_GUARD 392 + FAIL 0**。
- 完整门禁 `bash scripts/gate.sh`：**PASS=9 FAIL=0**。
- 证据：`test-results/mp-matrix/api_matrix_final.json`；细节见 `docs/tree-convergence-20261116.md`。
