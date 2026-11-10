#!/usr/bin/env bash
# Validate file mutation permission split and its compatibility migration.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java"
MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V26__split_file_mutation_permissions.sql"
# 修复迁移: V26 只 INSERT 绑定、从不置 status=1, 且其 INSERT 带
# NOT EXISTS(permission=…) 守卫 ⇒ 对"行已存在但 status=0"的库净效果为 0。
# 真正让这 5 个码可用的迁移是本文件所断言者。
FIX_MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.09.11.3__restore_file_mutation_permission_bindings.sql"

fail() { echo "FAIL: $*" >&2; exit 1; }

for permission in file:upload file:edit file:delete file:share file:folder:manage; do
  grep -Fq "'$permission'" "$MIGRATION" || fail "migration missing $permission"
  grep -Fq "hasAuthority('$permission')" "$CONTROLLER" || fail "controller missing $permission"
done
grep -Fq "view_menu.permission = 'file:view'" "$MIGRATION" || fail 'migration does not inherit file:view role bindings'

# ---- 修复迁移必须存在且真能把 5 个码置为启用 ----------------------------
# 背景(2026-09-11 复核): V22:25-52 把这 5 个码 status 置 0 并删绑定;
# V26 只补绑定不置 status ⇒ selectPermissionsByUserId 的 `AND m.status = 1`
# 让这 5 个码对**所有人(含 admin)**永不返回, 而旧契约只 grep 字面量, 恒 PASS。
# 因此这里必须断言: 存在一个迁移, 对这 5 个码执行 status=1。
[ -f "$FIX_MIGRATION" ] || fail "missing enabling migration: $(basename "$FIX_MIGRATION")"
grep -Eq "SET[[:space:]]+m\.?\`?status\`?[[:space:]]*=[[:space:]]*1" "$FIX_MIGRATION" \
  || fail 'enabling migration does not set status=1 on the file mutation codes'
for permission in file:upload file:edit file:delete file:share file:folder:manage; do
  grep -Fq "'$permission'" "$FIX_MIGRATION" || fail "enabling migration does not cover $permission"
done
grep -Fq "view_menu.\`permission\` = 'file:view'" "$FIX_MIGRATION" \
  || fail 'enabling migration does not rebuild bindings from file:view roles'
grep -Fq "if (getApp().globalData.demoMode)" "$ROOT/aigicrm-miniprogram/pages/files/index.js" || fail 'mobile demo file boundary is missing'
grep -Fq "saveDemoFolder" "$ROOT/aigicrm-miniprogram/pages/files/folder-form/index.js" || fail 'mobile demo folder persistence is missing'
grep -Fq "removeDemoFolder" "$ROOT/aigicrm-miniprogram/pages/files/index.js" || fail 'mobile demo folder delete boundary is missing'
for pair in \
  "file:upload|@PostMapping(\"/upload\")" \
  "file:folder:manage|@PostMapping(\"/folders\")" \
  "file:folder:manage|@PutMapping(\"/folders/{id}\")" \
  "file:folder:manage|@DeleteMapping(\"/folders/{id}\")" \
  "file:delete|@DeleteMapping(\"/{id}\")" \
  "file:edit|@PostMapping(\"/{id}/replace\")" \
  "file:share|@PostMapping(\"/{id}/share\")"; do
  permission="${pair%%|*}"
  mapping="${pair#*|}"
  grep -B1 -F "$mapping" "$CONTROLLER" | grep -Fq "hasAuthority('$permission')" \
    || fail "controller mapping mismatch for $mapping -> $permission"
done

# ---- 【2026-11-10 文件权限终版: 组织底座】 --------------------------------------------
# ①迁移两支存在(.3 表 + .10.1 组织快照/授权升级/ACL转换); ②授权端点四件+角色字典挂 file:acl:manage;
# ③判定链=组织矩阵(matrixActionOf/resolveModuleUsers(file)) + 例外授权(expiresAt 过期检查) + 本人恒管;
# ④公共"全员可见"兜底已删除(拍板①B); ⑤建夹门槛=canCreateFolder(授权 FOLDER:create) + owner/org 归属快照;
# ⑥v1 materializeAcl 防复活; ⑦上传者本人短路(改名/替换/删除恒可) + PC isOwn 行级放行。
ACL_CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java"
FILE_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileService.java"
PERM_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderPermissionService.java"
GRANT_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderTypeDefaultService.java"
MIG_BASE="$ROOT/aigicrm-backend/src/main/resources/db/migration"
[ -f "$MIG_BASE/V2026.11.09.3__folder_type_default.sql" ] || fail "missing folder_type_default migration"
[ -f "$MIG_BASE/V2026.11.09.2__normalize_folder_acl_perms.sql" ] || fail "missing acl perms normalize migration"
[ -f "$MIG_BASE/V2026.11.10.1__file_perm_org_foundation.sql" ] || fail "missing org-foundation migration"
grep -Fq "ADD COLUMN org_id" "$MIG_BASE/V2026.11.10.1__file_perm_org_foundation.sql" || fail "org snapshot columns missing"
grep -Fq "ADD COLUMN expires_at" "$MIG_BASE/V2026.11.10.1__file_perm_org_foundation.sql" || fail "grant expiry column missing"
grep -Fq "INSERT INTO role_perm_scope" "$MIG_BASE/V2026.11.10.1__file_perm_org_foundation.sql" || fail "file matrix seed rows missing"
for mapping in '@GetMapping("/type-defaults")' '@PostMapping("/type-defaults")' '@PutMapping("/type-defaults/{id}/renew")' '@DeleteMapping("/type-defaults/{id}")'; do
  grep -B1 -F "$mapping" "$ACL_CONTROLLER" | grep -Fq "hasAuthority('file:acl:manage')" \
    || fail "grant endpoint guard mismatch: $mapping"
done
# 逐夹 ACL 端点不得复活(已转换为 folder_id 定向授权, 配置一律走例外授权)
if grep -Eq '@(Get|Post|Delete)Mapping\("/\{id\}/acl' "$ACL_CONTROLLER"; then
  fail 'per-folder ACL endpoints retired in 2026-11-10; grants only'
fi
# 组织矩阵 + 例外授权(过期)
grep -Fq 'resolveModuleUsers(user, MODULE_FILE' "$PERM_SERVICE" || fail 'chain must consult org matrix via resolveModuleUsers(file)'
grep -Fq 'matrixActionOf' "$PERM_SERVICE" || fail 'action->matrix mapping missing'
grep -Fq 'getExpiresAt().isBefore(now)' "$PERM_SERVICE" || fail 'grant expiry check missing'
grep -Fq 'canCreateFolder' "$PERM_SERVICE" || fail 'folder create gate missing'
# 公共兜底删除: 判定链不得再出现 (view|download)&&public 的全员放行
if grep -Fq 'P_DOWNLOAD.equals(action)) && "public"' "$PERM_SERVICE"; then
  fail 'public view/download fallback must stay removed (ruling B)'
fi
# 归属快照: 建夹 owner=创建人 + org 快照; 上传 org 快照
grep -Fq 'folder.setOwnerId(creator)' "$FILE_SERVICE" || fail 'createFolder must set owner=creator'
grep -Fq 'folder.setOrgId(' "$FILE_SERVICE" || fail 'createFolder must snapshot org'
grep -Fq 'bizFile.setOrgId(' "$FILE_SERVICE" || fail 'upload must snapshot org'
# 建夹门槛走判定链
grep -Fq 'folderPermissionService.canCreateFolder(type, creator)' "$FILE_SERVICE" || fail 'createFolder gate must consult canCreateFolder'
# 动作细分(11-09 保留钉)
grep -Fq 'checkFile(fileId, FolderPermissionService.P_RENAME, userId)' "$FILE_SERVICE" || fail 'renameFile must check P_RENAME'
grep -Fq 'checkFile(fileId, FolderPermissionService.P_REPLACE, userId)' "$FILE_SERVICE" || fail 'replaceFile must check P_REPLACE'
# 授权必须过期: 保存校验 + 续期
grep -Fq '到期时间必须晚于当前时间' "$GRANT_SERVICE" || fail 'grant save must validate expiry when provided (到期可选, 2026-11-10 用户令权限都是勾选的)'
grep -Fq 'renewGrant' "$GRANT_SERVICE" || fail 'grant renew endpoint service missing'
# v1 物化防复活
if grep -Fq 'materializeAcl' "$FILE_SERVICE" "$GRANT_SERVICE"; then
  fail 'materializeAcl retired; do not resurrect'
fi
# 本人恒管(11-10 上午批保留钉) + PC isOwn
grep -Fq '上传者本人恒可管理自己上传的文件' "$PERM_SERVICE" || fail 'uploader-self short-circuit must stay'
grep -Fq 'isOwn(row)' "$ROOT/aigicrm-ui/src/views/file/FileTable.vue" || fail 'PC FileTable isOwn row gate must stay'
echo 'PASS: file mutation permission contract is aligned.'
