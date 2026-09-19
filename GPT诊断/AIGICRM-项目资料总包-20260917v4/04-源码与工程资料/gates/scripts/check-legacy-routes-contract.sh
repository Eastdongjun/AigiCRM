#!/usr/bin/env bash
# Keep legacy menu/bookmark routes aligned with their current pages.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROUTER="$ROOT/aigicrm-ui/src/router/index.js"
SPEC="$ROOT/aigicrm-ui/src/router/legacy-routes.spec.js"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "$(basename "$file") is missing: $needle"
}

[ -f "$ROUTER" ] || fail "Missing $ROUTER"
[ -f "$SPEC" ] || fail "Missing $SPEC"

require_contains "$ROUTER" "path: 'incentive/ranking'"
require_contains "$ROUTER" "name: 'LegacyIncentiveRanking'"
require_contains "$ROUTER" "component: () => import('@/views/score/ranking.vue')"
require_contains "$ROUTER" "permission: 'score:view'"

require_contains "$ROUTER" "path: 'system/org'"
require_contains "$ROUTER" "name: 'DeptManage'"
require_contains "$ROUTER" "path: 'system/dept'"
require_contains "$ROUTER" "name: 'LegacyDeptManage'"
require_contains "$ROUTER" "component: () => import('@/views/system/dept.vue')"
require_contains "$ROUTER" "permission: 'system:dept:view'"

require_contains "$SPEC" "keeps legacy incentive ranking menu path usable for score ranking"
require_contains "$SPEC" "keeps system dept organization route and legacy department menu path aligned"
require_contains "$SPEC" "path: 'system/dept'"
require_contains "$SPEC" "name: 'LegacyDeptManage'"

echo "PASS: legacy route compatibility contract is aligned."
