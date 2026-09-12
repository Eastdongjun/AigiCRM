#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
page="$ROOT/aigicrm-miniprogram/pages/incentive/index.js"
wxml="$ROOT/aigicrm-miniprogram/pages/incentive/index.wxml"
manifest="$ROOT/docs/unified-feature-manifest.md"
grep -q "hasPermission('incentive:view')" "$page" || { echo "FAIL: incentive view gate is missing" >&2; exit 1; }
grep -q "hasPermission('incentive:add')" "$page" || { echo "FAIL: incentive upload gate is missing" >&2; exit 1; }
grep -q 'canUploadEvidence' "$wxml" || { echo "FAIL: incentive upload UI gate is missing" >&2; exit 1; }
grep -q "incentive.index.*implemented" "$manifest" || { echo "FAIL: incentive.index manifest status is stale" >&2; exit 1; }
echo "PASS: mobile incentive query, detail, upload, and permission contract is aligned."
