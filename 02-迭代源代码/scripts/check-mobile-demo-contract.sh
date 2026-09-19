#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
demo="$ROOT/aigicrm-miniprogram/data/demo.js"
home="$ROOT/aigicrm-miniprogram/pages/home/index.js"
stats="$ROOT/aigicrm-miniprogram/pages/statistics/index.js"
detail="$ROOT/aigicrm-miniprogram/pages/statistics/detail/index.js"

grep -q "\['2', '今日到期'\]" "$demo" || { echo "FAIL: demo home cards omit today-expiring baseline" >&2; exit 1; }
grep -q "todayExpiring: 2" "$stats" || { echo "FAIL: demo statistics omit today-expiring baseline" >&2; exit 1; }
grep -q "todayExpiring: 2" "$detail" || { echo "FAIL: demo statistics detail omits today-expiring baseline" >&2; exit 1; }
grep -q "dashboard.todayExpiring" "$home" || { echo "FAIL: home page does not consume dashboard today-expiring field" >&2; exit 1; }

echo "PASS: mobile demo metrics share the today-expiring baseline."
