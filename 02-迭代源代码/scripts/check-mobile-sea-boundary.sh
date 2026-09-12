#!/usr/bin/env bash
# Keep public-sea import/config/delete mutations out of the mobile release.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$ROOT/aigicrm-miniprogram/api/sea.js"
PAGE="$ROOT/aigicrm-miniprogram/pages/sea/index.js"
WXML="$ROOT/aigicrm-miniprogram/pages/sea/index.wxml"

fail() { echo "FAIL: $*" >&2; exit 1; }

for token in importSea importPool createSea updateSea deleteSea deletePool saveSeaConfig; do
  ! grep -Fq "$token" "$API" || fail "mobile sea API exposes forbidden $token"
  ! grep -Fq "$token" "$PAGE" || fail "mobile sea page exposes forbidden $token"
done
for token in '导入' '配置公海' '保存配置'; do
  ! grep -Fq "$token" "$WXML" || fail "mobile sea UI exposes forbidden $token"
done
grep -Fq "url: '/api/sea/configs/visible'" "$API" || fail 'visible sea config API disappeared'
grep -Fq 'Boolean(item.canClaim)' "$PAGE" || fail 'backend canClaim flag is not consumed'
grep -Fq 'Boolean(item.canEdit)' "$PAGE" || fail 'backend canEdit flag is not consumed'
grep -Fq 'Boolean(item.canDelete)' "$PAGE" || fail 'backend canDelete flag is not consumed'
grep -Fq "if (!item || item.status !== 'available' || !item.canClaim) return" "$PAGE" \
  || fail 'mobile sea claim action is not guarded by backend canClaim'
grep -Fq 'bindtap="claim"' "$WXML" || fail 'mobile sea claim action disappeared'
echo 'PASS: mobile public-sea high-risk boundary is aligned.'
