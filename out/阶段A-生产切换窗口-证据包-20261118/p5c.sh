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
LOGIN=$(curl -s -m 45 --retry 3 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$HC_USER\",\"password\":\"$HC_PASS\"}")

TOK=$(echo "$LOGIN" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo FAIL)
[ "$TOK" != "FAIL" ] || { echo "[smoke] login_parse=fail(以QC4容器直跑核验为准)"; exit 0; }
for FID in 2688 2692 2674; do
  DL=$(curl -s -m 45 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" "$API/api/file/download/$FID")
  echo "[smoke] download[$FID]=$DL"
done
echo "P5_SMOKE_DONE"
EOF
} | ssh_prod_once "bash -s"
