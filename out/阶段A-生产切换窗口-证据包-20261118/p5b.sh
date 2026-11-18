#!/bin/bash
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
source "$REPO/scripts/deploy-lib.sh" || exit 1
emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_secret() { printf '%s=%s\n' "$1" "$(printf '%q' "${!2}")"; }
{
  echo 'set -Eeuo pipefail'
  emit_var HC_USER "${HEALTH_CHECK_USER:-admin}"
  emit_secret HC_PASS HEALTH_CHECK_PASS
  cat <<'EOF'
API="https://www.aigicrm.com"
TOK=$(curl -s -m 12 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$HC_USER\",\"password\":\"$HC_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
for S in "" "?scope=public" "?scope=incentive" "?scope=private"; do
  N=$(curl -s -m 12 -H "Authorization: Bearer $TOK" "$API/api/file/list${S%/}&current=1&size=10" 2>/dev/null \
    | python3 -c "import sys,json
d=json.load(sys.stdin)
r=(d.get('data') or {}).get('records') or []
print(f'code={d.get(\"code\")} n={len(r)} first={r[0][\"id\"] if r else None} perms={(r[0].get(\"perms\") if r else None)}')" 2>/dev/null || echo PARSE_FAIL)
  echo "[smoke] list[$S] $N"
done
EOF
} | ssh_prod_once "bash -s"
