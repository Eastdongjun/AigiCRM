# 交付报告

## 结果

业务页面 403 修复：行政/销售员角色授予部门查看权限（system:dept:view），新增客户等 9 个业务页面全部正常。AC-001~002 全 PASS。

## 交付范围

- REQ-001 → AC-001~002；数据库权限配置修复
- 明确排除：roles/positions（合理管控）；生产

## 变更清单

- 数据：sys_role_menu 2 条（role 9/11 → menu 98）

## 安装与启动

- 无代码部署；权限实时生效（用户重新登录即见）

## 验证证据

- curl 200；浏览器 9 页 0 403

## 部署状态与步骤

- 测试服已生效；生产需授权同步（同一 INSERT）

## 回滚与恢复

- DELETE sys_role_menu WHERE role_id IN (9,11) AND menu_id=98

## 已知限制与残余风险

- 生产未同步

## 版本与追踪

- FEAT-20260801-DEPT-PERMISSION-FIX
