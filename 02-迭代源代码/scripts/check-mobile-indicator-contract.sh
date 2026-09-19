#!/usr/bin/env bash
# Validate the mobile core-indicator overview contract without live credentials.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$ROOT/aigicrm-miniprogram/api/statistics.js"
PAGE="$ROOT/aigicrm-miniprogram/pages/indicator/index.js"
PAGE_WXML="$ROOT/aigicrm-miniprogram/pages/indicator/index.wxml"
APP="$ROOT/aigicrm-miniprogram/app.json"
APPS="$ROOT/aigicrm-miniprogram/pages/app/index.js"
AUTOMATOR_PACKAGE="$ROOT/scripts/miniprogram/package.json"
AUTOMATOR_SCRIPT="$ROOT/scripts/miniprogram/verify-indicator-overview.mjs"

fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq "url: '/api/statistics/indicator-stats'" "$API" || fail 'mobile indicator API wrapper route is missing'
grep -Fq "hasPermission('statistics:view')" "$PAGE" || fail 'mobile indicator page permission gate is missing'
grep -Fq "buildIndicatorView" "$PAGE" || fail 'mobile indicator page does not normalize display state in JS'
grep -Fq "applyState(patch)" "$PAGE" || fail 'mobile indicator page does not refresh normalized display state with data'
grep -Fq "indicatorView.metrics" "$PAGE_WXML" || fail 'mobile indicator overview metrics do not use normalized view state'
grep -Fq "indicatorView.ranking" "$PAGE_WXML" || fail 'mobile indicator ranking does not use normalized view state'
grep -Fq "indicatorView.showRankingEmpty" "$PAGE_WXML" || fail 'mobile indicator empty state does not use normalized view state'
! grep -Eq "stats\\.|ranking\\.length|index \\+ 1|\\? '核心'|\\|\\| 0|\\|\\| '未命名客户'" "$PAGE_WXML" || fail 'mobile indicator WXML still keeps display fallback logic'
grep -Fq '"pages/indicator/index"' "$APP" || fail 'mobile indicator page is not registered'
grep -Fq "key: 'indicator'" "$APPS" || fail 'mobile app menu indicator entry is missing'
grep -Fq "indicator: '/pages/indicator/index'" "$APPS" || fail 'mobile indicator navigation route is missing'
grep -Fq 'verify:indicator-overview' "$AUTOMATOR_PACKAGE" || fail 'mobile indicator DevTools automator script is not exposed'
grep -Fq 'verifyIndicatorOverviewPage' "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not verify overview rendering'
grep -Fq 'verifyPermissionBoundary' "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not verify permission boundary'
grep -Fq '核心客户六项指标' "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not assert indicator title'
grep -Fq '财务达标率' "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not assert finance threshold metric'
grep -Fq '客户排行' "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not assert ranking section'
grep -Fq 'indicator-overview.png' "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not capture overview evidence'
grep -Fq '没有统计查看权限' "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not assert permission toast'
grep -Fq "/pages/home/index" "$AUTOMATOR_SCRIPT" || fail 'mobile indicator DevTools automator does not assert permission fallback route'

echo 'PASS: mobile indicator overview contract is aligned.'
