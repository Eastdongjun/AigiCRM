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

# 【2026-10-07 钉随代码】容量加固(2026-10-06)后 dashboard 方法体=缓存壳, 统计装配移入 loadDashboard;
# 钉随实现下沉到真实方法体
dashboard_impl = method_body("computeDashboardShared")
if not re.search(r"customerService\.countExpiringTodayByScope\(\)", dashboard_impl):
    raise SystemExit("FAIL: dashboard today-expiring count must use scope-aware customer service")
print("PASS: dashboard today-expiring count uses scope-aware service")

analysis = method_body("analysis")
# 【P0-5 B-java23 2026-11-18】analysis 拆分后各统计计数统一走 scopedCustomerQuery(officeId, deptId)；
# 钉随语义不变：今日到期计数必须经该作用域查询构造，不得退化为无条件 count。
if not re.search(
        r'long todayExpiring = customerMapper\.selectCount\(\s*scopedCustomerQuery\(officeId, deptId\)',
        analysis, re.S) or not re.search(r'data\.put\("todayExpiring", todayExpiring\)', analysis):
    raise SystemExit("FAIL: analysis today-expiring count must honor analysis filters")
print("PASS: analysis today-expiring count honors analysis filters")
# 六个存量计数 + industryStats 共 7 处 scope 查询；下限 6 防退化
scoped_calls = len(re.findall(r"scopedCustomerQuery\(officeId, deptId\)", analysis))
if scoped_calls < 6:
    raise SystemExit("FAIL: analysis customer summaries do not consistently apply office/dept/customer scopes")
office_scope = method_body("applyOfficeScope")
if not re.search(r"if \(deptId != null\)", office_scope) or "expandSubtree(deptMapper, deptId)" not in office_scope:
    raise SystemExit("FAIL: applyOfficeScope must keep department subtree expansion")
print("PASS: analysis customer summaries apply department subtree filters")

daily = method_body("dailyStats")
weekly = method_body("weeklyStats")
if "applyFileScope(" not in daily or "applyFileScope(" not in weekly:
    raise SystemExit("FAIL: daily and weekly file stats apply account scope")
print("PASS: daily and weekly file stats apply account scope")

team = method_body("teamActivities")
# 【WP1.5/WP4 2026-11-13 统计单一源(锁定决策#7)】团队动态可见人员=客户可见负责人
# (CustomerVisibilityService.visibleOwnerIds, (customer,view) 单一源);
# 旧 (statistics,view) 槽调用 getCurrentScopeUserIds() 直调形态反钉防复活
if "customerVisibilityService.visibleOwnerIds()" not in team:
    raise SystemExit("FAIL: team activities do not use customer:view single source")
if "getCurrentScopeUserIds()" in team:
    raise SystemExit("FAIL: retired statistics-slot call still present in teamActivities (WP1.5)")
print("PASS: team activities resolve current account scope (customer:view single source)")
# 【P0-5 B-java28 2026-11-18】teamActivities 拆分后六路来源各自成方法；作用域谓词钉随到对应来源方法
for wrapper, method in (
    ("logWrapper", "appendCustomerLogActivities"),
    ("incWrapper", "appendIncentiveActivities"),
    ("fileWrapper", "appendFileActivities"),
    ("apprWrapper", "appendApprovalActivities"),
    ("custWrapper", "appendNewCustomerActivities"),
    ("sysWrapper", "appendSysLogActivities"),
):
    if not re.search(rf"{wrapper}\.in\([^\n]*visibleUserIds", method_body(method)):
        raise SystemExit(f"FAIL: team activity {wrapper} has scoped predicate")
    print(f"PASS: team activity {wrapper} has scoped predicate")

print("PASS: statistics scope contract is aligned; live database fixture coverage remains separate.")
PY
