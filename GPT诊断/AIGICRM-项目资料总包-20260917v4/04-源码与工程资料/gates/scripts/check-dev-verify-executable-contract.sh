#!/usr/bin/env bash
# Ensure shell scripts invoked directly by dev-verify are executable.

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

missing=""
while IFS= read -r script_path; do
  [ -n "$script_path" ] || continue
  full_path="$ROOT/$script_path"
  [ -f "$full_path" ] || {
    missing="${missing}${script_path} (missing)
"
    continue
  }
  [ -x "$full_path" ] || {
    missing="${missing}${script_path} (not executable)
"
  }
done < <(
  sed -n 's|.*run "\$ROOT/\(scripts/[^"]*\.sh\)".*|\1|p' "$DEV_VERIFY" | sort -u
)

[ -z "$missing" ] || fail "dev-verify directly invoked scripts need executable mode: $(printf '%s' "$missing" | tr '\n' ' ')"

syntax_failures=""
while IFS= read -r script_path; do
  [ -n "$script_path" ] || continue
  full_path="$ROOT/$script_path"
  if [ ! -f "$full_path" ]; then
    syntax_failures="${syntax_failures}${script_path} (missing)
"
    continue
  fi
  if ! bash -n "$full_path"; then
    syntax_failures="${syntax_failures}${script_path} (bash syntax)
"
  fi
done < <(
  {
    sed -n 's|.*run "\$ROOT/\(scripts/[^"]*\.sh\)".*|\1|p' "$DEV_VERIFY"
    sed -n 's|.*run bash "\$ROOT/\(scripts/[^"]*\.sh\)".*|\1|p' "$DEV_VERIFY"
  } | sort -u
)

while IFS= read -r script_path; do
  [ -n "$script_path" ] || continue
  full_path="$ROOT/$script_path"
  if [ ! -f "$full_path" ]; then
    syntax_failures="${syntax_failures}${script_path} (missing)
"
    continue
  fi
  if ! python3 -m py_compile "$full_path"; then
    syntax_failures="${syntax_failures}${script_path} (python syntax)
"
  fi
done < <(
  sed -n 's|.*run python3 "\$ROOT/\(scripts/[^"]*\.py\)".*|\1|p' "$DEV_VERIFY" | sort -u
)

while IFS= read -r script_path; do
  [ -n "$script_path" ] || continue
  full_path="$ROOT/$script_path"
  if [ ! -f "$full_path" ]; then
    syntax_failures="${syntax_failures}${script_path} (missing)
"
    continue
  fi
  if ! node --check "$full_path"; then
    syntax_failures="${syntax_failures}${script_path} (node syntax)
"
  fi
done < <(
  sed -n 's|.*run node "\$ROOT/\(scripts/[^"]*\.js\)".*|\1|p' "$DEV_VERIFY" | sort -u
)

[ -z "$syntax_failures" ] || fail "dev-verify invoked script syntax checks failed: $(printf '%s' "$syntax_failures" | tr '\n' ' ')"

echo "PASS: dev-verify invoked scripts exist, executable shell entrypoints are executable, and script syntax checks pass."
