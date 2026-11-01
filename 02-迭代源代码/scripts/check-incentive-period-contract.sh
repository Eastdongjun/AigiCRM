#!/usr/bin/env bash
# Contract for the shared incentive/score period semantics.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PERIOD_UTIL="$ROOT/aigicrm-ui/src/utils/incentive-period.js"
PERIOD_SPEC="$ROOT/aigicrm-ui/src/utils/incentive-period.spec.js"
UPLOAD="$ROOT/aigicrm-ui/src/composables/useIncentiveUpload.js"
UPLOAD_SPEC="$ROOT/aigicrm-ui/src/composables/useIncentiveUpload.spec.js"
STATS="$ROOT/aigicrm-ui/src/composables/useIncentiveStats.js"
STATS_DETAIL="$ROOT/aigicrm-ui/src/views/incentive/StatsDetail.vue"
STATS_DETAIL_SPEC="$ROOT/aigicrm-ui/src/views/incentive/StatsDetail.spec.js"
CUSTOMER_RANKING="$ROOT/aigicrm-ui/src/views/ranking/customer.vue"
SCORE_RANKING="$ROOT/aigicrm-ui/src/views/score/ranking.vue"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required incentive-period file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in "$PERIOD_UTIL" "$PERIOD_SPEC" "$UPLOAD" "$UPLOAD_SPEC" "$STATS" "$STATS_DETAIL" "$STATS_DETAIL_SPEC" "$CUSTOMER_RANKING" "$SCORE_RANKING"; do
  assert_file "$file"
done

assert_contains "$PERIOD_UTIL" "export const PERIOD_ALL = { label: '全部', value: '' }"
assert_contains "$PERIOD_UTIL" "export function buildPeriodOptions"
assert_contains "$PERIOD_UTIL" "\${y}全年"
assert_contains "$PERIOD_UTIL" "\${y}下半年"
assert_contains "$PERIOD_UTIL" "\${y}上半年"
assert_contains "$PERIOD_UTIL" "export function periodQueryValue"
assert_contains "$PERIOD_UTIL" "return value ? value : undefined"
assert_contains "$PERIOD_UTIL" "export function defaultRecordPeriod"
assert_contains "$PERIOD_UTIL" "\${now.getFullYear()}全年"

assert_contains "$PERIOD_SPEC" "选项含「全部」与「<年>全年/下半年/上半年」"
assert_contains "$PERIOD_SPEC" "expect(options.map((o) => o.value)).toEqual(['', '2026全年', '2026下半年', '2026上半年'])"
assert_contains "$PERIOD_SPEC" "expect(periodQueryValue('')).toBeUndefined()"
assert_contains "$PERIOD_SPEC" "expect(defaultRecordPeriod(new Date('2026-09-12T00:00:00Z'))).toBe('2026全年')"

assert_contains "$UPLOAD" "import { buildPeriodOptions, periodQueryValue, defaultRecordPeriod } from '@/utils/incentive-period'"
assert_contains "$UPLOAD" "const periodOptions = buildPeriodOptions()"
assert_contains "$UPLOAD" "const selectedPeriod = ref('')"
assert_contains "$UPLOAD" "period: periodQueryValue(selectedPeriod.value)"
assert_contains "$UPLOAD" "period: selectedPeriod.value || defaultRecordPeriod()"
assert_contains "$UPLOAD_SPEC" "周期口径: 默认「全部」、选项含「全年」、请求不携带空周期"
assert_contains "$UPLOAD_SPEC" "expect(list.selectedPeriod.value).toBe('')"
assert_contains "$UPLOAD_SPEC" "expect(getRecords).toHaveBeenCalledWith(expect.objectContaining({ period: undefined }))"

assert_contains "$STATS" "import { buildPeriodOptions, periodQueryValue } from '@/utils/incentive-period'"
assert_contains "$STATS" "const period = ref('')"
assert_contains "$STATS" "const periodOptions = buildPeriodOptions()"
assert_contains "$STATS" "const p = periodQueryValue(period.value)"
assert_contains "$STATS" "getCategoryStats({ period: p })"
assert_contains "$STATS" "getCustomerRecords({ period: p })"

assert_contains "$STATS_DETAIL" "import { periodQueryValue } from '@/utils/incentive-period'"
assert_contains "$STATS_DETAIL" "period: periodQueryValue(period.value)"
assert_contains "$STATS_DETAIL_SPEC" "expect(getRecords).toHaveBeenCalledWith({ period: '2026全年', parentCategoryId: 4 })"

assert_contains "$CUSTOMER_RANKING" "import { buildPeriodOptions, periodQueryValue } from '@/utils/incentive-period'"
assert_contains "$CUSTOMER_RANKING" "const period = ref('')"
assert_contains "$CUSTOMER_RANKING" "const periodOptions = buildPeriodOptions()"
assert_contains "$CUSTOMER_RANKING" "getCustomerRankings({ period: periodQueryValue(period.value) })"

# 【2026-11-01 学分只留全年】半年选项退役(存量由 V2026.11.01.2 并入全年), 录入与排行同写同显全年;
# ?period= 预选保留(录入页"查看排行"直达), 默认=defaultRecordPeriod()(当年全年)
assert_contains "$SCORE_RANKING" "import { buildPeriodOptions, defaultRecordPeriod } from '@/utils/incentive-period'"
assert_contains "$SCORE_RANKING" "option.label.endsWith('全年')"
assert_contains "$SCORE_RANKING" "String(route.query.period || '') || defaultRecordPeriod()"
assert_contains "$SCORE_RANKING" "const params = { period: selectedPeriod.value }"

echo "PASS: incentive and score period options, default periods, and query semantics are aligned."
