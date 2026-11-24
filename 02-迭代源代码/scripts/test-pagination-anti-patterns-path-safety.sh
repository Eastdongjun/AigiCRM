#!/usr/bin/env bash
# Regression test for check-pagination-anti-patterns.sh path resolution.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$ROOT/scripts/check-pagination-anti-patterns.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-pagination-path.XXXXXX")"
# 【退出码守卫】见 exit-guard.sh: bash 3.2 在 set -e + EXIT 陷阱下会把未定义变量引起的中止报成退出码 0
cleanup_tmp_dir() { rm -rf "$TMP_DIR"; }
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/exit-guard.sh"
exit_guard_install cleanup_tmp_dir

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

EXIT_GUARD_DONE=1
echo "PASS: pagination anti-pattern checker resolves project paths from any cwd."
