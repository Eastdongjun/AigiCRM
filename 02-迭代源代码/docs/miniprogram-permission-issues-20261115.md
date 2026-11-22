# 小程序权限问题清单（2026-11-15）

## 结论先说

- 小程序与 PC **是同一套后端权限**：同一 `sys_role_menu` 功能码、同一 `role_perm_scope` 数据范围、同一接口鉴权，不存在第二套权限。
- PC 改权限后，小程序**不是实时推送生效**；机制是：下一次接口响应带新的 `X-Perm-Epoch` → 小程序异步拉 `/auth/info` → 更新 `globalData.permissions`。页面是否立即重绘、缓存是否清理，取决于各页面实现。
- 因此“权限码正确、接口 403 正确”已通过矩阵验证；但**页面显隐、缓存、当前页数据**存在滞后/不一致问题，清单如下。

## 生效链路

1. PC 权限中心保存 → 后端 bump 权限版本并失效权限缓存。
2. 小程序下一次请求响应头 `X-Perm-Epoch` 变化。
3. `utils/request.js` 比较本地 `permEpoch`，不一致时调用 `loadCurrentUser()` 拉 `/auth/info`。
4. `globalData.currentUser/permissions` 更新；**不会自动重算已渲染页面的 data/按钮/列表**。
5. App.onShow 另有一次 `/auth/info` 兜底刷新。

## 问题清单

| 编号 | 严重度 | 模块/页面 | 现象 | 根因/证据 | 复现步骤 | 影响 | 建议 |
|---|---|---|---|---|---|---|---|
| MP-P1 | 高 | 全局 | PC 改权限后，用户停留在无请求页面时一直看到旧菜单/按钮 | 无推送；仅请求头 epoch 或 app.onShow 触发刷新（`utils/request.js:84-96`、`app.js:44-55`） | 用户停留首页不操作 → PC 撤销 `customer:add` → 小程序仍显示新增入口 | 权限撤销后 UI 滞后，点击才 403 | 页面 onShow 主动比对 epoch；或权限变更事件广播；对关键页面定时轻量校验 |
| MP-P2 | 高 | 应用中心 | epoch 刷新后应用中心卡片不重算，仍显示旧卡片 | `pages/app/index.js:100-137`：`apps` 仅在 `onLoad` 计算，`onShow` 只同步 tabBar 选中态 | 打开应用中心 → PC 取消 `file:view` → 触发一次请求 → 返回应用中心，文件卡片仍在 | 旧入口继续可见，点击 403 | `onShow` 在 epoch 变化时重新计算 `apps` |
| MP-P3 | 高 | 首页 | 30 秒内返回首页不刷新，权限/数据范围变化不可见 | `pages/home/index.js:104-118` 的 onShow 30s 节流 | 取消 `incentive:view` 后 10 秒内切回首页，激励入口仍在 | 首页入口/数据滞后，最长 30s | 权限 epoch 变化时绕过节流强制刷新 |
| MP-P4 | 高 | 各业务详情/列表 | 当前页已加载的按钮（编辑/删除/上传）和列表不会因权限变化自动消失 | `loadCurrentUser()` 只写 globalData，不刷新 page.data；按钮多用 pageData/canX 标志（如 `pages/customer/detail`、`pages/files/index`） | 客户详情页打开 → PC 撤销 `customer:edit` → 编辑按钮仍在，点击 403 | 权限撤销后旧操作仍可点，产生 403 与误判 | 权限 epoch 变化时发出页面刷新事件；页面订阅后重建 canX/按钮 |
| MP-P5 | 高 | 文件中心 | 已下载/预览过的文件在本地缓存，权限收窄/撤销后仍可离线打开 | `utils/file-cache.js` 缓存 150MB；仅登出调用 `clear()`（`utils/auth.js` logout），权限变化不清缓存 | 用户预览文件 → PC 撤销 `file:view`/缩小范围 → 不退出登录，仍能打开缓存文件 | 越权离线查看已缓存文件（客户端缓存泄露） | 权限 epoch 变化/收到 403 时清空 file-cache；或缓存键加入权限版本 |
| MP-P6 | 中高 | 全局 | 本地 `permEpoch` 为空时（清缓存/首次安装）不会触发刷新，只把新 epoch 存下 | `utils/request.js:88-94`：`if (prev && prev !== epoch)`，prev 为空直接跳过 | 清小程序缓存 → 登录 → PC 立即改权限 → 第一次请求只存 epoch 不拉 info | 首次请求可能沿用登录时的旧权限 | prev 为空时也应拉一次 `/auth/info`，再存 epoch |
| MP-P7 | 中高 | 全局 | 权限刷新失败被静默吞掉，且 epoch 仍被保存，后续不再重试 | `utils/request.js`：`loadCurrentUser().catch(()=>{})` 后无条件 `setStorageSync('permEpoch', ...)` | 弱网下 PC 改权限 → 刷新失败 → epoch 已更新，网络恢复也不再触发 | 权限长期不更新，直到下次 epoch 变化/冷启 | 刷新成功后再写 epoch；失败保留旧值并下次重试 |
| MP-P8 | 中 | 全局 | 接口返回 403 不触发权限刷新，页面继续保留旧状态 | `utils/request.js:102-110` 只对 401 踢登录，403 直接 reject | 权限已撤销，页面旧按钮点击 403；页面不自动纠正 | 用户反复点击 403，无法自愈 | 403 时触发一次 `/auth/info` + 页面刷新（防抖） |
| MP-P9 | 中 | 底部 TabBar | 底部 5 个 tab 固定显示，无 `file:view`/`incentive:view` 的用户也看到「四项服务/文件」 | `custom-tab-bar/index.js` TABS 静态，无权限过滤；页面 onLoad 再跳回首页 | 销售无文件权限 → 点「文件」→ 被弹回首页 | 无效入口、体验差，易误以为故障 | tabBar 按权限隐藏/置灰，或页面内明确无权限态 |
| MP-P10 | 中 | 我的/数据范围展示 | 显示的数据范围可能与实际生效范围不一致 | `pages/profile/index.js:70`、`pages/profile/scope/index.js:9` 使用 `user.dataScope`（角色基础范围），实际按 `role_perm_scope` 模块级执行 | 角色基础 self，但客户模块配置 all → 我的页显示“仅本人”，客户列表却是全量 | 用户/管理员误判权限 | 后端下发各模块生效范围摘要；前端按模块展示或标注“以实际数据为准” |
| MP-P11 | 中 | 客户表单 | 父码撤销后，字段码可能仍留在本地 permissions，日期字段短暂可编辑 | 后端保存时剪除字段码，但 MP 端 `globalData.permissions` 要等 epoch 刷新；表单 `canEditDates` 用本地权限（`pages/customer/form/index.js:155-159`） | 取消 `customer:edit` → 打开客户编辑，日期仍显示可改 → 保存 400 | 表单状态与后端不一致 | 权限变化时刷新表单；字段码判断同时校验父码 |
| MP-P12 | 中 | 文件下载 token | 旧下载 token 在权限撤销后可能仍有效至 TTL | 下载 token 为 1h JWT；MP 缓存 token，撤销权限/缩小范围不主动清（需结合后端下载校验确认） | 用户先取下载 token → 撤销文件权限 → 1h 内继续下载 | 潜在越权下载窗口 | 撤销权限时清空本地下载 token；后端下载校验每次校验当前权限（建议复核） |
| MP-P13 | 低中 | 我的/子页 | 个人中心、反馈等页面无业务权限门，仅登录态 | 静态扫描：`profile/index`、`profile/scope`、`profile/feedback`、`profile/wechat` 只有 requireLogin | 无权限用户仍可打开个人中心页面 | 一般无敏感数据，属可接受；若有敏感字段需加门 | 评估是否仅登录即可 |
| MP-P14 | 低 | 全局 | `isAdmin()` 未统一用于页面门禁；多数页面只判 `hasPermission` | 如 `pages/files/index.js:489` 文件页守卫只用 `file:view`；admin 依赖 DB 菜单绑定 | 若 admin 角色漏绑某码，小程序把 admin 挡回首页，但后端 admin 本可放行 | admin 体验/可维护性风险 | 页面门禁统一 `isAdmin() || hasPermission(code)` |
| MP-P15 | 低 | 全局 | `permEpoch` 是单键，不按用户隔离 | `utils/request.js` 使用全局 `permEpoch`；token 是分环境/用户的 | 同设备切换账号，可能错误比较/跳过刷新 | 偶发权限刷新异常 | epoch 存储加 userId 维度 |
| MP-P16 | 低 | 全局/可观测性 | 小程序没有“当前生效权限/来源”页面，出问题难定位 | 无对应页面/接口展示；PC 有权限中心/生效预览 | 用户反馈“看不到入口”时只能查后端 | 排障成本高 | 我的页增加只读“权限诊断”（权限码+模块范围） |

## 已确认正常的部分

- 小程序与 PC 同后端：撤码后 `/auth/info` 权限集立即变化，对应接口返回 403（API 矩阵 4,704 格 FAIL=0）。
- 数据范围实际生效：客户 all→self 计数 294→27、提醒 158→30、审批 55→16。
- 小程序 `X-Perm-Epoch` 自动刷新机制存在，只是“下一次请求/页面重入”触发，不是实时推送。

## 修复优先级建议

1. **P1/P2/P3/P4/P5**：权限变化后的页面重算 + 文件缓存清理（影响权限撤销可信度）。
2. **P6/P7/P8**：epoch 空值/失败/403 的自愈逻辑。
3. **P9/P10/P15**：TabBar、范围展示、epoch 按用户隔离。
4. **P12/P14/P16**：安全复核与可观测性。

## 修复状态（2026-11-15 第一批）

| 编号 | 状态 | 说明/证据 |
|---|---|---|
| MP-P1 | 部分修复 | epoch 变化强制刷新、页面 `onPermissionChanged` 通知；仍非实时推送，依赖下一次请求/onShow |
| MP-P2 | 已修复 | 应用中心 `onShow`/`onPermissionChanged` 重算 apps；实测撤 `file:view` 后文件卡自动消失 |
| MP-P3 | 已修复 | 首页 onShow 比对权限版本，变化时绕过节流并重载 |
| MP-P4 | 已修复 | 业务页面（列表/详情/表单/子页/fabushi）已全部接 `onPermissionChanged`；仅个人反馈/微信等登录态页面不做业务权限门 |
| MP-P5 | 已修复 | 权限变化 `markChanged` 清 file-cache；单测验证缓存文件被删除 |
| MP-P6 | 已修复 | epoch 本地为空时也触发一次 `/auth/info` |
| MP-P7 | 已修复 | 只有刷新成功才保存 epoch；失败保留旧值并下次重试 |
| MP-P8 | 已修复 | 403 触发一次去重/3s 节流的权限刷新 |
| MP-P9 | 已修复 | TabBar 按权限置灰；实测无 file:view 时文件 tab disabled，点击提示无权限 |
| MP-P10 | 已修复 | 后端 /auth/info 下发 moduleScopes；我的页显示基础范围 + 客户/激励/文件/公海/审批/提醒/统计模块范围 |
| MP-P11 | 已修复 | 客户日期/开票日字段同时校验父码 `customer:edit` |
| MP-P12 | 已复核 | 下载端点 @PreAuthorize(file:view) + canDownloadFile 逐请求校验；撤权后旧 token 403（代码复核） |
| MP-P13 | 保留 | 个人中心子页仅登录态，按现有产品口径可接受 |
| MP-P14 | 已修复 | `utils/auth.hasPermission` 对 admin 全局返回 true，所有页面入口/按钮统一兜底；后端仍二次校验 |
| MP-P15 | 已修复 | `permEpoch` 按“用户+环境”分键 |
| MP-P16 | 已修复 | 个人中心新增权限诊断页（权限码/模块范围/epoch/版本）；DevTools 实测 admin 87 码、7 模块范围 |

### 本轮实测证据

- DevTools 实测：撤 `file:view` 后，应用中心卡片由 `[customer,files,incentiveQuery]` 自动变为 `[customer,incentiveQuery]`，文件 tab `disabled=true`：
  `test-results/mp-matrix/permission_refresh_evidence.json`
- 单测：
  - `scripts/test-miniprogram-permission-refresh.js` PASS（版本递增/页面通知/缓存清理/TabBar 门禁）
  - `scripts/test-miniprogram-request-failures.js` PASS
  - 小程序其余单测 15/16 PASS（perf 因端口/账号 BLOCKED）
- UI 回归：`verify-mobile-shell` PASS、`verify-file-center` PASS。
