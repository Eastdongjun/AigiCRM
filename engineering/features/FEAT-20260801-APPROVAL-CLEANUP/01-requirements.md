# 需求规格

## 目标用户与场景

- 系统管理员清理自测残留数据；审批配置页不再显示乱码"????"条目

## 事实

- biz_approval_config id=1 flow_name="客户捞取"/flow_type="????" 为用户添加的测试配置（2026-08-01 实测确认）
- biz_approval_flow 无记录引用该 flow_type（4 条：claim/transfer/extend/storage）

## 假设

- 无（已核实）

## 阻断问题

无

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 删除 biz_approval_config 中"客户捞取/????"测试流程，保留"捞取客户审批/claim" | 用户输入 | Must |

## 业务规则与权限

- 无权限变化；删除仅影响该测试配置

## 异常与边界

- 若 id=1 不存在（重复执行）：DELETE 0 行，无副作用

## 非功能需求

- 可验证：删除后 SELECT 回读 + 浏览器页面确认

## 验收标准

|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given 测试服数据库，When 执行 DELETE（WHERE id=1 AND flow_name='客户捞取'），Then biz_approval_config 仅剩"捞取客户审批/claim" |
| AC-002 | REQ-001 | Given 系统设置→审批配置页，When 刷新页面，Then 表格仅显示"捞取客户审批"一行，无乱码条目 |

## 追踪关系

- REQ-001 → AC-001、AC-002
