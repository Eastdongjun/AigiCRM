#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PC_CUSTOMER="$ROOT/aigicrm-ui/src/api/customer.js"
PC_INCENTIVE="$ROOT/aigicrm-ui/src/api/incentive.js"
PC_FILE="$ROOT/aigicrm-ui/src/api/file.js"
PC_SEA="$ROOT/aigicrm-ui/src/api/sea.js"
PC_STATISTICS="$ROOT/aigicrm-ui/src/api/statistics.js"

MP_CUSTOMER="$ROOT/aigicrm-miniprogram/api/customer.js"
MP_INCENTIVE="$ROOT/aigicrm-miniprogram/api/incentive.js"
MP_FILE="$ROOT/aigicrm-miniprogram/api/file.js"
MP_SEA="$ROOT/aigicrm-miniprogram/api/sea.js"
MP_STATISTICS="$ROOT/aigicrm-miniprogram/api/statistics.js"

DEV_VERIFY="$ROOT/scripts/dev-verify.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_in_file() {
  local file="$1"
  local expected="$2"
  local label="$3"
  grep -Fq "$expected" "$file" || fail "$label is missing expected API path fragment: $expected"
}

require_pair() {
  local pc_file="$1"
  local pc_path="$2"
  local mp_file="$3"
  local mp_path="$4"
  local label="$5"
  require_in_file "$pc_file" "$pc_path" "PC $label"
  require_in_file "$mp_file" "$mp_path" "Mini Program $label"
}

assert_absent_in_miniprogram_api() {
  local fragment="$1"
  local label="$2"
  if rg -n --fixed-strings "$fragment" "$ROOT/aigicrm-miniprogram/api" >/tmp/aigicrm-mobile-api-parity-hit.txt; then
    cat /tmp/aigicrm-mobile-api-parity-hit.txt >&2
    fail "$label must remain PC-only and absent from miniprogram API modules"
  fi
}

require_pair "$PC_CUSTOMER" "/customer/list" "$MP_CUSTOMER" "/api/customer/list" "customer list"
require_pair "$PC_CUSTOMER" "/customer/add" "$MP_CUSTOMER" "/api/customer/add" "customer add"
require_pair "$PC_CUSTOMER" "/customer/full" "$MP_CUSTOMER" "/api/customer/full" "customer full submit"  # 【PR-05】同事务组合提交两端对齐
require_pair "$PC_CUSTOMER" "/customer/list/counts" "$MP_CUSTOMER" "/api/customer/list/counts" "customer counts"
require_pair "$PC_CUSTOMER" "/customer/users/simple" "$MP_CUSTOMER" "/api/customer/users/simple" "customer user selector"
require_pair "$PC_CUSTOMER" "/customer/group-names" "$MP_CUSTOMER" "/api/customer/group-names" "customer group-name selector"
require_pair "$PC_CUSTOMER" "/logs" "$MP_CUSTOMER" "/logs" "customer logs"
require_pair "$PC_CUSTOMER" "/contacts" "$MP_CUSTOMER" "/contacts" "customer contacts"
require_pair "$PC_CUSTOMER" "/invoices" "$MP_CUSTOMER" "/invoices" "customer invoices"
require_pair "$PC_CUSTOMER" "/payments" "$MP_CUSTOMER" "/payments" "customer payments"
require_pair "$PC_CUSTOMER" "/indicator" "$MP_CUSTOMER" "/indicator" "customer six indicators"
require_pair "$PC_CUSTOMER" "/relationship" "$MP_CUSTOMER" "/relationship" "customer relationship score"
require_pair "$PC_CUSTOMER" "/shares" "$MP_CUSTOMER" "/shares" "customer shares"
require_pair "$PC_CUSTOMER" "/release" "$MP_CUSTOMER" "/release" "customer release"

require_pair "$PC_INCENTIVE" "/incentive/categories" "$MP_INCENTIVE" "/api/incentive/categories" "incentive category tree"
require_pair "$PC_INCENTIVE" "/incentive/records" "$MP_INCENTIVE" "/api/incentive/records" "incentive records"
require_pair "$PC_INCENTIVE" "/incentive/customer-records" "$MP_INCENTIVE" "/api/incentive/customer-records" "incentive customer records"
require_pair "$PC_INCENTIVE" "/incentive/scoreboard" "$MP_INCENTIVE" "/api/incentive/scoreboard" "incentive scoreboard"
require_pair "$PC_INCENTIVE" "/incentive/category-stats" "$MP_INCENTIVE" "/api/incentive/category-stats" "incentive category stats"
require_pair "$PC_INCENTIVE" "/incentive/score/customer/" "$MP_INCENTIVE" "/api/incentive/score/customer/" "incentive customer score"
require_pair "$PC_INCENTIVE" "/incentive/ranking" "$MP_INCENTIVE" "/api/incentive/ranking" "incentive ranking"
require_pair "$PC_INCENTIVE" "/read" "$MP_INCENTIVE" "/read" "incentive read-state"
require_pair "$PC_INCENTIVE" "/incentive/read-stats" "$MP_INCENTIVE" "/api/incentive/read-stats" "incentive read stats"
require_pair "$PC_INCENTIVE" "/incentive/read-ranking" "$MP_INCENTIVE" "/api/incentive/read-ranking" "incentive read ranking"
require_pair "$PC_INCENTIVE" "/incentive/permission/check" "$MP_INCENTIVE" "/api/incentive/permission/check" "incentive grant self-check"

require_pair "$PC_FILE" "/file/list" "$MP_FILE" "/api/file/list" "file list"
require_pair "$PC_FILE" "/file/folders" "$MP_FILE" "/api/file/folders" "file folders"
require_pair "$PC_FILE" "/file/storage" "$MP_FILE" "/api/file/storage" "file storage"
require_pair "$PC_FILE" "/file/shared-to-me" "$MP_FILE" "/api/file/shared-to-me" "files shared to me"
require_pair "$PC_FILE" "/share" "$MP_FILE" "/share" "file share"
require_pair "$PC_FILE" "/acl" "$MP_FILE" "/acl" "file ACL read"
require_pair "$PC_FILE" "/replace" "$MP_FILE" "/replace" "file replace"
require_in_file "$MP_FILE" "/api/file/download/" "Mini Program file preview/download URL builder"
require_in_file "$MP_FILE" "/api/file/upload" "Mini Program file upload"
require_in_file "$MP_FILE" "/api/file/download-token" "Mini Program file download token"

require_pair "$PC_SEA" "/sea/list" "$MP_SEA" "/api/sea/list" "public sea list"
require_pair "$PC_SEA" "/sea/statistics" "$MP_SEA" "/api/sea/statistics" "public sea statistics"
require_pair "$PC_SEA" "/sea/configs/visible" "$MP_SEA" "/api/sea/configs/visible" "visible public sea configs"
require_pair "$PC_SEA" "/sea/claim-records" "$MP_SEA" "/api/sea/claim-records" "public sea claim records"
require_pair "$PC_SEA" "/sea/pool/" "$MP_SEA" "/api/sea/pool/" "public sea row delete"

require_pair "$PC_STATISTICS" "/statistics/dashboard" "$MP_STATISTICS" "/api/statistics/dashboard" "statistics dashboard"
require_pair "$PC_STATISTICS" "/statistics/team-activities" "$MP_STATISTICS" "/api/statistics/team-activities" "statistics team activities"
require_pair "$PC_STATISTICS" "/statistics/weekly-stats" "$MP_STATISTICS" "/api/statistics/weekly-stats" "statistics weekly stats"
require_pair "$PC_STATISTICS" "/statistics/daily-stats" "$MP_STATISTICS" "/api/statistics/daily-stats" "statistics daily stats"
require_pair "$PC_STATISTICS" "/statistics/analysis" "$MP_STATISTICS" "/api/statistics/analysis" "statistics analysis"
require_pair "$PC_STATISTICS" "/statistics/indicator-stats" "$MP_STATISTICS" "/api/statistics/indicator-stats" "statistics indicator stats"
require_pair "$PC_STATISTICS" "/statistics/customer-rankings" "$MP_STATISTICS" "/api/statistics/customer-rankings" "statistics customer rankings"

assert_absent_in_miniprogram_api "/api/customer/import" "customer import"
assert_absent_in_miniprogram_api "/api/customer/export" "customer export"
assert_absent_in_miniprogram_api "/api/sea/import" "public sea import"
assert_absent_in_miniprogram_api "/api/sea/org-tree" "public sea org tree"
assert_absent_in_miniprogram_api "/api/sea/configs'" "public sea config mutation/list"
assert_absent_in_miniprogram_api '/api/sea/configs"' "public sea config mutation/list"
assert_absent_in_miniprogram_api "/api/approval/config" "approval config"
assert_absent_in_miniprogram_api "/api/incentive/records/recalculate" "incentive batch recalculation"
assert_absent_in_miniprogram_api "/api/incentive/permission/grant" "incentive permission grant"
assert_absent_in_miniprogram_api "/api/score/grid" "score entry grid"
assert_absent_in_miniprogram_api "/api/score/entries/batch" "score batch entry"
assert_absent_in_miniprogram_api "/api/score/export/grid" "score export"

grep -Fq 'check-mobile-pc-api-parity-contract.sh' "$DEV_VERIFY" \
  || fail "dev-verify must run the mobile/PC API parity contract"

echo "PASS: mobile and PC API modules share implemented backend paths while PC-only APIs remain absent from miniprogram."
