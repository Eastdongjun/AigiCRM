#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }

CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java"
MINI_API="$ROOT/aigicrm-miniprogram/api/customer.js"
MINI_PAGE="$ROOT/aigicrm-miniprogram/pages/files/index.js"
PC_API="$ROOT/aigicrm-ui/src/api/customer.js"
PC_TAB="$ROOT/aigicrm-ui/src/views/customer/detail/FilesTab.vue"

grep -Fq '@GetMapping("/{id}/files")' "$CONTROLLER" || fail 'customer file read route is missing'
grep -Fq '@PostMapping("/{id}/files/{fileId}")' "$CONTROLLER" || fail 'customer file attach route is missing'
grep -Fq '@DeleteMapping("/{id}/files/{fileId}")' "$CONTROLLER" || fail 'customer file detach route is missing'
grep -Fq "customer:view') and hasAuthority('file:view" "$CONTROLLER" || fail 'customer file read permission guard is missing'
grep -Fq "customer:edit') and hasAuthority('file:view" "$CONTROLLER" || fail 'customer file mutation permission guard is missing'
grep -Fq "function getCustomerFiles(id)" "$MINI_API" || fail 'mini-program customer file read wrapper is missing'
grep -Fq "function attachCustomerFile(id, fileId)" "$MINI_API" || fail 'mini-program customer file attach wrapper is missing'
grep -Fq "function detachCustomerFile(id, fileId)" "$MINI_API" || fail 'mini-program customer file detach wrapper is missing'
grep -Fq 'detachCustomerFile(this.data.customerId' "$MINI_PAGE" || fail 'mini-program customer file detach action is missing'
grep -Fq 'export function getCustomerFiles(customerId)' "$PC_API" || fail 'PC customer file read wrapper is missing'
grep -Fq 'export function attachCustomerFile(customerId, fileId)' "$PC_API" || fail 'PC customer file attach wrapper is missing'
grep -Fq 'export function detachCustomerFile(customerId, fileId)' "$PC_API" || fail 'PC customer file detach wrapper is missing'
grep -Fq 'detachCustomerFile(customerId.value' "$PC_TAB" || fail 'PC customer file detach action is missing'
grep -Fq 'attachCustomerFile(customerId.value' "$PC_TAB" || fail 'PC customer file attach action is missing'

echo 'PASS: customer file read/attach/detach contract is aligned across backend, PC, and mobile.'
