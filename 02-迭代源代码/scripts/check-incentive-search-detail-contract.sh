#!/usr/bin/env bash
# Contract for the incentive search customer-detail timeline and preview feature.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SEARCH_VIEW="$ROOT/aigicrm-ui/src/views/incentive/search.vue"
SEARCH_SPEC="$ROOT/aigicrm-ui/src/views/incentive/search.spec.js"
TIME_UTIL="$ROOT/aigicrm-ui/src/utils/time-label.js"
TIME_UTIL_SPEC="$ROOT/aigicrm-ui/src/utils/time-label.spec.js"
API="$ROOT/aigicrm-ui/src/api/incentive.js"
API_DOC="$ROOT/docs/api/api-list.md"
FEATURE_AUDIT="$ROOT/docs/unified-feature-audit.md"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required incentive search-detail file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in "$SEARCH_VIEW" "$SEARCH_SPEC" "$TIME_UTIL" "$TIME_UTIL_SPEC" "$API" "$API_DOC" "$FEATURE_AUDIT"; do
  assert_file "$file"
done

assert_contains "$SEARCH_VIEW" "request.get('/incentive/customer-records'"
assert_contains "$SEARCH_VIEW" "showDetail(c)"
assert_contains "$SEARCH_VIEW" "markIncentiveRead(id)"
assert_contains "$SEARCH_VIEW" "groupRecordsByMonth(detailCust.value?.records)"
assert_contains "$SEARCH_VIEW" "relativeDay(r.createdAt)"
assert_contains "$SEARCH_VIEW" "暂无激励记录"
assert_contains "$SEARCH_VIEW" "preview(f.id, f.type, f.label, previewableOf(r))"
assert_contains "$SEARCH_VIEW" "downloadFile(f.id, f.label)"
assert_contains "$SEARCH_VIEW" "recordsByMonth.length"
assert_contains "$SEARCH_VIEW" "detailCust.totalScore"

assert_contains "$SEARCH_SPEC" "查看详情：弹窗渲染客户概览与记录时间线"
assert_contains "$SEARCH_SPEC" "查看详情：无记录时展示空态而不是空白"
assert_contains "$SEARCH_SPEC" "时间轴按月分组"
assert_contains "$SEARCH_SPEC" "灯箱预览：可左右循环切换"
assert_contains "$SEARCH_SPEC" "wrapper.vm.showDetail"
assert_contains "$SEARCH_SPEC" "markIncentiveRead"

assert_contains "$TIME_UTIL" "relativeDay"
assert_contains "$TIME_UTIL" "groupRecordsByMonth"
assert_contains "$TIME_UTIL" "UNKNOWN_MONTH_KEY"
assert_contains "$TIME_UTIL" "monthLabelOf"
assert_contains "$TIME_UTIL_SPEC" "按月倒序分组"
assert_contains "$TIME_UTIL_SPEC" "无有效时间的记录归入「时间待补」组且排最后"
assert_contains "$TIME_UTIL_SPEC" "兼容 ISO 串"

assert_contains "$API" "getCustomerRecords(params)"
assert_contains "$API" "/incentive/customer-records"
assert_contains "$API" "markIncentiveRead(recordId)"
assert_contains "$API" "/incentive/records/\${recordId}/read"
assert_contains "$API_DOC" "| GET | \`/api/incentive/customer-records\` | \`hasAuthority('incentive:view')\` | customerRecords | IncentiveController.java |"
assert_contains "$API_DOC" "| POST | \`/api/incentive/records/{id}/read\` | \`hasAuthority('incentive:view')\` | markRead | IncentiveController.java |"
assert_contains "$FEATURE_AUDIT" "| \`getCustomerRecords\` | \`GET\` | \`/incentive/customer-records\` |"
assert_contains "$FEATURE_AUDIT" "| \`markIncentiveRead\` | \`POST\` | \`/incentive/records/\${recordId}/read\` |"

echo "PASS: incentive search customer-detail timeline, read marking, preview, API, and tests are wired."
