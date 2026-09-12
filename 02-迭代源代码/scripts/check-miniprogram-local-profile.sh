#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="$ROOT/aigicrm-miniprogram/config/env.local.example.js"
DEFAULT="$ROOT/aigicrm-miniprogram/config/env.js"
fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$PROFILE" ] || fail 'local mini-program profile is missing'
grep -Fq "apiBaseUrl: 'http://127.0.0.1:8080'" "$PROFILE" || fail 'local profile must target the local backend'
grep -Fq 'demoMode: false' "$PROFILE" || fail 'local profile must disable demo mode'
grep -Fq "apiBaseUrl: ''" "$DEFAULT" || fail 'default profile must remain credential-free and unset'
grep -Fq 'demoMode: true' "$DEFAULT" || fail 'default profile must remain demo mode'
if rg -n "(password|secret|token|access_key|private_key)\s*[:=]" "$PROFILE"; then
  fail 'local profile must not contain credentials'
fi

echo 'PASS: mini-program local profile is free, credential-free, and isolated from the demo default.'
