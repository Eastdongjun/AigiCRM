# 需求规格

## 目标用户与场景

- 销售员（dataScope=org_and_children）打开客户列表页，负责人下拉正常无 403

## 事实

- simple 接口返回 List<Map>（非分页 records 结构）

## 假设

- 负责人下拉数据量与 simple 接口一致

## 阻断问题

无

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 客户列表负责人下拉改用 customer/users/simple | 测试报告 | Must |

## 业务规则与权限

- 无变化

## 异常与边界

- simple 接口 data 为数组（适配 .data || []）

## 非功能需求

- 无

## 验收标准

|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given wangleilei（销售员），When 打开客户列表/新增/激励/公海/文件/提醒 6 页，Then 0 个 403 |

## 追踪关系

- REQ-001→AC-001
