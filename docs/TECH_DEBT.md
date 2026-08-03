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
