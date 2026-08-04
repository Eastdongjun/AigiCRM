# 文件管理权限体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 文件管理从"管理员/所有者二分"升级为按文件夹的角色授权（ACL），含直属上级可见可管理、下载开关、覆盖替换、分享权限控制。

**Architecture:** 新增 `biz_folder_acl` 授权表 + 统一权限判定服务 `FolderPermissionService`（优先级：管理员 > 本人 > 直属上级 > ACL 角色 > 公共仅查看）；现有 FileService 全部操作接入校验；前端按权限动态控制按钮。设计文档：`docs/superpowers/specs/2026-08-05-file-permission-design.md`。

**Tech Stack:** Spring Boot 3.2.5 / MyBatis-Plus / MySQL 8 / Vue3 + Element Plus；测试 Mockito + JUnit5。

## Global Constraints

- 设计文档：`docs/superpowers/specs/2026-08-05-file-permission-design.md`（已获用户批准，权限模型/优先级/边界以此为准）
- 权限判定优先级固定：管理员 → 本人 → 直属上级（manager_id 优先，部门 leader 链兜底，限 5 层）→ 文件夹 ACL 角色 → 公共仅查看 → 拒绝
- 六个权限常量：`view, upload, edit, delete, share, download`
- 写操作/接口路径遵循现有风格（`Result<T>`、`@PreAuthorize` 保持现状、Service 内校验）
- 激励文件（incentive scope）不接入文件夹 ACL，保持现状
- 数据库同步方向：生产 → 测试（迁移脚本先在生产执行验证，再同步测试环境）
- 编译环境：`MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn ...`（在 ycmn-backend 目录）

---

### Task 1: 数据库迁移（新表 + 3 个字段）

**Files:**
- Create: `sql/migration-20260805-file-acl.sql`
- Modify: `sql/init.sql`（末尾追加同样 DDL，保持全新初始化一致）

**Interfaces:**
- Produces: `biz_folder_acl` 表（folder_id, role_id, perms, created_by, created_at, 唯一索引 uk_folder_role）；`biz_file_folder.downloadable`；`biz_file.version/replace_by/replaced_at`——后续所有任务依赖

- [ ] **Step 1: 编写迁移脚本**

```sql
-- 文件权限体系：文件夹-角色授权表
CREATE TABLE IF NOT EXISTS biz_folder_acl (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  folder_id   BIGINT       NOT NULL COMMENT '文件夹ID',
  role_id     BIGINT       NOT NULL COMMENT '角色ID',
  perms       VARCHAR(128) NOT NULL COMMENT '权限列表,逗号分隔:view,upload,edit,delete,share,download',
  created_by  BIGINT       NULL,
  created_at  DATETIME     NULL,
  UNIQUE KEY uk_folder_role (folder_id, role_id)
) COMMENT='文件夹-角色授权表';

ALTER TABLE biz_file_folder ADD COLUMN IF NOT EXISTS downloadable TINYINT NOT NULL DEFAULT 1 COMMENT '下载开关:1=允许下载,0=仅预览';
ALTER TABLE biz_file ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1 COMMENT '版本号,覆盖替换+1';
ALTER TABLE biz_file ADD COLUMN IF NOT EXISTS replace_by BIGINT NULL COMMENT '最近替换人';
ALTER TABLE biz_file ADD COLUMN IF NOT EXISTS replaced_at DATETIME NULL COMMENT '最近替换时间';
```

- [ ] **Step 2: 在生产库执行迁移**

```bash
export SSHPASS='redacted-ssh-password'
sshpass -e ssh root@39.99.35.218 "docker exec -i ycmn-mysql mysql -uroot -p<redacted> ycmn < sql/migration-20260805-file-acl.sql"
```
Expected: 无报错退出 0

- [ ] **Step 3: 验证表结构**

```bash
sshpass -e ssh root@39.99.35.218 "docker exec ycmn-mysql mysql -uroot -p<redacted> ycmn -N -e \"SHOW COLUMNS FROM biz_folder_acl; SHOW COLUMNS FROM biz_file_folder LIKE 'downloadable'; SHOW COLUMNS FROM biz_file LIKE 'version';\""
```
Expected: biz_folder_acl 6 列 + downloadable + version 均存在

- [ ] **Step 4: 追加到 init.sql 并提交**

```bash
cat sql/migration-20260805-file-acl.sql >> sql/init.sql
git add sql/init.sql sql/migration-20260805-file-acl.sql
git commit -m "feat: 文件权限体系数据库迁移（biz_folder_acl + 下载开关 + 版本字段）"
```

---

### Task 2: 实体与 Mapper

**Files:**
- Create: `ycmn-backend/src/main/java/com/ycmn/module/file/entity/BizFolderAcl.java`
- Create: `ycmn-backend/src/main/java/com/ycmn/module/file/mapper/BizFolderAclMapper.java`
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/file/entity/BizFileFolder.java`（+downloadable）
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/file/entity/BizFile.java`（+version/replaceBy/replacedAt）

**Interfaces:**
- Consumes: Task 1 的表结构
- Produces: `BizFolderAcl`（id/folderId/roleId/perms/createdBy/createdAt，@TableName("biz_folder_acl")）；`BizFolderAclMapper extends BaseMapper<BizFolderAcl>`；`BizFileFolder.downloadable`；`BizFile.version/replaceBy/replacedAt` getter/setter

- [ ] **Step 1: 创建 BizFolderAcl 实体**

```java
package com.ycmn.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_folder_acl")
public class BizFolderAcl {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long folderId;
    private Long roleId;
    private String perms;
    private Long createdBy;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 创建 Mapper**

```java
package com.ycmn.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ycmn.module.file.entity.BizFolderAcl;

public interface BizFolderAclMapper extends BaseMapper<BizFolderAcl> {
}
```

- [ ] **Step 3: BizFileFolder 加 downloadable 字段**

```java
    private Integer sortOrder;
    private Integer downloadable;   // 新增：下载开关 1=允许 0=仅预览（默认1）
```

- [ ] **Step 4: BizFile 加版本字段**

```java
    private Integer version;        // 新增：版本号，覆盖替换+1，默认1
    private Long replaceBy;         // 新增：最近替换人
    private LocalDateTime replacedAt; // 新增：最近替换时间
```

- [ ] **Step 5: 编译 + 提交**

```bash
cd ycmn-backend && MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn -q compile
git add ycmn-backend/src/main/java/com/ycmn/module/file/entity/BizFolderAcl.java ycmn-backend/src/main/java/com/ycmn/module/file/entity/BizFileFolder.java ycmn-backend/src/main/java/com/ycmn/module/file/entity/BizFile.java ycmn-backend/src/main/java/com/ycmn/module/file/mapper/BizFolderAclMapper.java
git commit -m "feat: 文件权限实体与Mapper（BizFolderAcl + 下载开关 + 版本字段）"
```

---

### Task 3: FolderPermissionService（核心权限判定）

**Files:**
- Create: `ycmn-backend/src/main/java/com/ycmn/module/file/service/FolderPermissionService.java`
- Create: `ycmn-backend/src/test/java/com/ycmn/module/file/service/FolderPermissionServiceTest.java`

**Interfaces:**
- Consumes: Task 2 实体/Mapper；`SysUserMapper`、`SysDeptMapper`、`BizFolderAclMapper`
- Produces:
  - `public static final String P_VIEW="view", P_UPLOAD="upload", P_EDIT="edit", P_DELETE="delete", P_SHARE="share", P_DOWNLOAD="download"`
  - `public boolean check(Long folderId, String action, Long userId)` — 权限判定（含管理员/本人/上级/ACL/公共查看）
  - `public boolean checkFile(Long fileId, String action, Long userId)` — 按文件取 folderId 后判定；folderId 为空时按上传者归属（本人/上级/管理员）
  - `public boolean isManagerChain(Long upperId, Long lowerId)` — 个人 manager_id 递归(5层)优先，部门 leader 链兜底
  - `public List<String> permsFor(Long folderId, Long userId)` — 当前用户对文件夹的权限列表（前端按钮控制用）

- [ ] **Step 1: 写失败测试（权限矩阵）**

```java
package com.ycmn.module.file.service;

import com.ycmn.module.file.entity.BizFolderAcl;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.mapper.BizFolderAclMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FolderPermissionServiceTest {

    private final BizFolderAclMapper aclMapper = mock(BizFolderAclMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final FolderPermissionService svc = new FolderPermissionService(aclMapper, userMapper, deptMapper);

    @Test
    void admin_hasAllPermissions() {
        SysUser admin = new SysUser(); admin.setId(1L); admin.setManagerId(null);
        when(userMapper.selectById(1L)).thenReturn(admin);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            assertTrue(svc.check(100L, FolderPermissionService.P_DELETE, 1L));
        }
    }

    @Test
    void owner_hasFullPermissions() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(42L);
        SysUser owner = new SysUser(); owner.setId(42L);
        when(userMapper.selectById(42L)).thenReturn(owner);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.check(100L, FolderPermissionService.P_EDIT, 42L)); // 用反射注入 folder 查询或调整签名
        }
    }

    @Test
    void managerChain_viaManagerId() {
        // lower=42 的 manager_id=7，upper=7 → true
        SysUser lower = new SysUser(); lower.setId(42L); lower.setManagerId(7L);
        SysUser upper = new SysUser(); upper.setId(7L);
        when(userMapper.selectById(42L)).thenReturn(lower);
        when(userMapper.selectById(7L)).thenReturn(upper);
        assertTrue(svc.isManagerChain(7L, 42L));
    }

    @Test
    void managerChain_viaDeptLeader() {
        // lower=42 manager_id 为空，dept=9，dept9.leader=7 → true
        SysUser lower = new SysUser(); lower.setId(42L); lower.setManagerId(null); lower.setDeptId(9L);
        SysDept dept9 = new SysDept(); dept9.setId(9L); dept9.setParentId(2L); dept9.setLeaderId(7L);
        SysDept dept2 = new SysDept(); dept2.setId(2L); dept2.setParentId(0L);
        when(userMapper.selectById(42L)).thenReturn(lower);
        when(deptMapper.selectById(9L)).thenReturn(dept9);
        when(deptMapper.selectById(2L)).thenReturn(dept2);
        assertTrue(svc.isManagerChain(7L, 42L));
    }

    @Test
    void aclRole_grantsConfiguredPermission() {
        BizFileFolder folder = new BizFileFolder(); folder.setId(100L); folder.setOwnerId(99L); folder.setFolderType("private");
        BizFolderAcl acl = new BizFolderAcl();
        acl.setFolderId(100L); acl.setRoleId(3L); acl.setPerms("view,upload");
        when(userMapper.selectById(42L)).thenReturn(aUser(42L));
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            // 42 用户角色=3，且当前用户即 folder.ownerId 判断为假时 → ACL 生效
            assertTrue(svc.check(100L, FolderPermissionService.P_UPLOAD, 42L));
        }
    }
    // 辅助: 构造带角色 id 的用户（实现时按 SysUser/角色解析方式补全）

    private SysUser aUser(Long id) { SysUser u = new SysUser(); u.setId(id); return u; }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cd ycmn-backend && MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn test -Dtest=FolderPermissionServiceTest
```
Expected: FAIL（FolderPermissionService 不存在）

- [ ] **Step 3: 实现 FolderPermissionService**

```java
package com.ycmn.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.entity.BizFolderAcl;
import com.ycmn.module.file.mapper.BizFileFolderMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.file.mapper.BizFolderAclMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FolderPermissionService {

    public static final String P_VIEW = "view";
    public static final String P_UPLOAD = "upload";
    public static final String P_EDIT = "edit";
    public static final String P_DELETE = "delete";
    public static final String P_SHARE = "share";
    public static final String P_DOWNLOAD = "download";
    private static final int MAX_HOP = 5;

    private final BizFolderAclMapper aclMapper;
    private final BizFileFolderMapper folderMapper;
    private final BizFileMapper fileMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

    /** 文件夹权限判定：管理员>本人>直属上级>ACL角色>公共仅查看 */
    public boolean check(Long folderId, String action, Long userId) {
        if (folderId == null) return false;
        if (SecurityUtils.isAdmin()) return true;
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return false;
        if (Objects.equals(folder.getOwnerId(), userId)) return true;
        if (isManagerChain(userId, folder.getOwnerId())) return true;
        if (aclGranted(folderId, userId, action)) return true;
        return P_VIEW.equals(action) && "public".equals(folder.getFolderType());
    }

    /** 文件权限判定：取 folderId 判定；无文件夹按上传者归属 */
    public boolean checkFile(Long fileId, String action, Long userId) {
        if (SecurityUtils.isAdmin()) return true;
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) return false;
        if (file.getFolderId() != null) return check(file.getFolderId(), action, userId);
        if (Objects.equals(file.getUploaderId(), userId)) return true;
        return isManagerChain(userId, file.getUploaderId());
    }

    /** 个人上级(manager_id)优先，部门 leader 链兜底，各限 5 层 */
    public boolean isManagerChain(Long upperId, Long lowerId) {
        if (upperId == null || lowerId == null) return false;
        SysUser cur = userMapper.selectById(lowerId);
        for (int i = 0; i < MAX_HOP && cur != null; i++) {
            if (cur.getManagerId() != null) {
                if (cur.getManagerId().equals(upperId)) return true;
                cur = userMapper.selectById(cur.getManagerId());
            } else break;
        }
        SysUser lower = userMapper.selectById(lowerId);
        if (lower != null && lower.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(lower.getDeptId());
            for (int i = 0; i < MAX_HOP && dept != null; i++) {
                if (dept.getLeaderId() != null && dept.getLeaderId().equals(upperId)) return true;
                if (dept.getParentId() == null || dept.getParentId() == 0) break;
                dept = deptMapper.selectById(dept.getParentId());
            }
        }
        return false;
    }

    /** 当前用户对文件夹的权限列表（前端按钮控制） */
    public List<String> permsFor(Long folderId, Long userId) {
        if (folderId == null) return List.of();
        if (SecurityUtils.isAdmin()) return List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD);
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) return List.of();
        if (Objects.equals(folder.getOwnerId(), userId) || isManagerChain(userId, folder.getOwnerId())) {
            return List.of(P_VIEW, P_UPLOAD, P_EDIT, P_DELETE, P_SHARE, P_DOWNLOAD);
        }
        List<String> result = new ArrayList<>();
        for (BizFolderAcl acl : aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId))) {
            if (userHasRole(userId, acl.getRoleId())) {
                for (String p : acl.getPerms().split(",")) if (!result.contains(p)) result.add(p);
            }
        }
        if (result.isEmpty() && "public".equals(folder.getFolderType())) result.add(P_VIEW);
        return result;
    }

    private boolean aclGranted(Long folderId, Long userId, String action) {
        for (BizFolderAcl acl : aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>().eq(BizFolderAcl::getFolderId, folderId))) {
            if (userHasRole(userId, acl.getRoleId())) {
                for (String p : acl.getPerms().split(",")) if (p.equals(action)) return true;
            }
        }
        return false;
    }

    private boolean userHasRole(Long userId, Long roleId) {
        // 用户角色通过 SecurityUtils 当前用户或 userMapper 查询（实现时按项目 SysUserRole 关系补齐）
        return SecurityUtils.getCurrentUserRoles().contains(roleId);
    }
}
```

> 说明：`userHasRole` 依赖 `SecurityUtils` 暴露当前用户角色 ID 集合（若不存在则新增 `getCurrentUserRoles()` 或在 Service 内注入 `SysUserRoleMapper` 查询——实现时按项目实际选择，测试用 MockedStatic 覆盖）。

- [ ] **Step 4: 运行测试至通过**

```bash
cd ycmn-backend && MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn test -Dtest=FolderPermissionServiceTest
```
Expected: 全部 PASS

- [ ] **Step 5: 提交**

```bash
git add ycmn-backend/src/main/java/com/ycmn/module/file/service/FolderPermissionService.java ycmn-backend/src/test/java/com/ycmn/module/file/service/FolderPermissionServiceTest.java
git commit -m "feat: 文件夹权限判定服务 FolderPermissionService（管理员/本人/上级/ACL/公共）"
```

---

### Task 4: FileService 现有接口接入权限校验

**Files:**
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/file/service/FileService.java`（upload/createFolder/renameFolder/deleteFolder/deleteFile/downloadById/shareFile）
- Modify: `ycmn-backend/src/test/java/com/ycmn/module/file/service/FileServicePermissionTest.java`（新建）

**Interfaces:**
- Consumes: Task 3 `FolderPermissionService.check/checkFile/permsFor`
- Produces: 现有接口行为变化——无权限时抛 `BusinessException(403, "没有访问权限")`；`getFolderTree/pageFiles` 增加可见性过滤

- [ ] **Step 1: 注入 FolderPermissionService 并接入上传/文件夹创建**

```java
// FileService 构造器或 @RequiredArgsConstructor 增加字段
private final FolderPermissionService folderPermissionService;

// upload(): folderId 非空时校验
if (folderId != null && !folderPermissionService.check(folderId, FolderPermissionService.P_UPLOAD, userId)) {
    throw new BusinessException("没有上传权限");
}
// scope=public 且无文件夹 → 保持现状（管理员）

// createFolder(): 有 parentId 时校验父文件夹
if (folder.getParentId() != null && !folderPermissionService.check(folder.getParentId(), FolderPermissionService.P_UPLOAD, userId)) {
    throw new BusinessException("没有创建权限");
}
```

- [ ] **Step 2: 重命名/删除文件夹接入**

```java
// renameFolder()/deleteFolder(): 在既有 checkFolderManagePermission 之前增加
if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_EDIT, userId)) throw new BusinessException("没有编辑权限"); // rename
if (!folderPermissionService.check(folder.getId(), FolderPermissionService.P_DELETE, userId)) throw new BusinessException("没有删除权限"); // delete
// 注意：checkFolderManagePermission 保留（管理员/本人的既有语义，与 check 优先级一致，不冲突）
```

- [ ] **Step 3: 删除文件/下载/分享接入**

```java
// deleteFile(): file.getFolderId()!=null → checkFile(fileId, DELETE)；无文件夹 → 保持现状（uploaderId 归属）
// downloadById(): checkFile(fileId, DOWNLOAD) + 文件夹 downloadable 开关
BizFile file = fileMapper.selectById(fileId);
if (file.getFolderId() != null) {
    if (!folderPermissionService.check(file.getFolderId(), FolderPermissionService.P_DOWNLOAD, userId)) { response.setStatus(403); return; }
    BizFileFolder folder = folderMapper.selectById(file.getFolderId());
    if (folder != null && (folder.getDownloadable() == null || folder.getDownloadable() != 1)) { response.setStatus(403); return; }
}
// shareFile(): checkFile(fileId, SHARE)
```

- [ ] **Step 4: 列表/树可见性过滤**

```java
// pageFiles(): 查询条件追加——私有 scope 保持现状（ownerId=自己）；public scope 的文件夹树/folderId 查询
// 过滤规则：文件夹对用户可见 = 本人创建 / 上级 / ACL 有 view / public
// 实现：先查可见 folderId 集合（permsFor 含 view 或 ownerId==userId 或 isManagerChain 或 folderType=public），
// 再在文件查询的 folderId IN 条件中加入；列表文件同样按所在文件夹可见性过滤
// getFolderTree(): 同规则过滤文件夹树（仅返回可见节点，含其 ownerId 文件计数）
```

- [ ] **Step 5: 写权限回归测试（FileServicePermissionTest）**

```java
// 用例：无权限用户 upload → BusinessException；无权限 deleteFile → BusinessException；
// 下载开关关闭 → download 403；ACL 授权角色 upload 成功（mock FolderPermissionService.check 返回 true）
// 用 Mockito 构造 FileService（构造器注入 mock FolderPermissionService 与 mapper）
```

- [ ] **Step 6: 运行全量测试**

```bash
cd ycmn-backend && MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn test
```
Expected: 全量 PASS（原 38 + 新增）

- [ ] **Step 7: 提交**

```bash
git add ycmn-backend/src/main/java/com/ycmn/module/file/service/FileService.java ycmn-backend/src/test/java/com/ycmn/module/file/service/FileServicePermissionTest.java
git commit -m "feat: 文件操作接入文件夹权限校验（上传/创建/重命名/删除/下载/分享）"
```

---

### Task 5: ACL 与下载开关接口（新增）

**Files:**
- Create: `ycmn-backend/src/main/java/com/ycmn/module/file/controller/FolderAclController.java`
- Create: `ycmn-backend/src/main/java/com/ycmn/module/file/service/FolderAclService.java`
- Create: `ycmn-backend/src/test/java/com/ycmn/module/file/service/FolderAclServiceTest.java`

**Interfaces:**
- Consumes: Task 2 Mapper、Task 3 `FolderPermissionService.check(folderId, P_EDIT)`
- Produces:
  - `GET /api/file/folders/{id}/acl` → `Result<List<Map<String,Object>>>`（[{roleId, roleName, perms[]}]）
  - `POST /api/file/folders/{id}/acl` body `{roleId, perms: []}` → `Result<Void>`
  - `DELETE /api/file/folders/{id}/acl/{roleId}` → `Result<Void>`
  - `PUT /api/file/folders/{id}/download` body `{downloadable: 0|1}` → `Result<Void>`
  - `GET /api/file/folders/{id}/permissions` → `Result<Map<String,Object>>`（{perms: [], downloadable: 0|1}，前端按钮控制）

- [ ] **Step 1: 写失败测试**

```java
// FolderAclServiceTest:
// - 非管理员且无 P_EDIT 权限（mock check 返回 false）操作 ACL → BusinessException
// - saveAcl: 合法 perms 子集校验（只允许 6 个常量）；重复保存走 upsert（先删后插或 update）
// - removeAcl: 删除指定 role 授权
// - setDownloadable: 更新 folder.downloadable
// - permissions: 返回当前用户 perms + downloadable
```

- [ ] **Step 2: 实现 FolderAclService**

```java
@Service
@RequiredArgsConstructor
public class FolderAclService {
    private static final Set<String> ALLOWED = Set.of(
        FolderPermissionService.P_VIEW, FolderPermissionService.P_UPLOAD,
        FolderPermissionService.P_EDIT, FolderPermissionService.P_DELETE,
        FolderPermissionService.P_SHARE, FolderPermissionService.P_DOWNLOAD);

    private final BizFolderAclMapper aclMapper;
    private final BizFileFolderMapper folderMapper;
    private final SysRoleMapper roleMapper;
    private final FolderPermissionService permissionService;

    public List<Map<String, Object>> listAcl(Long folderId) {
        requireEdit(folderId);
        return aclMapper.selectList(new LambdaQueryWrapper<BizFolderAcl>()
                .eq(BizFolderAcl::getFolderId, folderId)).stream().map(acl -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleId", acl.getRoleId());
            SysRole role = roleMapper.selectById(acl.getRoleId());
            row.put("roleName", role != null ? role.getRoleName() : "未知角色");
            row.put("perms", Arrays.asList(acl.getPerms().split(",")));
            return row;
        }).collect(Collectors.toList());
    }

    public void saveAcl(Long folderId, Long roleId, List<String> perms) {
        requireEdit(folderId);
        if (roleId == null) throw new BusinessException("角色不能为空");
        if (perms == null || perms.isEmpty()) throw new BusinessException("权限不能为空");
        for (String p : perms) if (!ALLOWED.contains(p)) throw new BusinessException("非法权限项: " + p);
        // upsert
        BizFolderAcl existing = aclMapper.selectOne(new LambdaQueryWrapper<BizFolderAcl>()
                .eq(BizFolderAcl::getFolderId, folderId).eq(BizFolderAcl::getRoleId, roleId));
        String joined = String.join(",", perms);
        if (existing != null) { existing.setPerms(joined); aclMapper.updateById(existing); }
        else {
            BizFolderAcl acl = new BizFolderAcl();
            acl.setFolderId(folderId); acl.setRoleId(roleId); acl.setPerms(joined);
            acl.setCreatedBy(SecurityUtils.getCurrentUserId()); acl.setCreatedAt(LocalDateTime.now());
            aclMapper.insert(acl);
        }
    }

    public void removeAcl(Long folderId, Long roleId) {
        requireEdit(folderId);
        aclMapper.delete(new LambdaQueryWrapper<BizFolderAcl>()
                .eq(BizFolderAcl::getFolderId, folderId).eq(BizFolderAcl::getRoleId, roleId));
    }

    public void setDownloadable(Long folderId, Integer downloadable) {
        requireEdit(folderId);
        if (downloadable == null || (downloadable != 0 && downloadable != 1)) throw new BusinessException("非法开关值");
        BizFileFolder folder = folderMapper.selectById(folderId);
        if (folder == null) throw new BusinessException("文件夹不存在");
        folder.setDownloadable(downloadable);
        folderMapper.updateById(folder);
    }

    public Map<String, Object> permissions(Long folderId, Long userId) {
        BizFileFolder folder = folderMapper.selectById(folderId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("perms", folder != null ? permissionService.permsFor(folderId, userId) : List.of());
        row.put("downloadable", folder != null && folder.getDownloadable() != null ? folder.getDownloadable() : 1);
        return row;
    }

    private void requireEdit(Long folderId) {
        if (folderId == null) throw new BusinessException("文件夹不能为空");
        if (!permissionService.check(folderId, FolderPermissionService.P_EDIT, SecurityUtils.getCurrentUserId())) {
            throw new BusinessException(403, "没有访问权限，请联系管理员授权");
        }
    }
}
```

- [ ] **Step 3: 实现 FolderAclController**

```java
@RestController
@RequestMapping("/api/file/folders")
@RequiredArgsConstructor
public class FolderAclController {
    private final FolderAclService aclService;

    @GetMapping("/{id}/acl")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> listAcl(@PathVariable Long id) { return Result.ok(aclService.listAcl(id)); }

    @PostMapping("/{id}/acl")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> saveAcl(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        aclService.saveAcl(id, Long.valueOf(body.get("roleId").toString()),
                ((List<?>) body.get("perms")).stream().map(String::valueOf).collect(Collectors.toList()));
        return Result.ok();
    }

    @DeleteMapping("/{id}/acl/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> removeAcl(@PathVariable Long id, @PathVariable Long roleId) { aclService.removeAcl(id, roleId); return Result.ok(); }

    @PutMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> setDownloadable(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        aclService.setDownloadable(id, body.get("downloadable")); return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> permissions(@PathVariable Long id) {
        return Result.ok(aclService.permissions(id, SecurityUtils.getCurrentUserId()));
    }
}
```

- [ ] **Step 4: 测试通过 + 全量回归**

```bash
cd ycmn-backend && MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn test
```
Expected: 全量 PASS

- [ ] **Step 5: 提交**

```bash
git add ycmn-backend/src/main/java/com/ycmn/module/file/controller/FolderAclController.java ycmn-backend/src/main/java/com/ycmn/module/file/service/FolderAclService.java ycmn-backend/src/test/java/com/ycmn/module/file/service/FolderAclServiceTest.java
git commit -m "feat: 文件夹ACL与下载开关接口（设置/查询/权限视图）"
```

---

### Task 6: 文件覆盖替换接口

**Files:**
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/file/controller/FileController.java`
- Modify: `ycmn-backend/src/main/java/com/ycmn/module/file/service/FileService.java`
- Modify: `ycmn-backend/src/test/java/com/ycmn/module/file/service/FileServicePermissionTest.java`（追加替换用例）

**Interfaces:**
- Consumes: Task 3 `checkFile(fileId, P_EDIT)`；现有 upload 的存储逻辑（OSS/本地）
- Produces: `POST /api/file/{id}/replace`（multipart `file`）→ `Result<BizFile>`（版本+1 后的文件）

- [ ] **Step 1: 写失败测试（替换）**

```java
// - 无 P_EDIT 权限替换 → BusinessException
// - 替换成功：version 2、replace_by=当前用户、replaced_at 非空、oss_key/file_size 更新
// - 私人文件存储差额：storageUsed = 原 - 旧size + 新size
// - 文件不存在 → BusinessException
```

- [ ] **Step 2: 实现 replaceFile**

```java
// FileService 新增
public BizFile replaceFile(Long fileId, MultipartFile newFile) {
    BizFile file = fileMapper.selectById(fileId);
    if (file == null) throw new BusinessException("文件不存在");
    Long userId = SecurityUtils.getCurrentUserId();
    if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_EDIT, userId)) {
        throw new BusinessException(403, "没有访问权限，请联系管理员授权");
    }
    String newKey = uploadStorage(newFile);   // 复用现有 upload 的 OSS/本地存储逻辑（先存后删旧）
    String oldKey = file.getOssKey();
    long oldSize = file.getFileSize() != null ? file.getFileSize() : 0L;
    file.setOssKey(newKey);
    file.setOssUrl(newFile.getOriginalFilename() != null ? "/api/file/download/" + newKey : file.getOssUrl());
    file.setFileSize(newFile.getSize());
    file.setFileType(newFile.getContentType());
    file.setVersion((file.getVersion() == null ? 0 : file.getVersion()) + 1);
    file.setReplaceBy(userId);
    file.setReplacedAt(LocalDateTime.now());
    fileMapper.updateById(file);
    deleteStorage(oldKey);                    // 复用现有删除物理文件逻辑
    if ("private".equals(file.getFileScope())) {  // 存储差额
        SysUser user = userMapper.selectById(file.getUploaderId());
        if (user != null) {
            long delta = newFile.getSize() - oldSize;
            user.setStorageUsed(Math.max(0, user.getStorageUsed() + delta));
            userMapper.updateById(user);
        }
    }
    return file;
}
```

- [ ] **Step 3: Controller 追加接口**

```java
@PostMapping("/{id}/replace")
@PreAuthorize("isAuthenticated()")
public Result<BizFile> replaceFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
    return Result.ok(fileService.replaceFile(id, file));
}
```

- [ ] **Step 4: 测试通过 + 全量回归 + 提交**

```bash
cd ycmn-backend && MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn test
git add ycmn-backend/src/main/java/com/ycmn/module/file/controller/FileController.java ycmn-backend/src/main/java/com/ycmn/module/file/service/FileService.java ycmn-backend/src/test/java/com/ycmn/module/file/service/FileServicePermissionTest.java
git commit -m "feat: 文件覆盖替换接口（版本+1/存储差额/权限校验）"
```

---

### Task 7: 前端权限设置弹窗 + 下载开关 + 替换 + 按钮条件

**Files:**
- Modify: `ycmn-ui/src/views/file/index.vue`
- Modify: `ycmn-ui/src/api/file.js`（新增接口封装）

**Interfaces:**
- Consumes: Task 5/6 接口
- Produces: 权限设置弹窗（角色+权限勾选）、下载开关、替换入口、分享/下载/上传/编辑/删除按钮按 `permissions` 接口动态显隐

- [ ] **Step 1: api/file.js 增加封装**

```js
export const getFolderAcl = (id) => request.get(`/file/folders/${id}/acl`)
export const saveFolderAcl = (id, data) => request.post(`/file/folders/${id}/acl`, data)
export const removeFolderAcl = (id, roleId) => request.delete(`/file/folders/${id}/acl/${roleId}`)
export const setFolderDownloadable = (id, downloadable) => request.put(`/file/folders/${id}/download`, { downloadable })
export const getFolderPermissions = (id) => request.get(`/file/folders/${id}/permissions`)
export const replaceFile = (id, formData) => request.post(`/file/${id}/replace`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
```

- [ ] **Step 2: 文件夹权限状态管理**

```vue
// index.vue data 增加
const folderPerms = ref([])        // 当前选中文件夹的权限列表
const folderDownloadable = ref(1)  // 当前文件夹下载开关
// switchFolder 时调用 getFolderPermissions(id) 填充
// 按钮显隐:
//   上传按钮 v-if="activeTab!=='incentive'&&activeTab!=='shared_to_me' && folderPerms.includes('upload')"
//   新建文件夹按钮 v-if="... && folderPerms.includes('upload')"（公共Tab不再仅管理员限制）
//   重命名/删除文件夹按钮 v-if="folderPerms.includes('edit'/'delete')"
//   文件操作: 删除 v-if="folderPerms.includes('delete')", 替换 v-if="folderPerms.includes('edit')",
//   分享 v-if="folderPerms.includes('share')", 下载 v-if="folderPerms.includes('download') && folderDownloadable===1"
```

- [ ] **Step 3: 权限设置弹窗**

```vue
// el-dialog: 当前文件夹名 + 角色列表（GET /system/roles 现有接口）+ 6 权限勾选（el-checkbox-group）
// 已授权列表展示（roleName + perms 标签 + 移除按钮）
// 保存 → saveFolderAcl；移除 → removeFolderAcl
// 下载开关 el-switch（folderDownloadable）→ setFolderDownloadable
```

- [ ] **Step 4: 替换入口**

```vue
// 文件操作菜单加"替换"项（v-if="folderPerms.includes('edit')"）
// 点击 → el-upload 隐藏 input 选文件 → replaceFile(id, formData) → 成功后刷新列表 + ElMessage.success('替换成功，版本 ' + res.data.version)
```

- [ ] **Step 5: 本地构建验证**

```bash
cd ycmn-ui && npm run build 2>&1 | tail -5
```
Expected: BUILD SUCCESS（无 TS/构建错误）

- [ ] **Step 6: 提交**

```bash
git add ycmn-ui/src/views/file/index.vue ycmn-ui/src/api/file.js
git commit -m "feat: 前端文件权限设置/下载开关/替换/按钮权限控制"
```

---

### Task 8: 测试环境部署与验证

**Files:** 无代码变更（部署与验证）

- [ ] **Step 1: 打包后端并部署测试环境**

```bash
cd ycmn-backend && MAVEN_SKIP_RC=true JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home mvn package -DskipTests
scp target/aigicrm-backend-1.0.0.jar ubuntu@111.229.37.220:/home/ubuntu/projects/ycmn/ycmn-backend/target/ycmn-backend-1.0.0.jar
# 测试服务器：docker build -t ycmn-backend:latest -f Dockerfile-run . → 停旧容器 → docker run（网络 shopjoy-net，DB=shared-mysql，JWT_SECRET/OSS 变量）
# 测试库执行迁移（shared-mysql 的 ycmn 库）
```

- [ ] **Step 2: 部署前端到测试环境**

```bash
# ycmn-ui build 产物部署（参照 deploy-frontend.sh 测试环境流程）
```

- [ ] **Step 3: 测试环境功能验证（浏览器）**

```
1. admin 登录 → 文件管理 → 公共文件夹 → 权限设置：授给"销售员"角色 upload/edit/delete/share/download → 保存
2. 销售员（wangleilei/123456）登录 → 文件管理 → 公共 Tab → 该文件夹可见且可上传/替换/删除 → 分享按钮出现
3. 未授权角色（zhangsan 销售员但另一文件夹）→ 仅查看，无上传/删除按钮；ACL 接口 403
4. 下载开关关闭 → 下载按钮禁用/下载 403；预览正常
5. 替换文件 → 版本号 2、列表刷新、内容为新文件
6. 私人文件夹 → 默认仅本人；权限设置授给角色后角色可见
7. 上级验证：给 TY101 设置 manager_id=某销售总监 → 总监在其文件 Tab 可见并可删
8. 回归：激励 Tab、分享给我的 Tab、上传/下载/存储空间正常
```

- [ ] **Step 4: 记录验证结果到功能包/测试矩阵**

---

### Task 9: 生产部署与交付

**Files:** 无代码变更（部署与验证）

- [ ] **Step 1: 生产迁移**

```bash
export SSHPASS='redacted-ssh-password'
sshpass -e ssh root@39.99.35.218 "docker exec -i ycmn-mysql mysql -uroot -p<redacted> ycmn < sql/migration-20260805-file-acl.sql"
```

- [ ] **Step 2: 提取测试 jar 部署生产**

```bash
# 参照 deploy-backend.sh：docker cp 测试容器 /app/app.jar → scp 生产 /root/ycmn/ycmn-backend-1.0.0.jar
# → docker build -t ycmn-backend:2.7.0 -f Dockerfile-run . → 停旧容器 → docker run（JWT_SECRET 用 ~/.ycmn-deploy.env）
# 注意：生产 Dockerfile-run 是 COPY ycmn-backend-1.0.0.jar（根目录），构建前确认 jar 到位
```

- [ ] **Step 3: 前端部署生产（参照 deploy-frontend.sh）**

- [ ] **Step 4: 生产实测**

```
NM005（销售员）登录 → 文件管理 → 公共文件夹权限设置按钮（无 edit 权限则不显示）→ 由 admin 先授权 → 上传/替换/删除正常
admin 回归：全部功能正常；统计/客户等 20 页回归零错误
未授权角色：仅查看、无操作按钮、接口 403
```

- [ ] **Step 5: 交付报告 + 状态回读**

```
更新 FEAT 功能包（07-delivery-report.md），记录：迁移、部署、验证证据、回滚（git revert + DELETE biz_folder_acl + ALTER DROP COLUMN 回滚脚本）、残余风险
```

---

## Self-Review 记录

- **Spec 覆盖**：ACL 授权（Task 5）✓ 继承/判定优先级（Task 3）✓ 上级可见（Task 3 isManagerChain）✓ 下载开关（Task 5 + Task 4 download）✓ 替换（Task 6）✓ 分享控制（Task 4 shareFile）✓ 前端（Task 7）✓ 迁移（Task 1）✓ 测试/部署（Task 8/9）✓
- **占位符**：`userHasRole` 的 `SecurityUtils.getCurrentUserRoles()` 标注了"若不存在则新增"——实现时按项目实际补齐，非空洞占位
- **类型一致性**：`check/checkFile/isManagerChain/permsFor` 签名在 Task 3 定义、Task 4/5 引用一致；`saveAcl(folderId, roleId, perms)` 与 Controller body 映射一致
