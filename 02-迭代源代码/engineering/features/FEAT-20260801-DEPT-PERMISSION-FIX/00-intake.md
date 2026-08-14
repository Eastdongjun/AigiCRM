# 功能受理

## 用户目标

新增客户菜单点击弹出"没有访问权限"，左侧菜单多个页面同样问题——修复。

## 当前行为

- 新增客户等 7 个业务页面（customer/add、customer/list、customer/detail、incentive/index、sea/index、reminder/index、statistics/index、score/entry）加载时调用 `/system/depts`（部门下拉，getDeptList）
- 该接口 `@PreAuthorize('system:dept:view')`（SysDeptController）
- 销售员（role 11）/行政（role 9）角色未授予 system:dept:view → 页面加载即 403"没有访问权限，请联系管理员授权"
- 王磊磊实测：/system/depts 403（修复前）

## 范围

- 数据库：sys_role_menu 给 role 9/11 绑定"基础数据-部门"菜单（id=98，permission=system:dept:view）
- 验证：接口 403→200；王磊磊 9 个业务页面无 403

## 不在范围

- roles/positions 接口 403（仅 admin 系统设置页面调用，合理管控）；生产

## 来源与事实

- 用户输入："点击菜单 新增客户 弹出 没有权限 左侧的菜单还有很多都是这样"
- 代码证据：add.vue onMounted 调 getDeptList；SysDeptController 要求 system:dept:view
- 角色权限：role 10 总监已有 system:dept:view（此前绑定），role 9/11 缺失

## 约束

- 测试服；数据变更（INSERT 幂等）

## 初始风险

- 低
