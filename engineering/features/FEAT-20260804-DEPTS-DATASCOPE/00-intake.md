# 功能受理

## 用户目标
修复 /api/system/depts 越权:销售员(TY101 等非管理员)当前可查看全公司部门列表。修复需同时满足:激励查询页部门筛选(前端 loadOrgs 依赖该接口)不失效。

## 当前行为
- /api/system/depts 与 /tree 有 @PreAuthorize('system:dept:view'),销售员通过菜单绑定持有该权限(激励查询页部门筛选是功能必需)
- SysDept 实体无 PII(部门名/编码/负责人姓名),但全公司组织架构对非管理员可见(与销售员 self 数据范围不符)
- users 接口已 403(销售员无 system:user:view),depts 漏收窄

## 范围
- SysDeptService.tree()/list() 增加数据范围过滤:管理员返回全部;非管理员仅返回本人部门及其祖先链
- 前端零改动(loadOrgs 通用,单个部门也能建树)

## 不在范围
- 菜单/权限码体系调整(保留 system:dept:view,仅收窄数据范围)
- 其他接口的数据范围审计

## 来源与事实
- SysDeptService.java(全量返回)、SecurityUtils.isAdmin()/getCurrentDeptId() 存在
- admin dept_id=1;TY101 dept_id=4
- search.vue:249 loadOrgs() 调用 /system/depts 构建部门筛选树

## 约束
- 生产部署需走标准流程;保留既有权限与前端行为

## 初始风险
- 销售员 dept_id 为空的用户将看到空部门列表(激励页部门筛选为空,不报错)——可接受,优于全量暴露
