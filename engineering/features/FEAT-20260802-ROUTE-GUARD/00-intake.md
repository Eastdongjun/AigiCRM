# 功能受理

## 用户目标

Copilot 报告：24 个销售员账号在统计分析页 403（/api/statistics/analysis，statistics:view 缺失）共 48 个错误。需消除页面级 403。

## 当前行为

- 前端路由无权限守卫：菜单隐藏的页面（统计分析/系统管理/学分录入等）仍可 URL 直接访问 → 页面加载调接口 → 403
- 菜单级已隐藏（v-if hasPerm），但 URL 直达无拦截

## 范围

- router/index.js：10 个管理/考核路由 meta.permission
- layout/index.vue：fetchUserInfo 后检查当前路由权限，无权限跳 /dashboard + 提示

## 不在范围

- 后端权限（接口层 403 保护保留）；生产

## 来源与事实

- Copilot 测试报告（24 销售员 × 2 = 48 错误，全部 /statistics/analysis）

## 约束

- 测试服；纯前端

## 初始风险

- 低；store 加载后检查（无时序误判）
