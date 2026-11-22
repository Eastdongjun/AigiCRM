# Java S3776 剩余批次规划（2026-11-18，基线 c0a50b13）

> 基线 72 条；B-java2（14）+ B-java4（4）落地后预期剩 54 条（若 Sonar 无新增）。
> 原则：每批 4–8 个函数；优先同文件/同域，便于一次测试；commit → 冻结 → QC 扫 → 下一批。

## B5（低复杂度 16–22，约 10 条）

| 文件 | 行 | 复杂度 | 拆分思路 |
|---|---|---|---|
| FabushiDashboardService | 384 | 16 | 成员/部门统计与“未分配”行拆出 |
| LifecycleService | 212 | 19 | 目标校验/角色复制/激励迁移/直属迁移拆出 |
| SysUserService | 474 | 27 | update 的角色替换与 org 重算拆出（与 B-java2 同文件，需基于最新） |
| FolderPermissionService | 644 | 24 | 按 grant 谓词拆分（与 B-java2 同文件） |
| FileService | 1426 | 26 | downloadById 的资源/range 响应拆分（同文件，需基于最新） |
| CustomerService | 1539 | 26 | 与 B-java2 同文件，基于最新 |
| FabushiDashboardService | 196 | 21 | 阅读榜聚合拆分 |
| PermissionMetadataService | 139 | 24 | 元数据扫描拆分 |
| ScoreService | 280 / 90 | 26 / 25 | 评分规则分支拆分 |
| SysRoleService | 309 | 51 | 角色保存大方法（可放 B6） |

## B6（中 27–40）

FileGrantService 155/198（32/30）、FileService 1337（34）、CustomerService 671/971（33/32）、
ApprovalService 664（26）、RolePermScopeService 162/271（26/30）、PermReconcileService 102（32）、
OrgScopeResolver 229/306（22/28）、ReminderService 124（34）、FolderTypeDefaultService 92（35）、
ScoreService 164（36）、SeaService 70（33）、SeaController 145（33）、CustomerExpireTask 152（33）、
LogAspect 142（29）、JwtAuthFilter 33（48，可放 B7）。

## B7（大 40–60）

FileGrantService 348（49）、FolderPermissionService 488/570（58/32）、CustomerService 179（44）、
ApprovalService 896（50）、RolePermScopeService 238/328（44/39）、SysRoleService 309（51）、
AuthorizationService 255（35）、UserDetailsServiceImpl 50（64）、PermCenterService 75（36）、
OrgScopeResolver 345（56）、StatisticsQueryService 842/1031（44/43）、ReminderService 282（44）、
IncentiveService 732（38）、ScoreService 164（36）。

## B8（巨型 80+，单独窗口 + 额外测试）

- FileService 208（114）——文件列表/范围主方法，先补特征测试再拆。
- IncentiveService 1287（115）——激励统计/持久化主方法。
- ApprovalService 255（87）——审批创建/节点生成。
- StatisticsQueryService 467（92）——统计查询主方法。
- UserDetailsServiceImpl 50（64）。
- FolderPermissionService 488（58）。
- OrgScopeResolver 345（56）。

## 约束

- 每批只改目标函数；新增 helper 参数 ≤7（避免 java:S107 新代码违规）。
- 新增 helper 声明特定异常（避免 java:S112）。
- 每次 `mvn -q test` 必须 1194/0；契约 0 FAIL。
- 同文件跨批（FileService/FolderPermission/CustomerService/SysUser/Approval/Incentive）必须
  以上一批落地后的 HEAD 为基线，禁止用旧 worktree 覆盖。
