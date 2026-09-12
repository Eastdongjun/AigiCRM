#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUTH_SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/auth/service/AuthService.java"

if ! grep -Fq 'm.getMenuType() == 1' "$AUTH_SERVICE"; then
  echo "FAIL: menu tree must keep directory nodes"
  exit 1
fi

if ! grep -Fq 'm.getMenuType() == 2 && m.getPath() != null && !m.getPath().isBlank()' "$AUTH_SERVICE"; then
  echo "FAIL: menu tree must exclude page/permission rows without route path"
  exit 1
fi

if ! grep -Fq 'm.getVisible() == null || m.getVisible() == 1' "$AUTH_SERVICE"; then
  echo "FAIL: menu tree must exclude hidden rows"
  exit 1
fi

echo "PASS: auth menu tree returns only visible directories and routable page nodes."
