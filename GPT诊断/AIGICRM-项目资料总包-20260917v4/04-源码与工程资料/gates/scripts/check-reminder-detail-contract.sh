#!/usr/bin/env bash
# Contract for the reminder detail drill-down feature wiring.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERMISSION="reminder:view"
ROUTE_PATH="reminder/detail/:id"
API_PATH="/reminder/\${id}"

CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/reminder/controller/ReminderController.java"
SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/reminder/service/ReminderService.java"
BACKEND_TEST="$ROOT/aigicrm-backend/src/test/java/com/aigicrm/module/reminder/service/ReminderServiceTest.java"
ROUTER="$ROOT/aigicrm-ui/src/router/index.js"
API="$ROOT/aigicrm-ui/src/api/reminder.js"
LIST_VIEW="$ROOT/aigicrm-ui/src/views/reminder/index.vue"
LIST_SPEC="$ROOT/aigicrm-ui/src/views/reminder/index.spec.js"
DETAIL_VIEW="$ROOT/aigicrm-ui/src/views/reminder/detail.vue"
DETAIL_SPEC="$ROOT/aigicrm-ui/src/views/reminder/detail.spec.js"
UTIL="$ROOT/aigicrm-ui/src/utils/reminder-detail.js"
UTIL_SPEC="$ROOT/aigicrm-ui/src/utils/reminder-detail.spec.js"
API_DOC="$ROOT/docs/api/api-list.md"
FEATURE_AUDIT="$ROOT/docs/unified-feature-audit.md"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required reminder-detail file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in \
  "$CONTROLLER" "$SERVICE" "$BACKEND_TEST" "$ROUTER" "$API" "$LIST_VIEW" "$LIST_SPEC" \
  "$DETAIL_VIEW" "$DETAIL_SPEC" "$UTIL" "$UTIL_SPEC" "$API_DOC" "$FEATURE_AUDIT"; do
  assert_file "$file"
done

assert_contains "$CONTROLLER" "@GetMapping(\"/{id}\")"
assert_contains "$CONTROLLER" "hasAuthority('$PERMISSION')"
assert_contains "$CONTROLLER" "reminderService.detail(id)"

assert_contains "$SERVICE" "public Map<String, Object> detail(Long id)"
assert_contains "$SERVICE" "assertVisible(reminder, \"无权查看该提醒\")"
assert_contains "$SERVICE" "customerBrief(customer)"
assert_contains "$SERVICE" "ownerName"
assert_contains "$SERVICE" "officeName"
assert_contains "$SERVICE" "throw new BusinessException(\"提醒不存在\")"

assert_contains "$BACKEND_TEST" "service.detail(21L)"
assert_contains "$BACKEND_TEST" "detail_rejectsReminderOutsideVisibleOwnerScope"
assert_contains "$BACKEND_TEST" "detail_reportsMissingReminder"

assert_contains "$ROUTER" "path: '$ROUTE_PATH'"
assert_contains "$ROUTER" "name: 'ReminderDetail'"
assert_contains "$ROUTER" "detail.vue"
assert_contains "$ROUTER" "permission: '$PERMISSION'"

assert_contains "$API" "getReminderDetail"
assert_contains "$API" "$API_PATH"

assert_contains "$LIST_VIEW" "buildReminderDetailLocation"
assert_contains "$LIST_VIEW" "router.push(buildReminderDetailLocation(row))"
assert_contains "$LIST_SPEC" "ReminderDetail"

assert_contains "$DETAIL_VIEW" "getReminderDetail"
assert_contains "$DETAIL_VIEW" "processReminder"
assert_contains "$DETAIL_VIEW" "goCustomer"
assert_contains "$DETAIL_VIEW" "无客户查看权限"
assert_contains "$DETAIL_VIEW" "提醒详情加载失败"

assert_contains "$DETAIL_SPEC" "getReminderDetail"
assert_contains "$DETAIL_SPEC" "processReminder"
assert_contains "$DETAIL_SPEC" "route.params.id = '541'"
assert_contains "$DETAIL_SPEC" "无权查看该提醒"

assert_contains "$UTIL" "REMINDER_DETAIL_ROUTE_NAME"
assert_contains "$UTIL" "buildReminderDetailLocation"
assert_contains "$UTIL" "NO_REMINDER_ID"
assert_contains "$UTIL" "remainingDaysText"
assert_contains "$UTIL_SPEC" "buildReminderDetailLocation"
assert_contains "$UTIL_SPEC" "remainingDaysText"

assert_contains "$API_DOC" "| GET | \`/api/reminder/{id}\` | \`hasAuthority('reminder:view')\` | detail | ReminderController.java |"
assert_contains "$FEATURE_AUDIT" "| \`GET\` | \`/api/reminder/{id}\` | \`hasAuthority('reminder:view')\` |"
assert_contains "$FEATURE_AUDIT" "| \`getReminderDetail\` | \`GET\` | \`/reminder/\${id}\` |"

echo "PASS: reminder detail backend, API, route, view, docs, and tests are wired."
