# 需求规格

## 目标用户与场景
- 非管理员(销售员):激励查询页部门筛选仍可用,但只能看到本人部门(含上级链)
- 管理员:部门管理不受影响(全部)

## 事实
- depts/tree 全量返回;销售员 dept_id=4;admin dept_id=1;SecurityUtils.isAdmin() 可用

## 假设
- 非管理员以本人 dept_id 为准;dept_id 为空返回空列表

## 功能需求
| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | SysDeptService.tree()/list():管理员返回全部启用部门;非管理员仅返回本人部门及其祖先链(按 parentId 上溯至根) | depts 越权 | Must |

## 验收标准
| 验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given TY101(销售员,dept_id=4)登录;When GET /api/system/depts;Then 仅返回部门 4 及其祖先链,无其他部门 |
| AC-002 | REQ-001 | Given admin 登录;When GET /api/system/depts;Then 返回全部启用部门 |
| AC-003 | REQ-001 | Given 激励查询页;When TY101 打开;Then loadOrgs 正常建树,页面无报错 |

## 需求确认
- [x] 用户已确认:是(用户指示"修复")

## 阻断问题

- 无

## 业务规则与权限

- system:dept:view 权限码保留(菜单绑定不变);仅查询数据范围收窄
- 管理员(isAdmin)不受限;非管理员按本人 dept_id 上溯祖先链过滤

## 异常与边界

- dept_id 为空 → 返回空列表(前端 loadOrgs catch 容错)
- 本人部门被禁用(status!=1)→ 按禁用部门不存在处理(空或祖先链截断)

## 非功能需求

- 兼容性:前端零改动;响应结构不变(List<SysDept>)

## 追踪关系

- REQ-001→AC-001/AC-002/AC-003
