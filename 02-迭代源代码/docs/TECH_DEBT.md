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
> 现状：迁移内存活 `biz_*` 表 28 张，`MybatisPlusConfig.TENANT_TABLES` 登记 42 张，**12 张未登记**（全部为既有漂移）。

**本次新增代码引入（R1-01）—— ✅ 2026-11-23 已闭合**

**处置结论：单集团模式**（用户 2026-11-23 确认）。系统只服务一个集团，`tenant_id` 恒为 1。

| 表 | 原问题 | 处置 |
|---|---|---|
| `biz_fabushi_recitation` | 无 `tenant_id` 列 + 未登记 | **`V2026.11.23.5` 补列**（`bigint NOT NULL DEFAULT '1'`，位于 `user_id` 之后），随后登记白名单 |
| `biz_fabushi_shelf_group` | 有 `tenant_id`，未登记 | **已登记**。单集团下 10 个 `user_id=0` 模板行 `tenant_id=1`，可见性正确 |
| `biz_fabushi_bookmark` | 有 `tenant_id`，未登记 | **已登记** |

**为什么单集团也要登记**：不登记 = 写入靠 `DEFAULT 1` 兜、查询完全不过滤，属**隐式契约**；登记后插件自动补 `tenant_id=1`、查询自动过滤，单集团下结果集不变但契约显式。既有 12 张 `biz_fabushi_*` 表全部登记，新 3 张不登记会造成范式断裂，下一个人会照抄错的那个。

**补列成本说明**：本表尚未上生产（生产实测落后 5 个迁移 / 缺 3 张新表，见部署文档 §2），此刻补列**零回填**；若等生产上线后再补，就要 ALTER 有数据的表。

**验证**：163 迁移在 `utf8mb4_general_ci` 与 `utf8mb4_0900_ai_ci` 两种库下各重放一次全成功；42 张白名单表 `tenant_id` 列齐全（等价于生产 `verifyTenantColumns` 启动自检通过）。

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

**既有漂移（R2-01，非本次引入，需逐表决策）**

`biz_file_grant`（有列）、`biz_file_link`、`biz_file_version`、`biz_folder_type_default`、
`biz_form_draft`（无列）、`biz_incentive_read`、`biz_lifecycle_item`（有列）、
`biz_lifecycle_request`、`biz_recycle_bin`（有列）、`biz_sea_config_region`、
`biz_sea_config_scope`、`biz_upload_session`（有列）

**风险**：生产强制 `aigicrm.tenant.enable=true`，未登记表**写入不被插件补租户、恒落默认 1**，
隔离列失真；查询不被租户过滤。同类历史事故：H4 `biz_customer_stage_amount` 漏白名单。
**关闭条件**：逐表给出「登记 / 豁免」结论并落代码；确认无遗漏后清空 `check-tenant-table-registry.sh` 的 `PENDING_FIX`。
