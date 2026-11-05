# 文件中心

> 来源锚点:`FileController`、`FileService`、`SysUserService.storageApplyInfo/validateStorageQuotaRequest/applyStorageQuota`、`BizFileFolder/BizFolderAcl/BizFileShare` 实体、ADR-20260909-customer-file-association。核实日期 2026-10-18。

## 1. 权限码与操作面

| 操作 | 权限码 |
| --- | --- |
| 浏览文件夹/列表/下载/存储查询 | `file:view` |
| 上传 | `file:upload`(叠加文件夹 ACL) |
| 替换 | `file:edit` |
| 删除 | `file:delete` |
| 分享 | `file:share` |
| 文件夹增删改 | `file:folder:manage` |
| 文件夹 ACL 授予/撤销 | `file:acl:manage`(授权操作仅 PC;小程序只读查看) |

## 2. 文件夹 ACL 模型(【更正 2026-10-08 需求对齐】按实际实现记录)

- ACL 按**角色**授予而非按用户:`biz_folder_acl(folder_id, role_id, perms)`,同一文件夹可挂多角色多行(唯一键 uk_folder_role);判定经 `userHasRole(userId, acl.roleId)` 桥接。未授权文件夹不可见其内容。
- 文件夹树面板管理(增删改),ACL 弹窗单独授权;**查看与修改 ACL 都要求 `file:acl:manage`**(后端 GET/PUT 与小程序一致,与 function-permission-matrix.md 同口径——原文档"file:view 只读查看"系独错)。

## 3. 存储配额与扩容申请(D1=A 2026-10-18)

### 3.1 配额与计费

- 每人默认 **10 GB**(新用户取系统设置 `user.default.storage`,写入 `sys_user.storage_quota`);`GET /file/storage` 查询已用/配额。
- **私人文件按差额计扣**:替换文件时 storageUsed = 原已用 − 旧 size + 新 size(最小 0),不重复计费。
- 配额调整只走 storage 类审批,不提供后台直接改配额入口。

### 3.2 申请资格(不新增权限码)

`GET /api/file/storage/apply-info`(`file:view`)返回申请面板数据:

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `used` | Long | 当前已用 bytes |
| `quota` | Long | 当前总配额 bytes |
| `maxQuota` | Long | 生效的单用户上限 bytes |
| `canApply` | boolean | 当前用户是否可发起申请 |
| `reason` | String/null | 不可申请原因(可申请时为 null) |
| `enabled` | boolean | 非管理员申请开关原值(管理端展示) |
| `allowDecrease` | boolean | 首版恒 false(只升不降) |
| `pending` | boolean | 当前申请人是否已有 reviewing/pending 的 storage 单;为 true 时 `canApply=false` |

- **系统管理员(role_code=`admin`)始终可申请**,不受开关/名单限制。
- **非管理员**须同时满足:系统设置 `file.storage.apply.enabled=true`,且本人 userId 出现在 `file.storage.apply.users`(JSON 数组,元素 userId)中。名单由管理员在「系统设置」多选人员维护。
- 非管理员不满足任一条件时 `canApply=false` 并返回 `reason`;提交接口同样二次校验(403),不能靠前端绕过。
- 配置键(迁移 V2026.10.18.5 预置,均可在系统设置修改):

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `file.storage.apply.enabled` | `true` | 非管理员申请开关(管理员不受限) |
| `file.storage.apply.users` | `[]` | 非管理员授权申请人 userId JSON 数组 |
| `file.storage.max.quota` | `107374182400`(100GB) | 单用户配额上限 bytes;非法值回退 100GB,并限制在 [1GB,1TB] |

### 3.3 申请与审批

- 提交复用 `POST /api/approval/submit`,body `{"type":"storage","extraData":"{\"quota\":<新总配额bytes>,\"reason\":\"...\"}"}`。
- 后端**忽略客户端 oldQuota**,提交时从库读取当前配额并校验:新配额必须是整数 bytes、**必须大于当前配额**、不得超过 `maxQuota`;理由 trim 后 1-200 字。
- 落单 `extraData` 规范化为 `{"quota":new,"oldQuota":old,"reason":"..."}`,审批详情/执行侧按此解析,`customerId` 对 storage 单无意义(客户端传值被忽略)。
- **同一申请人同时只能有一张在途(reviewing/pending)storage 单**;提交时先锁申请人用户行再数在途单,双击/并发返回明确拒绝。
- 审批流沿用 `biz_approval_config` 配置;迁移预置 `flow_type='storage'`、`flow_name='存储空间申请'`、审批人=`admin` 角色、`sign_mode=ANY`、`sort_order=1`。管理员可在审批流配置页调整审批人/阶段。旧 `biz_approval_flow` 的 storage 行保留,但提交侧优先读新 config。**管理员本人申请时,默认 admin 角色节点解析出的审批人若只有本人会被自审自批闸门拒绝;需先在「审批流程配置」增加其他审批人。**
- 审批通过执行 `SysUserService.applyStorageQuota`:在事务内对申请人行 `SELECT ... FOR UPDATE`,锁内重读当前配额并复核"只升不降 + 上限";若并发调整导致 `new<=current`,抛明确业务异常,审批事务整体回滚(该单保持未决,需驳回后重新申请)。
- 审计:执行成功写 `STORAGE_QUOTA_CHANGED` 结构化事件与 append-only 审计行,含 `userId`(申请人)、`operator`(审批操作人)、`oldQuota`、`newQuota`、`approvalId`;审计失败不阻断配额变更业务。
- 审批通过后前端可通过 `storage/apply-info` 重新读取新配额。

## 4. 分享与下载

- 文件分享(`biz_file_share`):分享给我的(`/file/shared-to-me`)与客户分享提醒(share_to)联动。
- 下载三通道:登录下载 `/download/{id}`、download-token 机制、**匿名公开下载 `/download/public/**`**(系统 logo 等静态资源,SecurityConfig 显式放行——新增公开资源须谨慎,该通道无鉴权)。

## 5. 文件与客户关联(ADR-20260909)

- 文件可关联客户;激励记录的佐证文件槽(安装前/中/后)引用文件中心实体,跨模块引用经归属校验(防"填他人文件 ID 保分",激励 update 只校验本次新引入引用)。
