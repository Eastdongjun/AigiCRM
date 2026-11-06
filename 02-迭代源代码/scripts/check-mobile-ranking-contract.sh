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
util="$ROOT/aigicrm-miniprogram/utils/ranking-tabs.js"
package_json="$ROOT/scripts/miniprogram/package.json"
verify="$ROOT/scripts/miniprogram/verify-ranking-center.mjs"

grep -q "'/api/score/ranking'" "$api" || { echo "FAIL: mobile ranking API route is missing" >&2; exit 1; }
grep -q "'/api/score/ranking/stats'" "$api" || { echo "FAIL: mobile ranking stats API route is missing" >&2; exit 1; }
grep -q "getRanking" "$api" || { echo "FAIL: mobile ranking API wrapper is missing" >&2; exit 1; }
grep -q "getRankingStats" "$api" || { echo "FAIL: mobile ranking stats API wrapper is missing" >&2; exit 1; }
# 【2026-11-06 重锚】/api/score/grid 以只读明细源放开(MP 学分明细页拼逐项得分, score:view 同门同码,
# 录入面仍在 PC): 反钉从"禁 grid"收窄为"禁写通道 entries/batch 与 score:entry 码"——防复活口径不变
! grep -q "'/api/score/entries/batch'" "$api" || { echo "FAIL: mobile score API exposes PC-only score entry batch write" >&2; exit 1; }
! grep -q "score:entry" "$page" || { echo "FAIL: mobile ranking page exposes PC-only score entry permission" >&2; exit 1; }
grep -q "score: 'score:view'" "$util" || { echo "FAIL: ranking util must bind registry code score:view" >&2; exit 1; }
grep -q "customer: 'statistics:view'" "$util" || { echo "FAIL: ranking util must bind registry code statistics:view" >&2; exit 1; }
grep -q "incentive: 'incentive:view'" "$util" || { echo "FAIL: ranking util must bind registry code incentive:view" >&2; exit 1; }
# 【2026-11-04 注册表对齐】页面经纯函数 util 派生 canTabs/默认页签/可见页签; 入口=注册码并集, 取代 mp-3 单码补丁
grep -q "rankingTabs.computeCanTabs(hasPermission, demo)" "$page" || { echo "FAIL: mobile ranking must derive canTabs via util computeCanTabs" >&2; exit 1; }
grep -q "rankingTabs.firstAllowedTab(canTabs)" "$page" || { echo "FAIL: mobile ranking default tab must come from util firstAllowedTab" >&2; exit 1; }
grep -q "rankingTabs.visibleMainTabs(canTabs)" "$page" || { echo "FAIL: mobile ranking main tabs must render via util visibleMainTabs" >&2; exit 1; }
grep -Fq "if (canTabs[rankTab] === false) return" "$page" || { echo "FAIL: mobile ranking must block switching to tabs the role has no code for" >&2; exit 1; }
! grep -q "hasPermission('statistics:view')" "$page" || { echo "FAIL: mobile ranking must not single-gate the whole page on statistics:view (registry union per tab instead)" >&2; exit 1; }
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
grep -q "parentStatsRows" "$page" || { echo "FAIL: mobile ranking page must normalize PC incentive parent stats rows" >&2; exit 1; }
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
# 【2026-11-06 重锚】明细页 UI 重设计(用户令"很丑,好好设计注意骨架"): 归一化函数更名 buildDetailView,
# 部门/周期并入 metaText 单行; 归一化意图不变(展示兜底一律在 JS, WXML 零回退逻辑, 见下行反钉)
grep -q "buildDetailView" "$detail" || { echo "FAIL: mobile ranking detail display state must be normalized in JS" >&2; exit 1; }
grep -q "header.nameText" "$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.wxml" || { echo "FAIL: mobile ranking detail WXML must render normalized name text" >&2; exit 1; }
grep -q "header.metaText" "$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.wxml" || { echo "FAIL: mobile ranking detail WXML must render normalized meta text" >&2; exit 1; }
! grep -Eq "\\|\\| '未命名用户'|\\|\\| '-'|\\{\\{nameInitial\\}\\}|\\{\\{score\\}\\}|\\{\\{period\\}\\}" "$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.wxml" || { echo "FAIL: mobile ranking detail WXML must not keep fallback display logic" >&2; exit 1; }
# 【2026-11-07 用户令 A=理顺码系】明细按大类分组与字典同源: 后端 getGrid 从 score_category 字典
# 回填 categoryName, MP 明细页按 categoryName 分组展示(行名=product)——拼音码不入 UI
grep -q 'listDataByCode("score_category")' "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/score/service/ScoreService.java" || { echo "FAIL: grid must enrich categoryName from score_category dictionary" >&2; exit 1; }
grep -q "item.categoryName" "$detail" || { echo "FAIL: mobile ranking detail must group by dictionary categoryName" >&2; exit 1; }
grep -q "itemsView.groups" "$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.wxml" || { echo "FAIL: mobile ranking detail WXML must render grouped breakdown" >&2; exit 1; }
# 【2026-11-07 用户令"部门排行做成明细, PC端也一样"】部门成员明细三件套:
# 后端端点(score:view 同门)+MP api 封装+dept 模式消费; PC 弹窗经 PC 构建验证
grep -q '@GetMapping("/ranking/dept-members")' "$backend" || { echo "FAIL: backend dept-members route is missing" >&2; exit 1; }
grep -q "'/api/score/ranking/dept-members'" "$ROOT/aigicrm-miniprogram/api/score.js" || { echo "FAIL: mobile dept-members API wrapper is missing" >&2; exit 1; }
grep -q "getScoreDeptMembers" "$detail" || { echo "FAIL: mobile ranking detail dept mode must load dept members" >&2; exit 1; }
grep -q "@GetMapping(\"/ranking\")" "$backend" || { echo "FAIL: backend ranking route is missing" >&2; exit 1; }
grep -q "@GetMapping(\"/ranking/stats\")" "$backend" || { echo "FAIL: backend ranking stats route is missing" >&2; exit 1; }
grep -q "hasAuthority('score:view')" "$backend" || { echo "FAIL: backend ranking permission is missing" >&2; exit 1; }
grep -q "score.ranking.*已实现" "$manifest" || { echo "FAIL: score.ranking manifest status is stale" >&2; exit 1; }
grep -q "score.entry.*PC-only" "$manifest" || { echo "FAIL: score.entry manifest must stay PC-only until mobile write approval" >&2; exit 1; }
grep -Fq '| 学分录入 | Grid | `GET /api/score/grid` | `score:manage` | Managed users/depts | Read | Yes | No |' "$matrix" || { echo "FAIL: score entry grid matrix must stay PC-only" >&2; exit 1; }
grep -Fq '| 学分录入 | Batch entry | `POST /api/score/entries/batch` | `score:entry` | Managed users/depts | Batch mutation | Yes | No |' "$matrix" || { echo "FAIL: score entry batch matrix must stay PC-only" >&2; exit 1; }
grep -Fq '| 统计排行 | Customer rankings | `GET /api/statistics/customer-rankings` | `statistics:view` | Role/org/group/customer scope | Read/statistics | Yes | Implemented |' "$matrix" || { echo "FAIL: customer rankings matrix status is stale" >&2; exit 1; }
grep -Fq '| 学分排行 | Ranking stats | `GET /api/score/ranking/stats` | `score:view` | Role/org/group scope | Read/statistics | Yes | Implemented |' "$matrix" || { echo "FAIL: score ranking stats matrix status is stale" >&2; exit 1; }
grep -Fq '| Score | `pages/ranking/index` | `score:view`, legacy `score:ranking` menu-compatible, or `statistics:view` for customer rankings | `/api/score/ranking`, `/api/score/ranking/stats`, `/api/statistics/customer-rankings` | Personal/dept score ranking, period filter, stats and customer ranking drilldown are implemented; score entry/config/export stay PC-first |' "$matrix" || { echo "FAIL: score mobile first-release row is stale" >&2; exit 1; }
test -s "$detail" || { echo "FAIL: mobile ranking detail page is missing" >&2; exit 1; }

echo "PASS: mobile score ranking page, API, permission, and manifest contract is aligned."
