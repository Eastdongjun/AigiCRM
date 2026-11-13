# 新模块权限接入契约（WP7 · 3 行调用）

> 施工规格书 WP7 交付物。新模块权限内核**零改动**（锁定决策 #10）：
> 只加 `sys_resource + sys_action + 菜单`，运行时范围解析自动生效。

## 前提

内核组件（V2026.11.13.2/3 已建表并回填）：

| 组件 | 职责 |
|------|------|
| `sys_resource` / `sys_action` | 资源/动作注册表（码=现有权限码字符串，一字不改） |
| `sys_perm_grant` | 统一授权表（主体×动作×12 档范围，effect 仅 ALLOW） |
| `OrgScopeResolver.resolveModuleUsers / resolveModuleUsersWithinGroup` | 双轨入口（读开关内置于方法入口） |
| `AuthorizationService` | 鉴权门面（can/assertAction/scope/assertData/isAdmin + perm.read.<module> 读开关） |
| `ScopeQuery` | 数据范围注入器（唯一允许的范围写法） |
| `PermEpochService` | 权限版本号（权限写点统一 bump） |

## 三行调用

模块拿到可见集合（OWNER 锚点，null=全量 / 空=不可见）：

```java
// ① 注册（Flyway 新迁移，自然键锚定，不硬编码 id）：
//    sys_resource (module_code='contract', resource_code='contract', owner_column='owner_id', ...)
//    sys_action   (code='contract:view', scope_required=1, view_like=1)
// ② 菜单：sys_menu.permission = 'contract:view'（页面行，菜单-码 1:1 契约自动覆盖）
// ③ 模块查询：
List<Long> visible = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "contract", "view");
```

列表/统计/导出统一注入（推荐写法）：

```java
scopeQuery.apply(wrapper, "contract:view");   // OWNER→owner_column IN / ORG→dept_column IN / 空集→1=0 / all→不加条件
```

单条判定：

```java
authorizationService.assertData("contract:view", record.getOwnerId(), record.getDeptId());
```

## 读开关（切轨）

```
sys_config: perm.read.<module> = old(默认) | new
```

- `old`：现有链路逐字节一致（测试桩/调用点零迁移）。
- `new`：走 `sys_perm_grant` 授权并集 + 12 档解析（锁定决策 #1/#2/#6/#8）。
- 切换前必须完成 WP8 对账（抽样用户旧/新可见集零差异 + 变宽名单业务签字）。

## 规约（违反即打回）

1. 禁止模块自己写 `IN owner_id`——一律 ScopeQuery / resolveModuleUsers。
2. 新码必须先注册 `sys_action`（uk_code 自然键）+ 菜单行，`scope_required` 按消费实况（入口/路由码=0）。
3. view_like 只标只读动作（上级/负责人开关唯一作用面，锁定决策 #8；写动作绝不放宽）。
4. 权限写点（角色/范围/岗位/组织/开关/菜单保存）统一调 `permEpochService.bump()`。
5. 迁移全部走 Flyway 新迁移；自然键锚定；显式 utf8mb4_general_ci。
