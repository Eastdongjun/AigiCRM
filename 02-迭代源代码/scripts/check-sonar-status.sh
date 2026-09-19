#!/usr/bin/env bash
# Read-only Sonar status probe. Exit 0=local scan clean and server Quality Gate OK,
# 2=environment/service capability incomplete, 1=actionable scan failure. Never
# prints credentials.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_KEY="${SONAR_PROJECT_KEY:-Eastdongjun_AigiCRM}"
TARGET="${1:-$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java}"

auth_log="$(mktemp)"
scan_log="$(mktemp)"
quality_gate_log="$(mktemp)"
trap 'rm -f "$auth_log" "$scan_log" "$quality_gate_log"' EXIT
status=0

if sonar auth status >"$auth_log" 2>&1; then
  grep -E 'Connected|Server|Org|Source' "$auth_log" || true
else
  echo 'SONAR_AUTH=FAIL'
  exit 2
fi

if sonar analyze --file "$TARGET" >"$scan_log" 2>&1; then
  echo 'SONAR_LOCAL_SCAN=CLEAN'
else
  if grep -Fq 'Vortex analysis is not available' "$scan_log"; then
    echo 'SONAR_LOCAL_SCAN=INCOMPLETE_VORTEX_UNAVAILABLE'
    echo '质量验证未完成：当前连接不提供 Vortex 分析。' >&2
    status=2
  else
    echo 'SONAR_LOCAL_SCAN=FAIL'
    tail -20 "$scan_log" >&2
    status=1
  fi
fi

if sonar quality-gate status -p "$PROJECT_KEY" >"$quality_gate_log" 2>&1; then
  if grep -Fq 'Quality Gate: [OK]' "$quality_gate_log"; then
    echo 'SONAR_QUALITY_GATE=OK'
  else
    echo 'SONAR_QUALITY_GATE=UNKNOWN_SUCCESS'
    tail -20 "$quality_gate_log" >&2
    [ "$status" -eq 1 ] || status=2
  fi
elif grep -Fq 'Not computed' "$quality_gate_log"; then
  echo 'SONAR_QUALITY_GATE=NOT_COMPUTED'
  echo '质量验证未完成：服务端 Quality Gate 未计算，不能声明 Sonar 门禁通过。' >&2
  [ "$status" -eq 1 ] || status=2
else
  echo 'SONAR_QUALITY_GATE=FAIL'
  tail -20 "$quality_gate_log" >&2
  status=1
fi

exit "$status"
