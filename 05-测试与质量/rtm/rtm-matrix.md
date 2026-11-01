# AIGICRM 需求→测试追溯矩阵（RTM）

> 生成：2026-10-31 ｜ 90分提升战役 B5-L 突击队 ｜ 机器可读版：`rtm-matrix.json`（同目录，schema `aigicrm-rtm-v1`）
> 铁律执行：有证据才连，没证据记 gap；所有证据可回溯到文件/报告条目。

## 0. 口径声明（先读）

1. **本项目无条目化 REQ-ID 体系**（已核实：需求散落于 `00-项目管理/` 各文档与源码注释，无统一 REQ 登记）。故需求单元口径为：
   - **A 后端业务模块 17 个**：按 32 个 Controller + 38 个 Service 归并的功能域（客户/公海/审批/激励/文件/统计/提醒/任务/回收/草稿/认证/用户/角色权限/组织/配置日志/定时任务/数据权限引擎）；
   - **B PC 前端 7 个**视图域；
   - **C 小程序 42 个**：《小程序原型与ANGCIM功能匹配报告》39 个矩阵行（覆盖 51 张原型页）+ 2026-10-16 需求 A/B/C 三条；
   - **D 法布施新模块 29 条**（A1–G4，需求说明书 v1.1 已复核、**未开工**，单列不计入上线产品分母。注：说明书第五节自称"26 条"与第三节条目表实数 29 不符，按条目实数入册）；
   - **E 非功能 4 个**（安全防护/架构守卫/审计留痕/配置兼容）。
2. **状态判定**：`covered`=有可核对测试证据且无未修复高危挂点；`partial`=仅单层证据 / 有未修复缺陷或待确认口径 / 部分子能力无证据；`gap`=无任何证据或能力未实现。
3. **证据源代号**：BT=后端 JUnit（85 类约 902 @Test，grep 计数）｜FS=前端 vitest（41 spec 约 260 it）｜STR=双端系统测试报告 2026-10-12（8 维度，25 已证实条目 H1-H4/M1-M4/L1-L17）｜MR=原型匹配报告（2026-09-05 时点判定）｜AC=AC 实现与验证报告 2026-10-17（冒烟 29/29、PC 阅读器 24/24）｜CONTRACT=小程序静态契约脚本（在库 29 个，STR 维度1 执行 21 个全 PASS）。
4. **对任务书数字的实测修正**（如实声明）：前端 spec 实为 **41** 个（非 43）；原型矩阵实为 **39 行**（覆盖 51 页，非 47 行）；`.vue` 零组件级 spec 实为 **77** 个（92 个 .vue 中 15 个有邻接 spec，非 92 全零）；`@DisplayName` 仅 SysUserServiceOrgSyncTest（4 处）使用，其余测试以方法名表达语义；"5 个架构守卫测试"实为 ArchitectureTest(3 用例)+StabilityInvariantTest(7 用例)+4 个 Guard 类的守卫家族。小程序页面现为 43 页（app.json；双端报告时点 41）。

## 1. 盘点汇总

| 侧 | 盘点结果 |
|---|---|
| 需求单元 | **101** = 模块 17 + PC 7 + 小程序 42 + 法布施 29 + 非功能 4 |
| 后端测试 | **85 类 / ≈902 @Test**（全部分配到单元，无孤儿） |
| 前端测试 | **41 spec / ≈260 it**；92 个 .vue 中 **15 有邻接 spec、77 零组件级 spec** |
| 小程序 | 43 页零单测框架；29 个契约脚本在库（21 个已执行全 PASS）；automator 冒烟 29/29 |
| 系统级 | 双端报告 8 维度、25 已证实条目（H4/M4/L17；L8 为待确认项非缺陷）+1 未证实 |
| 可复跑脚本 | `aigicrm-ui/preview-smoke-prod.mjs`（24/24）、`05-测试与质量/smoke-mp-redesign-ac-20261017.js`（29/29） |

## 2. 映射总表

### 2.1 A 组·后端业务模块（17 单元：covered 8 / partial 9 / gap 0）

| 需求单元 | 后端测试（类×用例数） | 其他证据 | 状态 | 关键说明 |
|---|---|---|---|---|
| M01 认证与账号 | JwtAuthFilterTest(11), JwtTokenProviderTest(5), UserDetailsServiceImplTest(7) | FS: login/index(6), ForgotPasswordDialog(2), token(10)；STR 维度3/4 强制改密闭环 | **covered** | — |
| M02 客户管理 | CustomerServiceTest(86), CustomerControllerTest(9), CustomerStageAmountTest(10), CustomerAccessServiceSeaPermTest(14), CustomerFullIdempotencyTest(4), CustomerExpireTaskTest(10), CustomerImportListenerTest(2), ImportGuardTest(5), CustomerFinanceGuardTest(5), CustomerFinanceAuditLogTest(3), TransferSubmitOwnershipTest(3) | FS: detail(5)/FinanceTab(7)/InfoTab(3)/useCustomerForm(21) 等 10 spec；STR 维度7 全链 | **partial** | 基本信息编辑(PUT)与删除/archive/restore 未测（⑦-10）；挂 H1/H3/L2/L3/L5 未修 |
| M03 公海池 | SeaServiceTest(9), SeaConfigServicePoolPermTest(6) | STR 维度5 捞取全链正例×2+负例×6；CONTRACT sea-boundary | **covered** | — |
| M04 审批中心 | ApprovalServiceTest(37), ApprovalConfigServiceTest(7), ApprovalApproveRejectSecurityTest(7), ExtendApprovalConcurrencyTest(2), ExtendSubmitOwnershipTest(4) | STR 维度5 审批正负例 | **partial** | 挂 M1(extend 越权发起)/L1(自审自批) 未修 |
| M05 提醒 | ReminderServiceTest(15) | FS: reminder index(11)/detail(4)/reminder-detail(12)；STR 维度5+DB 交叉 | **covered** | 到期窗口规则口径待产品确认（③-1 遗留） |
| M06 文件管理（含配额/对账/存储审批） | FileServicePermissionTest(55), FolderAclServiceTest(18), FolderPermissionServiceTest(20), StorageQuotaApplyServiceTest(23), StorageApprovalServiceTest(8), FileQuotaConcurrencyTest(7), FileKeyDownloadDispositionTest(4), FilePublicDownloadMapperTest(3), FilePublicDownloadMetaTest(4), FileStorageApplyInfoTest(2) | STR 维度5 ACL 全链（路径穿越 404 等）；AC PC 阅读器 24/24 | **covered** | H4 uploads 卷丢失属环境治理非代码缺陷 |
| M07 激励·四项激励 | IncentiveControllerPermissionTest(16), IncentiveShareVisibilityTest(15), IncentiveScoreTest(12), IncentiveUploadValidationTest(12), IncentiveRemoveFileTest(10), IncentiveReadRankingTest(5), IncentiveSpecificProductTest(5), IncentivePeriodFilterTest(3), IncentiveCustomerRecordsPeriodTest(2), IncentiveCustomerRecordsRecordIdTest(2) | FS: search(17)/useIncentiveUpload(16) 等 8 spec；STR 维度8 全链 | **partial** | 挂 M2(period 忽略)/M4/L9/L10/L11 未修；H2 已修未回归 |
| M08 统计分析 | StatisticsQueryServiceTest(16), StatisticsControllerPermissionTest(2), LedgerVoidedStatsContractTest(3) | FS: readstats(3)/customer-rank(4)；STR 维度2 对账零差异（17/17、18 项、23/23、11 类） | **covered** | H3 的统计侧本身对账一致，口径分裂记 M17 |
| M09 任务 | TaskServiceTest(8) | — | **partial** | 仅单测；八维度未覆盖任务链路 |
| M10 回收站 | RecycleServiceTest(40) | — | **partial** | 单测充分，无系统级验证 |
| M11 表单草稿 | FormDraftOptimisticLockTest(3) | — | **partial** | 仅乐观锁单测 |
| M12 系统管理-用户 | SysUserDataScopeOverrideTest(9), SysUserPageGroupScopeTest(9), SysUserOrgBoundaryTest(7), SysUserManageVisibilityTest(6), SysUserManagerIdUpdateTest(5), SysUserServiceOrgSyncTest(4), SysUserControllerOrgAdminTest(3), SysUserDeleteOrderTest(3) | FS: system/user(4)；STR 维度4 用户 CRUD+权限即时生效 | **covered** | — |
| M13 系统管理-角色与权限矩阵 | SysRoleServiceTest(36), PermissionMatrixTest(27), PermissionRuleEngineTest(18), PermissionSecurityFixTest(17), PermissionCacheServiceTest(8), RolePermScopeServiceTest(7) | FS: PermScopeManager(7), api/system(1)；STR 维度3/4 | **covered** | — |
| M14 系统管理-组织与数据范围 | OrgScopeResolverTest(30), SysPositionServiceTest(13), SysDeptWriteBoundaryTest(10), SysDeptServiceTest(5), OrgAdminServiceSecurityTest(7), GroupScopeUtilTest(4), SysPositionRuleGuardTest(3), SysPositionRuleGuardControllerTest(3) | STR 维度4 组织 CRUD | **covered** | — |
| M15 系统管理-配置/字典/日志 | SysConfigControllerLogoTest(7), SysAuditLogServiceTest(4), LogAspectTest(3) | FS: BasicSettings(1)；STR 维度4 15 项 configs+字典三处一致 | **partial** | 挂 L6(updated_at 失真)/L7(日志 detail 空) 未修；UI 渲染未实拍 |
| M16 定时任务 | ScheduledTaskControllerSecurityTest(2), CustomerExpireTaskTest(10) | — | **partial** | 仅安全与到期任务单测 |
| M17 数据权限引擎（横切） | OrgScopeResolverTest(30), ScopeServiceTest(14), DataScopeGuardTest(13), DataScopeTypeTest(4) | STR 维度3 auto_share 取证；StabilityInvariant fail-closed 不变量 | **partial** | **挂 H1/H3 两高危未闭环**（⑦-4：列表不消费 override，配置层无已验证止血） |

### 2.2 B 组·PC 前端（7 单元：covered 4 / partial 3 / gap 0）

| 需求单元 | 前端 spec（用例数） | 其他证据 | 状态 | 说明 |
|---|---|---|---|---|
| F01 工作台 | readstats(3) | STR 维度2 dashboard 18 项对账 | **covered** | — |
| F02 客户视图 | useCustomerForm(21), useCustomerDetail(11), customer-empty-state(13), customer-fields(8), detail(5), FinanceTab(7), InfoTab(3), customer-status(4), customer-rank(4), useCustomerList(4) | STR 维度7 前端静态验证 | **partial** | 渲染级 UI 未实拍（STR ⑤）；编辑 UI 未测 |
| F03 激励视图 | search(17), useIncentiveUpload(16), incentive-read-ranking(6), incentive-detail(4), incentive-period(4), incentive-read(2), StatsDetail(2), RecordTable(2) | STR 维度8 Playwright 四页+导出 | **partial** | 挂 M2/L10 |
| F04 提醒视图 | reminder/index(11), reminder/detail(4), reminder-detail(12) | STR 维度5 | **covered** | — |
| F05 登录 | login/index(6), ForgotPasswordDialog(2), token(10) | STR 维度3 | **covered** | — |
| F06 系统管理视图 | PermScopeManager(7), incentive-read-ranking(4), user(4), BasicSettings(1), api/system(1), storage-apply(3) | STR 维度4（API 级） | **partial** | UI 渲染未实拍 |
| F07 布局/路由/全局支撑 | pagination(14), time-label(14), format(9), token(10), store/user(5), app-version(3), router/access(3), legacy-routes(2), layout(1), useFileBlob(6), media-type(4) | — | **covered** | — |

### 2.3 C 组·小程序（42 单元：covered 30 / partial 9 / gap 3）

**2026-10-16 需求 A/B/C（AC 报告，冒烟 29/29）**

| 单元 | 证据 | 状态 |
|---|---|---|
| 需求A 文件管理页改版 | AC 冒烟：49 行/时间分组/⋯面板 5 动作+红删除/行点击进详情 | **covered** |
| 需求B 激励页去冗余 | AC：C 落地即覆盖 B；断言旧巨卡+红圈②③④为 0 | **covered** |
| 需求C 客户封面卡+激励详情页 | AC 冒烟：108 封面卡/锚点 3 分组/46 条历史/吸底栏 2 钮 | **covered** |

**原型矩阵转录（39 行，MR 判定为 2026-09-05 时点；其后证据叠加处已注明）**

| 原型页 | MR 判定 | 叠加证据 | RTM 状态 |
|---|---|---|---|
| 01 首页工作台 | 高匹配 | BT M08 + dashboard-contract + 维度6 链路2 | **covered** |
| 02 登录绑定 | 部分匹配 | auth-contract + 维度6 链路1 账密登录 PASS；微信 OAuth 无 | **partial** |
| 03 忘记密码 | 高匹配 | BT M01 + FS ForgotPasswordDialog + auth-contract | **covered** |
| 04 强制改密 | 待确认 | **升级**：维度4 pwdResetRequired=1+旧 token 全失效已验证 | **covered** |
| 05 微信绑定 | 缺口 | 无任何后续证据（P0 缺口） | **gap** |
| 06 客户列表 | 高匹配 | CustomerControllerTest/ServiceTest + customer-center-contract + 维度7 | **covered** |
| 07 客户详情 | 高匹配 | BT M02 + detail spec + 维度7 全字段 + 维度6 链路5 | **covered** |
| 08 新增/编辑客户 | 高匹配 | customer-form-contract + useCustomerForm(21) + 维度6 链路4；PC 编辑 PUT 未测 | **covered** |
| 09 高级筛选 | 高匹配 | 维度7 筛选与 DB 一致 | **covered** |
| 10 开票录入 | 高匹配 | CustomerStageAmountTest + FinanceTab spec + 维度7 | **covered** |
| 11 回款录入 | 高匹配 | 同上 | **covered** |
| 12 开票回款明细 | 高匹配 | LedgerVoidedStatsContractTest + FinanceTab + 维度7 | **covered** |
| 13 分享客户 | 高匹配 | 维度7 分享正例；挂 L4 未修 | **covered** |
| 14 公海池 | 高匹配 | SeaServiceTest + sea-boundary + 维度5 | **covered** |
| 15 捞取记录 | 高匹配 | 维度5 claim-records | **covered** |
| 16-22 价值服务案例/排行/统计/预览 | 高匹配 | BT M07 十类 + incentive/ranking-contract + 维度8 + 维度6 链路3/6；挂 H2(已修未回归)/M3 | **covered** |
| 23-26 文件库/预览/上传/全屏 | 高匹配 | BT M06 十类 + file-boundary + 维度5；AC 阅读器 24/24；H4 属环境 | **covered** |
| 27-28 审批中心/详情 | 高匹配 | BT M04 + approval-contract + 维度5 | **covered** |
| 29 消息通知 | 部分匹配 | ReminderServiceTest + reminder-contract；微信订阅消息无 | **partial** |
| 30 应用中心 | 部分匹配 | **升级**：维度6 链路2 应用中心 9 卡跳转 PASS | **covered** |
| 31 我的 | 部分匹配 | profile-contract；清除缓存/微信绑定/关于页待实现 | **partial** |
| 32 修改密码 | 待确认 | 维度4 改密链路(API 级) + L16 佐证页面存在；小程序端无专项实测 | **partial** |
| 33 关于 | 待确认 | 无证据 | **gap** |
| 34 客户统计 | 高匹配 | BT M08 + statistics-contract + 维度2 | **covered** |
| 35 价值服务查询 | 高匹配 | IncentiveCustomerRecords×2 + incentive-contract + 维度8 | **covered** |
| 36 申请延期提交 | 高匹配 | ExtendSubmitOwnership/Concurrency + 维度5/7 全链；挂 M1 | **covered** |
| 37 捞取确认 | 高匹配 | SeaServiceTest + 维度5 owner 变更+保护期 | **covered** |
| 38 审批处理 | 高匹配 | ApprovalApproveRejectSecurityTest + 维度5 | **covered** |
| 39 文件分享选人 | 高匹配 | FolderAcl/PermissionServiceTest + 维度5 分享可见 | **covered** |
| 40-42 通用弹窗/选择器/成功反馈 | 可实现（组件层） | 需求A 半屏面板冒烟；无组件测试框架 | **partial** |
| 43 联系人详情 | 高匹配 | BT M02 + customer-center-contract | **covered** |
| 44 个人排行 | 高匹配 | IncentiveReadRankingTest + FS(6) + ranking-contract + 维度6 链路6 | **covered** |
| 45 客户价值服务明细 | 高匹配 | 维度8 score/customer + AC 需求C 详情页 | **covered** |
| 46 视频文档预览 | 部分匹配 | AC PC 阅读器 24/24；小程序端实机未验 | **partial** |
| 47 数据权限说明 | 高匹配 | BT M17 四类 61 用例 + MR 截图 | **covered** |
| 48 用户协议 | 待确认 | 无证据 | **gap** |
| 49 空状态 | 可实现（组件层） | PC customer-empty-state(13)；小程序未逐一接入验证 | **partial** |
| 50 加载骨架 | 可实现（组件层） | PC pagination(14)；小程序骨架无验证 | **partial** |
| 51 案例分享 | 部分匹配 | IncentiveShareVisibilityTest + 维度8 分享制；微信转发卡片无 | **partial** |

### 2.4 D 组·法布施新模块（29 单元：全部 gap，单列）

《法布施模块需求说明书 v1.1》（2026-10-12，用户已复核、我方 8 条提升点全部纳入）A1 十大类别 / A2 书目审核制 / A3 电子书挂载 / A4 内容治理 / B1 书库浏览 / B2 在线阅读续读 / B3 书架 / B4 阅读记录 / B5 已读未读+NEW / C1 正能量流 / C2 结构化诵读 / C3 心得 / C4 木鱼双用 / D1 评论 / D2 重点描述 / D3 语音跟读 / D4 点赞 / E1 团队统计 / E2 个人统计 / E3 趋势目标 / E4 导出 / E5 续读率 / E6 成就徽章 / F1 自愿提醒 / F2 授权降级 / G1 权限隔离(21 用例契约) / G2 存储独立 / G3 操作留痕 / G4 统一内容表 —— **模块未开工（待甲方圈范围），无实现无测试，29 条全 gap**。不计入已上线产品覆盖率分母；开工时 G1 可参照 PermissionMatrixTest 模式落地。

### 2.5 E 组·非功能（4 单元：covered 3 / partial 1）

| 单元 | 证据 | 状态 |
|---|---|---|
| NFR01 接口安全防护 | JwtAuthFilterTest(11), IdempotencyServiceTest(7), PermissionSecurityFixTest(17), CustomerFullIdempotencyTest(4)；STR 路径穿越 404/token 交叉 | **covered** |
| NFR02 架构与稳定性守卫 | ArchitectureTest(3), StabilityInvariantTest(7) | **covered** |
| NFR03 审计留痕 | SecurityAuditServiceTest(10), LogAspectTest(3), CustomerFinanceAuditLogTest(3), SysAuditLogServiceTest(4)；STR 维度4；**挂 L7 未修** | **partial** |
| NFR04 配置与兼容 | MybatisPlusConfigTest(18), GlobalExceptionHandlerTest(10), ClientIpUtilsTest(5), ProfileYamlParseTest(2), FS app-version(3) | **covered** |

## 3. 已知 gap 带（如实入账）

| # | gap 带 | 定级 | 事实 |
|---|---|---|---|
| G1 | **前端组件级零 spec（最大 gap 带）** | gap | 92 个 .vue 中 **77 个**无邻接组件 spec（15 个有：layout/customer detail 系 3/incentive 3/login 2/reminder 2/system 4）。修正任务书"92 全零"口径 |
| G2 | **E2E 人工非自动化** | partial | 双端 8 维度为一次性人工+脚本执行；可复跑仅 2 个冒烟脚本（24/24、29/29），无常态化回归套件 |
| G3 | 小程序零单测框架 | partial | 43 页无 JS 单测；依赖 29 个静态契约脚本（21 已执行全 PASS）+automator+人工 |
| G4 | 客户编辑/删除无功能用例 | gap | PUT 基本信息编辑未测；删除/archive/restore 仅清理动作（STR ⑦-10） |
| G5 | 真机未测 | gap | 仅 DevTools 渲染器；M3/H2 回归、PDF/Office/视频真机兼容待做 |
| G6 | 微信身份链路 P0 缺口 | gap | 登录/绑定/解绑/换绑无接口无测试（MR P0，MP-05） |
| G7 | 法布施 29 条已立项未开工 | gap（单列） | 需求 v1.1 已复核，待甲方圈范围（条目实数 29，说明书自称 26 系其内部计数不符） |
| G8 | 未修复缺陷挂点 | partial | 截至 2026-10-13：H1/H3/M1/M2/M4 及 L1-L7/L9-L11/L13-L17 未确认修复；H2 本地已修未回归 |

## 4. 反向缺口（测试有、需求条目无）

1. **R1 守卫/契约类测试无书面需求条目**：ArchitectureTest / StabilityInvariantTest / LedgerVoidedStatsContractTest 源自代码裁决（PR-18 统计口径、2026-10-10 阶段总额制）与历史缺陷修复——"工程契约"未条目化，已由 NFR02/NFR03 承接。
2. **R2 前端 spec 引用内部编号**（B1/B3/B5/P2-6）对应需求未文档化。
3. **R3 SysConfigControllerLogoTest** 对应运营配置需求无书面出处（仅与 MP-31 间接相关）。
4. **R4 契约脚本 8 个无执行见证**（在库 29 vs 执行 21；live-qa-boundary/navigation/reconciliation 等 3 个 .py 无记录）。

## 5. 统计

| 范围 | 单元 | covered | partial | gap | covered 率 | covered+partial 率 |
|---|---|---|---|---|---|---|
| **已上线产品（A+B+C+E，不含法布施）** | **70** | **45** | **22** | **3** | **64.3%** | **95.7%** |
| 含法布施（+D） | 99 | 45 | 22 | 32 | 45.5% | 67.7% |

分组：A 后端 17（8/9/0）｜B PC 7（4/3/0）｜C 小程序 42（30/9/3）｜D 法布施 29（0/0/29）｜E 非功能 4（3/1/0）。

## 6. 抽验记录（5/5 通过）

| 映射 | 文件（均存在） | 语义核验 |
|---|---|---|
| M13→PermissionMatrixTest | `aigicrm-backend/src/test/java/com/aigicrm/architecture/PermissionMatrixTest.java`（27 @Test） | 类注释明示"权限矩阵自动化回归：写方法必须 @PreAuthorize 且权限码在字典"——直接对应 |
| M08→LedgerVoidedStatsContractTest | `.../architecture/LedgerVoidedStatsContractTest.java`（3 @Test） | "已作废台账不得计入任何统计口径"+阶段总额制口径钉——直接对应 |
| M07→IncentiveUploadValidationTest | `.../module/incentive/service/IncentiveUploadValidationTest.java`（12 @Test） | "上传入参校验与归属限制：分类/客户/文件槽/文件归属"——直接对应 |
| M03→SeaServiceTest | `.../module/sea/service/SeaServiceTest.java`（9 @Test） | "all 范围用户不得见禁用池存量客户"+completeClaim 回归——直接对应 |
| F02→FinanceTab.spec.js | `aigicrm-ui/src/views/customer/detail/FinanceTab.spec.js`（7 it） | 真实 mount FinanceTab.vue，断言阶段总额制 UI 契约（三态/折叠/作废置灰）——直接对应 |

## 7. 证据索引（回溯路径）

- 后端测试：`02-迭代源代码/aigicrm-backend/src/test/java/com/aigicrm/**`
- 前端 spec：`02-迭代源代码/aigicrm-ui/src/**/*.spec.js`
- 契约脚本：`02-迭代源代码/scripts/check-mobile-*`（29 个）
- 冒烟：`02-迭代源代码/aigicrm-ui/preview-smoke-prod.mjs`、`05-测试与质量/smoke-mp-redesign-ac-20261017.js`
- 系统测试：`05-测试与质量/双端系统测试报告-20261012.md`（v4）
- 原型矩阵：`05-测试与质量/audit-evidence/小程序原型与ANGCIM功能匹配报告.md`
- 需求：`00-项目管理/法布施模块需求说明书-20261012.md`、`00-项目管理/小程序需求A|B|C-*-20261016.md`、`00-项目管理/小程序需求AC实现与验证报告-20261017.md`
- 本矩阵：`05-测试与质量/rtm/rtm-matrix.md` + `rtm-matrix.json`

> 维护约定：新增测试或需求变更时，同步更新本表与 JSON；status 只能因新证据升降，禁止无证据改判。
