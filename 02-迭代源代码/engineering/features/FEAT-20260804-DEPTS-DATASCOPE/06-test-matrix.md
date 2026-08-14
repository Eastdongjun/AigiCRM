# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 生产 TY101 GET /api/system/depts → 仅 3 部门(id 1 公司→2 山西大区→4 太原办销售部,祖先链),无其他部门 |
| AC-002 | TEST-002 | PASS | 生产 admin GET → 10 部门全量;测试环境 admin → 13 部门全量 |
| AC-003 | TEST-003 | PASS | 生产 TY101 激励页接口(records/users/customer-records)全 200,loadOrgs 数据源正常 |

## 静态与构建验证

- mvn compile 成功;mvn test 34/34 PASS(BUILD SUCCESS);gate 报告 evidence/gate-report-*.json(failed: [])

## 功能与集成验证

- 生产实测:TY101 祖先链 3 部门、admin 10 部门;激励查询链路 200;users 403 保持

## UI 验证

不适用(ui_scope=none):前端零改动。

## 回归验证

- 权限隔离:TY101 /api/system/users 403 保持;depts 200(数据范围已收窄)
- 后端 34 个既有测试全绿

## 未覆盖与风险

- 非管理员 dept_id 为空的场景(测试账号登录受阻,以代码审查+前端容错覆盖)
- 数据范围仅上溯祖先链,不含下级部门(与需求一致)
