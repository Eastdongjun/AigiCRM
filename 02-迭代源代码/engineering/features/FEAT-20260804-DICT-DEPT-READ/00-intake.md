# 功能受理

## 用户目标

- 销售员账号（NM005/王磊磊）打开「新增客户」页 `https://www.aigicrm.com/customer/add` 时不再弹「没有访问权限，请联系管理员授权」，行业/开发类型/大区/办事处下拉数据正常加载、可以正常新增客户。
- 行政（05）、销售员（06）等非管理员业务角色在客户列表、客户详情、四项激励、学分录入、公海等页面不再因字典/部门接口 403 出现下拉空白或报错。

## 当前行为

- 已实测复现（浏览器登录 NM005/717500）：`/customer/add` 表单渲染成功，但弹出「没有访问权限，请联系管理员授权」，控制台 3 个 403：
  - `GET /api/system/depts`（大区/办事处下拉）→ 403
  - `GET /api/system/dict/data/industry`（行业字典）→ 403
  - `GET /api/system/dict/data/dev_type`（开发类型字典）→ 403
- 根因（生产库实测 + 源码确认）：
  - 后端 `SysDeptController` GET 接口要求 `system:dept:view`（菜单 32），`SysDictController` GET `/dict/data/*` 要求 `system:config:view`（菜单 34）；
  - 这两个权限码挂在「系统管理」菜单下，生产库 `sys_role_menu` 中只有系统管理员（admin）和销售总监（0）绑定；行政（05）、销售员（06）**未绑定** → `@PreAuthorize` 拒绝 → 403 → 前端 axios 拦截器统一弹「没有访问权限，请联系管理员授权」。
  - 前端 `src/api/system.js` 的 `getDepts/getDictData` 被 `customer/add.vue`、`customer/list.vue`、`customer/detail.vue`、`incentive/index.vue`、`incentive/search.vue`、`score/entry.vue`、`sea/index.vue` 调用（多个页面 try/catch 静默失败 → 下拉空白）。
- 管理员账号访问同一页面无报错（admin 拥有全部菜单）。

## 范围

- 后端 `SysDictController.java`：`GET /api/system/dict/data/{dictCode}`、`GET /api/system/dict/data/type/{typeId}` 权限由 `hasAuthority('system:config:view')` 放宽为 `isAuthenticated()`（登录即可读）。
- 后端 `SysDeptController.java`：`GET /api/system/depts`、`GET /api/system/depts/tree` 权限由 `hasAuthority('system:dept:view')` 放宽为 `isAuthenticated()`（登录即可读）。
- 只读 GET 接口放宽；POST/PUT/DELETE 写接口权限**保持不变**（`system:config:edit` / `system:dept:edit`）。
- 统计分析（`/statistics` 的 `analysis` 接口）与日报/周报文件统计按角色数据范围过滤：销售员(self)只统计个人数据，上级角色(dept_and_children 等)统计部门及下级，管理员(all)全量不变（用户 2026-08-04 补充规则）。
- 生产环境部署后端并回归验证（NM005 生产实测）。

## 不在范围

- 不改数据库角色/菜单绑定（生产库不动）。
- 不改前端代码（前端调用点和 403 弹窗逻辑均不动）。
- 不放开其他系统管理接口（`/system/users`、`/system/roles`、`/system/configs` 管理接口、`/system/logs` 等仍保持 `system:*` 权限）。
- 不做 UI 视觉调整。

## 来源与事实

- 用户报障：`https://www.aigicrm.com/customer/add`，账号 NM005 / 密码 717500。
- 生产服务器 `root@39.99.35.218`（Docker Compose @ /root/aigicrm/），容器 `aigicrm-mysql`，库 `aigicrm`：NM005=王磊磊，role_id=11 销售员(06)；行政(9/05)、销售员(11/06) 均未绑定菜单 32/34；销售总监(10/0)、管理员(1/admin) 已绑定。
- 后端源码：`/Users/donglai/东来/01-Projects/01-aigicrm/04-源码/aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java`、`SysDeptController.java`（已读，权限注解逐行确认）。
- 前端调用点：`/Users/donglai/东来/01-Projects/01-aigicrm/04-源码/aigicrm-ui/src/api/system.js` 与 `views/` 各页面（已 grep 确认）。
- 用户已选择修复方案 B（后端只读接口改登录即可读）。

## 约束

- 生产变更：部署前必须本地编译 + 测试 + 测试环境实测；部署后生产实测 NM005。
- 权限边界：只放开"登录用户读公共引用数据（字典/部门树）"，不放开任何管理写操作；字典/部门树为业务表单公共引用数据，不涉及敏感数据（部门结构、字典值均为全公司共享的业务基础数据，admin/销售总监已可读）。
- 保持接口路径、请求/响应结构不变（仅放宽鉴权条件）。
- 回滚方案：保留原 jar 或 git 回退重新部署即可恢复原权限。
- 遵守仓库既有风格：Controller 使用 `@PreAuthorize` 注解 + `Result<T>` 返回。

## 初始风险

- 部署生产后端存在短暂服务中断（需评估重启策略；docker compose 滚动重启，影响秒级）。
- 若存在其他被业务页面依赖却挂在系统权限下的接口未覆盖，需在验证阶段检查（影响分析步骤）。
- 放宽权限后字典/部门树对**所有登录用户**可读——与现状 admin/销售总监可读一致，风险低；写操作未放宽。
