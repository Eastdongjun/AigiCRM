# 技术实施计划

## 已确认影响范围

### 影响范围清单（写代码前逐项核对）

| 层 | 涉及项 | 核对结果 |
|---|---|---|
| 数据库 | 表/字段/索引/迁移脚本 | 无改动（已核实：不修角色绑定，只放宽接口鉴权 + 服务层查询过滤） |
| 后端 | 接口/Service/状态机/权限/事务 | `SysDictController.java`（2 个 GET 注解）、`SysDeptController.java`（2 个 GET 注解）、`CustomerService.java`（+1 公共方法）、`StatisticsQueryService.java`（3 个方法加过滤） |
| 前端 | 页面/按钮/组件/权限点/错误提示 | 无改动 |
| 其他 | 报表/导出/移动端/第三方接口 | 无（统计接口被 /statistics 页与首页看板共用，响应结构不变） |

### 隐藏入口检查（功能不仅来自页面）

- [x] Excel/CSV 批量导入：`/api/customer/import` 与字典/部门只读接口无关，且权限 `customer:import` 未变 —— 不受影响
- [x] 第三方接口 / SAP 同步 / OA 流程：无外部对接（系统内无集成）
- [x] 手机端 / 小程序：无独立端
- [x] 定时任务 / 自动流程：无定时任务调用统计/字典/部门接口（已 grep 无 @Scheduled 调用这些接口）
- [x] 后台管理员操作：admin 全部权限不变（all 数据范围 = 全量，行为零变化）
- [x] 历史数据兼容：不涉及数据表与字段

## 架构与关键决策

- **决策 1（最终版）：只读引用接口鉴权放宽为 `isAuthenticated()`，同时保留已勾选的隐藏权限载体 98/99**
  - 历程：初选方案 B（放宽接口）→ 发现隐藏权限项 98/99（基础数据-部门/字典，visible=0）后改选"勾选授权"（已生产库勾选行政/销售员）→ 用户最终确认**保留方案 B**（字典接口已改 isAuthenticated），部门接口同步放宽。
  - 原因：字典/部门树是业务表单公共引用数据（7+ 页面依赖）；放开为登录可读后新角色/漏配不再产生 403（直接回答"新增角色自动关联"顾虑：无需关联）；已勾选的 98/99 作为权限体系既有能力保留（双保险，无冲突）。
  - 写操作与其余系统管理接口权限**严格不变**。
- **决策 2：统计分析按角色数据范围过滤，复用 CustomerService 既有 scope 语义**
  - 原因：用户规则"统计数据分析里数据是个人的；上一级能看到下一级"即现有 `applyDataScope` 体系（self / dept / dept_and_children / org / custom / all）。现状 `analysis()` 全量查询导致销售员可见全公司经营数据（越权），`dailyStats/weeklyStats` 文件统计同样全量。
  - 实现：`CustomerService` 新增公共方法 `getCurrentScopeUserIds()`（返回当前用户可见的用户 ID 集合，`null` = 全量），复用私有 `getDescendantDeptIds/getUserIdsByDeptIds`；`StatisticsQueryService` 在 customer 查询加 `ownerId IN`、file 查询加 `uploaderId IN`。
  - 替代方案（拒绝）：给统计接口加 `@DataScope` 注解 → 现有 `DataScopeAspect` 只支持 `BizCustomer` 单实体注入，file 查询无法覆盖，且与 CustomerService 语义重复。
- **决策 3：`analysis()` 的 `officeId` 参数与数据范围 AND 合并**（选了办事处仍精确过滤，范围外办事处返回 0/空）。

## 数据、API 与事件契约

- `GET /api/system/depts`、`GET /api/system/depts/tree`、`GET /api/system/dict/data/{code}`、`GET /api/system/dict/data/type/{id}`：请求/响应结构不变，鉴权条件由 `hasAuthority(...)` 变为 `isAuthenticated()`。
- `GET /api/statistics/analysis`、`/daily-stats`、`/weekly-stats`：响应结构不变，数值按数据范围收窄。
- 无新接口、无新表、无幂等/并发问题。

## 权限与安全边界

- 认证：未登录访问仍 401（SecurityFilterChain 全局逻辑不变）。
- 授权：字典/部门只读 = 登录即读；写操作（POST/PUT/DELETE）保持 `system:config:edit`/`system:dept:edit`；其他系统管理接口不变。
- 数据范围：统计查询按 `SecurityUtils.getCurrentDataScope()` 解析（self=本人；dept=本部门；dept_and_children=部门及子部门；org/org_and_children/custom=组织矩阵；all=全量），与客户列表/详情/看板口径完全一致。
- 敏感字段：不涉及。

## 实施顺序

1. `SysDictController.java`：`listData`、`listDataByTypeId` 注解 → `isAuthenticated()`（已完成，含说明注释）。
2. `SysDeptController.java`：`list`、`tree` 注解 → `isAuthenticated()`（已完成，含说明注释）。
3. 生产库：给销售员（11）、行政（9）勾选隐藏权限 98/99（已完成并验证，双保险；回滚语句已记录）。
4. `CustomerService.java`：新增 `getCurrentScopeUserIds()` 公共方法（已完成，复用私有辅助，构造器签名不变）。
5. `StatisticsQueryService.java`：`analysis()` 全部 customer/file 查询 + `dailyStats()`/`weeklyStats()` file 查询加范围过滤（已完成）。
6. 单测：`CustomerServiceTest` 新增 4 个数据范围测试（all/self/null/dept_and_children，MockedStatic(SecurityUtils)）。
7. 本地编译（JDK17，~/.mavenrc 指向 /tmp/jdk-17.0.20+8 已恢复）+ `mvn test` 全量。
8. 浏览器实测（NM005 新增客户页/统计页；admin 回归；接口级 403/200 断言）。
9. 生产部署（docker compose 重建 backend 镜像），生产实测 NM005 + admin 回归。

## 测试策略

- 单测：`StatisticsQueryServiceTest`（新增）——self 范围只统计本人 ownerId/uploaderId；`getCurrentScopeUserIds` 各 dataScope 分支（all→null、self→[自己]、dept_and_children→部门+子部门用户）。
- 回归：`mvn test` 全量（ArchitectureTest、CustomerServiceTest、ApprovalServiceTest、IncentiveScoreTest、SysRoleServiceTest 等）。
- 接口级（浏览器/HTTP）：未登录 401；NM005 GET 字典/部门 200；NM005 POST 字典 403；NM005 GET users 403；admin 全通。
- E2E（浏览器实测，AC-001~004/009）：NM005 新增客户页无弹窗、下拉正常、可保存；NM005 统计页个人数据（AC-010/013）；admin 统计页全量（AC-012）；行政 dept_and_children 验证（AC-011，用测试环境或生产行政账号）。

## 迁移、发布与兼容

- 无数据迁移。发布 = 重建 `aigicrm-backend` Docker 镜像 + `docker compose up -d backend`（先生产备份/保存当前镜像标签以便回滚）。
- 兼容：前端零改动；接口结构零变化；鉴权为放宽方向，旧客户端不受影响。
- 回滚：git revert 本次 4 个文件改动 → 重新构建部署即可恢复原权限（原 jar 保留 `/root/aigicrm/backup/`）。

## 可观测性

- 保持现有日志；不改日志级别。
- 部署后验证：`curl` 接口状态码、浏览器控制台零 403、统计接口响应数值抽查。

## 回滚

- 回滚触发器：生产实测发现统计数值异常或新 403。
- 步骤：SSH 生产 → 恢复备份镜像（`/root/aigicrm/backup/` 保留原 jar/镜像标签）→ `docker compose up -d backend` → NM005/admin 复测。
- 数据零改动，无数据回滚需求。

## 风险与盲区

- 部署瞬间后端重启，生产接口秒级中断（可接受，低峰操作）。
- 统计口径变化：修复后销售员/行政看到的统计数值必然小于修复前（修复前是越权全量）——需向用户明示这是修复而非回归；admin 数值不变。
- `getCurrentScopeUserIds()` 与 `applyDataScope` 若将来演进需保持双口径一致（同一处解析 dataScope，避免分叉）。
- 测试环境（111.229.37.220）与生产角色数据可能不同，AC-011（行政 dept_and_children）优先在生产验证；若生产无行政账号，需用管理员在角色-用户处临时创建或只做代码级验证并如实标注。
- 未覆盖盲区：`dashboard()` 的 `seaStats`（公海统计全量）属公海共享业务常态，本次不动；若用户后续要求公海也按范围，另立需求。
