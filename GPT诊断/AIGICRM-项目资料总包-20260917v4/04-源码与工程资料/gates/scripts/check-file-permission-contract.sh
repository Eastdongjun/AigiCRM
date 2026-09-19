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
echo 'PASS: file mutation permission contract is aligned.'
