# 文件中心

> 来源锚点:`FileController`、`FileService`、`BizFileFolder/BizFolderAcl/BizFileShare` 实体、ADR-20260909-customer-file-association。核实日期 2026-09-24。

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

## 2. 文件夹 ACL 模型

- 每文件夹一行 ACL(`biz_folder_acl.perms`),按用户授予读/写语义;未授权文件夹不可见其内容。
- 文件夹树面板管理(增删改),ACL 弹窗单独授权;`file:view` 只读查看 ACL,`file:acl:manage` 才能改。

## 3. 存储配额

- 每人默认 **10 GB**(`sys_user.storage_quota`);`/file/storage` 查询已用/配额。
- **私人文件按差额计扣**:替换文件时 storageUsed = 原已用 − 旧 size + 新 size(最小 0),不重复计费。
- 配额调整走 storage 类审批(见 [approval.md](approval.md) §4)。

## 4. 分享与下载

- 文件分享(`biz_file_share`):分享给我的(`/file/shared-to-me`)与客户分享提醒(share_to)联动。
- 下载三通道:登录下载 `/download/{id}`、download-token 机制、**匿名公开下载 `/download/public/**`**(系统 logo 等静态资源,SecurityConfig 显式放行——新增公开资源须谨慎,该通道无鉴权)。

## 5. 文件与客户关联(ADR-20260909)

- 文件可关联客户;激励记录的佐证文件槽(安装前/中/后)引用文件中心实体,跨模块引用经归属校验(防"填他人文件 ID 保分",激励 update 只校验本次新引入引用)。
