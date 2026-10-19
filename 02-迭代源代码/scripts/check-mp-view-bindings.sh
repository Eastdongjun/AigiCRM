#!/usr/bin/env bash
# 静态门禁：WXML 读取派生字段但同目录 JS 没有生产处（防“有数据但卡片全空”类回归）。
# 由 scripts/dev-verify.sh 的 miniprogram 检查调用；也可单独执行。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec node "$ROOT/scripts/check-mp-view-bindings.mjs" \
  --root "$ROOT/aigicrm-miniprogram" \
  --baseline "$ROOT/scripts/mp-view-bindings-baseline.json" "$@"
