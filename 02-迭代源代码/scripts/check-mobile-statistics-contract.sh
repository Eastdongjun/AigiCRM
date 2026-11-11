#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
api="$ROOT/aigicrm-miniprogram/api/statistics.js"
page="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.wxml"
page_js="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.js"
backend="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/controller/StatisticsController.java"
service="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java"
automator_package="$ROOT/scripts/miniprogram/package.json"
automator_script="$ROOT/scripts/miniprogram/verify-statistics-center.mjs"

grep -q "getTeamActivities" "$api" || { echo "FAIL: mobile statistics API wrapper is missing getTeamActivities" >&2; exit 1; }
grep -q "getAnalysis" "$api" || { echo "FAIL: mobile statistics API wrapper is missing getAnalysis" >&2; exit 1; }
grep -q "'/api/statistics/analysis'" "$api" || { echo "FAIL: mobile statistics analysis API route is miswired" >&2; exit 1; }
grep -q "'/api/statistics/team-activities'" "$api" || { echo "FAIL: mobile statistics API route is miswired" >&2; exit 1; }
grep -q "getDailyStats" "$api" || { echo "FAIL: mobile statistics API wrapper is missing getDailyStats" >&2; exit 1; }
grep -q "getWeeklyStats" "$api" || { echo "FAIL: mobile statistics API wrapper is missing getWeeklyStats" >&2; exit 1; }
grep -q "'/api/statistics/daily-stats'" "$api" || { echo "FAIL: mobile daily statistics API route is miswired" >&2; exit 1; }
grep -q "'/api/statistics/weekly-stats'" "$api" || { echo "FAIL: mobile weekly statistics API route is miswired" >&2; exit 1; }
grep -q "getDailyStats" "$page_js" || { echo "FAIL: statistics detail does not load daily stats" >&2; exit 1; }
grep -q "getWeeklyStats" "$page_js" || { echo "FAIL: statistics detail does not load weekly stats" >&2; exit 1; }
grep -q "hasPermission('statistics:view')" "$page_js" || { echo "FAIL: statistics detail permission guard is missing" >&2; exit 1; }
grep -q "normalizeStats" "$page_js" || { echo "FAIL: statistics detail does not normalize dashboard metrics" >&2; exit 1; }
grep -q "protectionRate: percent(inProtection, myCustomers)" "$page_js" || { echo "FAIL: statistics detail protection rate is not data-driven" >&2; exit 1; }
grep -q "riskTotal: expiring + todayExpiring + expired" "$page_js" || { echo "FAIL: statistics detail risk total is not data-driven" >&2; exit 1; }
grep -q "normalizeDaily" "$page_js" || { echo "FAIL: statistics detail daily trend arrays are not normalized" >&2; exit 1; }
grep -q "normalizeWeekly" "$page_js" || { echo "FAIL: statistics detail weekly trend arrays are not normalized" >&2; exit 1; }
grep -q "buildDetailView" "$page_js" || { echo "FAIL: statistics detail does not normalize display state in JS" >&2; exit 1; }
grep -q "applyState(patch)" "$page_js" || { echo "FAIL: statistics detail does not refresh normalized display state with data" >&2; exit 1; }
grep -q "summaryTiles" "$page_js" || { echo "FAIL: statistics detail summary tiles are not normalized" >&2; exit 1; }
grep -q "customerRows" "$page_js" || { echo "FAIL: statistics detail customer rows are not normalized" >&2; exit 1; }
grep -q "approvalRows" "$page_js" || { echo "FAIL: statistics detail approval rows are not normalized" >&2; exit 1; }
grep -q "seaRows" "$page_js" || { echo "FAIL: statistics detail sea rows are not normalized" >&2; exit 1; }
grep -q "dailyRows" "$page_js" || { echo "FAIL: statistics detail daily rows are not normalized" >&2; exit 1; }
grep -q "weeklyRows" "$page_js" || { echo "FAIL: statistics detail weekly rows are not normalized" >&2; exit 1; }
grep -q "getAnalysis" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not load analysis data" >&2; exit 1; }
grep -q "getScoreRanking" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not load PC score ranking data" >&2; exit 1; }
grep -q "getScopeDepts" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not load PC department filter options" >&2; exit 1; }
grep -q "buildAnalysisParams" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not build PC analysis filter params" >&2; exit 1; }
grep -q "params.deptId = dept.id" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not pass deptId to analysis like PC" >&2; exit 1; }
grep -q "const params = { year }" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not pass year to analysis like PC" >&2; exit 1; }
grep -q "monthlyCustomers" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not consume monthly customer trend" >&2; exit 1; }
grep -q "monthlyFiles" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not consume monthly file trend" >&2; exit 1; }
grep -q "buildStatusRows" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not build customer status distribution" >&2; exit 1; }
grep -q "buildIndustryRows" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not build PC industry distribution" >&2; exit 1; }
grep -q "scoreTop5" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not keep PC score top5" >&2; exit 1; }
grep -q "enrichDashboardStats" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not derive dashboard rates from data" >&2; exit 1; }
grep -q "buildStatsView" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not normalize display state in JS" >&2; exit 1; }
grep -q "setStatisticsData" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview does not refresh normalized display state with data" >&2; exit 1; }
grep -q "refreshLoading" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview refresh loading state is not normalized" >&2; exit 1; }
grep -q "refreshText" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview refresh text is not normalized" >&2; exit 1; }
grep -q "showLoading" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview page loading state is not normalized" >&2; exit 1; }
grep -q "loadingText" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview loading text is not normalized" >&2; exit 1; }
grep -q "signalSummary" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview signal summary must be normalized in JS" >&2; exit 1; }
grep -q "buildOfficeRows" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview office rows are not normalized" >&2; exit 1; }
grep -q "buildScoreRows" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview score rows are not normalized" >&2; exit 1; }
grep -q "buildExpiringRows" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview expiring rows are not normalized" >&2; exit 1; }
grep -q "protectionRate: percent(inProtection, myCustomers)" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview protection rate is not data-driven" >&2; exit 1; }
grep -q "riskTotal: expiring + todayExpiring + expired" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview risk total is not data-driven" >&2; exit 1; }
grep -q "业务总览" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no business overview panel (unified naming 2026-11-04)" >&2; exit 1; }
grep -q "经营信号" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no signal summary panel" >&2; exit 1; }
grep -q "分析筛选" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no PC analysis filter panel" >&2; exit 1; }
grep -q "chooseYear" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no year filter UI" >&2; exit 1; }
grep -q "chooseDept" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no department filter UI" >&2; exit 1; }
grep -q "全部部门" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview department filter default copy is missing" >&2; exit 1; }
grep -q "月度趋势" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no monthly trend panel" >&2; exit 1; }
grep -q "月度新增客户" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no monthly customer trend" >&2; exit 1; }
grep -q "月度文件上传" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no monthly file trend" >&2; exit 1; }
grep -q "客户状态分布" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no customer status distribution" >&2; exit 1; }
grep -q "行业分布" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no PC industry distribution" >&2; exit 1; }
grep -q "人员学分 Top5" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no PC score top5" >&2; exit 1; }
grep -q "办事处对比" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview has no office comparison panel" >&2; exit 1; }
grep -q 'statsView.customerAsset.protectionWidth' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview protection progress is not bound to normalized data" >&2; exit 1; }
grep -q 'statsView.riskTiles' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview risk radar does not use normalized tiles" >&2; exit 1; }
grep -q 'statsView.analysisTiles' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview analysis tiles do not use normalized tiles" >&2; exit 1; }
grep -q 'statsView.approvalRows' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview approval rows do not use normalized data" >&2; exit 1; }
grep -q 'statsView.monthlyCustomer.totalText' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview monthly customer summary is not normalized" >&2; exit 1; }
grep -q 'statsView.monthlyFile.totalText' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview monthly file summary is not normalized" >&2; exit 1; }
grep -q 'statsView.industryRows' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview industry rows do not use normalized data" >&2; exit 1; }
grep -q 'statsView.officeRows' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview office rows do not use normalized data" >&2; exit 1; }
grep -q 'statsView.scoreRows' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview score rows do not use normalized data" >&2; exit 1; }
grep -q 'statsView.expiringRows' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview expiring rows do not use normalized data" >&2; exit 1; }
grep -q 'statsView.refreshLoading' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview refresh button does not use normalized loading state" >&2; exit 1; }
grep -q 'statsView.refreshText' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview refresh button does not use normalized text" >&2; exit 1; }
grep -q 'statsView.showLoading' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview loading card does not use normalized visibility" >&2; exit 1; }
grep -q 'statsView.loadingText' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview loading card does not use normalized copy" >&2; exit 1; }
grep -q 'statsView.signalSummary.rows' "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview signal summary rows do not use normalized data" >&2; exit 1; }
grep -q "风险合计" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" "$ROOT/aigicrm-miniprogram/pages/statistics/index.js" || { echo "FAIL: statistics overview risk summary UI is missing" >&2; exit 1; }
! grep -Eq "\\{\\{loading\\}\\}|wx:if=\"\\{\\{loading\\}\\}\"" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview WXML still binds raw loading state" >&2; exit 1; }
! grep -Eq "stats\\.(monthNew|approvalStats|myCustomers|protectionRate|riskTotal|inProtection|expiring|todayExpiring|expired) \\|\\| 0|analysis\\.(totalCustomers|yearNew|expiring|expired) \\|\\| 0|yearOptions\\[selectedYearIndex\\]|deptOptions\\[selectedDeptIndex\\]" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview WXML must not keep top-level dashboard branching logic" >&2; exit 1; }
! grep -Eq "monthly(Customer|File)Summary\\.(total|average|peak)|industryRows\\.length|officeComparison\\.length|scoreTop5\\.length|expiringDetails\\.length|index \\+ 1|\\|\\| 0|\\|\\| '-'" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview WXML still keeps collection fallback logic" >&2; exit 1; }
grep -q "stats-detail-action" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxml" || { echo "FAIL: statistics overview detail action must use local compact style" >&2; exit 1; }
grep -q "stats-signal-card" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: statistics overview signal summary style is missing" >&2; exit 1; }
grep -q ".stats-detail-action" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: statistics overview detail action style is missing" >&2; exit 1; }
grep -q "width: 236rpx" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: statistics overview detail action must stay compact" >&2; exit 1; }
grep -q "margin: 26rpx 0 0 auto" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: statistics overview detail action must align as a trailing action" >&2; exit 1; }
grep -q ".statistics-page .stats-section-head .text-btn" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: statistics overview section actions must use compact local button style" >&2; exit 1; }
grep -q "height: 42rpx" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: statistics overview section actions are too large" >&2; exit 1; }
grep -q "stats-refresh-action" "$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.wxml" || { echo "FAIL: statistics detail refresh action must use local compact style" >&2; exit 1; }
grep -q ".stats-refresh-action" "$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.wxss" || { echo "FAIL: statistics detail refresh action style is missing" >&2; exit 1; }
! grep -q "width:72%" "$ROOT/aigicrm-miniprogram/app.wxss" "$ROOT/aigicrm-miniprogram/pages/statistics/index.wxss" || { echo "FAIL: statistics overview still contains a fixed protection progress width" >&2; exit 1; }
grep -q "verify:statistics-center" "$automator_package" || { echo "FAIL: statistics center DevTools automator script is not exposed" >&2; exit 1; }
grep -q "verifyStatisticsCenterPage" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not verify page UI" >&2; exit 1; }
grep -q "stats-primary-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert primary card" >&2; exit 1; }
grep -q "stats-filter-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert analysis filters" >&2; exit 1; }
grep -q "filterState.deptId === 21" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert department filter state" >&2; exit 1; }
grep -q "filterState.selectedYearIndex === 1" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert year filter state" >&2; exit 1; }
grep -q "stats-trend-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert monthly trend cards" >&2; exit 1; }
grep -q "trend-bar-fill.customer" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert monthly customer bars" >&2; exit 1; }
grep -q "trend-bar-fill.file" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert monthly file bars" >&2; exit 1; }
grep -q "stats-status-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert customer status distribution" >&2; exit 1; }
grep -q "stats-industry-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert industry distribution" >&2; exit 1; }
grep -q "stats-score-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert score top5" >&2; exit 1; }
grep -q "stats-office-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert office comparison" >&2; exit 1; }
grep -q "stats-approval-card" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert approval summary" >&2; exit 1; }
grep -q "statistics-center.png" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not capture UI evidence" >&2; exit 1; }
grep -q "/pages/statistics/detail/index" "$automator_script" || { echo "FAIL: statistics center DevTools automator does not assert detail navigation" >&2; exit 1; }
grep -q 'detailView.tabs' "$page" || { echo "FAIL: mobile statistics detail tabs do not use normalized view state" >&2; exit 1; }
grep -q 'data-section="{{item.key}}"' "$page" || { echo "FAIL: mobile statistics detail tabs do not preserve section switching" >&2; exit 1; }
grep -q 'detailView.summaryTiles' "$page" || { echo "FAIL: mobile statistics detail does not display normalized summary tiles" >&2; exit 1; }
grep -q 'detailView.customerRows' "$page" || { echo "FAIL: mobile statistics detail does not display normalized customer rows" >&2; exit 1; }
grep -q 'detailView.approvalRows' "$page" || { echo "FAIL: mobile statistics detail does not display normalized approval rows" >&2; exit 1; }
grep -q 'detailView.seaRows' "$page" || { echo "FAIL: mobile statistics detail does not display normalized sea rows" >&2; exit 1; }
grep -q 'detailView.activities' "$page" || { echo "FAIL: mobile statistics detail does not display normalized team activities" >&2; exit 1; }
grep -q 'detailView.dailyRows' "$page" || { echo "FAIL: mobile statistics detail does not display normalized daily trend rows" >&2; exit 1; }
grep -q 'detailView.weeklyRows' "$page" || { echo "FAIL: mobile statistics detail does not display normalized weekly trend rows" >&2; exit 1; }
! grep -Eq "section==='|activities\\.length|daily\\.[a-zA-Z]+\\.length|weekly\\.[a-zA-Z]+\\.length|stats\\.|\\|\\|" "$page" || { echo "FAIL: statistics detail WXML must not keep dashboard branching or fallback logic" >&2; exit 1; }
grep -q 'team-activities' "$backend" || { echo "FAIL: backend team activities route is missing" >&2; exit 1; }
grep -q 'daily-stats' "$backend" || { echo "FAIL: backend daily statistics route is missing" >&2; exit 1; }
grep -q 'weekly-stats' "$backend" || { echo "FAIL: backend weekly statistics route is missing" >&2; exit 1; }
grep -q 'analysis' "$backend" || { echo "FAIL: backend analysis route is missing" >&2; exit 1; }
grep -q "@PreAuthorize(\"hasAuthority('statistics:view')\")" "$backend" || { echo "FAIL: statistics backend view permission is missing" >&2; exit 1; }
# 【2026-11-12 裁决A】统计单键统一: 旧双码 fallback 形态反钉防复活
! grep -q "@PreAuthorize(\"hasAnyAuthority('statistics:view','dashboard:view')\")" "$backend" || { echo "FAIL: retired dual-key statistics/dashboard OR still present" >&2; exit 1; }
grep -q "customerService.dailyNewCustomers()" "$service" || { echo "FAIL: daily customer stats do not use scope-aware customer service" >&2; exit 1; }
grep -q "customerService.weeklyNewCustomers()" "$service" || { echo "FAIL: weekly customer stats do not use scope-aware customer service" >&2; exit 1; }
grep -q "applyFileScope(" "$service" || { echo "FAIL: file statistics do not apply account scope" >&2; exit 1; }
grep -q "getCurrentScopeUserIds()" "$service" || { echo "FAIL: team activities do not resolve current account scope" >&2; exit 1; }
grep -q "visibleUserIds" "$service" || { echo "FAIL: team activities do not constrain visible user ids" >&2; exit 1; }

echo "PASS: mobile team-activity statistics contract is aligned."
