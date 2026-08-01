# YCMN 权限码字典(自动生成)

> 生成时间: 2026-08-02 | 来源: 生产 sys_menu + sys_role_menu
> 本文件由 scripts/gen-permission-dict.sh 自动生成,**禁止手改**;权限变更后重新运行。
> 格式约定: 权限码 = `模块:动作`(如 customer:view / system:user:add)

## 一、权限码清单(61 条)

| 权限码 | 菜单名 | 类型 | 路径 | 绑定的角色 |
|---|---|---|---|---|
| `approval:approve` | 审批通过 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `approval:reject` | 审批驳回 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `approval:view` | 审批管理 | 2 | `/approval` | 系统管理员,行政,销售员,销售总监 |
| `customer:add` | 客户新增 / 新增客户 | 2 / 3 | ` / /customer/add` | 系统管理员,行政,销售员,销售总监 |
| `customer:delete` | 客户删除 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `customer:edit` | 客户编辑 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `customer:export` | 客户导出 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `customer:import` | 客户导入 | 3 | `NULL` | 系统管理员 |
| `customer:view` | 客户管理 / 客户详情 | 2 | `/customer / /customer/detail/:id` | 系统管理员,行政,销售员,销售总监 |
| `dashboard:view` | 工作台 | 2 | `/dashboard` | 系统管理员,行政,销售员,销售总监 |
| `file:delete` | 文件删除 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `file:edit` | 文件编辑 | 3 | `NULL` | 系统管理员 |
| `file:upload` | 文件上传 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `file:view` | 文件管理 | 2 | `/file` | 系统管理员,行政,销售员,销售总监 |
| `incentive:delete` | 激励删除 | 2 | `NULL` | 系统管理员,行政,销售员,销售总监 |
| `incentive:edit` | 激励编辑 | 2 | `NULL` | 系统管理员,行政,销售员,销售总监 |
| `incentive:view` | 四项激励Plus版 | 2 | `/incentive` | 系统管理员,行政,销售员,销售总监 |
| `reminder:view` | 提醒管理 | 2 | `/reminder` | 系统管理员,行政,销售员,销售总监 |
| `score:entry` | 积分录入 | 2 | `/score/entry` | 系统管理员,行政,销售总监 |
| `score:manage` | 积分管理 | 3 | `NULL` | 系统管理员 |
| `score:ranking` | 积分排行 | 2 | `/score/ranking` | 系统管理员,行政,销售员,销售总监 |
| `score:view` | 积分查看 | 2 | `NULL` | 系统管理员,行政,销售员,销售总监 |
| `sea:claim` | 公海捞取 | 3 | `` | 系统管理员,行政,销售员,销售总监 |
| `sea:config:edit` | 公海配置编辑 | 3 | `NULL` | 系统管理员 |
| `sea:config:view` | 公海配置查看 | 3 | `NULL` | 系统管理员,行政,销售员 |
| `sea:pool:delete` | 公海删除 | 3 | `` | 系统管理员 |
| `sea:pool:edit` | 公海编辑 | 3 | `` | 系统管理员 |
| `sea:pool:import` | 公海导入 | 3 | `` | 系统管理员,销售员 |
| `sea:view` | 公海管理 | 2 | `/sea` | 系统管理员,行政,销售员,销售总监 |
| `statistics:scoreboard` | 积分榜 | 2 | `/scoreboard` | 系统管理员,行政,销售员,销售总监 |
| `statistics:view` | 统计分析 | 2 | `/statistics` | 系统管理员,行政,销售总监 |
| `system:config:edit` | 系统配置编辑 | 3 | `NULL` | 系统管理员 |
| `system:config:view` | 基础数据-字典 / 系统设置 | 2 | `/system/config / NULL` | 系统管理员,行政,销售员,销售总监 |
| `system:dept:add` | 新增组织 | 3 | `NULL` | 系统管理员 |
| `system:dept:delete` | 删除组织 | 3 | `NULL` | 系统管理员 |
| `system:dept:edit` | 部门编辑 | 3 | `NULL` | 系统管理员 |
| `system:dept:view` | 基础数据-部门 / 组织架构 | 2 | `/system/dept / NULL` | 系统管理员,行政,销售员,销售总监 |
| `system:group:add` | 集团新增 | 3 | `NULL` | 系统管理员 |
| `system:group:delete` | 删除集团 | 3 | `NULL` | 系统管理员 |
| `system:group:edit` | 集团编辑 | 3 | `NULL` | 系统管理员 |
| `system:group:view` | 集团管理 | 2 | `/system/group` | 系统管理员 |
| `system:log:view` | 日志管理 | 2 | `/system/log` | 系统管理员,销售总监 |
| `system:menu:edit` | 菜单编辑 | 3 | `NULL` | 系统管理员 |
| `system:menu:view` | 菜单查看 | 3 | `NULL` | 系统管理员 |
| `system:org:admin` | 组织管理员 | 3 | `NULL` | 系统管理员 |
| `system:position:add` | 岗位新增 | 3 | `NULL` | 系统管理员 |
| `system:position:delete` | 删除岗位 | 3 | `NULL` | 系统管理员 |
| `system:position:edit` | 岗位编辑 | 3 | `NULL` | 系统管理员 |
| `system:position:view` | 岗位管理 | 2 | `/system/position` | 系统管理员 |
| `system:role:add` | 角色新增 | 3 | `` | 系统管理员,销售总监 |
| `system:role:delete` | 删除角色 | 3 | `NULL` | 系统管理员 |
| `system:role:edit` | 角色编辑 | 3 | `` | 系统管理员,销售总监 |
| `system:role:view` | 角色管理 | 2 | `/system/role` | 系统管理员,销售总监 |
| `system:rule:add` | 规则新增 | 3 | `NULL` | 系统管理员 |
| `system:rule:delete` | 删除规则 | 3 | `NULL` | 系统管理员 |
| `system:rule:edit` | 规则编辑 | 3 | `NULL` | 系统管理员 |
| `system:rule:view` | 权限规则 | 2 | `/system/rule` | 系统管理员 |
| `system:user:add` | 用户新增 | 3 | `` | 系统管理员,行政,销售总监 |
| `system:user:delete` | 删除用户 | 3 | `NULL` | 系统管理员 |
| `system:user:edit` | 用户编辑 | 3 | `` | 系统管理员,行政,销售总监 |
| `system:user:view` | 人员管理 / 基础数据-用户 | 2 | `/system/user / NULL` | 系统管理员,行政,销售总监 |

## 二、角色权限矩阵

| 权限码 | 系统管理员 | 行政 | 销售员 | 销售总监 |
|---|---|---|---|---|
| `approval:approve` | ✅ | ✅ | ✅ | ✅ |
| `approval:reject` | ✅ | ✅ | ✅ | ✅ |
| `approval:view` | ✅ | ✅ | ✅ | ✅ |
| `customer:add` | ✅ | ✅ | ✅ | ✅ |
| `customer:delete` | ✅ | ✅ | ✅ | ✅ |
| `customer:edit` | ✅ | ✅ | ✅ | ✅ |
| `customer:export` | ✅ | ✅ | ✅ | ✅ |
| `customer:import` | ✅ |   |   |   |
| `customer:view` | ✅ | ✅ | ✅ | ✅ |
| `dashboard:view` | ✅ | ✅ | ✅ | ✅ |
| `file:delete` | ✅ | ✅ | ✅ | ✅ |
| `file:edit` | ✅ |   |   |   |
| `file:upload` | ✅ | ✅ | ✅ | ✅ |
| `file:view` | ✅ | ✅ | ✅ | ✅ |
| `incentive:delete` | ✅ | ✅ | ✅ | ✅ |
| `incentive:edit` | ✅ | ✅ | ✅ | ✅ |
| `incentive:view` | ✅ | ✅ | ✅ | ✅ |
| `reminder:view` | ✅ | ✅ | ✅ | ✅ |
| `score:entry` | ✅ | ✅ |   | ✅ |
| `score:manage` | ✅ |   |   |   |
| `score:ranking` | ✅ | ✅ | ✅ | ✅ |
| `score:view` | ✅ | ✅ | ✅ | ✅ |
| `sea:claim` | ✅ | ✅ | ✅ | ✅ |
| `sea:config:edit` | ✅ |   |   |   |
| `sea:config:view` | ✅ | ✅ | ✅ |   |
| `sea:pool:delete` | ✅ |   |   |   |
| `sea:pool:edit` | ✅ |   |   |   |
| `sea:pool:import` | ✅ |   | ✅ |   |
| `sea:view` | ✅ | ✅ | ✅ | ✅ |
| `statistics:scoreboard` | ✅ | ✅ | ✅ | ✅ |
| `statistics:view` | ✅ | ✅ |   | ✅ |
| `system:config:edit` | ✅ |   |   |   |
| `system:config:view` | ✅ | ✅ | ✅ | ✅ |
| `system:dept:add` | ✅ |   |   |   |
| `system:dept:delete` | ✅ |   |   |   |
| `system:dept:edit` | ✅ |   |   |   |
| `system:dept:view` | ✅ | ✅ | ✅ | ✅ |
| `system:group:add` | ✅ |   |   |   |
| `system:group:delete` | ✅ |   |   |   |
| `system:group:edit` | ✅ |   |   |   |
| `system:group:view` | ✅ |   |   |   |
| `system:log:view` | ✅ |   |   | ✅ |
| `system:menu:edit` | ✅ |   |   |   |
| `system:menu:view` | ✅ |   |   |   |
| `system:org:admin` | ✅ |   |   |   |
| `system:position:add` | ✅ |   |   |   |
| `system:position:delete` | ✅ |   |   |   |
| `system:position:edit` | ✅ |   |   |   |
| `system:position:view` | ✅ |   |   |   |
| `system:role:add` | ✅ |   |   | ✅ |
| `system:role:delete` | ✅ |   |   |   |
| `system:role:edit` | ✅ |   |   | ✅ |
| `system:role:view` | ✅ |   |   | ✅ |
| `system:rule:add` | ✅ |   |   |   |
| `system:rule:delete` | ✅ |   |   |   |
| `system:rule:edit` | ✅ |   |   |   |
| `system:rule:view` | ✅ |   |   |   |
| `system:user:add` | ✅ | ✅ |   | ✅ |
| `system:user:delete` | ✅ |   |   |   |
| `system:user:edit` | ✅ | ✅ |   | ✅ |
| `system:user:view` | ✅ | ✅ |   | ✅ |

