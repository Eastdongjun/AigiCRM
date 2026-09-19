#!/usr/bin/env bash
# Contract for the incentive upload record table and record mutation boundary.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

INDEX_VIEW="$ROOT/aigicrm-ui/src/views/incentive/index.vue"
RECORD_TABLE="$ROOT/aigicrm-ui/src/views/incentive/RecordTable.vue"
RECORD_TABLE_SPEC="$ROOT/aigicrm-ui/src/views/incentive/RecordTable.spec.js"
UPLOAD_COMPOSABLE="$ROOT/aigicrm-ui/src/composables/useIncentiveUpload.js"
UPLOAD_COMPOSABLE_SPEC="$ROOT/aigicrm-ui/src/composables/useIncentiveUpload.spec.js"
PAGINATION_UTIL="$ROOT/aigicrm-ui/src/utils/pagination.js"
API="$ROOT/aigicrm-ui/src/api/incentive.js"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java"
ARCH_TEST="$ROOT/aigicrm-backend/src/test/java/com/aigicrm/architecture/PermissionMatrixTest.java"
API_DOC="$ROOT/docs/api/api-list.md"
FEATURE_AUDIT="$ROOT/docs/unified-feature-audit.md"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required incentive record-table file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in \
  "$INDEX_VIEW" "$RECORD_TABLE" "$RECORD_TABLE_SPEC" "$UPLOAD_COMPOSABLE" "$UPLOAD_COMPOSABLE_SPEC" \
  "$PAGINATION_UTIL" "$API" "$CONTROLLER" "$ARCH_TEST" "$API_DOC" "$FEATURE_AUDIT"; do
  assert_file "$file"
done

assert_contains "$INDEX_VIEW" "provide(IncentiveUploadKey, upload)"
assert_contains "$INDEX_VIEW" "<RecordTable />"

assert_contains "$RECORD_TABLE" "injectIncentiveUpload"
assert_contains "$RECORD_TABLE" "pageSizes(pageSize)"
assert_contains "$RECORD_TABLE" "layout=\"sizes, prev, pager, next, jumper\""
assert_contains "$RECORD_TABLE" "onSizeChange"
assert_contains "$RECORD_TABLE" "pageNum.value = 1"
assert_contains "$RECORD_TABLE" "isRecordOwner(row)"
assert_contains "$RECORD_TABLE" "toggleShare(row, v)"
assert_contains "$RECORD_TABLE" "startReplace(row)"
assert_contains "$RECORD_TABLE" "handleDeleteRecord(row)"
assert_contains "$RECORD_TABLE" "userStore.isAdmin() || canEdit || row.userId === userStore.userId"
assert_contains "$RECORD_TABLE" "userStore.isAdmin() || canDelete || row.userId === userStore.userId"

assert_contains "$RECORD_TABLE_SPEC" "pageSizes"
assert_contains "$RECORD_TABLE_SPEC" "arrayContaining([30, 50, 100])"
assert_contains "$RECORD_TABLE_SPEC" "onSizeChange(50)"

assert_contains "$UPLOAD_COMPOSABLE" "pageSize = ref(10)"
assert_contains "$UPLOAD_COMPOSABLE" "pagedRecords"
assert_contains "$UPLOAD_COMPOSABLE" "isRecordOwner"
assert_contains "$UPLOAD_COMPOSABLE" "toggleShare"
assert_contains "$UPLOAD_COMPOSABLE" "updateRecord(row.id"
assert_contains "$UPLOAD_COMPOSABLE" "confirmUpload"
assert_contains "$UPLOAD_COMPOSABLE" "periodQueryValue(selectedPeriod.value)"
assert_contains "$UPLOAD_COMPOSABLE_SPEC" "toggleShare 非本人拒绝"
assert_contains "$UPLOAD_COMPOSABLE_SPEC" "confirmUpload 追加模式"
assert_contains "$UPLOAD_COMPOSABLE_SPEC" "周期口径"

assert_contains "$PAGINATION_UTIL" "export function pageSizes"

assert_contains "$API" "getRecords(params)"
assert_contains "$API" "addRecord(data)"
assert_contains "$API" "updateRecord(id, data)"
assert_contains "$API" "deleteRecord(id)"
assert_contains "$API" "/incentive/records"

assert_contains "$CONTROLLER" "@PutMapping(\"/records/{id}\")"
assert_contains "$CONTROLLER" "@DeleteMapping(\"/records/{id}\")"
assert_contains "$CONTROLLER" "SecurityUser user = SecurityUtils.getCurrentUser()"
assert_contains "$CONTROLLER" "if (!isAdmin && !incentiveService.canEdit())"
assert_contains "$CONTROLLER" "throw new BusinessException(403, \"无权编辑他人记录\")"
assert_contains "$CONTROLLER" "if (!isAdmin && !incentiveService.canDelete())"
assert_contains "$CONTROLLER" "throw new BusinessException(403, \"无权删除他人记录\")"

assert_contains "$ARCH_TEST" "incentiveRecordMutationEndpoints_keepInternalOwnershipAndGrantGuards"
assert_contains "$ARCH_TEST" "assertNoPreAuthorize(controller.getDeclaredMethod(\"updateRecord\""
assert_contains "$ARCH_TEST" "assertNoPreAuthorize(controller.getDeclaredMethod(\"deleteRecord\""

assert_contains "$API_DOC" "| PUT | \`/api/incentive/records/{id}\` | \`登录即可\` | updateRecord | IncentiveController.java |"
assert_contains "$API_DOC" "| DELETE | \`/api/incentive/records/{id}\` | \`登录即可\` | deleteRecord | IncentiveController.java |"
assert_contains "$FEATURE_AUDIT" "| \`updateRecord\` | \`PUT\` | \`/incentive/records/\${id}\` |"
assert_contains "$FEATURE_AUDIT" "| \`deleteRecord\` | \`DELETE\` | \`/incentive/records/\${id}\` |"

echo "PASS: incentive record table pagination, sharing, mutation API, and ownership guards are wired."
