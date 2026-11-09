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

# ---- 【2026-11-09 类型级默认权限 + ACL 词表归一 + 文件权限 v2】 ----------------------
# ① 新表迁移存在; ② 类型默认三端点(FileController 同包 FolderAclController)挂 file:acl:manage;
# ③ FileService 根级公共建夹门槛= admin ∨ canCreateRoot;
# ④ ACL 词表归一迁移存在(旧长词表 file:upload 等在判定/写入两端口径下全部失效, 归一后生效);
# ⑤ 【v2 主体扩展+动作细分】迁移存在(subject_type/subject_id), DELETE 路径三段式;
# ⑥ 判定链插类型授权实时生效层(typeGranted), 改名/替换/夹改名走 P_RENAME/P_REPLACE 细词;
# ⑦ v1 materializeAcl 物化已退役(勾了即生效/取消即收回), 防复活。
ACL_CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FolderAclController.java"
FILE_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileService.java"
PERM_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderPermissionService.java"
TYPE_DEFAULT_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderTypeDefaultService.java"
TYPE_DEFAULT_MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.09.3__folder_type_default.sql"
ACL_NORMALIZE_MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.09.2__normalize_folder_acl_perms.sql"
TYPE_SUBJECT_MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.09.5__file_perm_subject_and_action_split.sql"
[ -f "$TYPE_DEFAULT_MIGRATION" ] || fail "missing folder_type_default migration"
[ -f "$ACL_NORMALIZE_MIGRATION" ] || fail "missing acl perms normalize migration"
[ -f "$TYPE_SUBJECT_MIGRATION" ] || fail "missing file-perm v2 subject/action-split migration"
grep -Fq "ADD COLUMN subject_type" "$TYPE_SUBJECT_MIGRATION" || fail "v2 migration must add subject_type column"
grep -Fq "CHANGE COLUMN role_id subject_id" "$TYPE_SUBJECT_MIGRATION" || fail "v2 migration must rename role_id to subject_id"
for mapping in "@GetMapping(\"/type-defaults\")" "@PostMapping(\"/type-defaults\")" "@DeleteMapping(\"/type-defaults/{folderType}/{subjectType}/{subjectId}\")"; do
  grep -B1 -F "$mapping" "$ACL_CONTROLLER" | grep -Fq "hasAuthority('file:acl:manage')" \
    || fail "type-defaults endpoint guard mismatch: $mapping"
done
grep -Fq 'folderTypeDefaultService.canCreateRoot("public")' "$FILE_SERVICE" \
  || fail 'createFolder public-root gate must consult FolderTypeDefaultService.canCreateRoot'
# 【v2 动作细分】改名/替换/夹改名走独立细词(全局码仍 file:edit 伞, 存量 edit 授权由判定层读时展开)
grep -Fq 'checkFile(fileId, FolderPermissionService.P_RENAME, userId)' "$FILE_SERVICE" \
  || fail 'renameFile must check P_RENAME (v2 action split)'
grep -Fq 'checkFile(fileId, FolderPermissionService.P_REPLACE, userId)' "$FILE_SERVICE" \
  || fail 'replaceFile must check P_REPLACE (v2 action split)'
grep -Fq 'check(folder.getId(), FolderPermissionService.P_RENAME, SecurityUtils.getCurrentUserId())' "$FILE_SERVICE" \
  || fail 'renameFolder must check P_RENAME (v2 action split)'
# 【v2 类型授权层】判定链必须插实时生效的 typeGranted(角色|人 × 空间 × 动作)
grep -Fq 'typeGranted' "$PERM_SERVICE" \
  || fail 'permission chain must consult real-time type-grant layer'
grep -Fq 'P_RENAME = "rename"' "$PERM_SERVICE" || fail 'v2 must define P_RENAME'
grep -Fq 'P_REPLACE = "replace"' "$PERM_SERVICE" || fail 'v2 must define P_REPLACE'
# 【2026-11-10 用户令"A"】上传者本人恒可管理自己的文件——checkFile 里本人短路必须先于夹判定
# (此前"夹权优先于上传人"致公共夹里自己传的文件自己改不了名); PC 行级放行(isOwn)与 MP(isOwn)同口径
grep -Fq '上传者本人恒可管理自己上传的文件' "$PERM_SERVICE" \
  || fail 'checkFile must short-circuit uploader-self before folder judgment (2026-11-10 user order A)'
grep -Fq 'Objects.equals(file.getUploaderId(), userId)) return true;' "$PERM_SERVICE" \
  || fail 'checkFile uploader-self check must exist'
grep -Fq 'isOwn(row)' "$ROOT/aigicrm-ui/src/views/file/FileTable.vue" \
  || fail 'PC FileTable must relax own-file rows via isOwn (rename/replace/delete/share/download)'
# 【防复活】materializeAcl 物化语义已随 v2 退役: 类型授权实时生效, 不得再落 biz_folder_acl 快照
if grep -Fq 'materializeAcl' "$FILE_SERVICE" "$TYPE_DEFAULT_SERVICE"; then
  fail 'materializeAcl retired in v2 (real-time type-grant layer); do not resurrect'
fi
grep -Fq "'file:upload', 'upload'" "$ACL_NORMALIZE_MIGRATION" \
  || fail 'normalize migration must map file:upload -> upload'
echo 'PASS: file mutation permission contract is aligned.'
