# 需求规格

## 目标用户与场景

- 行政角色打开业务页面不出现 403；角色权限范围清晰一致

## 事实

- 页面可用性规则：菜单有权限（能进页面）→ 页面加载接口权限必须齐（否则 403）
- role 9 缺口：sea:config:view（公海池页池列表）、system:config:view（字典下拉）、approval:view/approve/reject（审批）

## 假设

- 行政负责公海捞取 + 审批处理（用户确认"再加审批处理能力"）

## 阻断问题

无

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 行政(9)补 sea:config:view、system:config:view | 用户输入 | Must |
| REQ-002 | 行政(9)补 approval:view/approve/reject | 用户输入 | Must |

## 业务规则与权限

- 销售员(11)权限范围保持现状（用户确认）

## 异常与边界

- 无

## 非功能需求

- 角色权限矩阵一致性

## 验收标准

|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001~002 | Given TY200（行政），When 调 sea/configs、system/configs、approval/list、approval/{id}/approve、reject，Then 全部非 403 |
| AC-002 | REQ-001~002 | Given TY200 登录，When 打开 公海池/审批/激励/新增客户/文件/统计 6 页，Then 0 个 403 |

## 追踪关系

- REQ-001~002→AC-001~002
