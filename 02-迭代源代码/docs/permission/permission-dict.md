# AIGICRM 权限码字典(自动生成)

> 生成时间: 2026-10-18 | 来源: 生产 sys_menu + sys_role_menu
> 本文件由 scripts/gen-permission-dict.sh 自动生成,**禁止手改**;权限变更后重新运行。
> 格式约定: 权限码 = `模块:动作`(如 customer:view / system:user:add)
>
> **状态列口径**: `启用` = sys_menu.status=1，该码会出现在角色管理页的配置树中(SysMenuService.tree() 带 status=1 过滤)，**管理员可勾选授予**; `停用` = status=0，**管理员看不到、也无法授予**（即便数据库里存在绑定）。

## 一、权限码清单(80 条；数据库 74 条 + 兼容/历史 6 条；启用 72 / 停用 2)

| 权限码 | 菜单名 | 类型 | 路径 | 状态 | 绑定的角色 |
|---|---|---|---|---|---|
| `approval:approve` | 审批通过 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `approval:reject` | 审批驳回 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `approval:view` | 审批管理 | 2 | `/approval` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `contact:manage` | 客户联系人管理 | 3 | `NULL` | 启用 | — |
| `contact:view` | 客户联系人查看 | 3 | `NULL` | 启用 | 系统管理员 |
| `customer:add` | 客户新增 / 新增客户 | 2 / 3 | ` / /customer/add` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `customer:date:edit` | 修改客户保护日期 | 3 | `NULL` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `customer:delete` | 客户删除 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `customer:edit` | 客户编辑 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `customer:export` | 客户导出 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `customer:import` | 客户导入 | 3 | `NULL` | 启用 | 系统管理员,销售员,销售总监 |
| `customer:share` | 历史兼容项, 不作为独立运行时授权(沿用客户访问/编辑守卫) | — | `—` | — | — |
| `customer:view` | 客户列表 / 客户管理 / 客户详情 | 2 | `/customer / /customer-group / /customer/detail/:id` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `dashboard:view` | 工作台 | 2 | `/dashboard` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `file:acl:manage` | 文件ACL管理 | 3 | `NULL` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `file:delete` | 文件删除 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `file:edit` | 文件编辑 | 3 | `NULL` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `file:folder:manage` | 文件夹管理 | 3 | `NULL` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `file:share` | 文件分享 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `file:upload` | 文件上传 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `file:view` | 文件管理 | 2 | `/file` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `finance:manage` | 客户财务管理 | 3 | `NULL` | 启用 | — |
| `finance:view` | 客户财务查看 | 3 | `NULL` | 启用 | 系统管理员 |
| `incentive:add` | 激励上传 | 2 | `/incentive` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `incentive:delete` | 历史兼容项, 不作为独立运行时授权(由授权表与服务范围控制) | — | `—` | — | — |
| `incentive:edit` | 激励编辑 | 3 | `NULL` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `incentive:manage` | 激励管理(他人记录) | 3 | `NULL` | 启用 | 系统管理员,行政,销售总监 |
| `incentive:person-rank:export` | 个人排行导出 | 3 | `NULL` | 启用 | 系统管理员,行政,销售总监 |
| `incentive:read-ranking:view` | 本月激励查询阅读排行榜 | 2 | `/system/incentive-read-ranking` | 启用 | 系统管理员 |
| `incentive:read-stats` | 激励查询阅读统计 | 3 | `NULL` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `incentive:view` | 四项服务 / 激励查询 / 激励统计 | 2 | `/incentive-group / /incentive/search / /incentive/stats` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `incentive:view-all` | 激励查看(全部)（历史残留, 不作为运行时授权(改由授权表按人核定)） | 3 | `NULL` | **停用** | — |
| `incentive:view-shared` | 激励查看(已分享)（历史残留, 不作为运行时授权(改由授权表按人核定)） | 3 | `NULL` | **停用** | — |
| `ranking:customer:view` | 客户排行查看 | 3 | `NULL` | 启用 | — |
| `reminder:view` | 提醒管理 | 2 | `/reminder` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `score:entry` | 积分录入 | 2 | `/score/entry` | 启用 | 系统管理员,行政,销售总监 |
| `score:manage` | 积分管理 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `score:ranking` | 历史残留, 不作为独立运行时授权(统一使用 score:view) | — | `—` | — | — |
| `score:view` | 积分排行 | 2 | `/score/ranking` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `sea:claim` | 公海捞取 | 3 | `` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `sea:config:edit` | 公海配置编辑 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `sea:config:view` | 公海配置查看 | 3 | `NULL` | 启用 | 系统管理员,行政,销售总监 |
| `sea:import` | 公海导入 | 3 | `` | 启用 | 系统管理员,行政,销售总监 |
| `sea:pool:delete` | 历史残留, 不作为独立运行时授权(运行时由池级 PERM_DELETE 守卫控制) | — | `—` | — | — |
| `sea:view` | 公海池 / 公海管理 / 捞取记录 | 2 | `/sea / /sea-group / /sea/records` | 启用 | 系统管理员,行政,销售员,销售总监 |
| `statistics:scoreboard` | 历史残留, 不作为独立运行时授权(统一使用 statistics:view) | — | `—` | — | — |
| `statistics:view` | 客户排行 / 核心客户六项指标 / 统计分析 | 2 | `/indicator / /ranking/customer / /statistics` | 启用 | 系统管理员,销售员,销售总监 |
| `system:config:edit` | 系统配置编辑 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:config:view` | 系统设置 | 2 | `/system/config / /system/config-group` | 启用 | 系统管理员,行政,销售总监 |
| `system:dept:edit` | 部门编辑 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:dept:view` | 组织架构 / 部门管理 | 2 | `/system/org / /system/org-group` | 启用 | 系统管理员,行政,销售总监 |
| `system:group:add` | 集团新增 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:group:delete` | 删除集团 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:group:edit` | 集团编辑 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:group:view` | 集团管理 | 2 | `/system/group` | 启用 | 系统管理员,销售总监 |
| `system:log:view` | 日志管理 | 2 | `/system/log` | 启用 | 系统管理员,销售总监 |
| `system:menu:edit` | 历史残留, 不作为运行时授权(菜单由迁移维护) | — | `—` | — | — |
| `system:menu:view` | 菜单查看 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:org:admin` | 组织管理员 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:position:add` | 岗位新增 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:position:delete` | 删除岗位 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:position:edit` | 岗位编辑 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:position:view` | 岗位管理 | 2 | `/system/position` | 启用 | 系统管理员,销售总监 |
| `system:recycle:purge` | 清空文件回收站 | 3 | `NULL` | 启用 | 系统管理员 |
| `system:recycle:restore` | 恢复已删除数据 | 3 | `NULL` | 启用 | 系统管理员 |
| `system:recycle:view` | 回收站 | 2 | `/system/recycle` | 启用 | 系统管理员 |
| `system:role:add` | 角色新增 | 3 | `` | 启用 | 系统管理员,销售总监 |
| `system:role:delete` | 删除角色 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:role:edit` | 角色编辑 | 3 | `` | 启用 | 系统管理员,销售总监 |
| `system:role:view` | 角色管理 | 2 | `/system/role` | 启用 | 系统管理员,销售总监 |
| `system:rule:add` | 规则新增 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:rule:delete` | 删除规则 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:rule:edit` | 规则编辑 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:rule:view` | 权限规则 | 2 | `/system/rule` | 启用 | 系统管理员,销售总监 |
| `system:user:add` | 用户新增 | 3 | `` | 启用 | 系统管理员,行政,销售总监 |
| `system:user:delete` | 删除用户 | 3 | `NULL` | 启用 | 系统管理员,销售总监 |
| `system:user:edit` | 用户编辑 | 3 | `` | 启用 | 系统管理员,行政,销售总监 |
| `system:user:view` | 人员管理 | 2 | `/system/user` | 启用 | 系统管理员,行政,销售总监 |
| `task:manage` | 任务管理 | 3 | `NULL` | 启用 | 系统管理员,销售员,销售总监 |
| `task:view` | 任务查看 | 3 | `NULL` | 启用 | 系统管理员,销售员,销售总监 |

> ⚠️ 表中「绑定的角色」取自 `sys_role_menu`，**不代表该码当前可授予**：`停用` 的码即使有绑定行，也不会进入任何用户的 permissions[]（`SysMenuMapper.selectPermissionsByUserId` 带 `AND m.status = 1`）。

## 二、角色权限矩阵

| 权限码 | 系统管理员 | 行政 | 销售员 | 销售总监 |
|---|---|---|---|---|
| `approval:approve` | ✅ | ✅ | ✅ | ✅ |
| `approval:reject` | ✅ | ✅ | ✅ | ✅ |
| `approval:view` | ✅ | ✅ | ✅ | ✅ |
| `contact:manage` |   |   |   |   |
| `contact:view` | ✅ |   |   |   |
| `customer:add` | ✅ | ✅ | ✅ | ✅ |
| `customer:date:edit` | ✅ | ✅ | ✅ | ✅ |
| `customer:delete` | ✅ | ✅ | ✅ | ✅ |
| `customer:edit` | ✅ | ✅ | ✅ | ✅ |
| `customer:export` | ✅ | ✅ | ✅ | ✅ |
| `customer:import` | ✅ |   | ✅ | ✅ |
| `customer:view` | ✅ | ✅ | ✅ | ✅ |
| `dashboard:view` | ✅ | ✅ | ✅ | ✅ |
| `file:acl:manage` | ✅ | ✅ | ✅ | ✅ |
| `file:delete` | ✅ | ✅ | ✅ | ✅ |
| `file:edit` | ✅ | ✅ | ✅ | ✅ |
| `file:folder:manage` | ✅ | ✅ | ✅ | ✅ |
| `file:share` | ✅ | ✅ | ✅ | ✅ |
| `file:upload` | ✅ | ✅ | ✅ | ✅ |
| `file:view` | ✅ | ✅ | ✅ | ✅ |
| `finance:manage` |   |   |   |   |
| `finance:view` | ✅ |   |   |   |
| `incentive:add` | ✅ | ✅ | ✅ | ✅ |
| `incentive:edit` | ✅ | ✅ | ✅ | ✅ |
| `incentive:manage` | ✅ | ✅ |   | ✅ |
| `incentive:person-rank:export` | ✅ | ✅ |   | ✅ |
| `incentive:read-ranking:view` | ✅ |   |   |   |
| `incentive:read-stats` | ✅ | ✅ | ✅ | ✅ |
| `incentive:view` | ✅ | ✅ | ✅ | ✅ |
| `incentive:view-all` |   |   |   |   |
| `incentive:view-shared` |   |   |   |   |
| `ranking:customer:view` |   |   |   |   |
| `reminder:view` | ✅ | ✅ | ✅ | ✅ |
| `score:entry` | ✅ | ✅ |   | ✅ |
| `score:manage` | ✅ |   |   | ✅ |
| `score:view` | ✅ | ✅ | ✅ | ✅ |
| `sea:claim` | ✅ | ✅ | ✅ | ✅ |
| `sea:config:edit` | ✅ |   |   | ✅ |
| `sea:config:view` | ✅ | ✅ |   | ✅ |
| `sea:import` | ✅ | ✅ |   | ✅ |
| `sea:view` | ✅ | ✅ | ✅ | ✅ |
| `statistics:view` | ✅ |   | ✅ | ✅ |
| `system:config:edit` | ✅ |   |   | ✅ |
| `system:config:view` | ✅ | ✅ |   | ✅ |
| `system:dept:edit` | ✅ |   |   | ✅ |
| `system:dept:view` | ✅ | ✅ |   | ✅ |
| `system:group:add` | ✅ |   |   | ✅ |
| `system:group:delete` | ✅ |   |   | ✅ |
| `system:group:edit` | ✅ |   |   | ✅ |
| `system:group:view` | ✅ |   |   | ✅ |
| `system:log:view` | ✅ |   |   | ✅ |
| `system:menu:view` | ✅ |   |   | ✅ |
| `system:org:admin` | ✅ |   |   | ✅ |
| `system:position:add` | ✅ |   |   | ✅ |
| `system:position:delete` | ✅ |   |   | ✅ |
| `system:position:edit` | ✅ |   |   | ✅ |
| `system:position:view` | ✅ |   |   | ✅ |
| `system:recycle:purge` | ✅ |   |   |   |
| `system:recycle:restore` | ✅ |   |   |   |
| `system:recycle:view` | ✅ |   |   |   |
| `system:role:add` | ✅ |   |   | ✅ |
| `system:role:delete` | ✅ |   |   | ✅ |
| `system:role:edit` | ✅ |   |   | ✅ |
| `system:role:view` | ✅ |   |   | ✅ |
| `system:rule:add` | ✅ |   |   | ✅ |
| `system:rule:delete` | ✅ |   |   | ✅ |
| `system:rule:edit` | ✅ |   |   | ✅ |
| `system:rule:view` | ✅ |   |   | ✅ |
| `system:user:add` | ✅ | ✅ |   | ✅ |
| `system:user:delete` | ✅ |   |   | ✅ |
| `system:user:edit` | ✅ | ✅ |   | ✅ |
| `system:user:view` | ✅ | ✅ |   | ✅ |
| `task:manage` | ✅ |   | ✅ | ✅ |
| `task:view` | ✅ |   | ✅ | ✅ |

