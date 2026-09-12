#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
api="$ROOT/aigicrm-miniprogram/api/statistics.js"
page="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.wxml"
page_js="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.js"
backend="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java"

grep -q "getTeamActivities" "$api" || { echo "FAIL: mobile statistics API wrapper is missing getTeamActivities" >&2; exit 1; }
grep -q "'/api/statistics/team-activities'" "$api" || { echo "FAIL: mobile statistics API route is miswired" >&2; exit 1; }
grep -q "getDailyStats" "$api" || { echo "FAIL: mobile statistics API wrapper is missing getDailyStats" >&2; exit 1; }
grep -q "getWeeklyStats" "$api" || { echo "FAIL: mobile statistics API wrapper is missing getWeeklyStats" >&2; exit 1; }
grep -q "'/api/statistics/daily-stats'" "$api" || { echo "FAIL: mobile daily statistics API route is miswired" >&2; exit 1; }
grep -q "'/api/statistics/weekly-stats'" "$api" || { echo "FAIL: mobile weekly statistics API route is miswired" >&2; exit 1; }
grep -q "getDailyStats" "$page_js" || { echo "FAIL: statistics detail does not load daily stats" >&2; exit 1; }
grep -q "getWeeklyStats" "$page_js" || { echo "FAIL: statistics detail does not load weekly stats" >&2; exit 1; }
grep -q "hasPermission('statistics:view')" "$page_js" || { echo "FAIL: statistics detail permission guard is missing" >&2; exit 1; }
grep -q "data-section=\"activity\"" "$page" || { echo "FAIL: mobile statistics detail has no team activity tab" >&2; exit 1; }
grep -q "data-section=\"trend\"" "$page" || { echo "FAIL: mobile statistics detail has no trend tab" >&2; exit 1; }
grep -q "stats.todayExpiring" "$page" || { echo "FAIL: mobile statistics detail does not display today-expiring metric" >&2; exit 1; }
grep -q 'team-activities' "$backend" || { echo "FAIL: backend team activities route is missing" >&2; exit 1; }
grep -q 'daily-stats' "$backend" || { echo "FAIL: backend daily statistics route is missing" >&2; exit 1; }
grep -q 'weekly-stats' "$backend" || { echo "FAIL: backend weekly statistics route is missing" >&2; exit 1; }

echo "PASS: mobile team-activity statistics contract is aligned."
