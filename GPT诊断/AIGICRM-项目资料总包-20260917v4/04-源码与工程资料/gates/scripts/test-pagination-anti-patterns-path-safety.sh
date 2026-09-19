#!/usr/bin/env bash
# Regression test for check-pagination-anti-patterns.sh path resolution.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$ROOT/scripts/check-pagination-anti-patterns.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-pagination-path.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

run_case() {
  local name="$1"
  local cwd="$2"
  local out="$TMP_DIR/$name.log"

  (cd "$cwd" && bash "$CHECK") >"$out" 2>&1

  if grep -q "No such file or directory" "$out"; then
    echo "FAIL: $name emitted missing-path output" >&2
    sed 's/^/  /' "$out" >&2
    return 1
  fi
  if ! grep -q "分页反模式扫描通过" "$out"; then
    echo "FAIL: $name did not report a successful pagination scan" >&2
    sed 's/^/  /' "$out" >&2
    return 1
  fi
}

run_case "source-root" "$ROOT"
run_case "workspace-root" "$ROOT/.."
run_case "external-cwd" "$TMP_DIR"

echo "PASS: pagination anti-pattern checker resolves project paths from any cwd."
