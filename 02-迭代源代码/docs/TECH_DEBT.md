# 架构技术债清单（ArchUnit 2026-08-03 全量扫描）

> 分层约束规则：Controller 不得依赖 Mapper/Entity（controller -> service -> mapper/entity）。
> 处置策略：**改到哪修到哪**——重构一个 Controller，就从本清单移除一个。

## 标准答案（已重构）

- **StatisticsController**（624 行 → 62 行薄壳 + StatisticsQueryService 602 行）✅ 已合规，作为后续重构模板

## 待重构清单（19 个，2026-08-13 核对）

> SysOrgController 已删除（2026-08-09 sys_dept/sys_org 双表收口）、
> SysLegalEntityController 已删除（2026-08-13 孤儿端点清理），从清单移除。

| # | Controller | 模块 | 风险等级 |
|---|-----------|------|---------|
| 1 | AuthController | auth | 高（登录/权限相关） |
| 2 | ApprovalController | approval | 高（审批流） |
| 3 | ApprovalConfigController | approval | 中 |
| 4 | CustomerController | customer | 高（核心业务） |
| 5 | FileController | file | 中（历史出过路径穿越漏洞 REV-01） |
| 6 | IncentiveController | incentive | 高（积分规则） |
| 7 | ReminderController | reminder | 中 |
| 8 | ScoreController | score | 高（积分/权限 403 事故相关） |
| 9 | SeaController | sea | 高（公海数据范围） |
| 10 | SysConfigController | system | 中 |
| 11 | SysDeptController | system | 中 |
| 12 | SysDictController | system | 中 |
| 13 | SysGroupController | system | 中 |
| 14 | SysLogController | system | 中 |
| 15 | SysMenuController | system | 中 |
| 16 | SysPermissionRuleController | system | 中 |
| 17 | SysPositionController | system | 中 |
| 18 | SysRoleController | system | 中 |
| 19 | SysUserController | system | 高（用户管理） |

## 重构模板（照 StatisticsController 做）

1. 新建 `XxxQueryService`（注入 mapper，搬入查询+组装逻辑，写 5 要素注释）
2. Controller 瘦身为路由壳（@PreAuthorize + Result.ok(service.xxx())）
3. 从 ArchitectureTest.java 的 `TECH_DEBT_CONTROLLERS` 清单移除该类名
4. `mvn -Dtest=ArchitectureTest test` 必须绿

## 检查命令

```bash
cd aigicrm-backend && mvn -q -Dtest=ArchitectureTest test
```

## 三脚本交叉核对发现（2026-08-03 完整检查）

> CI 中三脚本为必跑步骤；以下问题需修复后 CI 才能绿。

### 1. Schema/Entity 漂移（12+ 处）— aigicrm_database_test.sql 落后于实体

- SQL 缺表：biz_approval_config、sys_group、sys_legal_entity、sys_org、sys_org_admin、sys_permission_rule、sys_position、sys_position_rule
- SQL 缺列：biz_approval_node.config_id/sign_mode、biz_customer.group_id/legal_entity_id
- 处置：确认是否有对应迁移脚本（sql/migration-*.sql）；有则并入测试 SQL，无则补迁移

### 2. API Wiring 缺口（12 个 PUT 接口）

- /api/system/depts|dict|groups|legal-entities|menus|positions|roles|rules|users/{id} 等后端存在但前端 api 封装缺失
- 处置：前端补封装或确认前端使用方式后排除

### 3. 权限码未同步（system:rule:*、system:user:delete）

- SysPermissionRuleController / SysUserController 的 @PreAuthorize 权限码在 SQL 权限表中无对应记录
- 风险：与历史权限 403 事故同类（score:view 未绑角色）
- 处置：SQL 权限表补记录 + 绑定角色，测试环境验证

## 三脚本修复进展（2026-08-03 第二轮）

- ✅ 权限码：27 个缺失权限码已全部补入 sys_menu（含 score:view 历史 403 事故码）+ admin 角色绑定
- ✅ Schema：8 张缺表 + 2 表补列 + sys_org/sys_permission_rule 列名修正 + 3 张关联表，0 错误
- ⏳ API：剩 2 个（GET/PUT /system/roles/{roleId}/orgs）——**后端无此端点**，前端调用了不存在的接口，需业务确认（删前端或补后端）
- ℹ️ 5 个警告：sys_dept/sys_user_bak_wll_0721 为备份表无实体（正常噪音）

## 剩余问题闭环（2026-08-03 第三轮）

- ✅ role-orgs 2 个接口已补齐（SysRoleController GET/PUT /{id}/orgs + SysRoleService.updateOrgs）
- ✅ check_api_wiring 158/158 全匹配
- ✅ mvn 全量测试通过

## role-orgs 端到端验证（2026-08-03 第四轮）

- ✅ 后端接口已部署到测试环境（jar 替换 + 重启，健康检查 200）
- ✅ 冒烟测试 21/21 全过（含 role-orgs GET 断言，role=10 返回 200）
- ✅ 真实库无需迁移（新表与权限码均已存在）

## 权限体系扩展性结论（2026-08-03 实测）

- ✅ 新增角色机制正常：创建角色→分配菜单→绑定用户→权限即时生效（403/200 正确）
- ⚠️ **数据范围口径待业务确认**：org/org_and_children 当前按"客户负责人所属组织"过滤。
  客户 578 属大区48办事处49 但负责人是 admin（不在大区48）→ 大区经理不可见。
  若业务要求"按客户所属组织（officeId→大区）"过滤，需改 CustomerService.applyDataScope 口径。
- ⚠️ **org_id 维护要求**：管理角色（org/org_and_children）生效前提是用户维护 org_id；
  为空时自动降级"仅本人"（安全方向正确，不越权不 500）。
  新增管理岗用户时需在用户管理设置组织字段。

## 历史备份表清理（2026-08-16）

- ✅ 8 张 `*_bak_*` 历史快照表已归档 NAS `backup/archive/bak-tables-20260816/`（每表独立 .sql）后从生产删除（生产 63→55 表；其中 2 张空表）。
- ✅ 删除已版本化：`V19__drop_backup_tables.sql`（DROP IF EXISTS，幂等）——防止空库按 V1 重建时 bak 表复活；本文件尚未部署到生产（生产表已手删，下次常规部署时 V19 自动生效）。
- ✅ 数据字典已重生成（55 表，bak 表章节清零）；审查基准 dump 已重导出（55 表，无 bak 噪音）。

## 租户白名单登记漂移（2026-11-23 评审登记 · CODE-REVIEW-20261123 R1-01 / R2-01）

> 契约：`scripts/check-tenant-table-registry.sh`（2026-11-23 新增，已接入 `dev-verify.sh contracts`）
> 现状（2026-11-23 收口后）：迁移内存活 `biz_*` 表 28 张，`MybatisPlusConfig.TENANT_TABLES` 登记 **52 张**，
> 豁免 2 张（`biz_form_draft` / `biz_incentive_read`，理由见文末），**`PENDING_FIX` 已清空，脚本零 WARN**。

**本次新增代码引入（R1-01）—— ✅ 2026-11-23 已闭合**

**处置结论：单集团模式**（用户 2026-11-23 确认）。系统只服务一个集团，`tenant_id` 恒为 1。

| 表 | 原问题 | 处置 |
|---|---|---|
| `biz_fabushi_recitation` | 无 `tenant_id` 列 + 未登记 | **`V2026.11.23.5` 补列**（`bigint NOT NULL DEFAULT '1'`，位于 `user_id` 之后），随后登记白名单 |
| `biz_fabushi_shelf_group` | 有 `tenant_id`，未登记 | **已登记**。单集团下 10 个 `user_id=0` 模板行 `tenant_id=1`，可见性正确 |
| `biz_fabushi_bookmark` | 有 `tenant_id`，未登记 | **已登记** |

**为什么单集团也要登记**：不登记 = 写入靠 `DEFAULT 1` 兜、查询完全不过滤，属**隐式契约**；登记后插件自动补 `tenant_id=1`、查询自动过滤，单集团下结果集不变但契约显式。既有 12 张 `biz_fabushi_*` 表全部登记，新 3 张不登记会造成范式断裂，下一个人会照抄错的那个。

**补列成本说明**：本表尚未上生产（生产实测落后 5 个迁移 / 缺 3 张新表，见部署文档 §2），此刻补列**零回填**；若等生产上线后再补，就要 ALTER 有数据的表。

**验证**：163 迁移在 `utf8mb4_general_ci` 与 `utf8mb4_0900_ai_ci` 两种库下各重放一次全成功；52 张白名单表 `tenant_id` 列齐全（等价于生产 `verifyTenantColumns` 启动自检通过）。

---

### 单集团假设：开通第二集团前的阻断项（2026-11-23 登记）

> 当前架构假定 **单集团**（`tenant_id` 恒 1）。这不是"暂时没配第二个"，而是**有代码依赖这个前提**。
>
> **实证（2026-11-23 直连生产）**：53 张含 `tenant_id` 的表、共 **5764 行**，
> `tenant_id` **只有 `1` 一个值，零例外**。详见 `03-阶段/06-测试/02-证据/PROD-BASELINE-20261123.md` §二。
>
> 开通第二集团前，以下三项必须先处理，否则会静默产生错误行为：

| # | 位置 | 问题 | 后果 |
|---|---|---|---|
| MG-1 | `biz_fabushi_shelf_group` 的 10 个自动组模板行（`user_id=0, tenant_id=1`） | 模板行只有一份，不会按集团复制 | 集团 2 的用户**看不到任何自动分组**，书架功能残缺 |
| MG-2 | `biz_fabushi_shelf_group` 唯一键 `uk_fb_shelf_group_user_name (user_id, name)` | **不含 `tenant_id`** | 集团 2 插入同名自动组时**唯一键冲突**，迁移/初始化直接失败。需改为 `(tenant_id, user_id, name)` |
| MG-3 | 全库 `tenant_id` 均无单独索引（既有 12 张 `biz_fabushi_*` 亦如此） | 单集团下选择性为 0，建索引无意义 | 多集团后租户过滤列无索引，大表全扫。需按访问路径补 `(tenant_id, ...)` 复合索引 |

**关闭条件**：确定要支持多集团时，把本节升为独立需求，MG-1/MG-2/MG-3 逐项落地后才可开通第二集团。

---

**既有漂移（R2-01）—— ✅ 2026-11-23 已闭合（12 张全部收口）**

12 张逐表取证后分两类处置。取证方法：迁移块级判定（`CREATE TABLE`/`ALTER TABLE` 块内是否有
`tenant_id`）＋ 测试库 `information_schema` 列定义 ＋ 行数据 `tenant_id` 分布 ＋ 代码访问路径盘点。

| 表 | 库中 tenant_id | 存量行数 / 取值 | 访问路径 | 处置 |
|---|---|---|---|---|
| `biz_file_grant` | `NOT NULL DEFAULT 1` | 178 行 / 仅 `1` | MP 实体 + 注解 SQL；业务层另显式过滤 | **登记** |
| `biz_file_link` | `NOT NULL DEFAULT 1` | 0 行 | **代码零引用** | **登记**（防照抄） |
| `biz_file_version` | `NOT NULL DEFAULT 1` | 0 行 | MP 实体 + 3 条平铺 `@Select` | **登记** |
| `biz_folder_type_default` | `NOT NULL DEFAULT 1` | 62 行 / 仅 `1` | MP 实体 | **登记** |
| `biz_lifecycle_item` | `NOT NULL DEFAULT 1` | 0 行 | JdbcTemplate（`LifecycleService`） | **登记** |
| `biz_lifecycle_request` | `NOT NULL DEFAULT 1` | 0 行 | JdbcTemplate（`LifecycleService`） | **登记** |
| `biz_recycle_bin` | `NOT NULL DEFAULT 1` | 29 行 / 仅 `1` | JdbcTemplate（`RecycleBinService`/`RecycleService`） | **登记** |
| `biz_sea_config_region` | `NOT NULL DEFAULT 1` | 4 行 / 仅 `1` | **代码零引用**（已被带 JSON 列的 `biz_sea_config` 取代） | **登记**（防照抄） |
| `biz_sea_config_scope` | `NOT NULL DEFAULT 1` | 175 行 / 仅 `1` | **代码零引用** | **登记**（防照抄） |
| `biz_upload_session` | `NOT NULL DEFAULT 1` | 0 行 | JdbcTemplate（`FileChunkUploadService`） | **登记** |
| `biz_form_draft` | **无此列**，租户维度是 `group_id` | 3 行 | MP 实体 + Mapper | **EXEMPT** |
| `biz_incentive_read` | **无此列** | 1632 行 | 仅 JdbcTemplate，无 MP 实体/Mapper/XML | **EXEMPT** |

**登记 10 张的安全性依据**：`tenant_id` 取值集合恒为 `{1}`，零 NULL、零其他集团 ⇒ 单集团前提下
插件追加 `tenant_id=1` 不改变结果集，**无数据可见性变化**。同时纳入 `verifyTenantColumns` 启动自检。
其中 3 张（`biz_file_link`、`biz_sea_config_region`、`biz_sea_config_scope`）当前无任何代码引用，
登记属"防下一个人照抄错范式"；确需清理应走 `DROP` 迁移，而不是留在灰区。

**豁免 2 张的理由（不是"不属租户维度"，而是"不适用该插件"）**：

- `biz_form_draft`：租户维度列是 `group_id`。MP 3.5.6 的 `TenantLineHandler#getTenantIdColumn()`
  是**全局单值**，无法按表切换列名，所以进不了白名单。隔离由业务层保证——
  `FormDraftService:39/61/104/119` 四处读写全部 `.eq(getGroupId, groupId)`，保存时 `:92 setGroupId`；
  `FormDraftController:32/40/57/67` 的 `groupId` 取自 `GroupScopeUtil.currentGroupOrNull()`（会话），
  不取客户端入参，无越权入口。**硬加一个平行 `tenant_id` 列会造出两个租户真相源，比现状更差。**
- `biz_incentive_read`：无 `tenant_id` 列，且**无任何 MyBatis-Plus 访问路径**（无 `@TableName` 实体、
  无 Mapper、无 XML、无注解 SQL）。6 处访问全在 JdbcTemplate（`IncentiveService:462/465/1675/1687/1807`、
  `RecycleService:290/729/1007/1017`），按 `user_id`/`record_id` 归属过滤，两者均来自已被租户隔离的上下文。
  ⚠️ **诚实说明**：`check-native-sql-tenant-scope.sh` 是**文件级**判定（文件内出现一次 `tenant_id`
  即整文件合格），`IncentiveService` 通过该门禁**不等于**上面几条语句被逐条限定；此处豁免依据是
  上面那条归属链路，不是那个脚本。该脚本的粗粒度本身是另一笔待改进项。

**验证**：`bash scripts/check-tenant-table-registry.sh` → `白名单表 52 张`，**零 WARN**，退出码 0；
新增参数化测试 `MybatisPlusConfigTest#r201DriftTablesAreTenantIsolated`（10 个表逐个断言
`SELECT` 会被追加 `tenant_id` 条件），防止后续被删回灰区。

**关闭条件**：✅ 已达成——`PENDING_FIX` 清空，`EXEMPT` 有理由与证据，脚本零 WARN。


---

## Sonar 质量门未清项（2026-11-23 阶段 5 登记 · R3-01…R3-08）

> 完整证据：`03-阶段/06-测试/02-证据/STAGE5-DEBT-20261123.md`
> 门禁项目：本地 SonarQube `127.0.0.1:19000`，key `Eastdongjun_AigiCRM-local`，门 `AIGICRM-P0-5`
> 现状：**ERROR**（`new_blocker_violations=1`、`new_bugs=3`、`new_major_violations=290`、
> `new_security_hotspots_reviewed=0.0`）。本轮已把 BLOCKER 5→1、CRITICAL 15→0、BUG 10→3。

| 单号 | 规则 | 数量 | 为什么不能批量修 |
|---|---|---|---|
| R3-01 | `java:S5778` | 129 | 测试 lambda 内多次可能抛异常的调用；需逐例判断哪一次才是被测目标，批量改会把断言目标改错 |
| R3-02 | `java:S6204` | 54 | `Stream.toList()` 返回**不可变**列表，下游有 `add/remove/sort` 会抛 `UnsupportedOperationException`，须逐处确认可变性 |
| R3-03 | `java:S1168` | 36 | null→空集合是**契约变更**，调用方现有 null 判断变死代码，须先确认每个调用方语义 |
| R3-04 | `java:S6809` + `S2229 FileService:1294` | 11+1 | 与 S5.3 God class 拆分同一件事（都要把单文件删除抽成独立 bean）；单独做等于把 `FileService` 改两遍 |
| R3-05 | `java:S112` | 7 | 需定义专用异常类型并改调用方捕获，属 API 设计变更 |
| R3-06 | `java:S6863` | 2 | 命中点 `FileController:316/373` 是 `ResponseEntity.notFound().build()`——**明确 404，规则与语义不符**，为消红改 HTTP 语义会伤前端 |
| R3-07 | 项目 key 收敛 / 新增代码基线 | — | 三处 key 并存（`Eastdongjun_AigiCRM` @SonarCloud、`Eastdongjun_AigiCRM-local` @本地、`aigicrm-backend-local` 陈旧）；且 `new_code_period=PREVIOUS_VERSION` 却从未设 `sonar.projectVersion`，导致全项目存量被算作「新增」 |
| R3-08 | `java:S1874` `selectBatchIds` | 69 | 机械替换但需同步约 40 处测试 mock（否则 `when(...)` 打不中静默返回 null）；MINOR 不阻断 |

**关闭条件**：逐条给出「已修 / 确认误报并写明理由 / 升为独立需求」的结论后，从本表删除。

**已修不登记（供对照）**：`S3252`×7、`S3516`、`S2447`×3、`S1948`×3、`S2229`×2（`TaskService` 自调用）、
`S6857`、`S1452`、`S2259`×4、`S3776`、`S2699`、`S1192`×2，共 26 处，明细见证据文档 §2.3。

**环境噪声（非缺陷）**：JaCoCo 0.8.16 对 `net.sf.jsqlparser.parser.CCJSqlParserTokenManager#jjMoveNfa_0`
报 `MethodTooLargeException`（第三方生成代码方法体超限），插桩跳过该类，影响覆盖率统计完整性。
