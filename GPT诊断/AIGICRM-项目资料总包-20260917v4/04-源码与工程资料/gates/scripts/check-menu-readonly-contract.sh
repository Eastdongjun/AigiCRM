#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysMenuController.java"

if ! rg -Fq "@GetMapping(\"/tree\")" "$CONTROLLER"; then
  echo "FAIL: menu tree read endpoint is missing"
  exit 1
fi

if rg -n "@(PostMapping|PutMapping|PatchMapping|DeleteMapping)" "$CONTROLLER"; then
  echo "FAIL: menu controller must remain read-only"
  exit 1
fi

if ! rg -Fq "hasAuthority('system:menu:view')" "$CONTROLLER"; then
  echo "FAIL: menu tree endpoint must require system:menu:view"
  exit 1
fi

echo "PASS: system menu remains a read-only, view-authorized surface."
