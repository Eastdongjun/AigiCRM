#!/usr/bin/env bash
# Contract for the incentive statistics detail drill-down feature wiring.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERMISSION="incentive:view"
ROUTE_PATH="incentive/stats/detail/:childId"

ROUTER="$ROOT/aigicrm-ui/src/router/index.js"
API="$ROOT/aigicrm-ui/src/api/incentive.js"
STATS_VIEW="$ROOT/aigicrm-ui/src/views/incentive/stats.vue"
TABLE_VIEW="$ROOT/aigicrm-ui/src/views/incentive/StatsTableView.vue"
DETAIL_VIEW="$ROOT/aigicrm-ui/src/views/incentive/StatsDetail.vue"
DETAIL_SPEC="$ROOT/aigicrm-ui/src/views/incentive/StatsDetail.spec.js"
UTIL="$ROOT/aigicrm-ui/src/utils/incentive-detail.js"
UTIL_SPEC="$ROOT/aigicrm-ui/src/utils/incentive-detail.spec.js"
PERIOD_UTIL="$ROOT/aigicrm-ui/src/utils/incentive-period.js"
STATS_COMPOSABLE="$ROOT/aigicrm-ui/src/composables/useIncentiveStats.js"
API_DOC="$ROOT/docs/api/api-list.md"
FEATURE_AUDIT="$ROOT/docs/unified-feature-audit.md"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required incentive stats-detail file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in \
  "$ROUTER" "$API" "$STATS_VIEW" "$TABLE_VIEW" "$DETAIL_VIEW" "$DETAIL_SPEC" \
  "$UTIL" "$UTIL_SPEC" "$PERIOD_UTIL" "$STATS_COMPOSABLE" "$API_DOC" "$FEATURE_AUDIT"; do
  assert_file "$file"
done

assert_contains "$ROUTER" "path: '$ROUTE_PATH'"
assert_contains "$ROUTER" "name: 'IncentiveStatsDetail'"
assert_contains "$ROUTER" "StatsDetail.vue"
assert_contains "$ROUTER" "permission: '$PERMISSION'"

assert_contains "$API" "getRecords(params)"
assert_contains "$API" "/incentive/records"
assert_contains "$API" "getCategoryStats(params)"
assert_contains "$API" "/incentive/category-stats"

assert_contains "$STATS_VIEW" "provide(IncentiveStatsKey, stats)"
assert_contains "$STATS_VIEW" "StatsTableView"
assert_contains "$STATS_COMPOSABLE" "getCategoryStats({ period: p })"
assert_contains "$STATS_COMPOSABLE" "groupedData"

assert_contains "$TABLE_VIEW" "buildDetailLocation"
assert_contains "$TABLE_VIEW" "router.push(buildDetailLocation"
assert_contains "$TABLE_VIEW" "childId: child.id"
assert_contains "$TABLE_VIEW" "parentId: group.parentId"
assert_contains "$TABLE_VIEW" "period: period?.value"

assert_contains "$DETAIL_VIEW" "getRecords"
assert_contains "$DETAIL_VIEW" "parentCategoryId"
assert_contains "$DETAIL_VIEW" "filterRecordsByCategory"
assert_contains "$DETAIL_VIEW" "periodQueryValue(period.value)"
assert_contains "$DETAIL_VIEW" "slotFiles(row, slot)"
assert_contains "$DETAIL_VIEW" "downloadFileUtil"

assert_contains "$DETAIL_SPEC" "getRecords"
assert_contains "$DETAIL_SPEC" "parentCategoryId: 4"
assert_contains "$DETAIL_SPEC" "not.toContain('不应展示客户')"
assert_contains "$DETAIL_SPEC" "downloadFile"

assert_contains "$UTIL" "DETAIL_ROUTE_NAME"
assert_contains "$UTIL" "buildDetailLocation"
assert_contains "$UTIL" "filterRecordsByCategory"
assert_contains "$UTIL" "SLOT_FIELDS"
assert_contains "$UTIL" "slotFiles"
assert_contains "$UTIL" "sumScore"
assert_contains "$UTIL_SPEC" "filterRecordsByCategory"
assert_contains "$UTIL_SPEC" "buildDetailLocation"
assert_contains "$UTIL_SPEC" "slotFiles"
assert_contains "$UTIL_SPEC" "slotCounts"

assert_contains "$PERIOD_UTIL" "periodQueryValue"
assert_contains "$API_DOC" "| GET | \`/api/incentive/records\` | \`hasAuthority('incentive:view')\` | getRecords | IncentiveController.java |"
# 【重锚 2026-11-08】category-stats 权限码随 V2026.11.04.1"逐功能建码"已由 incentive:view
# 改为 incentive:stats:view(代码与 api-list.md 均为新码), 本钉原钉旧码随 regeneration 同步重锚
assert_contains "$API_DOC" "| GET | \`/api/incentive/category-stats\` | \`hasAuthority('incentive:stats:view')\` | categoryStats | IncentiveController.java |"
assert_contains "$FEATURE_AUDIT" "| \`GET\` | \`/api/incentive/records\` | \`hasAuthority('incentive:view')\` |"
assert_contains "$FEATURE_AUDIT" "| \`getCategoryStats\` | \`GET\` | \`/incentive/category-stats\` |"

echo "PASS: incentive stats-detail route, API, view, utility, docs, and tests are wired."
