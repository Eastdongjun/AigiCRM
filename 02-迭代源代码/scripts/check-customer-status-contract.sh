#!/usr/bin/env bash
# Contract for shared customer status labels and tag colors.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

UTIL="$ROOT/aigicrm-ui/src/utils/customer-status.js"
UTIL_SPEC="$ROOT/aigicrm-ui/src/utils/customer-status.spec.js"
CUSTOMER_LIST="$ROOT/aigicrm-ui/src/composables/useCustomerList.js"
REMINDER_UTIL="$ROOT/aigicrm-ui/src/utils/reminder-detail.js"
REMINDER_UTIL_SPEC="$ROOT/aigicrm-ui/src/utils/reminder-detail.spec.js"
REMINDER_DETAIL="$ROOT/aigicrm-ui/src/views/reminder/detail.vue"
REMINDER_DETAIL_SPEC="$ROOT/aigicrm-ui/src/views/reminder/detail.spec.js"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required customer-status file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in "$UTIL" "$UTIL_SPEC" "$CUSTOMER_LIST" "$REMINDER_UTIL" "$REMINDER_UTIL_SPEC" "$REMINDER_DETAIL" "$REMINDER_DETAIL_SPEC"; do
  assert_file "$file"
done

assert_contains "$UTIL" "export const CUSTOMER_STATUS_MAP"
assert_contains "$UTIL" "normal: { text: '正常', type: 'success' }"
assert_contains "$UTIL" "warning: { text: '预警', type: 'warning' }"
assert_contains "$UTIL" "expired: { text: '超期', type: 'danger' }"
assert_contains "$UTIL" "sea: { text: '公海', type: 'info' }"
assert_contains "$UTIL" "export function customerStatusText"
assert_contains "$UTIL" "CUSTOMER_STATUS_MAP[status]?.text || '—'"
assert_contains "$UTIL" "export function customerStatusTagType"
assert_contains "$UTIL" "CUSTOMER_STATUS_MAP[status]?.type || 'info'"

assert_contains "$UTIL_SPEC" "maps known status codes to Chinese labels and tag types"
assert_contains "$UTIL_SPEC" "falls back for unknown or empty status without throwing"
assert_contains "$UTIL_SPEC" "Object.keys(CUSTOMER_STATUS_MAP).sort()"

assert_contains "$CUSTOMER_LIST" "import { customerStatusTagType, customerStatusText } from '@/utils/customer-status'"
assert_contains "$CUSTOMER_LIST" "const getStatusType = (row) => customerStatusTagType(row?.status)"
assert_contains "$CUSTOMER_LIST" "const getStatusText = (row) => customerStatusText(row?.status)"

assert_contains "$REMINDER_UTIL" "import { CUSTOMER_STATUS_MAP } from './customer-status'"
assert_contains "$REMINDER_UTIL" "export function customerStatusLabel"
assert_contains "$REMINDER_UTIL" "CUSTOMER_STATUS_MAP[status]?.text"
assert_contains "$REMINDER_UTIL_SPEC" "expect(customerStatusLabel('warning')).toBe('预警')"
assert_contains "$REMINDER_UTIL_SPEC" "expect(customerStatusLabel('sea')).toBe('公海')"

assert_contains "$REMINDER_DETAIL" "import { customerStatusTagType } from '@/utils/customer-status'"
assert_contains "$REMINDER_DETAIL" "customerStatusLabel(customer.status)"
assert_contains "$REMINDER_DETAIL" "customerStatusTagType(customer.value?.status)"
assert_contains "$REMINDER_DETAIL_SPEC" "山西平阳重工机械有限责任公司"
assert_contains "$REMINDER_DETAIL_SPEC" "预警"

echo "PASS: customer status dictionary, list usage, reminder usage, and tests are aligned."
