#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVICE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java"

[ -f "$SERVICE" ] || { echo "FAIL: statistics service is missing" >&2; exit 1; }

python3 - "$SERVICE" <<'PY'
import re
import sys
from pathlib import Path

source = Path(sys.argv[1]).read_text(encoding="utf-8")

def method_body(name):
    match = re.search(r"\b" + re.escape(name) + r"\s*\([^)]*\)\s*\{", source)
    if not match:
        raise SystemExit(f"FAIL: {name} method is missing")
    start = match.end()
    depth = 1
    index = start
    while depth and index < len(source):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
        index += 1
    if depth:
        raise SystemExit(f"FAIL: {name} method body is incomplete")
    return source[start:index - 1]

for label, method, pattern in (
    ("daily customer stats use scope-aware service", "dailyStats", r"customerService\.dailyNewCustomers\(\)"),
    ("weekly customer stats use scope-aware service", "weeklyStats", r"customerService\.weeklyNewCustomers\(\)"),
):
    if not re.search(pattern, method_body(method)):
        raise SystemExit(f"FAIL: {label}")
    print(f"PASS: {label}")

dashboard = method_body("dashboard")
if not re.search(r"customerService\.countExpiringTodayByScope\(\)", dashboard):
    raise SystemExit("FAIL: dashboard today-expiring count must use scope-aware customer service")
print("PASS: dashboard today-expiring count uses scope-aware service")

analysis = method_body("analysis")
if not re.search(r"todayExpiringW", analysis) or not re.search(r'data\.put\("todayExpiring", todayExpiring\)', analysis):
    raise SystemExit("FAIL: analysis today-expiring count must honor analysis filters")
print("PASS: analysis today-expiring count honors analysis filters")
dept_tree_filters = len(re.findall(r"if \(deptId != null\).*?expandSubtree\(deptMapper, deptId\)", analysis, re.S))
if dept_tree_filters < 5:
    raise SystemExit("FAIL: analysis customer summaries do not consistently apply department subtree filters")
print("PASS: analysis customer summaries apply department subtree filters")

daily = method_body("dailyStats")
weekly = method_body("weeklyStats")
if "applyFileScope(" not in daily or "applyFileScope(" not in weekly:
    raise SystemExit("FAIL: daily and weekly file stats apply account scope")
print("PASS: daily and weekly file stats apply account scope")

team = method_body("teamActivities")
if "getCurrentScopeUserIds()" not in team:
    raise SystemExit("FAIL: team activities resolve current account scope")
print("PASS: team activities resolve current account scope")
for wrapper in ("logWrapper", "incWrapper", "fileWrapper", "apprWrapper", "custWrapper", "sysWrapper"):
    if not re.search(rf"{wrapper}\.in\([^\n]*visibleUserIds", team):
        raise SystemExit(f"FAIL: team activity {wrapper} has scoped predicate")
    print(f"PASS: team activity {wrapper} has scoped predicate")

print("PASS: statistics scope contract is aligned; live database fixture coverage remains separate.")
PY
