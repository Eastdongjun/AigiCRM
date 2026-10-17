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
- **org_id 自动落库**(2026-09-23 根修):insert 前 syncDefaultOrg——部门有集团→org=部门节点,否则兜底主集团顶级组织 1,显式指定尊重;编辑随部门重算。漏配 org 的用户对非 admin 是全租户表盲(fail-closed,见 [visibility.md](visibility.md) §2)。**【裁决 2026-10-04】单集团部署,兜底组织 1 维持现状**——"误分配集团"风险单集团下不存在(GPT P0-002 据此关闭;引入第二集团时需重评为"拒绝保存")。
- 重置密码发临时口令并置 pwd_reset_required(首登强制改密);admin 初口令同机制。
- 写接口守卫实际口径(【更正 2026-10-08 需求对齐】原"五写接口统一公式"与实现不符):**update/delete/reset-password 三接口统一 `admin ∨ canManageUser(target)`**;**create=功能权限 `system:user:add` + orgAdmin 调用方按 canManageOrg 校验**(非 orgAdmin 非 admin 持码者无对象级守卫,由可授角色/数据范围约束兜底);**change-password=自助接口**(仅校验旧密码)。

## 3. 角色与权限管理

- 角色权限=菜单树勾选(sys_role_menu);权限码与菜单行按 **permission 码**对账(渲染层按 path 去重)。【更正 2026-10-08】"每权限码只绑一行菜单"不是 DB 不变量:sys_menu.permission 无唯一约束、无菜单写接口,且同码多路由为合法模式(V2026.09.21.1 迁移注释明确定性),生产尚存少量手工写入的同 path 冗余行(AuthService 注释在案,仅渲染层消除重复)。
- `perm_version` 版本号驱动权限缓存即时生效(勾选/撤销 403→200→403 往返可验)。
- 敏感权限(如 system:user:edit、incentive:person-rank:export、incentive:read-ranking:view)默认仅 admin,其他角色由管理员勾选放开。
- **迁移纪律**:改 sys_menu/sys_role_menu 一律按 permission 码定位,禁硬编码行 id(legacy 库与重放库 id 错位曾致全员 403,23.1 事故)。

## 4. 岗位/组织/配置/字典/日志

- 岗位(position):绑定数据范围规则与集团;用户集团推导**岗位优先于 org_id**(visibility §2)。
- 组织(dept):树形,大区(2)/办事处(3)分层;leader 驱动审批兜底流与直属领导查找;客户/用户/公海池多处挂 office。
- 配置(config):`configs/public` 匿名只读(系统名/logo/客户参数等——见 business/README 数值速查);其余配置管理 `system:config:*`。
- 字典(dict):行业/开发类型等;开发类型终态四项(含"新建改建扩建大修"整词)。
- 日志(log):登录/操作审计,集团内可见(WithinGroup)。

### 4.1 回收站(已删除客户/已删除人员,2026-10-18)

- 入口:`/system/recycle`,权限 `system:recycle:view`(页面/列表)+`system:recycle:restore`(恢复),**默认仅系统管理员**;其他角色由管理员在角色管理勾选(perm_version 即时生效)。
- 数据口径:只列 `deleted=1` 的逻辑删行(显式 SQL 绕过 @TableLogic),不改动任何现有列表/查询;删除仍是终态,恢复是显式管理动作。
- 客户恢复闸门(任一不满足即拒绝):① 无同名活跃客户(防重复卡);② 原负责人未删除且存在;③ 无 `available/pending_approval` 公海池残留。恢复后 status=normal,回到原负责人名下。
- 人员恢复闸门:账号名未被活跃用户占用;原部门仍存在。**删除时已清除角色/岗位/组织关联(sys_user_role/sys_user_position/sys_user_org),恢复后须在人员管理重新分配角色**;恢复即 `perm_version+1`+权限缓存失效。
- 留痕:恢复动作写 `sys_log`(module=系统, action=恢复客户/恢复人员, 含客户名/账号与关键 id),管理员可在日志管理回查。
- 不在回收站的内容:经 SQL 直删且 deleted=1 的行虽然仍是软删,但若其负责人/部门等外键失效会被恢复闸门拒绝(需先修复依赖)。

## 5. 已知待裁决口径(非缺陷,产品决定)

- 行政(05)未绑 statistics:view/task:view(两库一致);
- 激励授权面板与角色管理勾选的边界(面板按人、角色按岗)。
