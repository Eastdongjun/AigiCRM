# 系统管理

> 来源锚点:`SysUserController`、`OrgAdminService.canManageUser`、角色/菜单迁移史。核实日期 2026-09-24。

## 1. 管理边界:admin 与组织管理员(orgAdmin)

```
可操作某用户 = admin 角色 ∨ (isOrgAdmin ∧ 该用户 org ∈ 本人受管组织 ∧ 目标不是 admin 角色账号)
```

- **组织管理员不得操作 admin 角色账号**(H1 安全:否则重置密码拿临时口令即接管全系统;角色硬校验放在组织范围判断之前)。
- orgAdmin 绑定存 `sys_org_admin`(组织→管理员多对多)。

## 2. 用户管理规则

- 建用户必填:账号/姓名/手机号/角色/**数据范围**(缺一报"XX不能为空")。
- **org_id 自动落库**(2026-09-23 根修):insert 前 syncDefaultOrg——部门有集团→org=部门节点,否则兜底主集团顶级组织 1,显式指定尊重;编辑随部门重算。漏配 org 的用户对非 admin 是全租户表盲(fail-closed,见 [visibility.md](visibility.md) §2)。
- 重置密码发临时口令并置 pwd_reset_required(首登强制改密);admin 初口令同机制。
- 用户管理五写接口守卫统一:`admin ∨ canManageUser(target)`。

## 3. 角色与权限管理

- 角色权限=菜单树勾选(sys_role_menu);**每权限码只绑一行菜单 + 必须带父链根菜单**(id=10 业务核心),否则权限矩阵对账与小程序 menus 树都会异常。
- `perm_version` 版本号驱动权限缓存即时生效(勾选/撤销 403→200→403 往返可验)。
- 敏感权限(如 system:user:edit、incentive:person-rank:export、incentive:read-ranking:view)默认仅 admin,其他角色由管理员勾选放开。
- **迁移纪律**:改 sys_menu/sys_role_menu 一律按 permission 码定位,禁硬编码行 id(legacy 库与重放库 id 错位曾致全员 403,23.1 事故)。

## 4. 岗位/组织/配置/字典/日志

- 岗位(position):绑定数据范围规则与集团;用户集团推导**岗位优先于 org_id**(visibility §2)。
- 组织(dept):树形,大区(2)/办事处(3)分层;leader 驱动审批兜底流与直属领导查找;客户/用户/公海池多处挂 office。
- 配置(config):`configs/public` 匿名只读(系统名/logo/客户参数等——见 business/README 数值速查);其余配置管理 `system:config:*`。
- 字典(dict):行业/开发类型等;开发类型终态四项(含"新建改建扩建大修"整词)。
- 日志(log):登录/操作审计,集团内可见(WithinGroup)。

## 5. 已知待裁决口径(非缺陷,产品决定)

- 行政(05)未绑 statistics:view/task:view(两库一致);
- 激励授权面板与角色管理勾选的边界(面板按人、角色按岗)。
