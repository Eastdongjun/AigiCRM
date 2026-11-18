#!/bin/bash
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
source "$REPO/scripts/deploy-lib.sh" || exit 1
HC_USER="${HEALTH_CHECK_USER:-admin}"
emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_secret() { printf '%s=%s\n' "$1" "$(printf '%q' "${!2}")"; }
{
  echo 'set -Eeuo pipefail'
  emit_var HC_USER "$HC_USER"
  emit_secret HC_PASS HEALTH_CHECK_PASS
  cat <<'EOF'
API="https://www.aigicrm.com"
TOK=$(curl -s -m 12 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$HC_USER\",\"password\":\"$HC_PASS\"}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['data']['token'] if d.get('code')==200 and d.get('data') and d['data'].get('token') else 'LOGIN_FAIL')")
[ "$TOK" != "LOGIN_FAIL" ] || { echo "[smoke] 登录失败"; exit 1; }
echo "[smoke] login=200 token=OK"
FIRST_FILE=$(curl -s -m 12 -H "Authorization: Bearer $TOK" "$API/api/file/list?current=1&size=5" \
  | python3 -c "import sys,json;r=(json.load(sys.stdin).get('data') or {}).get('records') or [];print(r[0]['id'] if r else 'NONE')")
echo "[smoke] list_first_file=$FIRST_FILE"
if [ "$FIRST_FILE" != "NONE" ]; then
  DL=$(curl -s -m 15 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" "$API/api/file/download/$FIRST_FILE")
  echo "[smoke] download=$DL"
fi
GRANTS=$(curl -s -m 12 -H "Authorization: Bearer $TOK" "$API/api/file/grants?size=1" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);rows=d.get('data') or [];print(f'{d.get(\"code\")} rows={len(rows)} sample_spaceType={(rows[0].get(\"spaceType\") if rows else None)} sample_source={(rows[0].get(\"source\") if rows else None)}')")
echo "[smoke] grants=$GRANTS"
echo "SMOKE_DONE"
EOF
} | ssh_prod_once "bash -s"
