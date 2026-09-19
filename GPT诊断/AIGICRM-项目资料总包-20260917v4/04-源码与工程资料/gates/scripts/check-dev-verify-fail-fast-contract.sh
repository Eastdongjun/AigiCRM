#!/usr/bin/env bash
# Prove dev-verify fails closed when a direct or wrapped command fails.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_VERIFY="$ROOT/scripts/dev-verify.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$DEV_VERIFY" ] || fail "Missing $DEV_VERIFY"

grep -Eq '^set -e$' "$DEV_VERIFY" || fail "dev-verify must enable set -e"
grep -Eq '^set -o pipefail$' "$DEV_VERIFY" || fail "dev-verify must enable pipefail"

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-dev-verify-fail-fast.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT
mkdir -p "$tmp_dir/scripts"

awk '
  /^case "\$MODE" in$/ {
    print
    print "  fail-fast-direct-probe) false; echo \"FAIL: direct failure was swallowed\" ;;"
    print "  fail-fast-run-probe) run false; echo \"FAIL: run failure was swallowed\" ;;"
    next
  }
  { print }
' "$DEV_VERIFY" > "$tmp_dir/scripts/dev-verify.sh"
chmod +x "$tmp_dir/scripts/dev-verify.sh"

assert_fails_closed() {
  local mode="$1"
  local log="$tmp_dir/$mode.log"
  local rc=0

  bash "$tmp_dir/scripts/dev-verify.sh" "$mode" >"$log" 2>&1 || rc=$?
  [ "$rc" -ne 0 ] || fail "$mode returned 0 after an injected failure"
  ! grep -Fq 'failure was swallowed' "$log" || fail "$mode continued after an injected failure"
}

assert_fails_closed fail-fast-direct-probe
assert_fails_closed fail-fast-run-probe

echo "PASS: dev-verify exits non-zero for direct and run-wrapped command failures."
