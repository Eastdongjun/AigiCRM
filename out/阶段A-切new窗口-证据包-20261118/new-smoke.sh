#!/bin/bash
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
source "$REPO/scripts/deploy-lib.sh" || exit 1
emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_secret() { printf '%s=%s\n' "$1" "$(printf '%q' "${!2}")"; }
{
  echo 'set -uo pipefail'
  emit_var HC_USER "${HEALTH_CHECK_USER:-admin}"
  emit_secret HC_PASS HEALTH_CHECK_PASS
  cat <<'EOF'
API="https://www.aigicrm.com"
TOK=$(curl -s -m 45 --retry 3 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$HC_USER\",\"password\":\"$HC_PASS\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
echo "[s] login=$([ -n "$TOK" ] && echo 200 || echo FAIL)"
FIRST=$(curl -s -m 15 "$API/api/file/list?scope=public&current=1&size=1" -H "Authorization: Bearer $TOK" \
  | python3 -c "import sys,json;r=(json.load(sys.stdin).get('data') or {}).get('records') or [];print(r[0]['id'] if r else 'NONE')" 2>/dev/null)
[ "$FIRST" != "NONE" ] && echo "[s] 公共下载[$FIRST]=$(curl -s -m 30 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" "$API/api/file/download/$FIRST")"
GT=$(curl -s -m 30 "$API/api/file/grants" -H "Authorization: Bearer $TOK" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('code'), len(d.get('data') or []))" 2>/dev/null)
echo "[s] grants code,len=$GT"
# 代表性需权限操作: 删除自己上传文件(建临时→列表出现→delete)太重; 用 PUT 改名自己文件改回——也重。
# 用只读代表性操作: incentives 列表(需权限码) 已覆盖; 另取 admin 专属 GET storage
ST=$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$API/api/file/storage" -H "Authorization: Bearer $TOK")
echo "[s] storage(权限码操作)=$ST"
echo "SMOKE_DONE"
EOF
} | ssh_prod_once "bash -s"
