# 配置-消费注册表（T9 2026-11-12 首批）

> 使命: 把"每个可勾选项 → 后端消费点 → PC 显隐 → MP 入口"变成机器可校验的注册表。
> 每行必须能 grep 到消费点(机器断言=scripts/check-permission-config-consumption.sh);
> 本批先覆盖任务 T1-T8 涉及的码与配置面, 后续逐步全量。
> 更新纪律: 新增/迁移权限码时同步本表+契约; 空消费格=必须写白名单理由。

## 一、功能码（sys_menu permission → 三端消费）

| code | 后端消费点 | PC 消费点 | MP 消费点 | 备注 |
|---|---|---|---|---|
| file:view | FileController:57(/folders) 等 file 域 GET 守卫 | router file 页 meta | files 页入 API 链 | |
| file:edit | FileController rename/replace 端点守卫 | FileTable canRename/canReplace(双词) | files:290 canEditFile(双词) | T1 双词兼容 |
| file:delete | FileController delete 端点 | FileTable canFileAction('file:delete') | buildFileRowView canDelete | |
| file:share | share 端点 | FileTable share | canShare | 公共 tab 无分享=设计 |
| file:upload | upload 端点 | useFilePage canUpload | files canUpload | 公共根仅管理员=后端铁律 |
| file:folder:manage | FileController 夹写端点(:89-101) | FolderPanel canManageFolders | files:489 canManageFolder | 双门=全局码∧行级 |
| file:acl:manage | FolderAclController type-defaults(:52-110) | FileSpaceDefaults 组件入口; PermCenter file pane(T6) | 无 MP 入口(纯管理面) | |
| sea:config:view | SeaConfigController(:89,106) | SeaConfigManager; PermCenter sea pane(T6) | 无 MP 入口 | |
| system:menu:view | SysMenuController:19(权限树) | PermCenter scope pane(T6) | 无 MP 入口 | 无它矩阵空白 |
| system:config:view | SysConfigController 等 | router /system/config meta | 无 | 3 页面行待裁决(T5 台账) |
| system:config:edit | SysConfigController 写端点 | FileSpaceDefaults/BasicSettings canEdit | 无 | |
| statistics:view | StatisticsController 全部 7 端点(单键, 裁决A) | router 工作台/统计 meta; dashboard 卡片 | home:133 卡片门控 | dashboard:view 已退役 |
| score:view | CustomerController:155(∨) 等 | score 页 | score 页 | |
| incentive:view | IncentiveController 列表端点 | incentive 页 | incentive:627 | |
| incentive:add | IncentiveController 上传 | 上传按钮 | home/incentive canUploadEvidence | |
| incentive:edit | CategoryManager 等写端点 | CategoryManager(常量化第一批) | 无(类别管理 PC-only) | |
| incentive:stats:view | IncentiveController:159(∨多参) | stats 页 | incentive:1018(∨常量化) | T9 多参修正入权威集 |
| incentive:rank:view | IncentiveController:159/166(∨多参) | stats canRank | incentive:1018 | **T9 翻案: 非纯前端码** |
| incentive:rank:export | 无后端端点(前端 CSV) | RankTableView 导出按钮 | 无 | FE_ONLY 白名单(consumption WHITELIST) |
| incentive:person-rank:export | 无后端端点(前端 CSV) | 个人排行导出 | 无 | FE_ONLY 白名单 |
| customer:view | CustomerController 列表/详情 | 客户页/路由 | customer 页 | |
| approval:approve/reject | ApprovalController 动作 | dashboard 审批卡 canApprove/canReject(常量化) | approval 页 | |

## 二、范围档（role_perm_scope module|action → 消费与配置面）

| module\|action | 后端消费点 | 配置面 | 死活 |
|---|---|---|---|
| customer\|view/edit/delete/finance_edit | canAccessCustomer(OrgScopeResolver 链) | PermScopeManager | 活 |
| file\|view/manage/delete | 文件矩阵判定链(FolderPermissionService) | PermScopeManager+文件页 | 活 |
| statistics\|view / sea\|view / incentive\|stats_view / task\|view / score\|view / system\|view / approval\|view / reminder\|view | resolveModuleUsers[WithinGroup] 各锚(动作轴声明表 15 档) | PermScopeManager | 活 |
| 其余(incentive 7 档/customer 5 档/…) | 无消费锚 | **写入面已 400 拦(T7)** | 死档→登记册 |

## 三、对象级授权（biz_folder_type_default → 判定与视图）

| 配置 | 判定点 | 视图 | 备注 |
|---|---|---|---|
| objectType=FILE 类型/夹级 | checkFile/permsForFiles(空间锚 T4) | row.perms(T2 行级)+FileTable 双词(T1) | |
| objectType=FOLDER | check(P_RENAME/P_DELETE) | folderActions(T3 分字段) | |
| 过期行 | grantHitIn expiresAt 过滤 | 授权列表红标(T7) | |
