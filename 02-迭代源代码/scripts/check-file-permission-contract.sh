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

# ---- 【2026-11-09 类型级默认权限 + ACL 词表归一】 ----------------------------------
# ① 新表迁移存在; ② 类型默认三端点(FileController 同包 FolderAclController)挂 file:acl:manage;
# ③ FileService 根级公共建夹门槛= admin ∨ canCreateRoot; 新夹继承=materializeAcl;
# ④ ACL 词表归一迁移存在(旧长词表 file:upload 等在判定/写入两端口径下全部失效, 归一后生效)。
ACL_CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java"
FILE_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileService.java"
TYPE_DEFAULT_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderTypeDefaultService.java"
TYPE_DEFAULT_MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.09.3__folder_type_default.sql"
ACL_NORMALIZE_MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.09.2__normalize_folder_acl_perms.sql"
[ -f "$TYPE_DEFAULT_MIGRATION" ] || fail "missing folder_type_default migration"
[ -f "$ACL_NORMALIZE_MIGRATION" ] || fail "missing acl perms normalize migration"
for mapping in "@GetMapping(\"/type-defaults\")" "@PostMapping(\"/type-defaults\")" "@DeleteMapping(\"/type-defaults/{folderType}/{roleId}\")"; do
  grep -B1 -F "$mapping" "$ACL_CONTROLLER" | grep -Fq "hasAuthority('file:acl:manage')" \
    || fail "type-defaults endpoint guard mismatch: $mapping"
done
grep -Fq 'folderTypeDefaultService.canCreateRoot("public")' "$FILE_SERVICE" \
  || fail 'createFolder public-root gate must consult FolderTypeDefaultService.canCreateRoot'
grep -Fq 'folderTypeDefaultService.materializeAcl(folder.getId(), type)' "$FILE_SERVICE" \
  || fail 'createFolder must materialize type-default ACL into new folder'
grep -Fq 'perms.remove(P_CREATE)' "$TYPE_DEFAULT_SERVICE" \
  || fail 'materializeAcl must strip create token before writing per-folder ACL'
grep -Fq "'file:upload', 'upload'" "$ACL_NORMALIZE_MIGRATION" \
  || fail 'normalize migration must map file:upload -> upload'
echo 'PASS: file mutation permission contract is aligned.'
