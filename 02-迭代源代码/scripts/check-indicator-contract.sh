#!/usr/bin/env bash
# Validate the local six-indicator contract without reading live data.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI="$ROOT/aigicrm-ui/src/views/customer/detail/IndicatorTab.vue"
COMPOSABLE="$ROOT/aigicrm-ui/src/composables/useCustomerDetail.js"
STATS="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java"
DOC="$ROOT/docs/系统操作说明.md"

fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq '/ 15' "$UI" || fail 'customer indicator UI must show a 15-point total'
grep -Fq "indicator02" "$COMPOSABLE" || fail 'five scored indicator fields are missing'
grep -Fq "indicator06" "$COMPOSABLE" || fail 'last scored indicator field is missing'
grep -Fq 'BizPaymentMapper' "$STATS" || fail 'statistics must load payment totals'
grep -Fq 'isCoreCustomer' "$STATS" || fail 'statistics must centralize core-customer threshold logic'
grep -Fq '开票、回款均 ≥ 100万' "$DOC" || fail 'operator documentation must state both financial thresholds'

echo 'PASS: six-indicator contract is consistent across UI, backend, and documentation.'
