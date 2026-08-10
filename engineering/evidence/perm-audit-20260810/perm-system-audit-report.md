# YCMN 权限体系全链路审计报告

- **修复状态**: 2026-08-10 修复批次已完成并在测试环境验证生效(见文末「修复执行记录」);生产部署待用户确认
- **审计对象**: 客户管理系统(AigiCRM/YCMN)权限体系 —— 认证 / 鉴权 / 数据范围 / 前端权限 / 数据模型 / 缓存一致性
- **代码基线**: `main@cf777d8`(含 2026-08-10 权限体系重构 c1bdfbd「每个权限=功能×范围」)
- **审计方式**: 4 路独立静态审计(认证鉴权 / 数据范围 / 前端 / 数据与缓存)+ 本地全量测试 + 测试环境(111.229.37.220)真实 API 权限矩阵实测;所有严重发现经交叉验证,标注 [实测]/[代码]
- **测试环境部署**: 已部署至最新代码(c1bdfbd 后),**生产未部署新模型**(生产仍为 9c6e72a 版本)

---

## 一、总体结论(先结果)

**认证可信、主链路鉴权闭合、缓存一致、SQL 安全;但 2026-08-10 的"功能×范围"权限重构处于半接入状态,并存在 2 个已实测确认的数据越权缺口。**

| 维度 | 结论 |
|---|---|
| 认证链路 | ✅ 可信: HS256 + 无默认密钥(未注入即启动失败) + jti 黑名单注销即时生效 + 禁用用户每请求实时校验 + Redis 缓存降级回源 |
| 端点鉴权 | ⚠️ 基本闭合: 全 24 个 Controller 逐端点扫描,无 fail-open;但 2 个真实越权缺口(见 P1-1/P1-2)+ 若干"无注解靠方法内守卫"的双轨不一致 |
| 数据范围 | ⚠️ 主链路闭合,但**两套解析口径并存**(`CustomerService.applyDataScope` vs `OrgScopeResolver`),导致列表/详情行为不一致、新模型配置半生效 |
| 前端权限 | ⚠️ 体验层: 后端是可靠边界,前端存在跨账号身份污染、路由守卫零权限检查、孤儿菜单可复现 3 个 P1 |
| 缓存一致性 | ✅ evict 点全覆盖(7 类写点 + afterCommit 延迟),role_perm_scope 实时查库即时生效,**不存在"权限调整 10 分钟不生效"问题** |
| SQL 安全 | ✅ 全链路参数化,无注入点 |
| 测试 | ✅ 本地 325/0/0 全绿;测试服矩阵实测 7 项核心行为 6 项正确、1 项暴露越权 |

**严重度分布: P0(可直接利用的越权)= 0,但 P1 = 10(其中 2 个已实测确认的数据越权)、P2 = 19、观察项 = 12。**

---

## 二、P1 级发现(按影响排序)

### P1-1 [实测✅] GET /api/system/users/{id} 无数据范围校验 —— 4 角色(含销售员/行政)可枚举全部用户 PII

- **证据**: `SysUserController.java:38-42` 仅注解 `hasAnyAuthority('system:user:view','system:org:admin')`;`SysUserService.getById(SysUserService.java:120-128)` 无任何集团/组织校验(列表接口 `page()` 有 `applyGroupScope`,写操作有 `canManageUser`,唯独读详情两处都没有)
- **实测(测试环境,全角色矩阵)**: `GET /api/system/users/1` → **admin/0/05/06 四个角色全部 HTTP 200**,返回 admin 的手机号与真实姓名;`GET /api/system/users` 列表同 200 total=30。4 个角色全部绑定 `system:user:view`(role_menu 实查)。
- **配置漂移加剧**: 实测 auth/info 显示 **06(销售员)持有 `system:user:add/edit/delete` 全套用户管理码、05(行政)持 add/edit、0(销售总监)持 delete**——矩阵上把用户管理权直接授给了业务角色(见 P2-18)。
- **影响**: 任何持 `system:user:view` 或 `system:org:admin` 的账号(含销售员、组织管理员)可按 ID 枚举任意用户全量 PII(phone/email/realName/组织归属),**跨集团无边界**。系 0801「销售员 GET /api/system/users 泄漏 PII」的同源残留变体。
- **修复**: `getById` 内加集团边界校验 + 组织管理员 `canManageUser` 同权校验,不通过返回 404/403;并复核列表接口是否同样按集团收窄。

### P1-2 [实测✅] "功能×范围"新模型半接入 —— 配置了 view=all 列表不生效但详情放开

- **背景**: 2026-08-10 重构引入 `role_perm_scope(role_code,module,action,scope_level)`,前端 PermScopeManager 提供全模块×全动作的范围配置矩阵。
- **证据**: `effectiveScopes`(`RolePermScopeService.java:26-44`)的**唯一消费方是 `CustomerAccessService`**(全仓 grep 仅 `CustomerAccessService.java` 引用),覆盖详情/编辑/删除判定;而**列表(`CustomerService.page:102 applyDataScope`)、统计、导出全部走旧 `dataScope` 路径,未接新模型**。
- **实测(测试环境,可逆验证)**: 管理员给 06 角色配 `customer:view=all` → 销售员 TY101 **旧 token 立即**可读任意客户详情(575 → 200),但**客户列表 total 仍 = 1**(未变 249);还原配置后列表/详情同步恢复。
- **影响**:
  1. **配置半生效**: 管理员配了范围,列表/导出/统计不生效,但详情按 ID 直达可读 → 列表隐藏 ≠ 不可访问,安全模型自相矛盾(列表面与详情面不一致);
  2. **死配置**: 测试库 39 行真实配置中,approval/score/sea/statistics/system 模块的配置**无任何消费方**(仅 customer 模块的详情判定消费)——用户配置了"销售员积分管理=all"等,实际零效果。
- **修复**: 列表/统计/导出统一按 `effectiveScopes(user, module, 'view')` 过滤(以 OrgScopeResolver 为唯一口径,见 P1-5);未接入的模块要么接入要么从 UI 隐藏。

### P1-3 [代码] 范围配置写口无 isAdmin 门槛 —— 存在提权路径

- **证据**: `RolePermScopeController.java:29-35` `PUT /api/system/perm-scopes/{roleCode}` 仅 `@PreAuthorize("hasAuthority('system:config:edit')")`;`RolePermScopeService.updateRoleScopes`(`:67-79`)无管理员校验、roleCode 任意、scope_level 任意字符串入库。同类功能 `IncentivePermissionController.grant/revoke`(`:38/75`)显式 `if (!isAdmin) throw`(注释自述"防 system:config:edit 落到非超管角色后形成局部提权")。
- **影响**: 一旦 `system:config:edit` 被授予非超管角色(字典注释证明该场景会真实发生),即可把自己角色在 customer 模块的 edit/delete 配成 `all` → **获得全公司客户编辑/删除权**(`CustomerAccessService.java:145-159` 直接消费该配置)。
- **修复**: updateScopes 增加 `SecurityUtils.isAdmin()` 守卫;复核 `SysConfigController.batchUpdate` 是否同样受限。

### P1-4 [代码] 离职带转移/手动转移功能必炸 —— 表 `biz_customer_transfer` 全链无 DDL

- **证据**: `SysUserService.java:323`(markLeave 离职带转移)与 `:354`(transferCustomers 手动转移)执行 `INSERT INTO biz_customer_transfer`;全仓 grep(db/migration V1-V7、sql/migration 全部 dated 脚本、测试库 schema)均无该表 DDL。
- **影响**: 带转移人参数的离职、手动转移客户,在事务内执行到该 INSERT 抛 "Table doesn't exist" → **整个离职事务回滚**(含已完成的客户入池/分享清理)。功能必炸,生产/测试同病。
- **修复**: 新增 Flyway `V8__create_customer_transfer.sql`(列参照 SysUserService.java:323 的 INSERT 列清单),或改为降级记录 sys_log。

### P1-5 [代码] 组织管理员范围两套口径不一致 —— 列表可见、详情 403

- **证据**: 列表口径 `CustomerService.applyDataScope`(`:662-678` org_and_children 分支)并入 `managedOrgIds`;详情口径 `OrgScopeResolver.java:161` `case "org_and_children": resolveDeptTree(userId, deptId)` **只解析本人 deptId 部门树,未并入 managedOrgIds**;`CustomerAccessService.canView` 走 resolver 窄口径。
- **影响**: 组织管理员(行政 05 兼任多办事处管理员)客户列表含授权组织客户,点详情 `canView=false` → 403;编辑/删除/日志/审批/人员下拉同病。方向是**误拒(fail-closed)**,但组织管理员功能整体不可用,且两套口径并存是回归雷区。
- **修复**: `resolveSingle` 的 org_and_children 分支改为"本人部门树 ∪ managedOrgIds 各组织子树"(与 applyDataScope 同口径),并加"列表可看 ⇔ 详情可看"一致性单测。

### P1-6 [代码] role_perm_scope 配 `custom` 范围静默退化为仅本人

- **证据**: `UserDetailsServiceImpl.java:101-112` 角色级 `sys_role_org` 仅在 `bestScope==CUSTOM` 时装载;`:125-139` 用户级 `sys_user_org` 仅在 `dataScopeOverride=="custom"` 时装载;`OrgScopeResolver.resolveCustom`(`:232-243`)完全依赖 `managedOrgIds`;而 `effectiveScopes` 可对任意 (role,module,action) 返回 `custom`。
- **影响**: 对 dataScope 非 custom 的角色在新模型里配 `custom` 范围(如 行政=customer:view=custom),登录时不装载任何组织 → 解析结果=仅本人。**自定义范围对新模型绝大多数角色静默失效**。
- **修复**: 二选一——(a) UserDetailsServiceImpl 按"任一角色 dataScope==custom 或 role_perm_scope 存在该角色 custom 配置"装载 sys_role_org;(b) effectiveScopes 命中 custom 时按 role_code 现查 sys_role_org。

### P1-7 [代码] 下载专用 token 不可吊销,且等价于完整用户会话

- **证据**: `JwtTokenProvider.generateDownloadToken`(`:109-118`)**不签发 jti** → `isBlacklisted(null)` 恒 false,黑名单机制对下载 token 完全失效;`JwtAuthFilter.java:33-41/71-74` 对 scope=download 仅做**目录前缀白名单**(`/api/file/download/`、`/api/score/export/`),通过后照常建立带完整 authorities 的会话。
- **影响**: 下载 token 设计上会进入 URL/`<img>` 请求/nginx 访问日志,泄漏后 1 小时内可用完整用户身份逐 ID 枚举下载该用户全部可访问文件(含客户附件、私有文件);被冒用者是管理员即全公司文件。
- **修复**: 下载 token 增加 jti 且支持拉黑;或改为绑定具体 ossKey 的一次性签名 URL;至少将前缀白名单收窄到具体资源。

### P1-8 [代码] 前端 401 不重置 user store → 30 秒内换账号登录显示上一账号身份

- **证据**: `request.js:15-24` `handleSessionExpired()` 只 `clearToken()`+跳转,不调 `userStore.logout()`(对比 layout/index.vue:319-321 手动退出调了 `$reset()`);`store/user.js:33-36` `fetchUserInfo` 30s TTL 命中直接复用且**缓存键不含账号标识**;login/index.vue:99-104 登录成功不重置 store。
- **影响**: 会话过期被踢后 30s 内用另一账号重登,layout 挂载的 `fetchUserInfo()` 命中缓存 → 页面渲染上一账号的 realName/菜单/权限,而 token 已是新账号——**界面身份错乱,新账号权限最长 30s+ 内"看起来不存在"**(后端 403 兜底,无数据泄露,但体验与正确性受损)。
- **修复**: `handleSessionExpired` 中调 `userStore.logout()`;登录成功后 `fetchUserInfo(true)`。

### P1-9 [代码] 路由守卫不检查 meta.permission,6 个业务模块无页面级门控

- **证据**: `router/index.js:185-196` 守卫只做 `isTokenValid()`,无任何 meta.permission 判断;`layout/index.vue:285-289` 的检查在 onMounted 执行(先渲染后踢回,且只查初始路由);仅 12 条路由带 meta.permission,customer/file/incentive/reminder/sea/approval/dashboard **全部无 meta**。
- **影响**: 无权限用户直接输 URL 进页面,前端不拦,渲染到接口 403 toast(后端是边界,无安全问题);带 meta 的路由有"内容闪现"。UX 与一致性缺陷。
- **修复**: `beforeEach` 中 `if (to.meta.permission && !userStore.hasPermission(to.meta.permission)) next('/dashboard')`,补全 6 模块路由的 meta.permission。

### P1-10 [代码] 孤儿菜单 bug 确认,且新权限矩阵页可系统性复现

- **证据**: 后端 `AuthService.buildMenuTree`(`:177-185`)从 parentId=0 对勾选行平铺列表递归,角色勾了子菜单未勾父分组时子行挂不出 → 权限生效(selectPermissionsByUserId 返回全部勾选行)但菜单不显示;前端 `layout/index.vue:186-199` 还 `.filter(group => group.children.length > 0)` 剔除空分组;`usePermissionData.js:100-116` `togglePerm` 只写勾选叶子 id 不含父分组 → **新矩阵页是新的制造入口**。
- **修复**: `SysRoleService.saveRoleMenus` 对勾选集做**祖先闭包**(把每个选中节点的父链补入),或 buildMenuTree 遇孤儿子节点自动提升父节点。

---

## 三、P2 级发现(中危/一致性)

| # | 发现 | 证据 | 修复方向 |
|---|---|---|---|
| P2-1 | `/api/customer/check-name` 跨范围存在性探测(可探测全公司任意客户名+创建人) **[实测✅]** | 实测: TY101(06)探测"内蒙古天润化肥股份有限公司" → 200 `{"exists":true, createdBy:"张皓翔"}`;`CustomerService.java:272-277/1173-1182` 无范围过滤 | 两条 SQL 按 `getCurrentScopeUserIds()` 过滤 |
| P2-2 | 集团边界为 OR 语义+租户插件默认关;多集团数据下 all 范围可跨集团读/改 | `CustomerAccessService.java:241-263`;`canEdit/canDelete` 不查集团;`MybatisPlusConfig.java:36-38` TENANT_ENABLE:false | canView/canEdit/canDelete 集团判定改 AND 强制(与 GroupScopeUtil 同口径) |
| P2-3 | `dept_and_children` 在 V7 注释为允许值,但 `OrgScopeResolver` switch 无此分支 → 静默降级 self;而 applyDataScope 却支持它 | `V7__create_role_perm_scope.sql:13`;`OrgScopeResolver.java:152-165` | switch 补分支或按枚举收敛,消除两套口径 |
| P2-4 | 评分表 `getGrid` 未按数据范围过滤,与 `exportGrid`(过滤)口径自相矛盾 | `ScoreService.java:90-122` vs `:340-355` | 与"公司级三接口"同一决策机制确认后统一 |
| P2-5 | 公海池客户详情: 原负责人/其组织范围成员无池权限仍可读(释放未清 ownerId) | `CustomerAccessService.java:62` 先判 owner;`SeaService.java:171-188` | 池视图优先判定,与公海可见性口径对齐 |
| P2-6 | 角色删除不清理 `role_perm_scope` → 同 code 重建角色时旧范围(可能过宽)自动复活 | `SysRoleService.delete:176-187` 不调 `deleteRoleScopes`(死方法) | delete 内注入 RolePermScopeService 清理 |
| P2-7 | 范围配置无删除路径: 配成 self 后无法回"未配置"语义(view 未配置=沿用角色 data_scope);前端无"恢复默认" | `RolePermScopeService.updateRoleScopes:74-77` UPSERT 不删旧;`deleteRoleScopes:83-85` 无调用方 | 传空列表=删除;前端加"恢复默认"按钮 |
| P2-8 | 角色删除不清理 sys_user_role、菜单删除不清理 sys_role_menu、用户删除残留 5 类孤儿(user_position/user_org/incentive_permission/folder_acl/org_admin)——与 P3-7 历史同型 | `SysRoleService.delete:183-184`;`SysMenuService.delete:37-40`;`SysUserService.deleteUser:276-312` | 级联删除规范 + pre-delivery-check 孤儿巡检 |
| P2-9 | role_code 可改名 → 范围配置孤儿化(新 code 无配置回落默认,可能意外收紧) | `SysRoleService.update` hasRoleFields 含 roleCode(`:135-138`) | update 检测改名时同步 UPDATE role_perm_scope |
| P2-10 | `sys_dept.group_id/legal_entity_id` 与 `biz_customer.group_id` 全 NULL 未回填(0809 group_name 死区同型);集团边界功能形同虚设 | `V1__baseline.sql:618-619/142-143`;无任何回填脚本;`CustomerAccessService.java:245-247` hasNewScope=false 静默跳过 | 一次性回填 `UPDATE sys_dept SET group_id=1 WHERE IS NULL`,纳入体检 |
| P2-11 | 9 个死码占权限字典 14%: customer:share / file:upload / file:delete / file:edit / file:share / incentive:delete / statistics:scoreboard / system:dept:add / system:dept:delete——代码与前端均零引用 | 三方比对(字典 vs @PreAuthorize 全量提取 vs 前端字符串) | 按 V2"先解绑再删菜单"模式清理,或真正启用 |
| P2-12 | Redis 故障时 `isBlacklisted` 无降级 → 全站带 token 请求 500 | `JwtAuthFilter.java:38` → `JwtTokenProvider.java:90-92` 无 try/catch | 包 try/catch 降级"不拉黑"并告警 |
| P2-13 | 修改/重置密码不使旧 token 失效(最长 7 天继续有效) | `AuthService.resetPassword:263-289`、`SysUserService.resetPassword:251-256` 不调 blacklistToken | 按 jti 集合吊销或 token_version claim |
| P2-14 | 主 JWT 可通过 `?token=` query 携带(任意 GET),可进访问日志/Referer | `JwtAuthFilter.java:61-66` | 仅允许 scope=download 短命 token 走 URL |
| P2-15 | `DataScopeType` 枚举声明序与注释矛盾: team(rank 7)被 org_and_children(rank 5)压制,双角色(team+org_and_children)用户跨部门下级语义丢失 | `DataScopeType.java` 声明序 vs `UserDetailsServiceImpl.java:95-99` min-rank | 调整 rank 或按业务确认宽松度 |
| P2-16 | `effectiveScopes` 每次每角色 1 条 JDBC,canView 热路径每详情请求 2-5 条额外 SQL(绕过 perm:ctx 缓存) | `RolePermScopeService.lookup:47-56`;`CustomerAccessService.java:145-159` | 短 TTL 本地缓存 + 写入失效,或并入 PermissionContext |
| P2-17 | 测试库 schema 落后于迁移链: 本地 `ycmn_database_test.sql` 无 role_perm_scope;写接口在未跑 V7 的环境 500(读侧有 try-catch 降级) | `RolePermScopeService.java:53-55`(读有降级)vs `:67-79`(写无) | 测试库按迁移链重建/补齐 |
| P2-18 | **角色权限码配置漂移(实测 auth/info 确认,与业务角色定位严重不符)**: 06 销售员持有 `system:user:add/edit/delete`(用户管理)+ `system:group:add/edit/delete/view`(集团管理)+ `score:manage`(积分项增删改)+ `approval:approve/reject` + `system:config:view`;0 销售总监持 `system:user:delete` + `system:config:edit`(可写范围配置=P1-03 提权路径对其开放);05 行政持 `customer:delete`;approval:approve/reject **4 角色全持有**(靠"指定审批人"守卫兜底,守卫实测有效) | 实测 auth/info + role_menu 实查;全模块矩阵: 06 对 score/items POST/PUT/DELETE 均 200(可改积分规则);06 对 users DELETE 400(过权限门) | 权限矩阵按角色定位收紧: 用户/集团管理码仅 admin;approval:approve/reject 只授审批人;score:manage 收归 admin(如需销售可配置则明确授权机制) |
| P2-19 | role_perm_scope 按配置生效的**删除面扩张风险(实测推导)**: 测试库配置 `05 customer delete=region` → 行政(NM100,dept7)的 region 范围=大区 3 子树,含 dept6(575 owner 所在)→ **行政可删除大区内任意非本人客户**,而列表/详情按 custom 组织(6,7)显示——"看得见的未必能删,看不见的反而能删",两个维度不一致 | role_perm_scope 实测查库(05 delete=region);`OrgScopeResolver.resolveByAncestorType`(大区锚点推导);实测 05 可读 575(200)且 canDelete 判定链成立;未实测删除(毁灭性) | 配置时按业务确认;建议 UI 展示"删除范围"与"查看范围"差异提示;或删除操作加二次确认/审计日志 |

---

## 四、观察项(记录备查)

1. `DataScopeGuard` 无生产调用点(grep 仅 MybatisPlusConfig 注释提及)——归属守卫语义正确,但新模块接入无强制入口
2. `CustomerAccessService.canExport` 是死代码(无调用),导出实际走 `page()` 的 view 范围——语义一致但冗余
3. `SysOrgAdminMapper.selectManagedOrgIds` 递归 CTE 无环保护,与 `OrgScopeResolver.collectSubtree`(245-257)不对称;parent_id 环脏数据可致递归超限
4. 8 张历史 bak 表被 V1 baseline 固化(biz_customer_bak_0624 等),全新环境会带历史备份表——建议新增迁移 DROP(保留 sys_user_archive)
5. 双系统开关(`perm.superior.see.subordinate`/`perm.org.leader.see.org`)默认全开 + 30s TTL: self 范围用户也可见全部直属下级链与本人负责组织子树——已确认是设计,但开关切换最长 30s 生效
6. 客户"全部"列表不再含"分享给我的"(`CustomerService.java:87-104` 旧分支禁用)——分享客户只出现在 shared_to_me 标签,功能回归
7. `effectiveScopes` 多角色并集按"已配置角色"收窄: 角色 A 配 dept、角色 B(dataScope=all)未配置时并集只含 dept——比预期更严(fail-closed)
8. 角色-规则绑定表 `sys_role_rule` 无写点(半死表,岗位-规则有入口);`sys_permission_rule` 的规则引擎列名白名单+转义正确
9. 前端按钮门控缺口: 客户列表 TopBar 新增/导入/导出无 v-if、详情页编辑/分享/释放控件不按权限码(后端兜底,UX 不一致);score/entry 前端 isAdmin 严于后端 score:manage(权限字典说"能",界面说"不能")
10. `score:manage` 已授予销售员(06)角色——积分项管理权授予销售是否业务意图需确认(role_perm_scope 配了 `06 score manage=all` 但无消费方=死配置)
11. PermScopeManager 范围下拉默认显示"仅本人",但后端 view 未配置时沿用角色 data_scope——配置页展示与实际生效语义不一致,易误导
12. 权限勾选全量替换 menuIds + 范围独立存储: 双端并发编辑互相覆盖;勾掉权限不清范围行,重勾后旧范围复活

---

## 五、测试阶段结果(证据)

### 5.1 本地全量测试

- `mvn test` 首次运行: **320 tests / 0 failures / 240 errors** —— 根因=本地 JDK 25 vs 项目 java.version=17,Mockito inline mock 字节码不兼容(**环境问题,非代码缺陷**)
- 加 `-Dnet.bytebuddy.experimental=true` 重跑: **325 tests / 0 failures / 0 errors / 0 skipped = PASS**
- 注: commit 声称"全量320/0"与实测 325 差异为新提交新增用例,无回归

### 5.2 测试环境权限矩阵实测(111.229.37.220,真实 API)

| 验证项 | 结果 | 证据 |
|---|---|---|
| admin/admin123 登录、61 权限、249 客户 | ✅ | 实测 |
| TY101(销售员 06/self)登录、35 权限、1 客户 | ✅ | 实测 |
| TY101 读自己客户详情 | ✅ 200 | id=573 |
| TY101 读他人客户详情(id=575) | ✅ 400「无权查看该客户」 | 越权拦截正确 |
| TY101 编辑他人客户 | ✅ 400「无权编辑该客户」 | 越权拦截正确 |
| TY101 删除他人客户 | ✅ 400「无权删除该客户」 | 越权拦截正确 |
| TY101 分享他人客户给自己(POST shares) | ✅ 400「无权编辑该客户」+ DB 无残留 | 分享守卫有效(agent 误报已排除) |
| **TY101 GET /api/system/users/1(admin 详情)** | ❌ **HTTP 200 返回手机号** | **P1-1 越权实锤** |
| role_perm_scope 配置 06 customer:view=all → 详情立即放开 | ✅ 旧 token 立即 200(无缓存延迟) | 实时查库生效 |
| **同上配置 → 客户列表 total 仍=1** | ❌ | **P1-2 半接入实锤** |
| 还原配置后列表/详情恢复 | ✅ | 可逆测试完成 |
| TY101 统计 analysis(按范围) | ✅ total=1 | 数据范围过滤 |
| TY101 公海 statistics(池级权限) | ✅ 200 | 池权限生效 |
| TY101 score/items(grid 候选池) | ✅ 200 | 权限码门控 |

### 5.3 全模块 × 全角色 API 矩阵实测(2026-08-10 补充)

**方法**: 4 个真实角色账号(admin / NO001-销售总监0 / NM100-行政05 / TY101-销售员06,测试密码用后还原)→ 扫描全部 177 个 Controller 端点 → 抽 102 个代表性请求(11 模块 × 读写删)逐个实测;创建类操作真实执行并事后清理;关键疑点补测存在数据的归属守卫。完整矩阵见 `test-evidence/matrix-all-modules-4roles.txt`。

**核心结果**:

| 验证面 | 结果 |
|---|---|
| 权限门与权限码一致(应 403 者 403) | ✅ 无"应 403 却 200"的代码级越权(除 P1-01 users/{id}) |
| 归属守卫(存在数据的越权操作) | ✅ 全部有效: TY101 改/删他人激励记录 403「无权编辑他人记录」;处理他人提醒 400「无权处理该提醒」;审批他人流程 400「无权审批」;行政编辑大区他人客户 400「无权编辑该客户」 |
| 数据范围一致性 | ✅ 列表/统计/详情三口径一致: admin=249、0 总监=249、05 行政=77(custom 组织)、06 销售员=1 |
| **P1-01 users/{id} 越权** | ❌ 4 角色全部 200(含 05/06) |
| **P2-01 check-name 探测** | ❌ 200 `exists:true` + 泄露创建人「张皓翔」 |
| **P2-18 权限码漂移** | ❌ 06 持 system:user:add/edit/delete、system:group 全套、score:manage(实测可增删改积分项)、approval:approve/reject;approval 审批码 4 角色全持有 |
| **P1-02 半接入再证** | ❌ 05 配 customer view=region 但列表按 custom 组织(77);delete=region 推导行政可删大区内任意客户(P2-19) |
| role_perm_scope 实时生效 | ✅ 详情判定 06 view=all 即时生效(已还原) |

**创建类操作清理验证**: 测试产生的 573 状态(sea→normal)、池记录、联系人/回款/发票/日志、激励分类、积分项、审批全部删除归零;误改的 admin 角色名已还原为「系统管理员」;NO001/NM100 密码哈希已还原(123456 登录已失效);分享记录 0 残留。

### 5.4 每用户逐账号检查(31 账号全量)

**方法**: 备份全部 31 用户密码哈希 → 临时设统一密码 → 31 账号逐个登录 + auth/info(实际权限码)+ 客户列表(数据范围)+ 激励记录(可见性)→ 还原哈希(抽查 5 用户哈希一致验证还原完整)。完整清单见 `test-evidence/per-user-check.txt`。

**结果**:

| 维度 | 结果 |
|---|---|
| 登录 | ✅ 30/30 启用用户全部登录成功;停用用户 TY100 正确拒绝(401) |
| 权限码按角色一致 | ✅ 06 角色 26 人全=35 码;05 角色 3 人全=30 码;0=48;admin=61——同角色内无个人差异 |
| 数据范围按 override 生效 | ✅ self(1-35 客户不等,本人负责)/ team(8-44)/ office(18)/ custom(NM100=77、TY200=75,用户级授权组织不同→可见数不同)/ all(249)全部实测通过 |
| 激励专项授权生效 | ✅ NM005=25 条(全量,专项授权 can_edit/can_delete);其余销售员=11-12(分享制) |
| 用户级组织授权 | ✅ NM100(orgs 6,7)=77 vs TY200(orgs 44,38)=75——「多个行政各看不同部门」目标达成 |
| 异常 | 无「登录成功但权限异常」;无「同角色权限码不一致」 |

**⚠️ 安全观察(非本次测试所致)**: 测试库 31 个账号中**大部分密码原本就是 123456**(0805 生产全账号验证的通用密码,随生产→测试同步带入)——若生产同态,弱口令面大,建议纳入安全加固(与 P2-13 密码策略一并处理)。

### 5.5 测试残留

- 全部 31 账号密码哈希已还原并抽查验证一致;测试期间无数据变更(逐账号测试全部为只读请求)。
- 测试库 role_perm_scope 原有 39 行配置(用户此前配置)未动;matrix 测试数据残留为 0(前节已验证)。

---

## 六、已确认正常项(防止过度修复)

1. **认证链路可信**: HS256 + 无默认密钥 + jti 黑名单注销即时生效 + 禁用/删除用户每请求实时校验 + Redis 缓存降级回源
2. **权限缓存 evict 全覆盖**: 7 类写点(用户/角色/菜单/岗位/规则/部门/组织管理员)全部精确失效,afterCommit 延迟防竞态,TTL 10min 兜底——**无"改了权限没 evict"的漏网点**
3. **role_perm_scope 修改即时生效**(实时查库,不走缓存): "权限调整立即生效"承诺在代码与实测层面成立
4. **SQL 全参数化**: MyBatis-Plus LambdaQueryWrapper / 规则引擎列名白名单+转义,无注入点
5. **0801 两个历史 PII 漏洞已修复**: users 列表已加码+集团边界;depts 已收窄至本人部门+祖先链
6. **激励分享制 / 公司级三接口(积分排行/激励查询/核心指标)"全员可见"** —— 均为已确认设计,非缺陷;评分表 getGrid 口径待统一(P2-4)
7. **多角色权限聚合已修复**: roleCodes/dataScopes 全量聚合,min-rank 取最宽,"一人只取第一个角色"的历史瓶颈已消除(design.md:51 为过期文档)

---

## 七、修复路线图(建议)

**第一批(安全,优先)**
1. P1-1: users/{id} 加集团/组织校验(半天)
2. P1-3: 范围配置写口加 isAdmin 守卫(1 行)
3. P1-7: 下载 token 加 jti 拉黑或绑定资源(半天)
4. P1-8: 前端 401 重置 user store(1 行)

**第二批(新模型完整性)**
5. P1-2: 列表/统计/导出接入 effectiveScopes(view 范围),未接入模块从 UI 隐藏或禁用
6. P1-5: OrgScopeResolver org_and_children 并入 managedOrgIds,收敛两套口径
7. P1-6: custom 范围装载修复(role_perm_scope 命中 custom 时加载 sys_role_org)
8. P1-10: 菜单祖先闭包,根治孤儿菜单
9. P2-7: 范围配置删除路径(恢复默认)

**第三批(数据与治理)**
10. P1-4: V8 迁移补 biz_customer_transfer 表(功能必炸,建议与第一批并行)
11. P2-6/7/8/9: 角色/菜单/用户删除级联清理
12. P2-10: group_id 一次性回填
13. P2-11: 死码清理

**第四批(一致性/韧性)**
14. P2-1/2/3/4/5: check-name 过滤、集团 AND 语义、dept_and_children 收敛、评分表口径决策、公海详情口径
15. P2-12/13/14/15/16: Redis 降级、密码吊销、URL token 收窄、枚举 rank、范围查询性能
16. 前端: P1-9 路由守卫 + P2 按钮门控 + 观察 9-12

---

## 附: 审计方法与限制

- 4 路独立 agent 静态审计(认证鉴权/数据范围/前端/数据与缓存)+ 主线程第一手源码核验 + 测试环境实测交叉验证
- 已排除 1 个误报(分享接口 IDOR,当前代码已有 assertCanEdit 守卫)
- 限制: 未做浏览器端 E2E(前端发现均为代码级);未做生产环境实测(新模型生产未部署);测试库评分数据量小,getGrid 口径矛盾为代码级证据
- 敏感信息: 本报告不含任何密码/密钥/token 原文;测试中出现的手机号为测试库数据,未外传

---

## 八、修复执行记录(2026-08-10,测试环境已验证)

### 已修复并验证(代码+测试+测试服实测)

| # | 修复项 | 实现 | 验证证据 |
|---|---|---|---|
| P1-01 | users/{id} 可见组织守卫 | SysUserService.getById → scopeResolver.resolveVisibleDeptIds,非 admin 目标 deptId 必须在可见部门 | 实测: TY101 读 admin 400「无权查看该用户」;读自己/同部门 200;admin 200 |
| P1-02 | 列表接入 role_perm_scope view 范围 | CustomerService.applyDataScope 前置判断 hasConfigured(customer,view)→ effectiveScopes+resolveScope 过滤(admin 跳过,未配置走旧路径) | 实测: 配 06 customer:view=all → TY101 列表 1→249;还原后回 1 |
| P1-03 | 范围配置写口 isAdmin | RolePermScopeController.updateScopes 加 isAdmin 守卫(与激励授权门禁一致) | 代码+RolePermScopeService 测试 |
| P1-04 | biz_customer_transfer 建表 | V8__create_customer_transfer.sql(对齐 INSERT 列) | 测试库已应用(V8 applied: 1) |
| P1-05 | org_and_children 并入 managedOrgIds | OrgScopeResolver.resolveOrgAndChildren(本人部门树∪授权组织) | 新测试 orgAndChildren_mergesManagedOrgIds 通过 |
| P1-06 | custom 范围回退装载 | OrgScopeResolver.resolveCustom 空 managedOrgIds 时按角色现查 sys_role_org | 新测试 customScope_withEmptyManagedOrgs 通过 |
| P1-07 | 下载 token 补 jti | JwtTokenProvider.generateDownloadToken + .id(UUID) | 新测试 downloadToken_nowHasJti 通过 |
| P1-08 | 401 重置 user store | request.js handleSessionExpired 动态 import useUserStore().$reset() | 代码+前端 50 测试通过 |
| P1-09 | 路由守卫 meta.permission | router beforeEach 检查+13 条路由补 meta | 代码+构建通过 |
| P1-10 | 菜单祖先闭包 | SysRoleService.saveRoleMenus 父链补全 + 前端 collectAncestors 双保险 | 代码 |
| P2-01 | check-name 范围过滤 | existsByName/getDuplicateInfo 按 getCurrentScopeUserIds 过滤 | 实测: 范围外真实客户 exists:false;自己客户 exists:true;不再泄露创建人 |
| P2-05 | scope_level 枚举校验 | RolePermScopeService.updateRoleScopes 非法值拒绝 | 新测试 updateRoleScopes_invalidScopeLevel 通过 |
| P2-06/08 | 删除级联清理 | 角色删 role_perm_scope+sys_user_role;菜单删 role_menu;用户删 4 类孤儿表 | 代码(级联清单核对) |
| P2-07 | 范围配置删除路径 | 空列表=deleteRoleScopes;前端「恢复默认」按钮 | 实测: admin PUT [] 200 生效;前端构建通过 |
| P2-09 | role_code 改名迁移范围 | RolePermScopeService.renameRoleCode + SysRoleService.update 挂接 | 新测试 renameRoleCode 通过 |
| P2-12 | Redis 故障降级 | isBlacklisted try/catch 返回 false+告警 | 新测试 isBlacklisted_redisDown 通过 |
| P2-13 | 改密吊销旧 token | markPasswordChanged(Redis jwt:pwd-changed:{userId})+filter 检查 iat;挂接 AuthService.resetPassword/SysUserService.resetPassword/changePassword | **实测: reset 后旧 token 403 拦截,新 token 200** |
| P2-14 | URL token 收窄 | getTokenFromRequest 仅下载路径接受 query token | 代码 |
| P2-16 | effectiveScopes 30s 缓存 | RolePermScopeService ConcurrentHashMap+TTL,写时失效 | 代码+RolePermScopeServiceTest 兼容 |
| P2-02 | 前端按钮门控 | TopBar 导入/导出/新客户 v-if;score/entry isAdmin→hasPermission('score:manage') | 前端构建通过 |
| 环境 | Lombok/JDK25 编译 | pom lombok 1.18.36→1.18.46(含 annotationProcessorPaths) | 本地 333/0/0 全绿 |

### 测试结果

- 后端: **332 tests / 0 failures / 0 errors**(清理 surefire 后独立计数;325 基线 + 7 个修复测试 + 4 个 reviewer 复核回归测试)
- 前端: **50/50 断言通过**,build 成功
- 测试服回归: 18/18 核心行为通过(越权读/改/删拦截、各模块 200、修复项生效);证据 `test-evidence/fix-verification-20260810.txt`

### 独立复核(engineering-reviewer)意见与处置

复核判定"可接受,无 fail-open 回退、无阻断级缺陷、无主链路回归";提出的 6 项小修已全部落实:

| 复核意见 | 处置 |
|---|---|
| getById 守卫缺"本人恒可读"特判 + 组织管理员口径不一致 | ✅ 已加本人特判 + all 范围补集团边界 + org-admin 管辖组织子树兜底(canManageUser 同权) |
| all 范围非 admin 仍可跨集团枚举 | ✅ visibleDeptIds==null 时走 applyGroupScope 集团边界(单集团无实害,多集团前必须) |
| dept_and_children 静默降级 self 且注释撒谎 | ✅ OrgScopeResolver 补 `case "dept_and_children"`(与 dept 同语义) |
| P1-01/P1-02 缺自动化回归测试 | ✅ 补 4 例(getById 同部门放行/跨部门拒绝/本人特判/dept_and_children 解析);测试服证据已落盘 |
| 改密吊销同秒竞态(iat 秒 vs changed 毫秒) | ✅ changed 下取整到秒 |
| 角色删除漏清 biz_folder_acl | ✅ SysRoleService.delete 补 `DELETE FROM biz_folder_acl WHERE role_id=?` |

### 待用户确认(未执行)

1. **生产部署**(新 jar+前端 dist+V8 迁移)——测试环境已验证,生产需按部署流程执行(先备份)
2. **P2-18 权限码配置收紧**(生产 role_menu 解绑销售员的 system:user:*/system:group:*/score:manage;总监 system:user:delete;4 角色 approval:approve/reject 收窄)——脚本已备 `sql/migration/20260810/fix-permission-batch1.sql`(默认注释,确认后执行)
3. **P2-10 group_id 回填**(同脚本,单集团回填 1)
4. **弱口令面**: 测试库大量账号密码=123456(生产同态待查)——建议对行政/总监等账号强制改密
5. **git 提交**(本次修复共 20+ 文件,未提交)

### 修复遗留(有意不做/低优先)

- P2-03 dept_and_children 枚举收敛: 解析器已 fail-closed(self),与 applyDataScope 口径差异记录在案,建议随"两套口径统一"重构一并处理
- P2-04 评分表 getGrid 口径: 需产品确认(公司级 vs 范围)
- P2-02 集团 AND 语义/租户开关: 单集团部署无实害,第二集团接入前必须处理
- P2-05 公海详情原负责人可读: 边界语义,与公海可见性口径对齐待专项

---

## 九、生产部署记录(2026-08-10 16:43-16:45,已上线)

| 项 | 内容 |
|---|---|
| 代码 | commit d6ec24d(修复批次)已推送 origin/main |
| 备份 | `/root/ycmn/backup-pre-permfix-20260810-164344/`(全库 dump 1MB + 旧 jar 95.9MB) |
| DB 迁移 | V8 biz_customer_transfer 建表 ✅(prod V8:1;仅加表无数据变更) |
| 后端 | jar 替换+重启,md5 `07a004d1...`,TY101 生产登录 OK |
| 前端 | 入口 `index-CKwikHZx.js`,version `20260810-164451`,prefetch 9 注入;回滚备份 `/Aigicrm/files/backup/dist-rollback-20260810-164445` |
| 回滚 | 后端: 备份目录 app.jar.bak;前端: dist-rollback 目录;DB: 无数据变更无需回滚 |

### 生产实测(2026-08-10 16:45,真实账号)

| 验证项 | 结果 |
|---|---|
| P1-01 users/{id} 越权 | ✅ TY101 GET users/1 → **403**(生产销售员无 system:user:view 权限码,注解层拦截) |
| 越权读/改他人客户 | ✅ 400「无权查看/编辑该客户」 |
| P2-01 check-name 范围外 | ✅ exists:False,无创建人泄露 |
| 各模块只读(客户/激励/公海/审批/提醒/积分/统计/文件) | ✅ 全部 200 |
| 前端版本 | ✅ 20260810-164451 |

### ⚠️ 生产权限矩阵实况(与测试库不同,已核实)

- 生产 06(销售员)27 码:**无 system:user:\*、无 system:group:\***——P2-18 的用户/集团管理码漂移在**生产不存在**(测试库才有,系 0809 同步前测试环境独立配置产生)
- 生产 06 **仍持 `score:manage`**(可增删改积分规则)——**生产端 P2-18 仅剩此项**,是否保留待业务确认;收紧脚本 `sql/migration/20260810/fix-permission-batch1.sql` 按生产实况调整为只解 `score:manage`(06)+ 复核 0/05 的审批码
- 生产角色权限数: admin 63 / 0(总监)49 / 05(行政)32 / 06(销售员)27

### 生产残余风险(已如实标注)

1. **score:manage 授予销售员**(生产在售)——建议收紧或确认业务意图
2. **弱口令面**: 生产账号密码策略待核查(测试库大量 123456 系 0805 重置残留)
3. group_id 回填未执行(单集团无实害,脚本已备)
4. 新模型 role_perm_scope 生产是否已有配置待查(生产未配置过则列表行为与修复前一致)
