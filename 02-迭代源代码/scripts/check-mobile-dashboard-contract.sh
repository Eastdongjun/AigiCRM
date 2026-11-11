#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
page="$ROOT/aigicrm-miniprogram/pages/home/index.js"
api="$ROOT/aigicrm-miniprogram/api/statistics.js"
manifest="$ROOT/docs/unified-feature-manifest.md"
backend="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java"
service="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java"
automator_package="$ROOT/scripts/miniprogram/package.json"
automator_script="$ROOT/scripts/miniprogram/verify-mobile-shell.mjs"
statistics_detail_js="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.js"
statistics_detail_wxml="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.wxml"
statistics_detail_wxss="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.wxss"
grep -q "getDashboardData" "$page" || { echo "FAIL: mobile dashboard does not load dashboard data" >&2; exit 1; }
# 【2026-11-12 裁决A】统计单键统一: 门禁仅看 statistics:view; dashboard:view 已退役(反钉防复活)
! grep -q "hasPermission('dashboard:view')" "$page" || { echo "FAIL: retired dashboard:view gate still present" >&2; exit 1; }
grep -q "hasPermission('statistics:view')" "$page" || { echo "FAIL: statistics permission gate is missing" >&2; exit 1; }
grep -q "showDashboardCards" "$page" || { echo "FAIL: dashboard visibility state is missing" >&2; exit 1; }
grep -q "dashboard.todayExpiring" "$page" || { echo "FAIL: mobile home does not display today-expiring metric" >&2; exit 1; }
grep -q "stats.todayExpiring" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: mobile statistics does not display today-expiring metric" >&2; exit 1; }
grep -q "statsView.riskTiles" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: mobile statistics does not render normalized risk tiles" >&2; exit 1; }
grep -q "filterSummary" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: mobile statistics filter summary must be normalized in JS" >&2; exit 1; }
grep -q "filterChips" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: mobile statistics filter chips must be normalized in JS" >&2; exit 1; }
# 【2026-11-06 用户令骨架收口 A 案】资产卡红盒(riskClass/riskTitle/riskText)删除——风险数字归信号卡
# tile、保护期分布归雷达 tiles; 三钉翻防复活(资产卡不再复读风险数字)
grep -q "riskClass" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" && { echo "FAIL: statistics customer risk note must not revive (removed 2026-11-06, signal tile + radar own the numbers)" >&2; exit 1; }
grep -q "riskTitle" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" && { echo "FAIL: statistics customer risk note title must not revive (removed 2026-11-06)" >&2; exit 1; }
grep -q "riskText" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" && { echo "FAIL: statistics customer risk note text must not revive (removed 2026-11-06)" >&2; exit 1; }
grep -q "statsView.filterSummary" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: mobile statistics filter summary UI is missing" >&2; exit 1; }
grep -q "statsView.filterChips" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: mobile statistics filter chip UI is missing" >&2; exit 1; }
grep -q "statsView.customerAsset.riskClass" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" && { echo "FAIL: statistics asset-card risk note UI must not revive (removed 2026-11-06)" >&2; exit 1; }
grep -q "stats-filter-head" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: mobile statistics refined filter header style is missing" >&2; exit 1; }
grep -q "stats-risk-note" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" && { echo "FAIL: statistics risk note style must not revive (removed 2026-11-06)" >&2; exit 1; }
grep -q "refreshLoading" "$statistics_detail_js" || { echo "FAIL: mobile statistics detail refresh state must be normalized in JS" >&2; exit 1; }
grep -q "refreshText" "$statistics_detail_js" || { echo "FAIL: mobile statistics detail refresh text must be normalized in JS" >&2; exit 1; }
grep -q "backDisabled" "$statistics_detail_js" || { echo "FAIL: mobile statistics detail back disabled state must be normalized in JS" >&2; exit 1; }
grep -q "detailView.refreshLoading" "$statistics_detail_wxml" || { echo "FAIL: mobile statistics detail refresh UI must use normalized state" >&2; exit 1; }
grep -q "detail-hero-actions" "$statistics_detail_wxml" || { echo "FAIL: mobile statistics detail hero actions are missing" >&2; exit 1; }
grep -q "detail-refresh-action" "$statistics_detail_wxss" || { echo "FAIL: mobile statistics detail refined refresh action style is missing" >&2; exit 1; }
for field in myCustomers inProtection expiring expired; do
  grep -q "dashboard.$field" "$page" || { echo "FAIL: mobile home does not display dashboard.$field" >&2; exit 1; }
done
for field in myCustomers inProtection expiring todayExpiring monthNew; do
  grep -q "stats.$field" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: mobile statistics does not display stats.$field" >&2; exit 1; }
done
grep -q 'wx:if="{{showDashboardCards}}"' "$ROOT/aigicrm-miniprogram/pages/home/index.wxml" || { echo "FAIL: dashboard cards are not gated in WXML" >&2; exit 1; }
grep -q "buildHomeView" "$page" || { echo "FAIL: home page does not normalize WXML state in JS" >&2; exit 1; }
grep -q "homeView.expiringRows" "$ROOT/aigicrm-miniprogram/pages/home/index.wxml" || { echo "FAIL: home expiring rows do not use normalized view state" >&2; exit 1; }
! grep -Eq "expiring\\.length|ownerName \\|\\||expireDate \\|\\||remainingDays \\|\\|" "$ROOT/aigicrm-miniprogram/pages/home/index.wxml" || { echo "FAIL: home WXML still keeps expiring fallback logic" >&2; exit 1; }
grep -q "'/api/statistics/dashboard'" "$api" || { echo "FAIL: dashboard API route is missing" >&2; exit 1; }
grep -q "@PreAuthorize(\"hasAuthority('statistics:view')\")" "$backend" || { echo "FAIL: dashboard backend statistics permission is missing" >&2; exit 1; }
# 【2026-11-12 裁决A】统计单键统一: 旧双码形态反钉防复活
! grep -q "@PreAuthorize(\"hasAnyAuthority('statistics:view','dashboard:view')\")" "$backend" || { echo "FAIL: retired dual-key statistics/dashboard OR still present" >&2; exit 1; }
grep -q "countExpiringTodayByScope" "$service" || { echo "FAIL: dashboard today-expiring does not use scope-aware count" >&2; exit 1; }
grep -q "dashboard.view.*已实现" "$manifest" || { echo "FAIL: dashboard manifest status is stale" >&2; exit 1; }
grep -Fq '| 工作台 | View dashboard | `GET /api/statistics/dashboard` | `statistics:view` | Current user/org/group scope inside service | Read/statistics | Yes | Implemented |' "$ROOT/docs/function-permission-matrix.md" || { echo "FAIL: dashboard matrix mobile status is stale" >&2; exit 1; }
grep -Fq '| Home | `pages/home/index` | Authenticated plus `statistics:view` for dashboard cards | `/api/auth/info`, `/api/statistics/dashboard`, reminder pending/expiring APIs | Shows user, role, dashboard cards and quick entries when permitted |' "$ROOT/docs/function-permission-matrix.md" || { echo "FAIL: home mobile page matrix row is stale" >&2; exit 1; }
grep -q 'verify:mobile-shell' "$automator_package" || { echo "FAIL: mobile shell DevTools automator script is not exposed" >&2; exit 1; }
grep -q 'verifyHomePage' "$automator_script" || { echo "FAIL: mobile shell DevTools automator does not verify home dashboard" >&2; exit 1; }
grep -q 'hm-chart-row' "$automator_script" || { echo "FAIL: mobile shell DevTools automator does not assert dashboard chart rows" >&2; exit 1; }
grep -q '今日到期' "$automator_script" || { echo "FAIL: mobile shell DevTools automator does not assert today-expiring dashboard metric" >&2; exit 1; }
grep -q 'home dashboard cards must be hidden without dashboard/statistics permission' "$automator_script" || { echo "FAIL: mobile shell DevTools automator does not assert dashboard permission hiding" >&2; exit 1; }
grep -q 'mobile-shell-home.png' "$automator_script" || { echo "FAIL: mobile shell DevTools automator does not capture home dashboard evidence" >&2; exit 1; }
echo "PASS: mobile dashboard data, permission, API, and manifest contract is aligned."
