# 功能受理

## 用户目标

Copilot 自动化测试报告（22:50）显示多账号 403——按角色系统性排查修复。

## 当前行为

- 报告 22:50 生成（早于我 22:55 行政权限修复）：行政 32 错误大部分已被覆盖
- 实测残留：销售员 wangleilei 2 个真缺口：
  1. /api/system/users（客户列表负责人下拉，dataScope=org_and_children 时触发）→ system:user:view 缺失
  2. /api/statistics/analysis（统计分析页——菜单无此页，预期防护，不修）
- 根因：list.vue 负责人下拉用 /api/system/users（需 system:user:view），但已有 /api/customer/users/simple（仅需 customer:view，后端注释明确"不需要 system:user:view"）

## 范围

- list.vue：负责人下拉接口 system/users → customer/users/simple（适配返回结构）

## 不在范围

- statistics/analysis（菜单无页面，预期 403）；生产

## 来源与事实

- Copilot 测试报告 + 实测确认
- 后端 CustomerController:223 simpleUserList 权限 customer:view

## 约束

- 测试服；纯前端

## 初始风险

- 低
