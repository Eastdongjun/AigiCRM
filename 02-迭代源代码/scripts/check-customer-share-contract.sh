#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java"
PAGE="$ROOT/aigicrm-miniprogram/pages/customer/share/index.js"
MANIFEST="$ROOT/docs/unified-feature-manifest.md"

grep -q "@GetMapping(\"/{id}/shares\")" "$CONTROLLER"
grep -B1 -A2 -q "@GetMapping(\"/{id}/shares\")" "$CONTROLLER"
grep -B1 -A2 -q "@PostMapping(\"/{id}/shares\")" "$CONTROLLER"
grep -B1 -A2 -q "@DeleteMapping(\"/{id}/shares/{userId}\")" "$CONTROLLER"
grep -q "hasAuthority('customer:view')" "$CONTROLLER"
grep -q "hasAuthority('customer:edit')" "$CONTROLLER"
grep -q "hasPermission('customer:view')" "$PAGE"
grep -q "hasPermission('customer:edit')" "$PAGE"
grep -q "customer:edit.*add/revoke" "$MANIFEST"

echo "PASS: customer share read/mutation permissions match backend, mobile page, and manifest."
