#!/usr/bin/env bash
# P0-5/B14: ensure the AIGICRM quality gate is strict on new-code defects and
# non-blocking on new-code coverage (user decision 2026-11-18).
set -euo pipefail

HOST="${SONAR_HOST_URL:-http://127.0.0.1:19000}"
PROJECT="${SONAR_PROJECT_KEY:-Eastdongjun_AigiCRM-local}"
GATE="${SONAR_GATE_NAME:-AIGICRM-P0-5}"
# 【GL-01 2026-11-24】不再内置安装默认口令：本地凭据经 SONAR_AUTH 或 SONAR_USER/SONAR_PASSWORD 传入，缺则 fail-closed
if [ -n "${SONAR_TOKEN:-}" ]; then AUTH="$SONAR_TOKEN:"; else AUTH="${SONAR_AUTH:-${SONAR_USER:-admin}:${SONAR_PASSWORD:?未配置 SONAR_PASSWORD（本地 Sonar 口令经环境变量传入，脚本不写安装默认值）}}"; fi
api() { curl -s -u "$AUTH" "$@"; }

api -X POST "$HOST/api/qualitygates/create" -d "name=$GATE" >/dev/null || true
for cond in "new_blocker_violations GT 0" "new_critical_violations GT 0" \
            "new_bugs GT 0" "new_vulnerabilities GT 0" "new_major_violations GT 0"; do
  # shellcheck disable=SC2086
  set -- $cond
  api -X POST "$HOST/api/qualitygates/create_condition" \
    -d "gateName=$GATE" -d "metric=$1" -d "op=$2" -d "error=$3" >/dev/null || true
done

# Coverage is tracked (JaCoCo) but non-blocking: remove any inherited new_coverage condition.
cov_id=$(api "$HOST/api/qualitygates/show?name=$GATE" | python3 -c \
  'import sys,json; d=json.load(sys.stdin); print(next((c["id"] for c in d["conditions"] if c["metric"]=="new_coverage"), ""))')
if [ -n "$cov_id" ]; then
  api -X POST "$HOST/api/qualitygates/delete_condition" -d "id=$cov_id" >/dev/null
fi

api -X POST "$HOST/api/qualitygates/select" \
  -d "gateName=$GATE" -d "projectKey=$PROJECT" >/dev/null
api "$HOST/api/qualitygates/get_by_project?project=$PROJECT"
echo
