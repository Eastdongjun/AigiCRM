#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAYOUT="$ROOT/aigicrm-ui/src/layout/index.vue"

if grep -Fq "group.children.filter(c => c.subChildren.length)" "$LAYOUT"; then
  echo "FAIL: sidebar must not render submenu items in a separate first pass"
  exit 1
fi

if grep -Fq "group.children.filter(c => !c.subChildren.length)" "$LAYOUT"; then
  echo "FAIL: sidebar must not render leaf menu items in a separate second pass"
  exit 1
fi

if ! grep -Fq 'v-for="item in group.children"' "$LAYOUT"; then
  echo "FAIL: sidebar must iterate group.children directly to preserve backend sort order"
  exit 1
fi

echo "PASS: sidebar preserves backend menu order while rendering submenus and leaf items."
