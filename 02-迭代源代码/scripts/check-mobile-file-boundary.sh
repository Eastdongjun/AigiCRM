#!/usr/bin/env bash
# Keep high-risk file mutations out of the first mobile release.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$ROOT/aigicrm-miniprogram/api/file.js"
PAGE="$ROOT/aigicrm-miniprogram/pages/file/index.js"
WXML="$ROOT/aigicrm-miniprogram/pages/file/index.wxml"
DETAIL="$ROOT/aigicrm-miniprogram/pages/files/detail/index.js"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java"

fail() { echo "FAIL: $*" >&2; exit 1; }
for token in deleteFile saveAcl removeAcl setDownloadable; do
  ! grep -Fq "$token" "$API" || fail "mobile file API exposes forbidden $token"
  ! grep -Fq "$token" "$PAGE" || fail "mobile file page exposes forbidden $token"
done
for token in '删除' '替换' '分享设置' 'ACL'; do
  ! grep -Fq "$token" "$WXML" || fail "mobile file UI exposes forbidden $token"
done
grep -Fq 'uploadFile' "$API" || fail 'mobile file upload contract disappeared'
grep -Fq 'getDownloadToken' "$API" || fail 'mobile file preview/download contract disappeared'
grep -Fq 'shareFile' "$API" || fail 'mobile file share API contract disappeared'
grep -Fq "hasPermission('file:share')" "$PAGE" || fail 'mobile file share permission gate disappeared'
grep -Fq 'bindtap="shareFile"' "$WXML" || fail 'mobile file share action disappeared'
grep -Fq 'replaceFile' "$API" || fail 'mobile file replace API contract disappeared'
grep -Fq "hasPermission('file:edit')" "$DETAIL" || fail 'mobile file replace permission gate disappeared'
grep -Fq 'chooseMessageFile' "$DETAIL" || fail 'mobile file replace picker disappeared'
grep -Fq "showError(error, '分享失败')" "$PAGE" || fail 'mobile file share failure feedback disappeared'
grep -Fq 'catch((error) => showError(error, '\''替换失败'\''))' "$DETAIL" || fail 'mobile file replace failure feedback disappeared'
grep -Fq '@PostMapping("/{id}/share")' "$CONTROLLER" || fail 'backend file share route disappeared'
grep -Fq '@PostMapping("/{id}/replace")' "$CONTROLLER" || fail 'backend file replace route disappeared'
echo 'PASS: mobile file high-risk boundary is aligned.'
