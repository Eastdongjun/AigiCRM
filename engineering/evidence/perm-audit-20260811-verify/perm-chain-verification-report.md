# YCMN 权限链路完整性审计报告（2026-08-11）

> 审计对象：`/Users/donglai/东来/01-Projects/01-aigicrm/04-源码`（main@c056041，工作树干净）
> 审计方法：5 维度并行子代理审计 + 对抗验证（9/9 关键发现 CONFIRMED）+ 测试服 Playwright 真实浏览器实测 + 测试库数据核验
> 结论：**权限管理"勾选能保存、保存后即时生效"的链路是通的，但存在 1 个 P0 级"保存意外全量授权"漏洞、2 个前端功能失效、多个 P1 权限治理缺陷。当前"权限管理能否生效"的答案是：部分生效、部分失效、且有一个危险的全量放大入口。**

---

## 一、核心结论（回答用户问题）

| 问题 | 结论 | 证据 |
|---|---|---|
| 勾选权限能否保存并生效？ | **能**。保存后 role_menu 重建 + Redis 缓存即时 evict，下一请求按新权限裁决 | SysRoleService.update:160 evictByRole；PermissionCacheService 全覆盖 |
| 打开权限框点"保存"是否安全？ | **不安全（P0）**。任何角色打开权限框即显示 74-77 全勾选，随手保存即授予几乎全部权限 | Playwright 实测 4 角色全部 >74 勾选（销售员绑定 37 显示 76） |
| 权限管理里"最复杂的权限管理"能否生效？ | **大部分不生效**。PermScopeManager 功能勾选列恒 false 点不动；role_perm_scope 的 scope_level 运行时零消费 | PermScopeManager.vue:49 + RolePermScopeService:96（无调用方） |
| 停用角色是否撤销权限？ | **不能（P1）**。selectPermissionsByUserId 无 sys_role.status 过滤 | SysMenuMapper.java:12-15 |
| 权限调整后旧 token 是否立即生效？ | **是**。标准写点全部 evict，TTL 10min 仅兜底 | PermissionCacheService 审计 |

---

## 二、🔴 P0 实锤：权限分配弹窗打开即全选，保存即全量授权

### 触发链路（代码级 + 实测级双重证实）

```
saveRoleMenus 祖先闭包(SysRoleService.java:208-229)
  └─ 无条件把选中节点的 parent_id 父链补入 role_menu
     └─ 目录行(menu_type=1, 菜单 1/10/20/30)被写入 role_menu（测试库 4 角色各 4 行）
        └─ SysRoleService.getById:60 → menuMapper.selectMenuIdsByRoleId 返回含目录行
           └─ 前端 role.vue openPermDialog:249 → checkedMenuIds 含目录行 id
              └─ el-tree :default-checked-keys="checkedMenuIds" 且未设 check-strictly
                 └─ element-plus 2.14.1 tree-store.mjs:91-95
                    defaultCheckedKeys.forEach → node.setChecked(true, !checkStrictly=true) 深传播
                       └─ 目录下所有后代(菜单+按钮)全部勾选
                          └─ handleSavePerm:260-263 getCheckedKeys() 全量提交
                             └─ SysRoleService.update:152-155 delete 全部 + 按全选重建
                                └─ 角色被授予目录下全部权限码
```

### Playwright 实测数据（测试服 111.229.37.220，admin 登录）

| 角色 | role_menu 实际绑定 | 打开权限框勾选显示 | 放大倍数 |
|---|---|---|---|
| 销售总监(0) | 63 | **77（全选）** | 1.22× |
| 销售员(06) | 37 | **76** | **2.05×** |
| 系统管理员(admin) | 75 | 74 | ~1× |
| 行政(05) | 45 | **76** | 1.69× |

截图证据：`perm-dialog-salesman-checked.png`（销售员权限框，76 勾选/77 节点）。
测试环境：测试服部署线为桌面端 YCMN-v3（含 PasswordResetRequiredFilter），与权威源码 main 的权限链路代码一致（role.vue 相同）。

### 后果
- **最小权限角色被意外放大**：销售员本应只看到 37 个绑定，打开权限框显示 76 全选。管理员想删除一个权限、或只是打开确认一下随手点保存，就把 customer/file/incentive/reminder/sea/approval/statistics/score 全模块 + 系统管理的用户增删改/角色增删改/配置编辑全部授予。
- 记忆中的"勾选没生效"同源解释：取消一个节点 → 父分组仍在 role_menu → 下次打开深传播又勾回；同权限码多行下 `DISTINCT` 其它行仍授码。

### 修复方向
1. `role.vue` 权限树加 `check-strictly`（参考 SeaConfigManager.vue:54/104 先例）——消除深传播回显失真。
2. `saveRoleMenus` 祖先闭包改为**只补 menu_type=1 目录行**（目录行 permission 为空，不产生 RBAC 授权），不要补带真实权限码的父菜单（menu_type=2）。
3. 一次性清理 role_menu 中已写入的 16 条目录行（生产 + 测试）。
4. `handleSavePerm` 提交前过滤：仅提交真实权限载体（menu_type=2/3），不提交目录行。

---

## 三、前端权限管理失效（"最复杂的权限管理"）

### P1：PermScopeManager（V2 功能×范围×锚点矩阵）功能勾选列恒 false 且点击无反应
- **位置**：`PermScopeManager.vue:49` 调 `hasPerm(col,item)`/`togglePerm(col,item)`，但 `matrixCols`(:108-113) 生成的 `col={key:'ROLE:xxx', label}` **无 `.id`**。
- `usePermissionData.js:96` `rolePermMap.value[role.id]` → `rolePermMap[undefined]` → undefined → `hasPerm` 恒 false；`togglePerm`(:116) `if(!set) return` → 点击无任何反应。
- **影响**：V2 权限体系的核心"功能勾选"完全失效。ROLE/POSITION/USER 三种模式下，已有权限的角色回显为"未授权"，且无法配置任何功能权限。范围/锚点下拉可用，形成"功能死、范围活"的割裂。

### P2：PermMatrixManager 权限矩阵模式不可达
- **位置**：`config.vue:34` 恒传 `mode="overview"`，PermMatrixManager 的 `v-else`（矩阵分支）永不渲染。
- 唯一传完整角色对象 `r(.id)` 的矩阵实现（链路正常）永远不展示 → 功能权限配置的唯一可用入口是 role.vue 那个 P0 越权弹窗。

### P3：role.vue 弹窗跨角色勾选累积
- `default-checked-keys` 只增不减，菜单树加载失败时先开角色 A 再开 B，B 保留 A 的勾选叠加。建议显式 `setCheckedKeys` 或加 `:key=currentRoleId` 强制重建。

---

## 四、role_perm_scope 数据范围配置"配置了不生效"

### P1：scope_level 完全未生效（每模块/每动作范围运行时零消费）
- **位置**：`RolePermScopeService.java:96`（effectiveScopes）。
- 全仓 grep 证实：`getScopeLevel` 无任何生产读取点；`effectiveScopes`/`configuredScopes`/`configsFor`/`hasConfigured` 全部无调用方；`scopeConfigs` 仅被 `anchorOf`(:168-173) 消费。
- 运行时实际范围 code 仍来自 `SecurityUser.dataScopes/dataScope`（即 `sys_role.data_scope` 与 `sys_user.data_scope_override`），不是 `role_perm_scope.scope_level`。
- **影响**：管理员在权限范围管理器里给某角色配置"客户=仅本人"等，对列表/详情/编辑/删除的可见数据**零影响**。配置存进了表，但没有任何代码读它做判定。`RolePermScopeService` 批③注释自认"USER>POSITION>ROLE 优先级与生效尚未接入解析链路"。
- 测试库 15 条配置（ROLE 0/05/06/admin 的 `* *` 通配 + USER 级）全部为"纯建模"，无运行时效果。

### P2：anchor=ORG 锚点在客户模块实际不可用
- `anchorOf` 链路已接通（驱动 OWNER↔ORG 分支），但 ORG 分支依赖 `biz_customer.dept_id`——该列**从不落库**（CustomerService.create:257-271 只设 ownerId/officeId，全仓无 `customer.setDeptId` 调用）。ORG 配置要么空列表要么全拒绝。

### P2：统计/报表硬编码 OWNER 锚点，与列表口径脱节
- `CustomerService.getCurrentScopeUserIds`(:722-737) 硬编码 `.anchor(OWNER)`，不随 role_perm_scope 配置。配置 ORG 锚点后列表与统计口径必然分歧。

### P3：approval/reminder/score/incentive/sea 等模块完全不消费 role_perm_scope
- 除 customer 模块的 anchor 外，每模块每动作范围模型零接线。

---

## 五、权限治理缺陷（P1 级）

### P1：停用角色不撤销权限
- `SysMenuMapper.selectPermissionsByUserId:12-15` 只 JOIN `sys_menu`/`sys_role_menu`/`sys_user_role`，**不 JOIN `sys_role`**。`sys_role.status=0` 不参与过滤 → 把角色停用后，该角色绑定的权限码仍进入用户 authorities。"停用角色即撤销权限"治理操作失效（over-grant）。

### P1：admin 缺授（新权限码全局 403）
- admin 的权限码完全来自 `role_menu` 绑定，只在 init.sql 初始化时 `INSERT INTO sys_role_menu SELECT 1,id FROM sys_menu` 一次性建立。`SysMenuService.create/update` 只插菜单 + evictAll，**不把新菜单绑定到 role_id=1**。
- 后果：运行时新增的任何权限码（新接口 @PreAuthorize、新按钮菜单），在角色页手动勾选前包括 admin 在内无人持有 → 新接口全局 403。代码层加新权限码漏一步绑定即整体拒绝。

### P1：文件模块按钮级权限码是装饰性权限
- `FileController` 全部 14 个端点 @PreAuthorize 一律 `file:view`；`file:upload/delete/edit/share` 在全部 Java 源码零引用。实际写权限由文件夹 ACL 平面裁决，不由角色页配置。勾掉角色的 `file:delete` 不会阻止删除。

### P1：祖先闭包绕过 validateGrantableRole 权限子集校验
- 后端校验只覆盖提交集合，闭包计算出的 expanded 集合（含真实权限码的父菜单，menu_type=2）绕过子集校验直接入库。非超管可通过构造只含子节点的请求获得父菜单的 RBAC 权限码（提权旁路）。

### P2：isAdmin() 硬编码 role_code 数据旁路（约 35 处）
- `SecurityUtils.isAdmin()` 以 `hasRole("admin")` 在数据层放行，覆盖数据范围/文件夹 ACL/租户隔离。租户过滤对 admin 不附加 tenant 条件。

### P2：updateOrgs 无集团边界与可授予性校验
- 持 `system:role:edit` 的非超管可对任意 roleId 覆写 role_org，orgIds 可指向自身数据范围之外的组织，扩大 custom 数据范围可见集合。

### P3：SysUserService.update roleId=null 无法解除角色
- 用户编辑仅当 `roleId != null` 时删除重建 sys_user_role；roleId=null 时旧绑定保留且依然生效。"移除该用户角色"无可达路径。且与多角色模型（SecurityUser.roleCodes）不一致：写路径只能落单角色。

### P3：基础数据伪菜单(98/99/100)携带真实管理码
- 勾选"基础数据-部门"即获得 `system:dept:view`，可访问组织管理员枚举接口。

---

## 六、确认正常的部分（非缺陷）

- **写入链路即时生效**：勾选保存后 role_menu 变更即时失效 Redis 权限上下文，下一请求重建生效；TTL 10min 仅兜底。
- **标准写点 evict 全覆盖**：用户/角色/岗位/组织管理员/菜单/规则/范围配置均有 evict（evict/evictByRole/evictByPosition/evictAll）。
- **非超管提权防护主链路**：创建/修改角色时 validateGrantableRole 拦截 admin 角色、更宽 dataScope、超出自身权限子集。
- **客户模块 OWNER 锚点口径一致**：列表/详情/编辑/删除走同一解析链路（OrgScopeResolver.resolveScope/canAccess）。
- **租户隔离已上线**：26 表 tenant_id + TENANT_ENABLE=true（生产）。

---

## 七、修复优先级建议

| 优先级 | 修复项 | 风险等级 |
|---|---|---|
| P0 | role.vue 加 check-strictly + saveRoleMenus 只补目录行 + 清理 16 条目录行 + handleSavePerm 过滤 | 越权放大 |
| P1 | selectPermissionsByUserId 补 `JOIN sys_role r AND r.status=1` | 停用角色不失效 |
| P1 | 修复 PermScopeManager 功能勾选（matrixCols 携带 role.id）或恢复 PermMatrixManager 可达 | 功能权限无法配置 |
| P1 | 新菜单自动绑定 admin（SysMenuService.create/update） | 新接口全局 403 |
| P1 | 接入 role_perm_scope.scope_level 到 OrgScopeResolver（或明确下线该模型） | 配置不生效 |
| P2 | ORG 锚点补 dept_id 落库或禁用 ORG 选项 | 配置误导 |
| P2 | updateOrgs 补集团边界校验 | 跨集团写 |
| P3 | SysUserService roleId=null 删绑定 | 角色无法解除 |

---

## 八之二、修复实施与验证记录（2026-08-11 同日）

用户选择"P0+P1 全部修复"。已实施 5 项修复（未 commit）：

| # | 修复 | 文件 | 验证 |
|---|---|---|---|
| 1 | P0 role.vue 权限框加 check-strictly + setCheckedKeys 精确回显 + handleSavePerm 补祖先链 | role.vue | el-tree 行为实测：修复前 5 绑定→11 勾选，修复后 5 绑定=5 勾选 |
| 2 | P0/P1 saveRoleMenus 闭包补全父链 + 非超管闭包集合过权限子集校验 | SysRoleService | 新增 2 测试锁定（持子缺父→拒，全持有→放行） |
| 3 | P1 selectPermissionsByUserId/selectMenusByUserId/selectRolesByUserId 补 r.status=1 | SysMenuMapper/SysRoleMapper | 停用角色权限码不再生效 |
| 4 | P1 新菜单自动绑定 admin（按 role_code 解析，非硬编码 id=1）+ @Transactional | SysMenuService | 编译+测试通过 |
| 5 | P1 PermScopeManager 功能勾选列传 col.role | PermScopeManager.vue | 功能列恢复可配 |

**独立复核（engineering-reviewer）**：复核发现 3 个问题已全部修正——
- B（P2）admin 绑定硬编码 role_id=1 → 改按 role_code='admin' 解析；
- C（P3）闭包测试空转 → 改为精确场景（持子权限缺父权限被拦 + 全持有放行）；
- D（P3）审批 ROLE 解析不过滤角色状态 → 新增 selectActiveUserIdsByRoleId 供审批用，evictByRole 保留原方法。

**测试结果**：后端全量 344/344 全绿（JDK17，Java 25 下 Mockito 无法 mock JdbcTemplate 是环境问题非代码缺陷）；前端 build 成功。

> ⚠️ 复核 agent 缺陷 A（孤儿按钮）基于中间版本代码分析——最终代码 collectDirAncestors 已改为补**所有祖先**（含 menu_type=2 父菜单），孤儿按钮问题在当前代码不存在（node 实测 collectDirAncestors(40) 返回 [10,11]）。

## 八之三、部署记录（2026-08-11 同日，用户确认"先测试服后生产"）

**测试服（111.229.37.220）**：
- 后端 jar（f2a3890f，含修复）docker cp 替换 + restart，`Started in 7.581s`；前端 dist（index-BzGLLf2f）替换。
- Playwright 实测：销售总监 77→**63**、销售员 76→**48**、行政 76→**43**——勾选数回落至实际绑定。
- 备份：`/home/ubuntu/projects/ycmn/backup-test-app-20260811-215653.jar`；容器 `/tmp/dist-bak`。

**生产（39.99.35.218，www.aigicrm.com）**：
- 后端 jar（f2a3890f）备份旧 jar（backup-pre-permfix-20260811-215857.jar）→ docker cp → restart，`Started in 12.247s`，容器实际 md5 独立回读=f2a3890f ✓
- 前端 NAS dist 备份（/Aigicrm/files/ycmn-dist-rollback-20260811-permfix）→ 替换 → prefetch 9 个 → 版本 20260811-220004，公网入口 index-BzGLLf2f.js ✓
- **生产 UI 实测（admin 经 HEALTH_CHECK 账号登录）**：销售总监 63、销售员 37、系统管理员 74、行政 44——全部精确等于实际绑定，P0 深传播在生产消除，0 控制台错误 ✓
- deploy.log 已记录。

## 八、测试环境操作记录（透明声明）

审计过程中对测试服(111.229.37.220)做了以下操作，均已回滚/清理：
1. 临时清除 admin `pwd_reset_required`（YCMN-v3 专有强制改密门禁）用于登录 → **已恢复为 1**。
2. 创建临时角色 `p0_verify_tmp`(id=12) 验证 → **已删除，role_menu/sys_user_role/sys_role 零残留**。
3. Playwright 打开权限框仅点"取消"，未保存任何角色权限。
4. 测试服 role_menu 有并行会话操作痕迹（文件按钮权限码迁移：移除 134-143、新增 47/48/110/111/120），**非本次审计操作**，未改动。

> ⚠️ 测试服为并行使用环境，后端容器名 ycmn-backend 连 shared-mysql；桌面端 YCMN-v3 与权威源码 main 是两条未合并的线（YCMN-v3 含 V10/V11 SQL 修复 + PasswordResetRequiredFilter）。**两条线必须合并，否则下次从权威部署会回退**。
