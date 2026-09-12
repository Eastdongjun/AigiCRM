#!/usr/bin/env bash
# Read-only Sonar status probe. Exit 0=local scan clean, 2=environment/service
# capability incomplete, 1=actionable scan failure. Never prints credentials.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java}"

auth_log="$(mktemp)"
scan_log="$(mktemp)"
trap 'rm -f "$auth_log" "$scan_log"' EXIT

if sonar auth status >"$auth_log" 2>&1; then
  grep -E 'Connected|Server|Org|Source' "$auth_log" || true
else
  echo 'SONAR_AUTH=FAIL'
  exit 2
fi

if sonar analyze --file "$TARGET" >"$scan_log" 2>&1; then
  echo 'SONAR_LOCAL_SCAN=CLEAN'
  exit 0
fi

if grep -Fq 'Vortex analysis is not available' "$scan_log"; then
  echo 'SONAR_LOCAL_SCAN=INCOMPLETE_VORTEX_UNAVAILABLE'
  echo '质量验证未完成：当前连接不提供 Vortex 分析。' >&2
  exit 2
fi

echo 'SONAR_LOCAL_SCAN=FAIL'
tail -20 "$scan_log" >&2
exit 1
