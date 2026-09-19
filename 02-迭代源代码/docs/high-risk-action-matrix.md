# PC 高风险动作映射

本表把 PC 界面动作、路由 feature key、后端权限和数据范围放在同一处。按钮隐藏只改善体验，最终拒绝必须由后端控制器、服务 ACL 或数据范围守卫完成。源码行号来自 `docs/unified-feature-audit.md` 的本地生成报告。

| Feature / 页面动作 | PC feature key | 后端权限 / 守卫 | 数据范围或对象边界 | 当前证据 |
| --- | --- | --- | --- | --- |
| 客户新增 | `customer:add` | `customer:add` + 客户创建组织校验 | 当前用户允许组织/集团 | `CustomerController` create；`customer/add.vue` |
| 客户编辑/释放 | `customer:edit` | `customer:edit` + `CustomerAccessService`；释放另校验负责人、公海池 | 客户归属与匹配公海池 | `CustomerController` update/release；`MainTable.vue` |
| 客户删除 | `customer:delete` | `customer:delete` + 删除访问校验 | 客户可删除范围 | `CustomerController` delete；`MainTable.vue` |
| 财务录入/删除 | `customer:edit` + `finance:view` 读取 | `customer:edit` 写入；`finance:view` 仅读敏感摘要 | 客户可编辑/可查看范围 | `FinanceTab.vue`；`CustomerController` finance endpoints |
| 联系人维护 | `customer:edit` + `contact:view` 读取 | `customer:edit` 写入；`contact:view` 读取守卫 | 客户可编辑/可查看范围 | `InfoTab.vue`；`CustomerController` contacts endpoints |
| 客户分享 | `customer:edit` | `customer:edit` + 客户访问校验 | 分享目标和客户归属 | `detail.vue`；customer share endpoints |
| 文件上传/删除/分享 | `file:upload` / `file:delete` / `file:share` | `FileController` 细分权限 + `FileService` 文件/文件夹 ACL | 文件夹 owner/ACL、共享对象 | `FileController`；`FileService` |
| 文件夹 ACL | `file:acl:manage` | `FolderAclController` 全部 ACL endpoints | ACL 管理者与目标文件夹 | `FolderAclController.java:20` |
| 公海捞取/删除 | `sea:view` + 行级 `canClaim/canDelete` | `SeaService` 池级权限和状态校验 | 可见公海池、行状态、池操作权限 | `sea/index.vue`；`SeaService` |
| 审批通过/驳回 | `approval:approve` / `approval:reject` | `ApprovalService` 当前节点审批人校验 | 当前审批节点 | `approval/index.vue`；approval security tests |
| 积分项管理 | `score:manage` | `ScoreController` `score:manage` | 配置管理边界 | `ScoreController.java:20-39` |
| 积分批量录入 | `score:entry` | `ScoreController` `score:entry` + service participant/data-scope 校验 | 可管理人员/部门 | `ScoreController.java:45-65` |
| 用户创建/编辑/删除 | `system:user:add/edit/delete` | `SysUserController` + `OrgAdminService` | 组织管理员管理组织；目标用户不可越权 | `SysUserController`；`SysUserControllerOrgAdminTest` |
| 系统配置/权限规则 | 对应 `system:*` feature key | 控制器 `@PreAuthorize` + service guard | 管理员/组织范围 | system views/controllers |
| 客户保护期批处理 | 无普通用户 feature key | `TaskController` 方法体强制 `SecurityUtils.isAdmin()`，非管理员返回 403 | 全库客户保护期数据，仅系统管理员 | `TaskController.java`；`check-task-admin-contract.sh` |

## 维护规则

- 新增高风险按钮必须同时补 PC feature key、后端权限/守卫、数据范围和回归证据。
- 文件变更权限已由 V22 拆分；V22 会继承既有 `file:view` 角色绑定，下载/列表仍使用 `file:view`。

## 字典反向漂移说明

这些权限仍可能出现在历史 `permission-dict.md`，但不应被当作当前接口能力：

| 历史权限 | 当前事实 | 证据 |
| --- | --- | --- |
| `customer:share` | 客户分享沿用 `customer:edit` 与客户访问守卫；字典权限待数据库矩阵复核后再决定是否重新启用。 | `CustomerController` share endpoints；`docs/superpowers/specs/2026-08-05-permission-system-design.md:58` |
| `incentive:delete` | 激励删除由授权表 grant 能力和服务范围控制，旧码保留作历史字典记录。 | `IncentivePermissionController.java:19`；`IncentiveService` |
| `incentive:view-all` / `incentive:view-shared` | 已由 `biz_incentive_grant` 的查看能力替代。 | `IncentivePermissionController.java:19` |
| `score:ranking` | 已统一为 `score:view`。 | `ScoreController.java`；PC `score/ranking` route |
| `sea:pool:delete` | 池删除入口使用 `sea:view` 进入公海域，最终删除仍由 `SeaService.deletePool` 的池级权限判定，不再由控制器硬门单独判定。 | `SeaController.java:69`；`SeaService` |
| `statistics:scoreboard` | 历史积分榜权限，当前排行接口使用 `score:view`。 | `ScoreController.java` |
| `system:menu:edit` | 历史残留，菜单写入端点已移除；当前仅允许使用 `system:menu:view` 读取菜单树。 | `SysMenuController.java:19` |
- 重新运行 `python3 scripts/gen-unified-feature-audit.py --root . --out docs/unified-feature-audit.md` 更新按钮与行号来源。
