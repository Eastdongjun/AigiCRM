#!/usr/bin/env bash
# Guard daily development automation against accidental remote or production actions.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

targets=(
  ".githooks/pre-push"
  ".vscode/extensions.json"
  ".vscode/settings.json"
  ".vscode/tasks.json"
  "scripts/dev-evidence.sh"
  "scripts/dev-verify.sh"
  "scripts/install-git-hooks.sh"
  "scripts/verify-sea-claim-matrix.sh"
)

forbidden='(^|[;&|[:space:]])(ssh|scp|rsync|mysql|flyway|docker([[:space:]]+compose)?|kubectl|helm)([[:space:]]|$)|git[[:space:]]+(push|commit|add|reset|checkout)|deploy-(backend|frontend|db)|auto-pipeline|smoke-test\.sh[[:space:]]+prod|prod(uction)?[[:space:]]+(deploy|migration|smoke)'

status=0
for rel in "${targets[@]}"; do
  path="$ROOT/$rel"
  [ -f "$path" ] || continue

  matches="$(
    sed -E '/^[[:space:]]*#/d; s/#.*$//' "$path" |
      grep -nE "$forbidden" || true
  )"
  if [ -n "$matches" ]; then
    echo "FAIL: unsafe automation command pattern in $rel" >&2
    echo "$matches" >&2
    status=1
  fi
done

if [ "$status" -eq 0 ]; then
  echo "PASS: daily development automation contains no forbidden remote/deploy patterns."
fi

exit "$status"
