#!/usr/bin/env bash
# Keep public-sea delete wired through row flags and service-layer pool guards.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java"
SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/sea/service/SeaService.java"
ARCH_TEST="$ROOT/aigicrm-backend/src/test/java/com/aigicrm/architecture/PermissionMatrixTest.java"
API="$ROOT/aigicrm-ui/src/api/sea.js"
VIEW="$ROOT/aigicrm-ui/src/views/sea/index.vue"
DICT="$ROOT/docs/permission/permission-dict.md"
MATRIX="$ROOT/docs/function-permission-matrix.md"
RISK="$ROOT/docs/high-risk-action-matrix.md"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq "$needle" "$file" || fail "$(basename "$file") is missing: $needle"
}

for file in "$CONTROLLER" "$SERVICE" "$ARCH_TEST" "$API" "$VIEW" "$DICT" "$MATRIX" "$RISK"; do
  [ -f "$file" ] || fail "Missing $file"
done

assert_contains "$DICT" '`sea:pool:delete`'
assert_contains "$CONTROLLER" '@DeleteMapping("/pool/{id}")'
assert_contains "$CONTROLLER" '@PreAuthorize("hasAuthority('\''sea:view'\'')")'
assert_contains "$CONTROLLER" 'seaService.deletePool(id);'

assert_contains "$SERVICE" 'public void deletePool(Long seaPoolId)'
assert_contains "$SERVICE" '"available".equals(pool.getStatus())'
assert_contains "$SERVICE" 'hasPoolPerm(pool.getPoolId(), SeaConfigService.PERM_DELETE'
assert_contains "$SERVICE" '!visiblePoolIds.contains(pool.getPoolId())'
# 【2026-10-18 biz-3】公海删除必须先条件结束池行，再走客户统一删除内核
# (分享/提醒清理+删除快照)；禁止回退到直接 customerMapper.deleteById 绕过。
assert_contains "$SERVICE" 'customerService.deleteFromSeaPool(pool.getCustomerId())'
assert_contains "$SERVICE" 'seaPoolMapper.delete(new LambdaQueryWrapper<BizSeaPool>()'
assert_contains "$SERVICE" '.eq(BizSeaPool::getStatus, "available")'

assert_contains "$API" 'export function deleteSeaPool(id)'
assert_contains "$API" 'request.delete(`/sea/pool/${id}`)'
assert_contains "$VIEW" 'deleteSeaPool'
assert_contains "$VIEW" 'row.canDelete&&row.status==='\''available'\'''
assert_contains "$VIEW" 'handleDelete(row)'

assert_contains "$ARCH_TEST" 'seaDeleteEndpoint_requiresSeaViewAndRetainsPoolDeleteGuard'
assert_contains "$MATRIX" '`DELETE /api/sea/pool/{id}`'
assert_contains "$MATRIX" 'service-layer pool delete permission remains final guard'
assert_contains "$RISK" '`sea:pool:delete`'
assert_contains "$RISK" 'SeaService.deletePool'

echo "PASS: sea delete endpoint, service guard, UI entry, permission dictionary, and docs are aligned."
