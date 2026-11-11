# 动作枚举与映射（T10.3 2026-11-12 立）

> 三套"动作"词汇的映射单一说明: 功能码动作(权限码后缀) / 矩阵动作(role_perm_scope 的 action) /
> 文件动作词(文件夹视图 perms 细词)。新增码时动作必须命中本枚举(契约软告警期, 硬拦待全量铺开后)。

## 一、矩阵动作（role_perm_scope.action, 15 档活档=动作轴声明表）

| 动作 | 消费锚 | 出现的 module |
|---|---|---|
| view | resolveModuleUsers[*] 通用读档 | customer/file/statistics/sea/task/score/system/approval/reminder/incentive(stats_view 除外) |
| edit / delete / finance_edit | canAccessCustomer 客户三档 | customer |
| manage | 文件矩阵判定(矩阵 manage 档) | file |
| stats_view | 四项激励独立数据范围(2026-11-05 拍板B) | incentive |

## 二、文件动作词（按钮视图/授权 perms CSV 细词）

`view / upload / download / rename / replace / share / delete`（+历史伞词 `edit` 读时兼容, csvHas 展开;
夹行动作 `create/rename/delete` 走 folderActions 分字段）。权威=FolderPermissionService 常量,
契约=check-file-action-vocab(三端白名单)+check-file-perms-view-contract(视图消费词)。

## 三、功能码动作 → 矩阵动作 的映射（同义动作归档）

| 功能码后缀 | 矩阵档 | 说明 |
|---|---|---|
| :view / :read-* | view | 读类一律 view 档 |
| :edit / :add / :upload / :share / :date:edit / :finance:edit | manage 或对应 edit/finance_edit | 写类按域归档(文件域写=manage, 客户域写=edit) |
| :delete | delete | 文件/客户均有 delete 档 |
| :export / :import | view | 导入导出范围跟读档(不单独设档) |
| :approve / :reject / :manage / :config:edit | 无矩阵档 | 纯功能开关, 无数据范围语义 |

## 四、软告警口径

新增权限码若后缀不在上表 → check-permission-config-consumption 报 WARN(不拦);
矩阵新增 (module,action) 不在 15 档 → RolePermScopeService 写入面 400 硬拦(T7) +
check-scope-axis-contract ①③硬拦。
