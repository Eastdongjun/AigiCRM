#!/usr/bin/env bash
# Validate the mobile core-indicator overview contract without live credentials.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="$ROOT/aigicrm-miniprogram/api/statistics.js"
PAGE="$ROOT/aigicrm-miniprogram/pages/indicator/index.js"
APP="$ROOT/aigicrm-miniprogram/app.json"
APPS="$ROOT/aigicrm-miniprogram/pages/app/index.js"

fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq "url: '/api/statistics/indicator-stats'" "$API" || fail 'mobile indicator API wrapper route is missing'
grep -Fq "hasPermission('statistics:view')" "$PAGE" || fail 'mobile indicator page permission gate is missing'
grep -Fq '"pages/indicator/index"' "$APP" || fail 'mobile indicator page is not registered'
grep -Fq "key: 'indicator'" "$APPS" || fail 'mobile app menu indicator entry is missing'
grep -Fq "indicator: '/pages/indicator/index'" "$APPS" || fail 'mobile indicator navigation route is missing'

echo 'PASS: mobile indicator overview contract is aligned.'
