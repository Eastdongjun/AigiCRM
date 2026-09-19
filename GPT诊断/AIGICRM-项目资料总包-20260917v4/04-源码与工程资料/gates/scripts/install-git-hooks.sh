#!/usr/bin/env bash
# Install or remove repository-local Git hooks for AIGICRM.
# This only changes .git/config in the current checkout.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-install}"
HOOKS_PATH="$ROOT/.githooks"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

install_hooks() {
  cd "$ROOT"
  git rev-parse --show-toplevel >/dev/null

  local current
  current="$(git config --local --get core.hooksPath || true)"
  if [ -n "$current" ] && [ "$current" != "$HOOKS_PATH" ]; then
    fail "core.hooksPath is already set to '$current'; refusing to overwrite it"
  fi

  chmod +x "$ROOT/.githooks/pre-push" "$ROOT/scripts/dev-verify.sh" "$ROOT/scripts/verify-sea-claim-matrix.sh"
  git config --local core.hooksPath "$HOOKS_PATH"
  echo "PASS: installed local Git hooks at $HOOKS_PATH"
}

uninstall_hooks() {
  cd "$ROOT"
  local current
  current="$(git config --local --get core.hooksPath || true)"
  if [ "$current" = "$HOOKS_PATH" ]; then
    git config --local --unset core.hooksPath
    echo "PASS: removed local Git hooks path"
  else
    echo "SKIP: core.hooksPath is not $HOOKS_PATH"
  fi
}

case "$MODE" in
  install) install_hooks ;;
  uninstall) uninstall_hooks ;;
  status)
    cd "$ROOT"
    echo "core.hooksPath=$(git config --local --get core.hooksPath || true)"
    ;;
  *)
    fail "Usage: scripts/install-git-hooks.sh [install|uninstall|status]"
    ;;
esac
