#!/usr/bin/env bash
# 校验"核心客户六项指标"契约在 UI / 后端 / 文档三处一致。
#
# 【2026-09-12 修正契约与权威文档的口径分歧】
#   原契约断言 `grep -Fq "indicator06" "$COMPOSABLE"`，失败信息为
#   "last scored indicator field is missing"。该断言**与权威口径矛盾**：
#
#   权威口径（docs/系统操作说明.md §12）：
#     所谓"六项指标" = **1 项硬性门槛（开票、回款均 ≥ 100 万）+ 5 项 1-3 分评分**，
#     "五项评分满分 15 分，核心客户要求五项合计至少 12 分"。
#   实现侧与之一致：
#     · 后端 StatisticsQueryService.java:69-71 indicatorScore()
#         = relationshipScore + indicator02 + indicator03 + indicator04 + indicator05
#       **不含 indicator06**（全仓仅实体字段声明，无任何读取点）
#     · 前端 useCustomerDetail.js indicatorTotal 同样只加这 5 项（上限 15）
#     · IndicatorTab.vue 显示 '/ 15'
#   ⇒ 评分项就是 **5 项**，不是 6 项。
#
#   indicator_06 是**遗留列**：存在于 V1__baseline.sql:268 与实体
#   BizCustomerIndicator.java:23，生产 dump 22 行中 8 行有值（1~2），
#   但**代码从未读取**。
#
#   故本契约改为：断言 5 项评分字段齐全，并**显式断言 indicator06 不参与计分**，
#   把这个事实固化为可回归的约束，避免它日后被误当评分项重新启用。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI="$ROOT/aigicrm-ui/src/views/customer/detail/IndicatorTab.vue"
COMPOSABLE="$ROOT/aigicrm-ui/src/composables/useCustomerDetail.js"
STATS="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java"
DOC="$ROOT/docs/系统操作说明.md"

fail() { echo "FAIL: $*" >&2; exit 1; }

# ---- 1) UI 必须显示 15 分上限（5 项 × 3 分）----
grep -Fq '/ 15' "$UI" || fail 'customer indicator UI must show a 15-point total (/ 15)'

# ---- 2) 前端 5 项评分字段必须齐全 ----
for f in relationshipScore indicator02 indicator03 indicator04 indicator05; do
  grep -Fq "$f" "$COMPOSABLE" || fail "scored indicator field missing in UI composable: $f"
done

# ---- 3) 前端计分公式必须恰好是这 5 项 ----
total_line="$(grep -n 'indicatorTotal[[:space:]]*=[[:space:]]*computed' "$COMPOSABLE" | head -1 || true)"
[ -n "$total_line" ] || fail 'UI indicatorTotal definition not found'
for f in relationshipScore indicator02 indicator03 indicator04 indicator05; do
  printf '%s' "$total_line" | grep -Fq "indicatorForm.$f" \
    || fail "UI indicatorTotal must include indicatorForm.$f"
done

# ---- 4) 后端计分公式必须恰好是这 5 项 ----
grep -Eq 'getRelationshipScore\(\)' "$STATS" || fail 'backend score must include relationshipScore'
for g in getIndicator02 getIndicator03 getIndicator04 getIndicator05; do
  grep -Fq "$g" "$STATS" || fail "backend score missing $g"
done

# ---- 5) 遗留列不得参与计分（把事实固化为约束）----
if printf '%s' "$total_line" | grep -Fq 'indicator06'; then
  fail 'UI indicatorTotal must not include indicator06 (vestigial column; docs §12 says 5 scored items)'
fi
# 后端 indicatorScore 方法体内不得出现 getIndicator06
if awk '/int indicatorScore|indicatorScore\(/,/^    }/' "$STATS" | grep -Fq 'getIndicator06'; then
  fail 'backend indicatorScore must not include getIndicator06 (vestigial column)'
fi

# ---- 6) 其余既有断言 ----
grep -Fq 'BizPaymentMapper' "$STATS" || fail 'statistics must load payment totals'
grep -Fq 'isCoreCustomer' "$STATS" || fail 'statistics must centralize core-customer threshold logic'
grep -Fq '开票、回款均 ≥ 100万' "$DOC" || fail 'operator documentation must state both financial thresholds'
grep -Fq '五项评分满分 15 分' "$DOC" || fail 'operator documentation must state the 5-item / 15-point rule'

echo 'PASS: 六项指标契约一致（1 硬门槛 + 5 项评分，上限 15 分；indicator06 确认不参与计分）'
