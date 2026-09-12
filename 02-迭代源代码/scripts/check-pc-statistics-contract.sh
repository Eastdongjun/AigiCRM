#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
service="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java"
page="$ROOT/aigicrm-ui/src/views/statistics/index.vue"
api="$ROOT/aigicrm-ui/src/api/statistics.js"

grep -q 'data.put("todayExpiring", todayExpiring)' "$service" || { echo "FAIL: analysis response omits today-expiring metric" >&2; exit 1; }
grep -q "getAnalysis" "$page" || { echo "FAIL: PC statistics page does not load analysis data" >&2; exit 1; }
grep -q "analysis.todayExpiring" "$page" || { echo "FAIL: PC statistics page does not display today-expiring metric" >&2; exit 1; }
grep -q "request.get('/statistics/analysis'" "$api" || { echo "FAIL: PC statistics analysis API route is miswired" >&2; exit 1; }

echo "PASS: PC statistics today-expiring response, API, and view contract is aligned."
