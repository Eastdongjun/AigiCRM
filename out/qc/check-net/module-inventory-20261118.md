# AIGICRM 五源模块清单 v0.1（2026-11-18，W0b）

> QC 端口 01-claude-1c ｜ HEAD `10a6aa99` ｜ 只读盘点，未改任何代码 ｜ 目的：以当前代码为准重建模块清单，替代过期 RTM/23 链图。
> 五源：①后端 Controller ②PC 路由+views ③MP `app.json` ④Flyway+表 ⑤权限码+菜单。

## 一、五源计数（实测）

| 源 | 实测 | 旧口径（RTM/文档） | 差异 |
|---|---|---|---|
| 后端 Controller | **44** | RTM 32 | +12（法布施 7 + FileGrant/FileRecycle/Lifecycle/RecycleExport 等） |
| PC 路由 path | **49** | — | — |
| PC `.vue` | **111** | RTM 92 | +19 |
| PC spec 文件 | **47** | RTM 41 | +6 |
| MP 页面（`app.json` pages） | **51** | RTM 43 / 文档 56 | 需以 json 为准 |
| Flyway 迁移 | **155** | — | — |
| 后端测试类 | **120** | — | 与"120 套件"一致 |
| 后端测试用例 | **1187** | — | 与门禁基线一致 |
| 权限码（契约口径） | **96** | 文档 74/77/95 不一 | 以 `check-permission-binding-coverage.sh` 实测 96 为准 |
| 生产表（ycmn） | **93**（10.3MB） | 旧文档 87 | 新治理表 + 法布施 |
| 数据范围轴（`check-scope-axis-contract.sh`） | **18** | — | 见 §四 |

## 二、44 Controller → 18 域映射（v0.1）

| # | 域 | Controller（数量） |
|---|---|---|
| 1 | 认证 | Auth（1） |
| 2 | 客户 | Customer（1） |
| 3 | 生命周期/交接 | Lifecycle（1） |
| 4 | 公海 | Sea（1） |
| 5 | 审批 | Approval, ApprovalConfig（2） |
| 6 | 文件 | File, FileGrant, FileReconciliation, FolderAcl（4） |
| 7 | 激励 | Incentive, IncentivePermission（2） |
| 8 | 积分 | Score（1） |
| 9 | 统计 | Statistics（1） |
| 10 | 提醒 | Reminder（1） |
| 11 | 任务/定时 | Task, ScheduledTask（2） |
| 12 | 表单草稿 | FormDraft（1） |
| 13 | 回收站 | Recycle, FileRecycle, RecycleExport（3） |
| 14 | 系统管理 | SysAuditLog, SysConfig, SysDept, SysDict, SysGroup, SysLog, SysMenu, SysPermissionRule, SysPosition, SysRole, SysRuleVersion, SysUser（12） |
| 15 | 权限中心/数据范围 | PermCenter, PermissionExplain, RolePermScope, Scope（4） |
| 16 | 法布施 | FabushiActivity, FabushiBook, FabushiPost, FabushiRead, FabushiReading, FabushiSection, FabushiStats（7） |
| 17 | 公共/支撑 | **0 个 `*Controller.java`**（会话框架记 Common×2；当前代码未找到，待 W0b 复核是否改名/并入其他包） |
| 18 | 部署/运维 | 0 个 Controller（非代码域，按脚本/容器/迁移计） |
| **合计** | | **44** |

> 注：域 17 的"0 Controller"已复核——**44 个 `*Controller.java` 全部有域归属**；全仓 `@RestController` 共 **45** 个，第 45 个是 `GlobalExceptionHandler`（`@RestControllerAdvice` 支撑类，不属业务域）。会话框架记的 "Common×2" 属旧口径或已并入其他包；域 18 是非代码运维域。W0b 结论：域 17/18 无业务 Controller，**不是漏项**。

## 三、法布施独立域（重点，防再漏）

- 7 Controller；12 张 `biz_fabushi_*` 表；10 个权限码（view/create/book:view/book:manage/content:view/content:manage/file:delete/stats:view/stats:export/activity:manage）；PC 8 页 + H5 阅读器容器；MP 7 页。
- 文档漂移：需求书 5 码 / 技术设计 9 码 / 代码 10 码；设计 11 张 `fb_` 表 vs 代码 12 张 `biz_fabushi_*`；设计 12 MP 页 vs 代码 7 法布施页 —— **D13 需求覆盖必须逐条列"文档≠实现"裁决**。

## 四、18 个数据范围轴（来自 `check-scope-axis-contract.sh` AXIS 声明表）

`customer:view`、`customer:edit`、`customer:delete`、`customer:finance_edit`、`file:view`、`file:manage`、`file:delete`、`file:version_rollback`、`approval:view`、`reminder:view`、`sea:view`、`incentive:stats_view`、`task:view`、`score:view`、`system:view`、`fabushi:stats_view`、`fabushi:file_view`、`fabushi:file_delete`。

- 已执行范围核对：仅 **`customer:view`**（28 个启用用户 × 7 档计数 + 27/28 ID 集合），不是"客户域全部 4 个轴"。
- 其余 17 轴：U/未执行；其中 file 4 轴、fabushi 3 轴、customer 3 轴为 P0 风险面。

## 五、旧口径失效清单（W0b 必须重标）

- RTM（2026-10-31）：32 Controller / 92 `.vue` / 43 MP 页 / 41 spec 已过期；法布施 29 条"未开工"已作废。
- 23 链图（2026-11-08 @`882ad63a`）：缺 11 个新 Controller（法布施 7 + FileGrant/FileRecycle/Lifecycle/RecycleExport）与 59 个迁移；降为交叉校验/负空间来源。
- 表单草稿、生命周期、回收导出、文件对账等新域在旧图中无位置。

## 六、W0b 待补

1. ~~域 17 的 0 Controller 复核~~ **已完成**：44 个 `*Controller.java` 全归属；第 45 个 `@RestController` 是 `GlobalExceptionHandler` 支撑类。
2. PC 视图目录→域映射、MP 页→域映射、表→域映射、权限码→域映射（四列补齐）。
3. 合同映射表（191 PASS → 域/维/L0 证据）。
4. 三层矩阵 v1（L1 域×维 247 格 + L2 对象行 + L3 链阶段）。

## 六、四列域映射（W0 遗留补齐，2026-11-18）

### PC 视图目录 → 域（17 目录 / 102 vue）

| 目录 | 域 | 目录 | 域 |
|---|---|---|---|
| approval | 5 审批 | ranking | 9 统计（排行） |
| customer | 2 客户 | reminder | 10 提醒 |
| dashboard | 2 客户（工作台） | score | 8 积分 |
| error | 17 公共 | sea | 4 公海 |
| fabushi | 16 法布施 | statistics | 9 统计 |
| file | 6 文件 | system | 14 系统管理 / 15 权限中心 |
| incentive | 7 激励 | task | 11 任务 |
| indicator | 2 客户（指标） | mp | 17 公共（H5 容器：FilePicker/FabushiReader） |
| login | 1 认证 | | |

### MP 页 → 域（51 页）

| 前缀 | 页数 | 域 |
|---|---|---|
| pages/login | 2 | 1 认证 |
| pages/home + pages/app | 2 | 2 客户（工作台/入口） |
| pages/customer | 12 | 2 客户（含 finance/contact/share/log/filter/indicator） |
| pages/files | 4 | 6 文件（含 picker H5 入口） |
| pages/incentive | 4 | 7 激励 |
| pages/ranking | 2 | 9 统计（排行） |
| pages/statistics | 2 | 9 统计 |
| pages/indicator | 1 | 2 客户（指标） |
| pages/task | 1 | 11 任务 |
| pages/profile | 9 | 14 系统（个人中心/scope/diagnostic/agreement/privacy/wechat） |
| pages/approval | 2 | 5 审批 |
| pages/reminder | 1 | 10 提醒 |
| pages/sea | 2 | 4 公海 |
| pages/fabushi | 7 | 16 法布施 |
| **合计** | **51** | |

### 表 → 域

- `biz_customer*`(7) → 2；`biz_file*`(6)+`biz_folder_type_default` → 6；`biz_approval*`(4) → 5；`biz_incentive*`(4) → 7；`biz_score*`(3) → 8；`biz_reminder` → 10；`biz_sea_*`(3) → 4；`biz_fabushi_*`(12) → 16；`sys_*`(约 20) → 14/15/1；其余支撑表 → 17。

### 权限码 → 域

- 95 码按前缀映射（customer:/file:/approval:/incentive:/score:/reminder:/sea:/fabushi:/system:/statistics:/task:）——与域一一对应；`incentive:*` 含 rank/stats 跨 7/9，`statistics:*` → 9，`system:*` → 14/15。详见 `contract-map-20261118.md` 与 `mp-crosswalk-20261118.md`。
