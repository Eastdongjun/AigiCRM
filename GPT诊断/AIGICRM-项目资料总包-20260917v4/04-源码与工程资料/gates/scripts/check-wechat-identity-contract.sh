#!/usr/bin/env bash
# Validate the storage-only WeChat identity foundation.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V27__add_wechat_identity_columns.sql"
ENTITY="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/system/entity/SysUser.java"

fail() { echo "FAIL: $*" >&2; exit 1; }
for token in wechat_openid wechat_unionid uk_sys_user_wechat_openid uk_sys_user_wechat_unionid; do
  grep -Fq "$token" "$MIGRATION" || fail "migration missing $token"
done
grep -Fq 'private String wechatOpenid;' "$ENTITY" || fail 'entity missing wechatOpenid'
grep -Fq 'private String wechatUnionid;' "$ENTITY" || fail 'entity missing wechatUnionid'
grep -B1 -A1 -F 'private String wechatOpenid;' "$ENTITY" | grep -Fq '@JsonIgnore' || fail 'openid is not hidden from JSON'
grep -B1 -A1 -F 'private String wechatUnionid;' "$ENTITY" | grep -Fq '@JsonIgnore' || fail 'unionid is not hidden from JSON'
echo 'PASS: WeChat identity storage contract is aligned (storage only).'
