# role 0（销售总监）权限清单与最小权限建议（2026-11-18，B16）

> 用户拍板：**应收窄**。本报告只列事实与建议，不做生产变更；实施由 0b 出配置/迁移，生产应用需窗口授权。
> 数据源：新库 `aigicrm_prod_20261118`；`sys_role` / `sys_role_menu` / `sys_perm_grant` / `role_perm_scope` / `sys_user_role`。

## 一、角色与用户

- `sys_role`：id 10，销售总监，role_code=`0`，**data_scope=all**，status=1。
- 持有用户：uid 11 `NO001` 马吉亮（status=1, deleted=0）。
- 权限来源：`sys_role_menu` 86 个权限码 + `sys_perm_grant` ROLE 0 的 64 条 grant（action_code）+ USER 11 的 3 条 grant（customer:view/edit/delete）。

## 二、system 权限（26 码 / 27 grants）

| 权限码 | 来源 | 建议 |
|---|---|---|
| system:config:edit | grant+menu | **移除**（系统配置写） |
| system:config:view | grant+menu（2 行） | **移除**（系统设置读，非销售总监职责） |
| system:dept:edit | grant+menu | **移除** |
| system:dept:view | grant+menu（2 行） | 保留（组织树只读，选人/审批配置需要） |
| system:group:add / delete / edit | **grant**（菜单未列） | **移除**（集团管理写） |
| system:group:view | grant+menu | **移除**（集团管理读） |
| system:log:view | grant+menu | **移除**（系统日志） |
| system:menu:view | grant+menu | **移除**（菜单管理） |
| system:org:admin | grant+menu | **移除**（组织管理员） |
| system:position:add / delete / edit | grant+menu | **移除** |
| system:position:view | grant+menu | 保留（岗位只读） |
| system:role:add / delete / edit | grant+menu | **移除** |
| system:role:view | grant+menu | 保留（角色只读，权限中心展示） |
| system:rule:add / delete / edit | grant+menu | **移除** |
| system:rule:view | grant+menu | **移除**（权限规则非职责） |
| system:user:add / delete / edit | grant+menu | **移除** |
| system:user:view | grant+menu | 保留（人员只读） |

- 建议移除：**18 条写/管理 + 5 条读 = 23 条**；保留 4 条只读（dept:view / position:view / role:view / user:view）。
- 其中 `system:group:add/delete/edit` 只在 grant 中、未在菜单矩阵——说明 grants 与菜单矩阵存在漂移，收窄时两处都要清。

## 三、legacy / ops 类建议

| 权限码 | 建议 |
|---|---|
| `finance:view` / `finance:manage` | **移除**（legacy；现行 `customer:finance:edit`） |
| `incentive:view-all` / `incentive:view-shared` | **移除**（历史残留；运行时已由授权表/范围控制） |
| `incentive:person-rank:export` | **待你/用户确认**（gen-perm 标 legacy；若前端仍用则该码应转正） |
| `file:reconcile` | **移除**（对账属 admin/ops） |
| `sea:config:edit` | **待业务确认**（公海配置写；销售总监是否需要管理公海策略） |
| `system:dept` 范围行 `all/OWNER` | 保留（若 dept:view 保留，范围只读） |

## 四、保留（业务面）

approval:approve/reject/view、contact:view、customer:*（含 finance:edit/import/export/date:edit）、fabushi:*（view/create/stats/file）、file:*（view/download/edit/upload/share/acl/folder/delete，去 reconcile）、incentive:*（add/edit/view/rank/read-stats/stats）、reminder:view、score:*、statistics:view、task:manage/view、sea:claim/config:view/import/view。

## 五、数据范围

- 角色 `data_scope=all`；`role_perm_scope`：customer:view all/ORG、customer:edit/delete/finance all/ORG、file:manage dept、file:delete self、incentive:add self 等。
- 用户 11 另有 USER 级 grants：customer:view all/OWNER、edit all/OWNER、delete all/ORG。
- 本报告只收窄**功能权限**；数据范围是否收窄由 B16 另行拍板。

## 六、实施与验收

1. 迁移/配置：删除上述 23 条 system 绑定（`sys_role_menu` + `sys_perm_grant` 两处），清理 legacy/ops 码；保留 4 条只读。
2. 回归：role 0 用户登录后菜单/按钮不出现系统管理写入口；业务面 403/200 与预期一致；权限中心保存不报错。
3. 生产应用需窗口授权；先在测试库新快照演练。
