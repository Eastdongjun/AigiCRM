# 小程序权限修复计划（2026-11-15）

## 一、目标与原则

**目标**：PC 权限中心修改功能码/数据范围后，小程序在“下一次请求或页面重入”时可靠生效；撤销权限后不残留入口、按钮、缓存文件或旧数据；所有修复有单测 + DevTools 实测 + 权限矩阵回归证据。

**原则**：
1. 不为实时性引入复杂推送；优先用 `X-Perm-Epoch` + 页面生命周期 + 主动刷新。
2. 权限判断统一入口：`isAdmin() || hasPermission(code)`；数据范围以后端为准。
3. 缓存必须带“权限版本”，权限变化即失效。
4. 先修安全/越权风险，再修体验，最后补可观测性与自动化。
5. 每项有明确文件、验收标准、回归命令；不通过不合并。

## 二、当前状态（第一批已完成）

| 状态 | 编号 |
|---|---|
| 已修复 | MP-P2 应用中心重算、MP-P3 首页绕节流、MP-P5 文件缓存清理、MP-P6 空 epoch 刷新、MP-P7 成功才落 epoch、MP-P8 403 刷新、MP-P9 TabBar 权限、MP-P11 字段码父码、MP-P15 epoch 按用户隔离 |
| 部分修复 | MP-P1 非实时、MP-P4 详情页未全覆盖、MP-P10 范围文案/后端摘要、MP-P14 admin 兜底未全覆盖 |
| 待处理 | MP-P12 下载 token 复核、MP-P16 权限诊断页 |
| 保留 | MP-P13 个人中心仅登录态（当前产品口径） |

证据：`docs/miniprogram-permission-fix-20261115.md`、`test-results/mp-matrix/permission_refresh_evidence.json`。

## 三、分阶段计划

### 阶段 1：权限生效可靠性（P0/P1，预计 1–1.5 天）

| 项 | 问题 | 方案 | 涉及文件 | 验收 |
|---|---|---|---|---|
| 1.1 详情页刷新 | 已打开详情页的编辑/删除/上传按钮不随撤权消失 | 详情页统一接 `onPermissionChanged`：重拉详情 + 重算 canX；失去查看权限时返回上一页/首页 | `pages/customer/detail/*`、`incentive/detail/*`、`approval/detail/*`、`sea/claim-detail/*`、`files/*` | 撤 `customer:edit`/`file:delete` 后按钮消失；不出现可点后 403 |
| 1.2 页面级门禁统一 | 部分页面只判 hasPermission，admin 漏绑码会被挡 | 抽 `utils/permission.js`：`can(code)`、`canAny(codes)` = `isAdmin() || hasPermission`；替换页面守卫 | 全小程序页面（优先 files/app/home/customer/incentive） | admin 无码仍可进；普通用户无码被挡 |
| 1.3 数据范围摘要 | “我的”范围显示基础范围，实际模块范围不同 | 后端 `/auth/info` 增加 `moduleScopes`（customer/incentive/file/sea/approval/reminder/statistics），MP 按模块展示或标注 | backend `AuthService/UserInfoResponse`、`RolePermScopeService`；MP profile/scope | 模块范围与实际列表计数一致；不再误标“仅本人” |
| 1.4 下载 token 复核 | 撤权后旧下载 token 可能仍有效至 1h | 后端下载端点逐请求校验当前权限；MP 权限变化清空 dlToken 缓存 | backend FileService/FileController；MP files/incentive detail | 撤 `file:view/download` 后旧 token 下载 403；MP 不再复用旧 token |
| 1.5 文件缓存版本化 | 缓存只在登出清 | 缓存键加入权限版本（或 markChanged 清缓存）；已做 markChanged，补单测覆盖“撤权→缓存不可读” | `utils/file-cache.js`、`utils/permission-refresh.js` | 撤权后已缓存文件不可打开；重新获权后可重新缓存 |

### 阶段 2：体验与一致性（P2，预计 0.5–1 天）

| 项 | 问题 | 方案 | 验收 |
|---|---|---|---|
| 2.1 TabBar 无权限态 | 已置灰，但无权限用户仍可能通过 deep link 进入 | 保留置灰 + 页面统一守卫；点击提示统一文案 | 无权限点 tab 不跳转/明确提示 |
| 2.2 首页/应用中心刷新节流 | 已修权限变化，但数据范围变化仍可能 30s 内不刷新 | 权限/范围版本变化统一绕过 30s 节流 | 改范围后下一次进入首页立即更新 |
| 2.3 个人中心 | 子页仅登录态 | 产品确认是否保持；如保留则标注“仅登录可见” | 与产品口径一致 |
| 2.4 统一错误提示 | 403 在各页提示不一致 | 抽 `showPermissionError`，区分“无功能权限/数据范围外/需重新登录” | 文案统一、不误踢登录 |

### 阶段 3：可观测性与自动化（P3，预计 0.5–1 天）

| 项 | 内容 | 验收 |
|---|---|---|
| 3.1 权限诊断页 | “我的”增加只读诊断：当前权限码数、模块范围、epoch、最近刷新时间 | 用户可自助提供诊断截图 |
| 3.2 自动化权限刷新用例 | 新增 DevTools 用例：撤码 → 触发请求 → 应用中心/文件/详情/TabBar 自动更新 | CI/本地可重复，证据落盘 |
| 3.3 权限矩阵回归 | 修复后重跑 24 码 × 28 账号 × 7 范围（4,704 格） | FAIL=0，PASS/DATA_GUARD 与基线一致 |
| 3.4 性能脚本 | 配真实测试账号 + 9423 端口，或改用手工清单 | 不再 BLOCKED |

## 四、测试与证据要求

每项修复必须附：

1. **单测**：对应 Node 脚本（权限刷新、TabBar、字段码、请求失败）。
2. **DevTools 实测**：撤码前/后截图或 JSON（应用中心卡片、TabBar disabled、详情按钮、接口 403）。
3. **矩阵回归**：24×28×7 至少受影响码全格；最终全量 4,704 格 FAIL=0。
4. **数据范围**：客户/提醒/审批在 all/self 下计数对比。
5. **证据文件**：`test-results/mp-matrix/` + `docs/miniprogram-permission-fix-20261115.md` 更新。

## 五、验收标准

- PC 撤功能码 → 小程序下一次请求/onShow 后：入口消失、按钮消失、接口 403；无旧卡片/旧按钮残留。
- PC 收窄数据范围 → 小程序列表/统计数字与 PC 一致；无旧范围数据残留。
- 撤 `file:view` 后：文件 Tab 置灰、已缓存文件不可打开、旧下载 token 不可用。
- admin 任意页面不被页面门禁误挡。
- 小程序单测全 PASS（perf 可 BLOCKED 但需登记），矩阵 FAIL=0。

## 六、风险与回滚

| 风险 | 应对 |
|---|---|
| 频繁刷新 `/auth/info` 造成请求放大 | epoch 变化才强制；403 刷新 3s 节流；页面 onShow 比对版本 |
| 清文件缓存影响离线体验 | 仅权限变化时清；重新获权后按需重新缓存 |
| 后端 moduleScopes 增加登录响应体积 | 仅下发 7 个模块的短码，非全量矩阵 |
| 下载 token 校验影响性能 | 复用现有权限缓存/鉴权链，逐请求只读缓存 |
| 代码回滚 | 小程序改动集中在 utils + 页面 onPermissionChanged/TabBar，可按文件回退；后端 moduleScopes/下载校验单独提交可独立回滚 |

## 七、交付物清单

- 代码：小程序 `utils/permission-refresh.js`、`utils/permission.js`、`api/auth.js`、`utils/request.js`、页面 `onPermissionChanged`、TabBar、profile/scope、customer/form。
- 后端（阶段 1.3/1.4）：`UserInfoResponse` 增加 moduleScopes；下载端点权限复核。
- 测试：`scripts/test-miniprogram-permission-refresh.js`、DevTools `verify-permission-refresh.mjs`、矩阵回归。
- 文档：本计划、修复记录、问题清单、矩阵工作簿。
