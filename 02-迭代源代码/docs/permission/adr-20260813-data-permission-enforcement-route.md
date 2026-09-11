# ADR-20260813 数据权限强制路线：维持应用层双锚点，不引入 SQL 级拦截器

> 状态：已接受（2026-08-13）
> 关联：`design.md`（权限设计）、`TECH_DEBT.md`、2026-08-10 全系统审计（对照若依/Jeecg 差距分析）

## 背景

2026-08-10 全系统审计对照若依提出 **P0 建议**：引入 `DataPermissionInnerInterceptor`
（SQL 级数据权限插件，`ancestors` 路径列 + 操作日志注解为 P1/P2）。该建议提出时，
项目尚处于"各查询手写 owner_id IN"状态。

2026-08-11 权限架构重构（main@b987a36，生产已部署）落地了**应用层统一数据访问层**，
彻底改变了该建议的前提。

## 决策

**维持应用层强制，不引入 SQL 级数据权限拦截器。** 该审计项关闭为"已决策不实施"，
后续审计不再记为缺失。

## 依据

1. **双锚点已覆盖全部查询路径**（2026-08-11 重构核心成果）：
   - `ScopeRequest`/`ScopeDecision` 双锚点：列表与详情同源判定，根治了"列表按范围、
     详情按 id"的经典越权（P1-05 已闭环）；
   - `OrgScopeResolver` 七级范围（self/team/dept/office/region/all/org_admin），
     请求实时解析，多角色并集；
   - `DataScopeGuard` 归属守卫（isAdmin ∨ 授权 ∨ 本人），修复了历史 fail-open。
2. **模型不匹配**：若依的 SQL 插件建立在"每行有 dept_id 列"模型上。AIGICRM 数据范围
   以 **owner_id（用户级）** 为主锚点（客户/激励/文件/积分等），team 还需沿
   manager_id 递归 5 层解析——这类逻辑无法用列值 IN 表达，SQL 插件只覆盖最简单
   的一小部分，反而造成"两套口径"的错配风险。
3. **双边界成本**：TenantLineInnerInterceptor（集团隔离）已在 SQL 层。再加一层数据
   权限拦截器 = 同一查询两处改写，排查难度倍增；且应用层已有 30+ 单测覆盖
   （OrgScopeResolver/CustomerAccessService/DataScopeGuard 正反例）。
4. **性能已兜底**：权限上下文 Redis 缓存（TTL 10min，写时精确 evict），解析成本
   不在热路径。

## 证据（应用层覆盖采样）

- 核心类：`common/scope/`（OrgScopeResolver/ScopeRequest/ScopeDecision/DataScopeGuard）
- 消费方（10+ 模块）：ReminderService、SysUserService、SysRoleService、RolePermScopeService、
  ScoreService、IncentiveService、SysLogService、SeaController、ScopeService、UserDetailsServiceImpl
- 统计/分析/审批/公海均经 resolver 过滤（ApprovalService.applyDataScope、
  StatisticsQueryService 范围过滤，0804/0805 已上线验证）

## 备选（触发条件）

若未来出现**绕过 Service 层的查询路径**（原生 SQL 报表、多源直连、外部数据同步读），
再评估 SQL 级拦截器作为纵深防御——届时以新增路径为范围做增量评估，不全局铺开。

## 后果

- 新查询接入规范：必须走 Service 层 + resolver 过滤，禁止 Controller 直查 Mapper
  （ArchitectureTest 已有分层门禁）；
- 审计口径更新：对照若依的"SQL 级数据权限"差距项标记为 ADR 决策项，不再作为缺陷；
- `@AigicrmLog` 操作日志注解（P2）仍按 2026-08-13 计划独立实施，与本文无关。
