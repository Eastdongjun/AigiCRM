# AIGICRM 小程序测试报告（2026-11-15）

环境：微信开发者工具自动化（AppID `wx513e6dd7eabdae7b`）+ 测试环境 `http://111.229.37.220`（生产数据快照）。
账号：admin（87 权限码）+ 生产销售账号 TY003（测试临时改密，结束后已恢复原密码）。

## 一、结论摘要

| 层面 | 结果 |
|---|---|
| 静态完整性（页面/JS/API/权限码） | **PASS** |
| 小程序权限契约与生成物 | **PASS** |
| 小程序单测/契约脚本 | **14 PASS / 1 BLOCKED** |
| DevTools UI 自动化 | **6 个页面组 PASS；8 个失败待分诊（多为测试脚本/演示模式漂移）** |
| 真实登录态页面冒烟（admin） | **PASS（页面均可加载，核心数据可见）** |
| 权限码/数据范围运行态 | **PASS（拒绝 403、范围 34→294）** |
| 性能自动化 | **BLOCKED**（需真实账号 + 自动化端口 9423） |

## 二、静态检查

- 42 个页面，`.js/.wxml/.json` 齐全。
- 84 个 JS 文件 `node --check` 0 语法错误。
- 68 个 API URL 均存在于后端 API 清单。
- 24 个 `hasPermission` 权限码均在注册表/生成常量中。
- PC/MP 权限码生成物一致（85 码）；小程序权限契约 24 码全部登记且启用。

## 三、小程序单测/契约

- 14 个脚本 PASS（customer filter/idempotency、data-scope-label、files-scope、incentive files/panes/upload、ranking-tabs、relationship-dict、reminder-list、request-failures、statistics-page、upload-handback、dial 等）。
- 1 个 BLOCKED：`test-miniprogram-perf-regression.js` 需 `HCU/HCP` 真实账号与 `MP_WS=ws://127.0.0.1:9423`。

## 四、DevTools UI 自动化结果

### PASS
- `verify-mobile-shell`：首页看板、快捷入口、权限拒绝、应用中心过滤、导航、PC-only 边界。
- `verify-customer-center`：客户列表/详情/开票回款/联系人/分享/时间轴/指标入口。
- `verify-customer-forms`：客户/联系人/财务表单、分享、跟进、六项指标编辑。
- `verify-file-center`：文件列表/预览下载/容量卡/文件夹 ACL 视图。
- `verify-approval-center`：审批列表/统计/通过/驳回/释放。
- `verify-sea-center`：公海统计/列表/领取/编辑/删除守卫/领取详情。

### 失败待分诊（当前判定为测试脚本或 demo 模式漂移，未确认为业务缺陷）
| 脚本 | 现象 | 初步判断 |
|---|---|---|
| verify-incentive-attachments | 上传/详情通过；列表页 demo 下被非 demo 请求带回登录页 | 测试脚本 demo 模式兼容问题 |
| verify-statistics-center | 缺“人员积分 Top5”文案 | 文案/选择器过期 |
| verify-indicator-overview | 权限 toast 未出现 | demo 模式/页面守卫状态问题 |
| verify-ranking-center | 默认页签不是客户排行，`.ranking-card` 为 0 | 测试未切页签 |
| verify-reminder-center | 处理后记录未移除 | 需按当前处理逻辑核对，可能是真实行为 |
| verify-profile-center | 缺“华东办事处”文案 | 测试注入数据/文案过期 |
| verify-auth-center | 空提交先弹“同意协议”而非“请输入用户名密码” | 校验顺序变更，测试未同步 |
| verify-feedback-notification | 未捕获对象错误 | 需单独排查 |

> 失败项没有一条被直接认定为“线上权限/数据越界”；需要按页面逐条复现后再定性。

## 五、真实登录态页面冒烟（admin，87 权限码）

| 页面 | 结果 |
|---|---|
| 激励查询 | 加载成功；6 个筛选 chip（全部行业/类型/产品/组织/开始/截止） |
| 客户列表 | 加载 10 条真实客户 |
| 文件中心 | 加载 10 个真实文件 |
| 审批 | 页面加载（当前 0 条待处理数据） |
| 提醒 | 页面加载（当前 0 条待处理数据） |
| 统计 | 2 个统计卡；客户总数 294 |
| 核心指标 | 4 个指标卡（覆盖客户 24） |
| 学分排行 | 页面加载（默认页签非客户排行，卡片 0） |
| 我的 | 集团/数据范围卡正常（全部数据） |
| 首页 | 页面加载（卡片由 view 数据渲染） |

销售账号 TY003 真实态：客户/文件/统计/指标/我的可加载；激励因缺 `incentive:view` 被正确挡回首页。

## 六、权限码/数据范围运行态

- 从角色移除 `statistics:view`、`approval:view`、`reminder:view` 后：
  - `/auth/info` 权限集三码消失；
  - `/statistics/dashboard`、`/approval/list`、`/reminder/list` 均 **403**。
- 客户范围 `*:*` 从 self 改 all：`/customer/list` total **34 → 294**，恢复后回 34。
- 小程序 `X-Perm-Epoch` 刷新逻辑存在；权限变更在下一次请求/页面重入后生效。

## 七、建议

1. **分诊 8 个失败脚本**：优先确认 `verify-reminder-center`（处理后记录未移除）是否真实缺陷；其余多为选择器/文案/demo 模式漂移。
2. **UI 测试改用真实登录态**：避免 demo 模式下非 demo 请求把页面带回登录页。
3. **修复 `dataScope` 显示口径**：`/auth/info.dataScope` 仍是基础范围，实际按 `role_perm_scope` 模块级执行。
4. **性能自动化**准备专用账号与 9423 端口，或纳入有 devtools 的 CI。
5. 测试账号改密均已恢复；生产数据仅测试库副本，未影响生产。
