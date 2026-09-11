# AIGICRM / AigiCRM 权限体系审计报告

> 审计日期：2026-08-08 ｜ 审计对象：客户管理系统（AIGICRM v2.7 / AigiCRM）
> 审计方式：本地源码 + 数据库快照 + 迁移脚本 + Service 层守卫逐项验证（只读，未连接生产库）
> 证据来源：
> - 后端源码 `aigicrm-backend/src/main/java/com/aigicrm/`（git main，0807 版）
> - 前端源码 `aigicrm-ui/`（git main，0807 版）
> - 数据库快照 `aigicrm_database_test.sql`（0803 测试库）+ `sql/migration-20260805-perm-codes.sql` + `sql/migration/20260807/perm-scope-switches.sql`
> - 权限字典 `docs/permission/permission-dict.md`（0805 自动生成，66 码）

---

## 一、权限体系总览（8 层）

```
浏览器 / API 客户端
   │
   ▼
① 认证层  JwtAuthFilter → JWT 验签 → 每次请求实时查库装载权限（无缓存，调整立即生效）
   │
   ▼
② 功能权限层  sys_menu.permission → sys_role_menu → sys_user_role
   │            → SimpleGrantedAuthority → @PreAuthorize 方法级鉴权（66 权限码）
   ▼
③ 数据范围层  DataScopeType 9 枚举（all/custom/region/office/dept/team/self + 2 废弃旧码）
   │            OrgScopeResolver：多角色并集 / user.data_scope_override 用户级覆盖 / org_admin 升级
   ▼
④ 岗位与规则层  sys_position → sys_permission_rule（column_filter/org_filter/field_visible/
   │              action_control/time_range）+ 集团/法人归属
   ▼
⑤ 规则引擎层  PermissionRuleEngine（SQL 拼接）+ ActionGuardAspect（操作控制）
   │            + FieldFilterAdvice（字段级过滤）+ CustomerAccessService（客户归属五级）
   ▼
⑥ 文件 ACL 层  biz_folder_acl + FolderPermissionService 五级判定
   ▼
⑦ 集团/租户层  MyBatis-Plus 租户插件（8 表白名单，开关默认关）+ GroupScopeUtil
   ▼
⑧ 前端显示层  pinia 存 permissions/menus → 动态菜单 + v-if 按钮（纯展示，可被篡改）
```

**核心设计取向**：安全边界永远在后端。前端只负责隐藏 UI，任何客户端判断都可被绕过（console 改 store）；真正的授权裁决在后端 @PreAuthorize + Service 层守卫，且**每次请求实时查库、无权限缓存**——管理员调整权限后下一次请求即生效，无需用户重新登录。

---

## 二、各层详解

### ① 认证层（security/）

| 组件 | 职责 | 关键点 |
|---|---|---|
| `SecurityConfig` | 过滤链 | 无状态 JWT；`anyRequest().authenticated()`（仅登录态兜底）；放行白名单见下 |
| `JwtAuthFilter` | 每请求认证 | token 验签 → 实时 `loadUserByUsername` 重载权限 → 写入 SecurityContext |
| `JwtTokenProvider` | 签发/验签 | 载荷仅 `subject=userId`+username，无权限信息；secret 无默认值（缺失启动即失败） |
| `UserDetailsServiceImpl` | **权限装载核心** | 见下方装载链路 |
| `SecurityUser` / `SecurityUtils` | 运行时主体 | `isAdmin()` 硬编码角色码 `admin` |

**白名单匿名接口（permitAll）**：
- `/api/auth/login`、`/api/auth/captcha`、`/api/auth/forgot-password/**`
- `/api/system/configs/public`、`/api/system/configs/logo`（白名单 key + 路径穿越防护）
- `/api/file/download/public/**`（强制 `public/` 前缀 + normalize 校验）
- Swagger 仅非 prod 放行

**权限装载链路**（sys_menu.permission → authorities）：
```
sys_menu(m.permission) JOIN sys_role_menu JOIN sys_user_role WHERE user_id=? AND m.status=1
  → Set<permission> → SimpleGrantedAuthority（UserDetailsServiceImpl:50-53）
  → 供 @PreAuthorize("hasAuthority('customer:view')") 使用
```
角色装载：`roleMapper.selectRolesByUserId` → 全部角色 `dataScope` 并集（多角色取最宽 `min rank`）；`user.data_scope_override` 非空则**整体取代**角色并集；岗位 → 集团/法人归属；角色/岗位规则聚合按 priority 排序 → `PermissionRuleEngine.evaluate` 预求值；组织管理员 `managedOrgIds`（递归 CTE）若当前 scope 更窄则升级为 org_and_children。

### ② 功能权限层（@PreAuthorize 接口矩阵，66 码）

Controller 用到的权限码均可映射到 sys_menu 行（无"代码用了但菜单没配"的空码）。完整矩阵见第四节。

**放行/降权清单**（刻意设计）：
- 部门/字典只读接口（`/api/system/depts` GET、`/api/system/dict/data/*` GET）→ **仅认证**（业务表单基础数据，非管理功能）
- `/api/common/scope/users|depts`、`/api/org/tree`、`/api/org/my-scope` → 仅认证（数据范围在 Service 强制）
- 客户/激励/公海/审批等核心模块 per-id 接口 → @PreAuthorize + Service 层归属守卫双层

### ③ 数据范围层（六级）

| 枚举 | code | 含义 | 解析 |
|---|---|---|---|
| ALL | `all` | 全集团 | null（不过滤） |
| CUSTOM | `custom` | sys_role_org 自定义集合 | 逐 org 展开子树 |
| REGION | `region` | 本大区及以下 | 沿祖先链找 deptType=2 |
| OFFICE | `office` | 本办事处及以下 | 沿祖先链找 deptType=3 |
| DEPT | `dept` | 本部门及以下 | 部门树（含子级） |
| TEAM | `team` | 本人+递归下属（≤5 层） | manager_id 递归 |
| SELF | `self` | 仅本人 | List.of(userId) |
| ORG / ORG_AND_CHILDREN | （旧码） | @Deprecated 兼容 | 映射 dept 树 |

- **多角色**：任一角色 all → 短路全集团；否则并集
- **用户级覆盖**：`data_scope_override` 单值取代角色并集
- **org_admin 升级**：管理某组织的用户 scope 自动升至 org_and_children
- **两个系统级开关**（`perm.superior.see.subordinate` / `perm.org.leader.see.org`）**默认开启** → 直接上级/组织负责人默认可见递归下属/整棵子树（扩大可见面，需 sys_config 显式关闭）
- 未知 code 默认 SELF（fail-closed）

### ④⑤ 岗位与规则引擎

- **岗位 ≠ 角色**：岗位**不贡献权限码**，只贡献数据级权限规则 + 集团/法人归属
- 规则类型：`column_filter`（列条件）、`org_filter`（组织过滤）、`field_visible`（字段可见）、`action_control`（操作控制）、`time_range`（时间范围）
- `PermissionRuleEngine` 支持 `${user.userId}`、`${time.today}` 等变量
- `ActionGuardAspect`：按 Controller 类名/方法名前缀推断实体+动作，规则命中 FALSE 返回 403（**未配置规则默认放行 = fail-open 设计**）
- `FieldFilterAdvice`：响应体字段级过滤（hidden/visible）

### ⑥ 文件 ACL 层

`biz_folder_acl`（folder_id+role_id+perms）+ `biz_file_folder.downloadable` + `biz_file.version`

五级判定（FolderPermissionService）：**管理员 > 本人 > 直属上级（manager_id 链 ≤5 跳，部门 leader 链兜底）> ACL 角色（查看/上传/编辑/删除/分享/下载 6 权）> 公共仅查看**。操作接口统一 `file:view` 门槛，ACL 为唯一仲裁（消除双重门槛）。

### ⑦ 集团/租户层

- `sys_role`/`sys_position` 加 `group_id`（默认主集团 1）；`biz_*` 8 表加 `tenant_id`
- 租户插件：8 表白名单（biz_customer/biz_customer_share/biz_file_folder/biz_file/biz_incentive_record/biz_sea_pool/biz_approval/biz_reminder），`aigicrm.tenant.enable` 开关**默认关**；开启时 admin 钉主集团 1、多集团取最小集团（过度收紧，fail-closed）
- `GroupScopeUtil`：admin 不过滤，其余按 groupIds，取不到退主集团 1

### ⑧ 前端显示层

- 登录后 `/api/auth/info` 一次拉取 permissions+menus → pinia `useUserStore`（`permissions` 存 Set）
- **菜单**：后端 menus 树动态渲染（FEAT-20260804-DYNAMIC-MENU，角色勾选即显示）
- **按钮**：`v-if="userStore.hasPermission('xxx')"` 纯前端隐藏（无 v-hasPerm 指令）
- **路由守卫**：`beforeEach` 只验 token；`meta.permission` 校验在 layout onMounted（仅覆盖 statistics/score/system 系列路由；customer/file/incentive/sea/reminder/approval 等**无 meta.permission**）
- **显示层不实时刷新**：已登录用户 F5 才重新拉取；后端始终实时裁决（无窗口期）

---

## 三、角色与数据

**4 个业务角色**（测试库快照）：

| id | 角色 | code | data_scope | 定位 |
|---|---|---|---|---|
| 1 | 系统管理员 | admin | all | 全量 |
| 9 | 行政 | 05 | dept_and_children | 行政事务 |
| 10 | 销售总监 | 0 | all | 全量可见 |
| 11 | 销售员 | 06 | self | 仅本人 |

**权限码清单**：66 条（`docs/permission/permission-dict.md`）。按模块分布：
- 客户：`customer:view/add/edit/delete/export/import/share`（7）
- 文件：`file:view/upload/edit/delete/share`（5）
- 激励：`incentive:view/add/edit/delete`（4）
- 公海：`sea:view/claim/pool:edit/pool:delete/pool:import/config:view/config:edit/export/import`（9）
- 审批：`approval:view/approve/reject`（3）
- 考核分析：`statistics:view/scoreboard/score:entry/score:view/score:manage/score:ranking`（6）
- 系统管理：`system:user/dept/org:admin/position/role/rule/config/menu/group/log` 全系（28+）
- 通用：`dashboard:view`

**业务菜单 × 岗位/角色 权限矩阵**（基于 2026-08-08 测试库实时查询）

| 序号 | 菜单 | 总部负责人 | 大区负责人 | 办事处负责人 | 部门负责人 | 直接上级 | 销售 | 行政 | 销售总监 |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 工作台 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 2 | 客户管理 | | | | | | | | |
| 3 | ├ 客户列表 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 4 | ├ 新建客户 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 5 | 文件管理 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 6 | 四项激励 | | | | | | | | |
| 7 | ├ 激励上传 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 8 | ├ 激励查询 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 9 | ├ 激励统计 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 10 | 提醒管理 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 11 | 公海管理 | | | | | | | | |
| 12 | ├ 公海主页 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 13 | ├ 捞取记录 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 14 | 审批管理 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 15 | 考核与分析 | | | | | | | | |
| 16 | ├ 统计分析 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 17 | ├ 积分录入 | · | · | · | · | · | · | ✓ | ✓ |
| 18 | ├ 积分排行 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| — | **数据范围** | **全集团** | **本大区↓** | **本办事处↓** | **本部门↓** | **直属下属链≤5层** | **本部门(测试)** | **本部门** | **本部门及以下** |

> **列含义**：前 5 列（总部/大区/办事处/部门负责人、直接上级）为**组织岗位概念**（对应岗位：总部总经理/大区经理/办事处主任/部门负责人；直接上级＝manager_id 链）——**菜单默认取销售员基线，差异体现在数据范围**；后 3 列为系统**真实角色**，按测试库实时绑定填充。
>
> **数据范围（测试库实时）**：系统管理员 all；行政 org（旧码→解析仅本部门）；销售总监 org_and_children（旧码→本部门及以下）；销售员 **dept（测试库已改，非 self）**；测试部门经理 org；测试大区经理 org_and_children。**生产与测试不一致**：生产销售员 data_scope=self、销售总监=all、行政=dept（见 0805 生产部署记录）。数据范围生效链路：多角色并集取最宽 → 用户级 data_scope_override 单值覆盖 → org_admin 自动升级。
>
> **⚠️ 岗位与规则层当前未接线**（测试库实时）：6 个岗位（总部总经理/大区经理/办事处主任/部门负责人/销售人员）已定义但 **sys_user_position 0 条绑定**；10 条权限规则（org_filter/column_filter/time_range/field_visible/action_control）已定义但 **sys_position_rule 与 sys_role_rule 均 0 条绑定**；sys_org_admin 空；manager_id 全空。→ **"负责人/直接上级"目前实际靠角色 data_scope 实现，岗位+规则引擎为就绪未启用状态**。测试库另有测试角色：**测试大区经理205624 / 测试部门经理205624**（菜单：customer 全、file 查看/分享、sea 查看/主页、statistics:view），用于验证组织级数据范围。
>
> **系统管理菜单**（人员/组织架构/角色/岗位/集团/权限规则/设置/日志）：仅系统管理员全量；销售总监可看 人员/组织/角色/设置/日志（system:user:view/add/edit、dept:view、role:view/add/edit、config:view、log:view）；行政可看 人员/设置/组织（system:user:view/add/edit、config:view、dept:view）；**测试库销售员亦可看 人员/设置/组织（system:user:view、config:view、dept:view）——测试库配置较生产宽松，生产销售员仅经菜单 98/99 间接持有 dept:view/config:view**。完整 66 权限码 × 角色矩阵见附录 A。

---

## 四、Service 层守卫验证结论（逐项核实）

| Service | 判定 | 依据 |
|---|---|---|
| **FolderAclService** | ✅ 安全 | saveAcl/removeAcl/setDownloadable 全部 `requireEdit` → `check(P_EDIT)` 五级判定；公共文件夹兜底不授 edit/delete/share/upload；任意登录用户无法改他人 ACL |
| **FileService** | ⚠️ **有绕过路径** | 文件夹级操作均有 check/checkFile；但 `upload(folderId=null, scope=public)` 无写权限校验 → 任意 file:view 用户可写入免登录公开下载区 |
| **IncentiveService** | ✅ 安全（纵深缺口） | 守卫在 Controller 四选一（admin∨矩阵码∨biz_incentive_permission∨本人），无绕过；Service 方法裸奔属纵深不足 |
| **CustomerService** | per-id ✅ / create ⚠️ | assertCanView/Edit/Delete 全链路强制（admin→owner→共享→数据范围→拒绝）；create 仅需 customer:add 且信任请求体 ownerId（可伪造归属）|
| **SeaService** | ✅ 安全 | claimRecords applicantId 强制当前用户；统计按数据范围过滤；completeClaim 来源可信 |
| **ApprovalService** | approve/reject ✅ / submit ⚠️ | 审批人归属强制，无越权审批；submit 非 claim 类型不校验申请人-客户归属（可诱导审批变更他人客户）|

---

## 五、风险清单（按严重度排序）

### 🔴 P0 — 高危（建议立即修复）

| # | 位置 | 问题 | 影响 |
|---|---|---|---|
| R1 | `FileService.upload`（file:190-196） | `folderId=null` 且 `scope=public` 时无任何写权限校验 | 任意持有 file:view 的用户可向 **permitAll 免登录** 的 `/api/file/download/public/**` 公开区写入任意文件并全网公开；与 createFolder"公共根夹仅 admin"不对称 |
| R2 | `JwtAuthFilter`（:51-56） | **所有 GET 请求**接受 `?token=` query 认证，且未限定下载/导出端点 | token 落入服务器日志 / Referer / 浏览器历史，扩大令牌泄露面；日志脱敏与 token 轮换无法完全兜底 |

### 🟠 P1 — 中危（建议近期修复）

| # | 位置 | 问题 | 影响 |
|---|---|---|---|
| R3 | `CustomerService.create()`（:256-269） | 直接 POST `/api/customer/add` 信任请求体 `ownerId/officeId`，未强制当前用户 | 持 customer:add 者可伪造新客户归属/污染他人客户列表与统计（对比：两条导入路径均已强制 currentUserId）|
| R4 | `ApprovalService.submit` 非 claim 类型（extend/transfer/storage） | 不校验申请人与 customerId 的归属 | 任意 approval:view 用户可对他人客户提交延期/转移/扩容审批，诱导审批人通过后变更他人客户 |
| R5 | `FolderAclService.saveAcl` | 只需 P_EDIT 即可写入**任意权限到任意角色** | ACL-edit 持有者可在本文件夹内给自己角色补写 delete/share，实现"编辑者→删除者/分享者"自提权（限定在已有 edit 权的文件夹）|
| R6 | 前端 `org.vue:228-230` | `hasPerm(perm){return true}` 临时全开放桩 | "新增组织"按钮对所有登录用户可见（不构成后端越权，但展示层失效）|
| R7 | `IncentiveService.updateRecord/deleteRecord` | 守卫在 Controller 方法体内，Service 方法无守卫 | 未来新增调用方若未过 Controller 即越权（当前无绕过）|

### 🟡 P2 — 低危 / 架构待完善

| # | 位置 | 问题 |
|---|---|---|
| R8 | FileController 7 个写接口（upload/createFolder/renameFolder/deleteFolder/deleteFile/replaceFile/shareFile）| 全部仅 `file:view`；菜单已定义 `file:upload/edit/delete/share` 码但**接口未引用**——菜单码与接口保护脱节（ACL 仲裁消除了部分风险，但权限码语义不透明）|
| R9 | 删除/编辑合并 edit 码 | SysLegalEntity/SysConfig/SysDict/ApprovalConfig 删除用 edit；SysDept DELETE=dept:edit 而 SysOrg DELETE=dept:delete（**同一张表两种删除码，规则不一致**）|
| R10 | `DataScopeAspect` + `@DataScope` 注解 | 全仓库**零实际使用**（死代码）；数据范围过滤全靠各 Service 手工调 resolver，**新查询漏加即整表可见** |
| R11 | `DataScopeGuard.isVisible`（:39）| 静态方法 `scope==null` 返回 true（调用方漏传即放行）|
| R12 | `PermissionRuleEngine`（:105,122）| `in` 操作符对 `rule_value` 原样拼接不转义；管理员配置的恶意规则值可 SQL 注入 |
| R13 | 两个数据范围开关 | `perm.superior.see.subordinate` / `perm.org.leader.see.org` 默认开启 → 上级/负责人默认看超范围数据 |
| R14 | 租户插件 | 多集团取排序最小集团（过度收紧，合法数据被静默过滤）；admin 钉死 tenant 1 与 GroupScopeUtil"admin 全集团"口径冲突 |
| R15 | 前端权限码不一致 | 组织按钮用 `system:org:add` 而路由/菜单用 `system:dept:view`（dept/org 命名混用）；customer/file/incentive/sea 等路由无 meta.permission |
| R16 | 登录限流后锁号提示 | 5 个未知密码账号（NM005 王磊磊等）连续试错可能触发 15 分钟锁定，属运维注意项 |

---

## 六、已具备的防护（正面确认）

- ✅ **customer 模块权限模型最规范**：view/add/edit/delete/import/export/share 码分离 + 每 per-id 接口 assertCanView/Edit/Delete + 数据范围 Service 强制
- ✅ **FolderAcl 守卫真实**：五级判定正确，任意登录用户无法改他人 ACL
- ✅ **Incentive 四选一守卫**：admin∨矩阵码∨用户级授权∨本人，无绕过；updateRecord 服务端不触碰归属字段
- ✅ **Sea 捞取链路**：applicantId 强制当前用户，统计按数据范围过滤
- ✅ **审批 approve/reject**：审批人归属强制（approverId=当前用户 AND pending），节点置 approved 后不可重复审批
- ✅ **权限实时生效无窗口期**：无权限缓存，调整即生效；后端始终是安全边界
- ✅ **文件下载**：`downloadPublic` 强制 public/ 前缀 + 路径穿越防护（REV-01 已修）；下载走 fetch blob
- ✅ **目录/字典只读降权**：基础数据接口 isAuthenticated，业务表单不再被管理权限卡住
- ✅ **已知越权已修**：depts 全量可见（0804）、ApprovalConfig 写操作用 view（0804）、激励 PUT 越权（0805）、统计泄漏（0805）等均有修复记录
- ✅ **数据范围引擎**：多角色并集、用户级覆盖、org_admin 升级、环保护、未知 code 默认 SELF（fail-closed）

---

## 七、整改建议（按优先级）

| 优先级 | 建议 | 对应风险 |
|---|---|---|
| **P0** | R1：`FileService.upload` 对 `folderId=null` 的 public/incentive scope 加与 createFolder 一致的 admin 门槛（或要求必须显式指定文件夹） | R1 |
| **P0** | R2：`JwtAuthFilter` 将 query token 认证限定到文件下载/导出白名单端点，或改为仅 Bearer 头 | R2 |
| P1 | R3：`CustomerService.create()` 强制/校验 `ownerId=当前用户`（对齐导入路径） | R3 |
| P1 | R4：`ApprovalService.submit` 非 claim 类型补申请人-客户归属校验 | R4 |
| P1 | R5：`saveAcl` 对写入超出自身已有权限的 perms 做约束（不可写 delete/share 当自身无 P_DELETE/P_SHARE） | R5 |
| P1 | R6：`org.vue` hasPerm 接入真实权限判断 | R6 |
| P1 | R7：激励守卫下沉到 Service（`IncentiveService.updateRecord/deleteRecord` 自身校验） | R7 |
| P2 | R8：FileController 写接口换用细粒度权限码或建立"菜单码 = 接口码"门禁校验 | R8 |
| P2 | R9：统一删除权限码（dept:edit vs dept:delete 二选一）；给独立删除动作配 delete 码 | R9 |
| P2 | R10：启用 `@DataScope` 注解接入切面，或在 pre-delivery-check 增加"新增查询必须带 scope 过滤"扫描 | R10 |
| P2 | R11：`isVisible` 对 scope==null 改为拒绝或强制调用方显式传"不过滤"标记 | R11 |
| P2 | R12：PermissionRuleEngine `in` 分支对 rule_value 转义/参数化 | R12 |
| P2 | R13：评估两个系统开关的默认值（改为默认关闭，按需开启） | R13 |
| P2 | R15：统一 dept/org 命名；给核心业务路由补 meta.permission | R15 |
| 运维 | 生产库复核 `sea:pool:import` 销售员绑定；5 个未知密码账号勿盲目试错（防锁号） | — |

---

## 八、审计局限

1. **数据库为 0803 测试库快照**，与生产（70+ 菜单、63 用户、0805/0806/0807 迁移后）存在差异；角色矩阵已用 0805 迁移脚本修正，但个别绑定（如 `sea:pool:import`）需以生产库实际数据为准
2. 未连接生产库/测试库做运行时实测（本次为源码级只读审计）；`UserDetailsServiceImpl` 的单角色 vs 多角色、租户开关实测效果等建议在测试环境按 `perm-test-20260805` 报告流程复核
3. 前端 `src/index.vue`（旧硬编码菜单）为孤儿文件，未计入主链路
