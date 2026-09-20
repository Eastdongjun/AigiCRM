#!/usr/bin/env bash
# Contract for the monthly incentive read-ranking feature wiring.
#
# 【PR-22.G-01 定性 2026-09-20】本契约是**结构检查**(wiring/口径存在性), 非集团隔离行为验证。
# 集团隔离的行为前提由以下两层承担: ① monthlyReadAggregate 的 fail-closed 集团过滤
# (下方断言钉住其结构凭据); ② check-native-sql-tenant-scope.sh 审计契约(2026-09-20
# 全量原生 SQL 审计固化, JdbcTemplate 触 biz_* 表必须带租户/集团限定)。
# 跨集团双账号的行为级验证属 PR-22 活体批次(已完成, 见 runs/20260919-pr22-*)。

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERMISSION="incentive:read-ranking:view"
ROUTE_PATH="system/incentive-read-ranking"
API_PATH="/incentive/read-ranking"
MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.09.13.4__add_incentive_read_ranking_menu.sql"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/controller/IncentiveController.java"
SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java"
ROUTER="$ROOT/aigicrm-ui/src/router/index.js"
API="$ROOT/aigicrm-ui/src/api/incentive.js"
VIEW="$ROOT/aigicrm-ui/src/views/system/incentive-read-ranking.vue"
VIEW_SPEC="$ROOT/aigicrm-ui/src/views/system/incentive-read-ranking.spec.js"
UTIL="$ROOT/aigicrm-ui/src/utils/incentive-read-ranking.js"
UTIL_SPEC="$ROOT/aigicrm-ui/src/utils/incentive-read-ranking.spec.js"
BACKEND_TEST="$ROOT/aigicrm-backend/src/test/java/com/aigicrm/module/incentive/service/IncentiveReadRankingTest.java"
ARCH_TEST="$ROOT/aigicrm-backend/src/test/java/com/aigicrm/architecture/PermissionMatrixTest.java"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_file() {
  [ -f "$1" ] || fail "Missing required read-ranking file: $1"
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "Missing '$needle' in $file"
}

for file in "$MIGRATION" "$CONTROLLER" "$SERVICE" "$ROUTER" "$API" "$VIEW" "$VIEW_SPEC" "$UTIL" "$UTIL_SPEC" "$BACKEND_TEST" "$ARCH_TEST"; do
  assert_file "$file"
done

assert_contains "$MIGRATION" "$PERMISSION"
assert_contains "$MIGRATION" "/system/incentive-read-ranking"
assert_contains "$MIGRATION" "admin"
assert_contains "$MIGRATION" "NOT EXISTS"

assert_contains "$CONTROLLER" "@GetMapping(\"/read-ranking\")"
assert_contains "$CONTROLLER" "hasAuthority('$PERMISSION')"
assert_contains "$CONTROLLER" "incentiveService.readRanking(period)"

assert_contains "$SERVICE" "public Map<String, Object> readRanking(String period)"
assert_contains "$SERVICE" "monthlyReadAggregate(month)"
assert_contains "$SERVICE" "readRate"
# 【PR-22.G-01】钉住集团隔离结构凭据: 无集团归属返回空(fail-closed)+成员边界过滤
assert_contains "$SERVICE" "currentGroupOrNull"
assert_contains "$SERVICE" "userIdsInGroup"

assert_contains "$ROUTER" "path: '$ROUTE_PATH'"
assert_contains "$ROUTER" "permission: '$PERMISSION'"
assert_contains "$ROUTER" "incentive-read-ranking.vue"

assert_contains "$API" "getIncentiveReadRanking"
assert_contains "$API" "$API_PATH"

assert_contains "$VIEW" "getIncentiveReadRanking"
assert_contains "$VIEW" "$PERMISSION"
assert_contains "$VIEW_SPEC" "ReadRanking"
assert_contains "$UTIL" "readRate"
assert_contains "$UTIL" "readRateText"
assert_contains "$UTIL_SPEC" "incentive-read-ranking"

# 测试经 readRankingAsAdmin 助手以管理员上下文调用（内部 service().readRanking(period)），
# 不再直接出现 readRanking("2026-09") 字面量。
assert_contains "$BACKEND_TEST" "readRankingAsAdmin(\"2026-09\")"
assert_contains "$BACKEND_TEST" "service().readRanking(period)"
assert_contains "$ARCH_TEST" "$PERMISSION"

echo "PASS: incentive read-ranking backend, migration, frontend route/API/view, and tests are wired."
