#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
page="$ROOT/aigicrm-miniprogram/pages/home/index.js"
api="$ROOT/aigicrm-miniprogram/api/statistics.js"
manifest="$ROOT/docs/unified-feature-manifest.md"
grep -q "getDashboardData" "$page" || { echo "FAIL: mobile dashboard does not load dashboard data" >&2; exit 1; }
grep -q "hasPermission('dashboard:view')" "$page" || { echo "FAIL: dashboard permission gate is missing" >&2; exit 1; }
grep -q "hasPermission('statistics:view')" "$page" || { echo "FAIL: statistics fallback permission gate is missing" >&2; exit 1; }
grep -q "showDashboardCards" "$page" || { echo "FAIL: dashboard visibility state is missing" >&2; exit 1; }
grep -q "dashboard.todayExpiring" "$page" || { echo "FAIL: mobile home does not display today-expiring metric" >&2; exit 1; }
grep -q "stats.todayExpiring" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: mobile statistics does not display today-expiring metric" >&2; exit 1; }
for field in myCustomers inProtection expiring expired; do
  grep -q "dashboard.$field" "$page" || { echo "FAIL: mobile home does not display dashboard.$field" >&2; exit 1; }
done
for field in myCustomers inProtection expiring todayExpiring monthNew; do
  grep -q "stats.$field" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: mobile statistics does not display stats.$field" >&2; exit 1; }
done
grep -q 'wx:if="{{showDashboardCards}}"' "$ROOT/aigicrm-miniprogram/pages/home/index.wxml" || { echo "FAIL: dashboard cards are not gated in WXML" >&2; exit 1; }
grep -q "'/api/statistics/dashboard'" "$api" || { echo "FAIL: dashboard API route is missing" >&2; exit 1; }
grep -q "dashboard.view.*implemented" "$manifest" || { echo "FAIL: dashboard manifest status is stale" >&2; exit 1; }
echo "PASS: mobile dashboard data, permission, API, and manifest contract is aligned."
