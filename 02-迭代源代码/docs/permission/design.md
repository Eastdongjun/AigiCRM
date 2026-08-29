# YCMN 权限设计

> 人工维护文档。权限码清单见 `permission-dict.md`(自动生成)。
> 修改权限体系时必须先更新本文,再改代码。
> 本文 2026-08-27 经全系统功能级审计后重写,与代码功能事实对齐。

## 一、总体架构（五层 + 支撑机制）

```text
┌─────────────────────────────────────────────────────────────┐
│ 第1层 菜单/页面    sys_menu 三层树                            │
│   menu_type=1 模块分组 → 2 页面入口 → 3 按钮权限点            │
│ 第2层 功能权限码   "模块:动作" 字符串（接口层 @PreAuthorize）  │
│ 第3层 角色打包     sys_role + sys_role_menu + sys_user_role   │
│ 第4层 数据范围     三层引擎（见 §三），服务层 applyDataScope   │
│ 第5层 资源归属     owner本人 / 上级链 / 文件ACL / 公海池级     │
└─────────────────────────────────────────────────────────────┘
支撑机制：
- JWT 认证（24h）+ 多角色并集 + 防提权校验
- perm_version 缓存自愈（角色/规则变更后在线用户自动刷新权限）
- 改密 token 撤销（DB 级 pwd_changed_at 比对）
- LogAspect 全局 AOP 审计所有写接口
```

## 二、认证链路

```text
浏览器 → JWT(Authorization: Bearer) → JwtAuthFilter
  → 解析 token → 查 sys_user(校验 deleted/status/leave_status)
  → 查 sys_user_role → 角色（多角色并集，非取第一个）
  → 查 sys_role_menu + sys_menu.permission → 权限码集合
  → 查 role_perm_scope → 数据范围配置（见 §三）
  → 查 sys_role_rule / sys_position_rule → 权限规则 → RuleResult
  → 注入 SecurityContext
```

- Token: JWT(HS256),有效期 24h(记住我 7 天),存 localStorage
- 登录接口 `/api/auth/login` 匿名可访问;失败 5 次锁定 15 分钟
- 改密后旧 token 立即失效（DB pwd_changed_at 兜底，JwtAuthFilter 比对）
- 权限缓存：Redis PermissionContext + perm_version 版本比对自愈
  （角色/规则/范围变更时 +1，命中缓存时版本不一致即重建）

## 三、数据范围（三层引擎，本系统核心）

### 3.1 配置层（按优先级）

| 层     | 配置位置                                               | 说明                          |
| ------ | ------------------------------------------------------ | ----------------------------- |
| 用户级 | `role_perm_scope`(assignee_type=USER)                  | 用户显式配置，最高优先        |
| 岗位级 | `role_perm_scope`(assignee_type=POSITION)              | 岗位配置                      |
| 角色级 | `role_perm_scope`(assignee_type=ROLE)                  | 角色配置                      |
| 兜底   | `sys_role.data_scope` + `sys_user.data_scope_override` | 无 role_perm_scope 匹配时回退 |

### 3.2 解析规则

- 匹配优先级：USER > POSITION > ROLE；同主体内 精确(模块,动作) > 通配(\* \*)
- 多角色数据范围取**并集**（取最宽）
- 范围枚举（六级）：`all` > `custom` > `region` > `office` > `dept` > `team` > `self`
  （`custom` 时从 sys_role_org / sys_user_org 加载授权组织集合）

### 3.3 落地

- 所有"看数据"的查询必须走 `OrgScopeResolver` / `applyDataScope` /
  `getCurrentScopeUserIds`（admin 返回 null=全量）
- **禁止** `selectList(null)` 全量查询绕过数据范围层（2026-08-27 修复
  indicatorStats 的教训）
- 统计模块按账号数据范围过滤，例外需在 role_perm_scope 显式配 all
  （如积分排行 score:ranking 全员可见），不得代码写死

## 四、资源归属（第5层）

- **文件模块**：文件夹 ACL（biz_folder_acl）控制 upload/edit/delete/share/download
  六动作；判定链 = 管理员 > 文件夹主人 > 直属上级链 > ACL 角色 > 公共兜底(仅view)
- **上级链**：个人上级 manager_id 优先，未设置走部门树 leader_id（5 层上限）
- **公海**：池级 ACL（PERM_CLAIM/PERM_EDIT/PERM_DELETE）
- **激励**：分享制（本人 + shared=1 可见），改/删走本人/管理员判定
- **客户**：owner 本人 + 上级链 + 池级（canEdit/canAccess）

## 五、规则引擎（字段/操作级，第4.5层）

- 表：`sys_permission_rule`（规则定义）+ `sys_role_rule`（角色绑定）+
  `sys_position_rule`（岗位绑定）
- 生效链路：登录时按 角色+岗位 加载规则 → PermissionRuleEngine.evaluate
  → RuleResult 缓存 → ActionGuardAspect（全局AOP，拦截所有controller）
  - FieldFilterAdvice（响应字段过滤）
- 实体识别：@GuardEntity 显式注解优先，类名推断兜底（2026-08-27 接通）
- 规则类型：org_filter / column_filter / time_range / field_visible / action_control

## 六、角色体系（4 角色）

| 角色       | role_code | 数据范围       | 特点                   |
| ---------- | --------- | -------------- | ---------------------- |
| 系统管理员 | admin     | all            | 全部权限               |
| 行政       | 05        | office         | 管理类权限,无销售数据  |
| 销售总监   | 0         | custom         | 管理 + 销售,自定义组织 |
| 销售员     | 06        | team(通配self) | 业务权限,本人及下级    |

> 多角色并集：用户可绑多个角色，权限码取并集、数据范围取最宽
> （role_perm_scope 按 USER>POSITION>ROLE 匹配，通配兜底）。

## 七、关键安全点(踩坑记录)

1. **权限码必须查库确认**: 改权限前先查 sys_menu(勿猜,曾因 dept:view 保护写操作出事)
2. **所有写接口都要 @PreAuthorize**: 不只第一个,GET 也可能泄漏(用户列表曾无权限码全量泄漏)
3. **数据范围是 SQL 层强制**: 前端隐藏不等于安全,越权测试必须直接调 API
4. **查询禁止绕行数据范围**: 统计/报表必须走 applyDataScope
   (2026-08-27 indicatorStats selectList(null) 越权已修复)
5. **防提权**: validateGrantableRoleAndScope——非 admin 不能授 admin 角色、
   不能授超自身数据范围的角色/范围
6. **权限变更生效**: 角色/规则/范围变更后 evictByXxx + perm_version+1 缓存自愈
7. 登录失败锁定、密码 BCrypt 加密、软删除(deleted 标记)为既有安全基线

## 八、与代码的对应

- `JwtAuthFilter`(com.ycmn.security): 认证 + token 撤销比对
- `SecurityConfig`(com.ycmn.config): 安全规则/CORS
- `UserDetailsServiceImpl`(com.ycmn.security): 权限聚合（角色/权限码/范围/规则）
- `OrgScopeResolver`(com.ycmn.common.scope): 数据范围解析（三层引擎）
- `PermissionRuleEngine`(com.ycmn.common.engine): 规则求值
- `ActionGuardAspect`(com.ycmn.common.engine): 操作控制切面
- `FieldFilterAdvice`(com.ycmn.common.engine): 字段过滤
- `FolderPermissionService`(com.ycmn.module.file.service): 文件 ACL 判定
- `applyDataScope()`: Service 层数据范围拼装
- `SysRoleController` 等: @PreAuthorize 示例
