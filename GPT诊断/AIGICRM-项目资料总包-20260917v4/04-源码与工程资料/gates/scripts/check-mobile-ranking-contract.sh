#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
api="$ROOT/aigicrm-miniprogram/api/score.js"
page="$ROOT/aigicrm-miniprogram/pages/ranking/index.js"
detail="$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.js"
backend="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java"
manifest="$ROOT/docs/unified-feature-manifest.md"
matrix="$ROOT/docs/function-permission-matrix.md"
statistics_api="$ROOT/aigicrm-miniprogram/api/statistics.js"
package_json="$ROOT/scripts/miniprogram/package.json"
verify="$ROOT/scripts/miniprogram/verify-ranking-center.mjs"

grep -q "'/api/score/ranking'" "$api" || { echo "FAIL: mobile ranking API route is missing" >&2; exit 1; }
grep -q "'/api/score/ranking/stats'" "$api" || { echo "FAIL: mobile ranking stats API route is missing" >&2; exit 1; }
grep -q "getRanking" "$api" || { echo "FAIL: mobile ranking API wrapper is missing" >&2; exit 1; }
grep -q "getRankingStats" "$api" || { echo "FAIL: mobile ranking stats API wrapper is missing" >&2; exit 1; }
! grep -q "'/api/score/grid'" "$api" || { echo "FAIL: mobile score API exposes PC-only score entry grid" >&2; exit 1; }
! grep -q "'/api/score/entries/batch'" "$api" || { echo "FAIL: mobile score API exposes PC-only score entry batch write" >&2; exit 1; }
! grep -q "score:entry" "$page" || { echo "FAIL: mobile ranking page exposes PC-only score entry permission" >&2; exit 1; }
grep -q "hasPermission('score:view')" "$page" || { echo "FAIL: mobile ranking permission guard is missing" >&2; exit 1; }
grep -q "hasPermission('statistics:view')" "$page" || { echo "FAIL: mobile ranking statistics permission fallback is missing" >&2; exit 1; }
grep -q "'/api/statistics/customer-rankings'" "$statistics_api" || { echo "FAIL: mobile customer ranking API route is missing" >&2; exit 1; }
grep -q "getCustomerRankings" "$page" || { echo "FAIL: mobile ranking page does not load customer rankings" >&2; exit 1; }
grep -q "currentPeriod()" "$page" || { echo "FAIL: mobile ranking page does not centralize the selected period" >&2; exit 1; }
grep -q "customerPeriodOptions" "$page" || { echo "FAIL: mobile customer ranking page has no PC-like customer period selector" >&2; exit 1; }
grep -q "currentCustomerPeriodParam" "$page" || { echo "FAIL: mobile customer ranking page does not omit empty all-period like PC" >&2; exit 1; }
grep -q "getCustomerRankings({ period: this.currentCustomerPeriodParam() })" "$page" || { echo "FAIL: mobile customer rankings do not receive the selected customer period like PC" >&2; exit 1; }
grep -q "getRankingStats" "$page" || { echo "FAIL: mobile ranking page does not load score stats" >&2; exit 1; }
grep -q "getScoreRanking" "$page" || { echo "FAIL: mobile ranking page does not load score personal/dept rankings" >&2; exit 1; }
grep -q "scoreActiveTab" "$page" || { echo "FAIL: mobile ranking page has no personal/dept score tab state" >&2; exit 1; }
grep -q "periodOptions" "$page" || { echo "FAIL: mobile ranking page has no score period selector" >&2; exit 1; }
grep -q "defaultRecordPeriod" "$page" || { echo "FAIL: mobile ranking page does not default score period to current-year full period" >&2; exit 1; }
grep -q "defaultPeriodIndex" "$page" || { echo "FAIL: mobile ranking page does not select the default full-year score period" >&2; exit 1; }
! grep -q "const periods = \\[{ label: '全部', value: '' }\\]" "$page" || { echo "FAIL: mobile score ranking exposes an empty all-period option unlike PC score ranking" >&2; exit 1; }
grep -q "scoreStats" "$page" || { echo "FAIL: mobile ranking page has no score stats model" >&2; exit 1; }
grep -q "topItemCount" "$page" || { echo "FAIL: mobile ranking page does not preserve score stats top item count" >&2; exit 1; }
grep -q "topItemCount" "$page" || { echo "FAIL: mobile ranking page does not display score stats top item count through view state" >&2; exit 1; }
grep -q "个人/部门排行使用评分周期" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking page does not communicate PC score period semantics" >&2; exit 1; }
grep -q "onCustomerPeriodChange" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile customer ranking period picker UI is missing" >&2; exit 1; }
grep -q "rankingView.customerPeriodText" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile customer ranking period label is missing" >&2; exit 1; }
grep -q "scoreList" "$page" || { echo "FAIL: mobile ranking page has no score ranking list model" >&2; exit 1; }
grep -q "scoreListView" "$page" || { echo "FAIL: mobile ranking page must normalize score rows before WXML rendering" >&2; exit 1; }
grep -q "customerListView" "$page" || { echo "FAIL: mobile ranking page must normalize customer rows before WXML rendering" >&2; exit 1; }
grep -q "parentStatsText" "$page" || { echo "FAIL: mobile ranking page must normalize PC incentive parent stats text" >&2; exit 1; }
grep -q "tabView" "$page" || { echo "FAIL: mobile ranking page must normalize tab active classes" >&2; exit 1; }
grep -q "buildRankingView" "$page" || { echo "FAIL: mobile ranking page must normalize shell state before WXML rendering" >&2; exit 1; }
grep -q "setRankingData" "$page" || { echo "FAIL: mobile ranking page must refresh normalized shell state with data changes" >&2; exit 1; }
grep -q "summaryCards" "$page" || { echo "FAIL: mobile ranking stats cards must be normalized before WXML rendering" >&2; exit 1; }
grep -q "contextSummary" "$page" || { echo "FAIL: mobile ranking context summary must be normalized before WXML rendering" >&2; exit 1; }
grep -q "contextChips" "$page" || { echo "FAIL: mobile ranking context chips must be normalized before WXML rendering" >&2; exit 1; }
grep -q "showScoreEmpty" "$page" || { echo "FAIL: mobile score ranking empty state must be normalized before WXML rendering" >&2; exit 1; }
grep -q "showCustomerEmpty" "$page" || { echo "FAIL: mobile customer ranking empty state must be normalized before WXML rendering" >&2; exit 1; }
grep -q "scoreTabsView" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized score tabs" >&2; exit 1; }
grep -q "customerListView" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized customer rows" >&2; exit 1; }
grep -q "rankingView.summaryCards" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized stats cards" >&2; exit 1; }
grep -q "rankingView.contextSummary" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized context summary" >&2; exit 1; }
grep -q "rankingView.contextChips" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized context chips" >&2; exit 1; }
grep -q "rankingView.topScoreText" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized top score text" >&2; exit 1; }
grep -q "ranking-context-card" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxss" || { echo "FAIL: mobile ranking context card style is missing" >&2; exit 1; }
grep -q "rankingView.scorePeriodText" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized score period text" >&2; exit 1; }
grep -q "rankingView.showLoading" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must render normalized loading state" >&2; exit 1; }
! grep -Eq "scoreActiveTab===|activeTab===|\\? .* :|byParent &&|parentIndex < incentiveParents|item\\.core \\?|scoreStats\\.[a-zA-Z]+ \\|\\||periodOptions\\[periodIndex\\]|customerPeriodOptions\\[customerPeriodIndex\\]|\\{\\{loading\\}\\}|!loading && .*\\.length===0" "$ROOT/aigicrm-miniprogram/pages/ranking/index.wxml" || { echo "FAIL: mobile ranking WXML must not keep score/customer branching or fallback logic" >&2; exit 1; }
grep -q "openDetail" "$page" || { echo "FAIL: mobile ranking detail action is missing" >&2; exit 1; }
grep -Fq '/pages/customer/detail/index?id=${item.customerId}&from=ranking' "$page" || { echo "FAIL: mobile ranking customer archive route is stale" >&2; exit 1; }
grep -q '"verify:ranking-center": "node verify-ranking-center.mjs"' "$package_json" || { echo "FAIL: mobile ranking DevTools automator npm entry is missing" >&2; exit 1; }
grep -q "verifyRankingCenterPage" "$verify" || { echo "FAIL: mobile ranking DevTools automator page check is missing" >&2; exit 1; }
grep -q "ranking-center.png" "$verify" || { echo "FAIL: mobile ranking DevTools automator screenshot is missing" >&2; exit 1; }
grep -q "switchScoreTab" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify personal/dept tab switching" >&2; exit 1; }
grep -q "switchTab" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify customer ranking tab switching" >&2; exit 1; }
grep -q "onPeriodChange" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify period filter" >&2; exit 1; }
grep -q "onCustomerPeriodChange" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify customer period filter" >&2; exit 1; }
grep -q "customerPeriodOptions" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify customer all-period semantics" >&2; exit 1; }
grep -q "2026全年" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify score full-year period semantics" >&2; exit 1; }
grep -q "42 个文件" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify PC top item count semantics" >&2; exit 1; }
grep -q "/pages/customer/detail/index?id=501&from=ranking" "$verify" || { echo "FAIL: mobile ranking DevTools automator does not verify customer archive navigation source" >&2; exit 1; }
grep -q "buildRankingDetailView" "$detail" || { echo "FAIL: mobile ranking detail display state must be normalized in JS" >&2; exit 1; }
grep -q "rankingDetailView.nameText" "$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.wxml" || { echo "FAIL: mobile ranking detail WXML must render normalized name text" >&2; exit 1; }
grep -q "rankingDetailView.deptText" "$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.wxml" || { echo "FAIL: mobile ranking detail WXML must render normalized dept text" >&2; exit 1; }
! grep -Eq "\\|\\| '未命名用户'|\\|\\| '-'|\\{\\{nameInitial\\}\\}|\\{\\{score\\}\\}|\\{\\{period\\}\\}" "$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.wxml" || { echo "FAIL: mobile ranking detail WXML must not keep fallback display logic" >&2; exit 1; }
grep -q "@GetMapping(\"/ranking\")" "$backend" || { echo "FAIL: backend ranking route is missing" >&2; exit 1; }
grep -q "@GetMapping(\"/ranking/stats\")" "$backend" || { echo "FAIL: backend ranking stats route is missing" >&2; exit 1; }
grep -q "hasAuthority('score:view')" "$backend" || { echo "FAIL: backend ranking permission is missing" >&2; exit 1; }
grep -q "score.ranking.*implemented" "$manifest" || { echo "FAIL: score.ranking manifest status is stale" >&2; exit 1; }
grep -q "score.entry.*PC-only" "$manifest" || { echo "FAIL: score.entry manifest must stay PC-only until mobile write approval" >&2; exit 1; }
grep -Fq '| 学分录入 | Grid | `GET /api/score/grid` | `score:manage` | Managed users/depts | Read | Yes | No |' "$matrix" || { echo "FAIL: score entry grid matrix must stay PC-only" >&2; exit 1; }
grep -Fq '| 学分录入 | Batch entry | `POST /api/score/entries/batch` | `score:entry` | Managed users/depts | Batch mutation | Yes | No |' "$matrix" || { echo "FAIL: score entry batch matrix must stay PC-only" >&2; exit 1; }
grep -Fq '| 统计排行 | Customer rankings | `GET /api/statistics/customer-rankings` | `statistics:view` | Role/org/group/customer scope | Read/statistics | Yes | Implemented |' "$matrix" || { echo "FAIL: customer rankings matrix status is stale" >&2; exit 1; }
grep -Fq '| 学分排行 | Ranking stats | `GET /api/score/ranking/stats` | `score:view` | Role/org/group scope | Read/statistics | Yes | Implemented |' "$matrix" || { echo "FAIL: score ranking stats matrix status is stale" >&2; exit 1; }
grep -Fq '| Score | `pages/ranking/index` | `score:view`, legacy `score:ranking` menu-compatible, or `statistics:view` for customer rankings | `/api/score/ranking`, `/api/score/ranking/stats`, `/api/statistics/customer-rankings` | Personal/dept score ranking, period filter, stats and customer ranking drilldown are implemented; score entry/config/export stay PC-first |' "$matrix" || { echo "FAIL: score mobile first-release row is stale" >&2; exit 1; }
test -s "$detail" || { echo "FAIL: mobile ranking detail page is missing" >&2; exit 1; }

echo "PASS: mobile score ranking page, API, permission, and manifest contract is aligned."
