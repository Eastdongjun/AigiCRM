# 文件管理权限体系设计

> 状态：已获用户批准（2026-08-05）
> 目标系统：AigiCRM（aigicrm），后端 Spring Boot 3.2.5 + MyBatis-Plus，前端 Vue3

## 1. 目标与背景

文件管理现有权限为"管理员/所有者"二分：
- 公共文件/文件夹：仅管理员可创建/删除/管理，所有人可看
- 私人文件/文件夹：仅上传者本人可见可管
- 分享：任何人可把文件分享给指定个人（`biz_file_share`）

用户需求：按文件夹自定义授权给角色（公共文件夹的创建/编辑/删除/分享/下载权限），直属上级可见并管理下级文件，私人文件支持覆盖替换。

## 2. 用户已确认的需求（2026-08-05）

| 需求点 | 决策 |
|---|---|
| 授权对象 | 按角色（folder 级 ACL） |
| 授权层级 | 文件夹授权 + 文件继承 |
| 上级权限 | 直属上级可见 + 可管理（**个人上级 `sys_user.manager_id` 优先，未设置时用部门 leader 链**：`dept_id` 沿部门树向上逐级找 `sys_dept.leader_id`） |
| 下载控制 | 文件夹"允许下载"开关（关闭 → 仅预览不可下载） |
| 替换功能 | 覆盖替换（保留文件记录，内容更新，版本号+1） |
| 分享控制 | 有"分享"权限的角色才能分享文件给个人 |
| 不做（YAGNI） | 文件级独立 ACL、版本历史回滚、文件夹分享给个人、移动端 |

## 3. 权限模型

**六个权限项**（文件夹级，角色勾选组合）：

| 权限 | 含义 |
|---|---|
| `view` | 看到文件夹及文件、在线预览 |
| `upload` | 上传文件、创建子文件夹 |
| `edit` | 重命名文件夹、编辑文件、替换文件 |
| `delete` | 删除文件夹/文件 |
| `share` | 把文件分享给个人（现有分享功能） |
| `download` | 下载文件（受文件夹"允许下载"开关约束） |

**权限判定优先级**（高→低，命中即止）：
1. 管理员（`SecurityUtils.isAdmin()`）= 一切权限（现状不变）
2. 上传者本人 = 自己文件/文件夹的完全权限
3. 直属上级 = 下级完全权限（判定见 §6：`manager_id` 优先，未设时沿部门树向上找 `leader_id`）
4. 用户所在角色命中文件夹 ACL 且含该权限 = 通过
5. 公共文件夹且动作为 `view` = 通过（仅查看）
6. 其余 = 拒绝（403）

**继承**：文件权限 = 所属文件夹 ACL；上传者本人始终有自己文件的完全权限（即使文件夹未授权给他）。

## 4. 数据模型

**新增表 `biz_folder_acl`**：

```sql
CREATE TABLE biz_folder_acl (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  folder_id   BIGINT       NOT NULL COMMENT '文件夹ID',
  role_id     BIGINT       NOT NULL COMMENT '角色ID',
  perms       VARCHAR(128) NOT NULL COMMENT '权限列表,逗号分隔:view,upload,edit,delete,share,download',
  created_by  BIGINT       NULL,
  created_at  DATETIME     NULL,
  UNIQUE KEY uk_folder_role (folder_id, role_id)
) COMMENT='文件夹-角色授权表';
```

**改动 `biz_file_folder`**（加 1 字段）：

```sql
ALTER TABLE biz_file_folder
  ADD COLUMN downloadable TINYINT NOT NULL DEFAULT 1 COMMENT '下载开关:1=允许下载,0=仅预览';
```

**改动 `biz_file`**（加 3 字段）：

```sql
ALTER TABLE biz_file
  ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '版本号,覆盖替换+1',
  ADD COLUMN replace_by BIGINT NULL COMMENT '最近替换人',
  ADD COLUMN replaced_at DATETIME NULL COMMENT '最近替换时间';
```

## 5. 接口设计

**新增接口**（需文件夹"权限设置"权或管理员；权限设置权=对目标文件夹有 `edit` 权限）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/file/folders/{id}/acl` | 查文件夹已授权角色列表（roleId+perms） |
| POST | `/api/file/folders/{id}/acl` | 添加/更新角色授权 `{roleId, perms[]}` |
| DELETE | `/api/file/folders/{id}/acl/{roleId}` | 移除角色授权 |
| PUT | `/api/file/folders/{id}/download` | 切换下载开关 `{downloadable}` |
| POST | `/api/file/{id}/replace` | 覆盖替换文件（multipart 新文件 + 原 ID） |

**改动现有接口**（权限校验统一走 `FolderPermissionService`）：

| 接口 | 校验动作 |
|---|---|
| `POST /api/file/upload` | `upload`（本人/上级/ACL） |
| `POST /api/file/folders` | `upload`（建子文件夹） |
| `PUT /api/file/folders/{id}` | `edit`（重命名） |
| `DELETE /api/file/folders/{id}` | `delete` |
| `DELETE /api/file/{id}` | `delete` |
| `GET /api/file/{id}/download` | `download` + 下载开关 |
| `POST /api/file/{id}/share` | `share` |
| `GET /api/file/folders/tree`、`GET /api/file/page` | 可见性过滤（本人/上级/ACL/公共） |

## 6. 权限判定服务

`FolderPermissionService`（统一入口，供 FileService 与新增接口调用）：

```
boolean check(folderId, action, userId):
  admin → true
  folder.ownerId == userId → true
  isManagerChain(userId, folder.ownerId) → true        // 个人上级优先 + 部门leader链兜底
  folder_acl 中存在 (folderId, 用户任一角色) 且 perms 含 action → true
  action == view 且 folder.folderType == public → true
  else → false
```

- `isManagerChain(upper, lower)`：
  1. **个人上级优先**：从 lower 沿 `sys_user.manager_id` 向上递归（限 5 层），任一节点==upper 即 true；
  2. **部门 leader 链兜底**：若 lower 的 `manager_id` 为空，从 lower 的 `dept_id` 沿部门树（parent_id）向上逐级，任一级部门 `leader_id`==upper 即 true（限 5 层）；
  3. 管理员直接 true。
- 数据前提：当前生产库 `manager_id` 全空（需在人员管理中逐步维护个人上级）；`dept.leader_id` 为部门负责人（销售部门由销售总监统管，行政部各有负责人，2026-08-05 已修正行政用户部门归属）。
- 文件操作先取 `file.folderId` 再走 `check`；无文件夹的文件（folderId 空）按 uploaderId 归属判定（本人/上级/管理员）。

## 7. 前端交互

- **文件管理页**：文件夹行操作区（hover 菜单或右键）加「权限设置」；弹窗内：
  - 角色下拉 + 六个权限项勾选（角色多选），保存到 ACL
  - 已授权角色列表，可移除
- **文件夹属性区**：「允许下载」开关（切换即调 PUT 接口）
- **文件操作菜单**：加「替换」入口 → 选新文件 → 调 POST replace（成功后刷新列表，显示版本号）
- **分享按钮**：仅当用户对所在文件夹有 `share` 权限时显示
- **下载按钮**：仅当有 `download` 权限且文件夹 `downloadable=1` 时可用；下载被拒时提示"该文件夹禁止下载"

## 8. 边界与兼容

- **公共文件夹默认**：管理员/创建者创建后默认全员可查看；未授权角色保持仅查看。比现状（仅管理员可管理）更灵活，创建/编辑/删除等操作必须 ACL 授权。
- **私人文件夹**：默认仅本人；本人可在权限设置里授给角色。
- **删除级联**：删除文件夹时同步删除其 `biz_folder_acl` 记录（防孤儿）。
- **替换文件**：新文件覆盖 OSS 对象，更新 `file_size`/`oss_key`，`version+1`、`replace_by`/`replaced_at` 记录；私人文件替换不重复扣存储空间（扣除旧 size、计入新 size 的差额）。
- **下载开关**：关闭时非管理员下载一律拒绝（预览不受影响）。
- **现有分享兼容**：`biz_file_share` 保留；被分享者仍可看；分享动作需 `share` 权限。
- **激励文件**（incentive scope）：不走文件夹 ACL，保持现状（上传者本人/管理员）。

## 9. 测试要点

- 权限优先级矩阵：管理员/本人/上级/ACL 角色/仅查看/无权限 六类 × 六动作
- 上级链：manager_id 直系/间接/部门 leader 兼任；无上级时回退
- 继承：文件随文件夹 ACL；文件夹重命名/移动后权限不丢
- 下载开关：开关关闭后下载 403、预览 200；替换文件不破坏开关状态
- 替换：版本号递增、存储差额、OSS 覆盖、被分享者看到新内容
- 兼容回归：激励文件、现有分享、公共/私人文件旧数据
- 权限设置接口：非管理员/无 edit 权访问 ACL 接口 → 403

## 10. 实施顺序（预估）

1. 数据库迁移（新表 + 2 个 ALTER）
2. `FolderPermissionService` + 单测
3. FileService 现有接口接入校验
4. ACL/下载/替换接口 + 单测
5. 前端权限设置弹窗、下载开关、替换入口、分享/下载按钮条件
6. 测试环境部署验证 → 生产部署

## 11. 风险

- 权限校验改动触及所有文件操作，需完整回归（现有 20 页全测）
- 公共文件夹"默认仅查看"改变现有行为（现全员可看不变，管理操作从"仅管理员"变为"ACL 授权"）——需向用户确认过渡策略
- 上级链递归有性能上限（限 5 层，且仅登录用户触发）
- **数据前提**：`manager_id` 当前全空，个人上级需逐步维护（人员管理增加字段）；`dept.leader_id` 语义为部门负责人（销售总监统管销售部门），2026-08-05 已修正 3 名行政用户部门归属（毛颖娜→7、李艳梅→5、肖赟→38，回滚：NM100→6/TY100→4/TY200→44）
- 行政数据范围变化：修正后行政用户 `dept_and_children` 范围=各自行政部（原=销售部），客户业务数据可见性收窄——已告知用户并确认
