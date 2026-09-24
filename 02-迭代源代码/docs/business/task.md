# 待办任务

> 来源锚点:`TaskController`、`TaskService.page/assertAssigneeVisible`。核实日期 2026-09-24。

## 1. 权限与操作面

| 操作 | 权限码 |
| --- | --- |
| 任务列表/统计 | `task:view` |
| 创建/编辑/完成/取消 | `task:manage`(管理操作仅 PC) |

## 2. 可见性(含 IDOR 收口)

- 非 admin 不带筛选时**默认只看自己的任务**;admin 看全部。
- 显式指定 `assigneeId` 查他人任务:须在 **task 模块可见人员集合**内,越权 403(2026-09-21 权限审计批次 1 收口;此前参数型 IDOR 可枚举他人任务)。
- 任务可挂客户(customerId 筛选)。

## 3. 状态机与超期

```
pending → completed / cancelled
超期 = status=pending 且 due_date < 今天(overdue 筛选)
```

## 4. 工作台联动

- 工作台(dashboard)加载时调用 `/api/task/statistics`——该接口属 task:view 门禁,**权限行缺失时全员 403 弹错**(V2026.09.23.1 迁移 id 碰撞事故即此处:历史事故记录见 git 5ae921f2)。

## 5. 权限行登记状态

- `task:view`/`task:manage` 按 V2026.09.19.8 登记、23.1 修复恢复并绑定 admin/0/06;**行政(05)未绑**(既有口径,放开待用户裁决)。
