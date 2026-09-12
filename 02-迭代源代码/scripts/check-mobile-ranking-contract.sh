#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
api="$ROOT/aigicrm-miniprogram/api/score.js"
page="$ROOT/aigicrm-miniprogram/pages/ranking/index.js"
detail="$ROOT/aigicrm-miniprogram/pages/ranking/detail/index.js"
backend="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/score/controller/ScoreController.java"
manifest="$ROOT/docs/unified-feature-manifest.md"

grep -q "'/api/score/ranking'" "$api" || { echo "FAIL: mobile ranking API route is missing" >&2; exit 1; }
grep -q "getRanking" "$api" || { echo "FAIL: mobile ranking API wrapper is missing" >&2; exit 1; }
grep -q "hasPermission('score:view')" "$page" || { echo "FAIL: mobile ranking permission guard is missing" >&2; exit 1; }
grep -q "openDetail" "$page" || { echo "FAIL: mobile ranking detail action is missing" >&2; exit 1; }
grep -q "@GetMapping(\"/ranking\")" "$backend" || { echo "FAIL: backend ranking route is missing" >&2; exit 1; }
grep -q "hasAuthority('score:view')" "$backend" || { echo "FAIL: backend ranking permission is missing" >&2; exit 1; }
grep -q "score.ranking.*implemented" "$manifest" || { echo "FAIL: score.ranking manifest status is stale" >&2; exit 1; }
test -s "$detail" || { echo "FAIL: mobile ranking detail page is missing" >&2; exit 1; }

echo "PASS: mobile score ranking page, API, permission, and manifest contract is aligned."
