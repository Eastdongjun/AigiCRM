# 架构技术债清单（ArchUnit 2026-08-03 全量扫描）

> 分层约束规则：Controller 不得依赖 Mapper/Entity（controller -> service -> mapper/entity）。
> 处置策略：**改到哪修到哪**——重构一个 Controller，就从本清单移除一个。

## 标准答案（已重构）

- **StatisticsController**（624 行 → 62 行薄壳 + StatisticsQueryService 602 行）✅ 已合规，作为后续重构模板

## 待重构清单（21 个）

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
| 14 | SysLegalEntityController | system | 中 |
| 15 | SysLogController | system | 中 |
| 16 | SysMenuController | system | 中 |
| 17 | SysOrgController | system | 中 |
| 18 | SysPermissionRuleController | system | 中 |
| 19 | SysPositionController | system | 中 |
| 20 | SysRoleController | system | 中 |
| 21 | SysUserController | system | 高（用户管理） |

## 重构模板（照 StatisticsController 做）

1. 新建 `XxxQueryService`（注入 mapper，搬入查询+组装逻辑，写 5 要素注释）
2. Controller 瘦身为路由壳（@PreAuthorize + Result.ok(service.xxx())）
3. 从 ArchitectureTest.java 的 `TECH_DEBT_CONTROLLERS` 清单移除该类名
4. `mvn -Dtest=ArchitectureTest test` 必须绿

## 检查命令

```bash
cd ycmn-backend && mvn -q -Dtest=ArchitectureTest test
```

## 三脚本交叉核对发现（2026-08-03 完整检查）

> CI 中三脚本为必跑步骤；以下问题需修复后 CI 才能绿。

### 1. Schema/Entity 漂移（12+ 处）— ycmn_database_test.sql 落后于实体

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
