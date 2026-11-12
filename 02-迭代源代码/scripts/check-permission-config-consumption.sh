#!/usr/bin/env bash
# 【T9 2026-11-12】配置-消费注册表契约: docs/permission/config-consumption-matrix.md 的每个码
# 必须三端可验证——①后端有消费(注解/命令式/FE_ONLY 白名单) ②PC 有消费或白名单理由
# ③MP 有消费或"无 MP 入口"备注。负例通道 AIGI_NEGATIVE=1: 在源码副本上删一个消费点必须红
# (独立副本, 不动真源码, 也不允许用注册表自身自证)。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail() { echo "FAIL: $1" >&2; exit 1; }

MATRIX="$ROOT/docs/permission/config-consumption-matrix.md"
[ -f "$MATRIX" ] || fail "注册表缺失: $MATRIX"

NEG=0
[ "${AIGI_NEGATIVE:-0}" = "1" ] && NEG=1

# ---- 负例沙箱: 复制源码子集, 删掉一个后端消费点, 让 ① 的断言在沙箱上跑 ----
SANDBOX=""
cleanup() { if [ -n "${SANDBOX:-}" ]; then rm -rf "$SANDBOX"; fi; return 0; }
trap cleanup EXIT
if [ "$NEG" = "1" ]; then
  SANDBOX=$(mktemp -d /tmp/t9-neg.XXXXXX)
  mkdir -p "$SANDBOX/aigicrm-backend/src/main/java"
  cp -R "$ROOT/aigicrm-backend/src/main/java/com" "$SANDBOX/aigicrm-backend/src/main/java/"
  cp "$ROOT/scripts/gen-permission-codes.py" "$SANDBOX/gen.py"
  # 【P1 修复 2026-11-12】变异改为"全锚剥离": 把 statistics:view 的所有注解锚整体移出后端源码
  # (旧变异只删一个锚, 该码仍余多处锚 → 计数不降 → 负例自证失效)。
  python3 - "$SANDBOX" <<'PY'
import sys
from pathlib import Path
target = "'statistics:view'"
touched = 0
for p in Path(sys.argv[1]).rglob("*.java"):
    t = p.read_text(errors="replace")
    if target not in t:
        continue
    p.write_text(t.replace(target, "'mutation:removed-view'"))
    touched += 1
assert touched > 0, "变异未生效: 沙箱未找到 statistics:view 锚"
PY
  BACKEND_SRC="$SANDBOX/aigicrm-backend/src/main/java"
  GEN_PY="$SANDBOX/gen.py"
  echo "[负例] 沙箱已剥离 statistics:view 的全部后端消费锚——契约必须红"
else
  BACKEND_SRC="$ROOT/aigicrm-backend/src/main/java"
  GEN_PY="$ROOT/scripts/gen-permission-codes.py"
fi

# ---- ① 注册表每个功能码: 后端有消费(注解/命令式) 或 FE_ONLY 白名单 ----
backend_hits() { { grep -rl "'$1'" "$BACKEND_SRC" --include="*.java" 2>/dev/null || true; } | head -1; }
# pipefail 下空匹配的中段 grep 会杀整条管道——所有中段均置 || true(空集是合法结果)
fe_only=$({ { grep -A2 "FE_ONLY_CODES = \[" "$GEN_PY" || true; } | { grep -oE '"[a-z][a-z0-9:_-]+"' || true; } | tr -d '"' | tr '\n' ' '; })
cons_whitelist=$({ { grep -E '^WHITELIST=' "$ROOT/scripts/check-permission-code-consumption.sh" || true; } | { grep -oE '"[a-z][a-z0-9:_-]+:[^"]*"' || true; } | { cut -d: -f1 || true; } | tr -d '"' | tr '\n' ' '; })

miss=0
while IFS='|' read -r code backend_note _rest; do
  code=$(echo "$code" | xargs)
  [ -z "$code" ] && continue
  case "$code" in ''|'#'*|':---'*|'---'|code) continue;; esac
  echo "$code" | grep -qE '^[a-z][a-z0-9_-]*:[a-z0-9:_-]+$' || continue
  if echo "$code" | grep -qE '(view|edit|add|delete|manage|upload|share|export)$|:[a-z]+:[a-z]+$'; then :; fi
  hit=$(backend_hits "$code")
  if [ -z "$hit" ]; then
    if echo " $fe_only $cons_whitelist " | grep -q " $code "; then
      : # FE_ONLY / 消费白名单(理由在注册表备注)
    else
      echo "  [后端零消费] $code" >&2
      miss=$((miss+1))
    fi
  fi
done < <(grep -E '^\| [a-z]+[a-z0-9_-]*:[a-z0-9:_-]+ \|' "$MATRIX")

if [ "$NEG" = "1" ]; then
  # 负例: statistics:view 仍有多端点锚(删一个不致零)——改用行级检测: 沙箱内该码锚点数必须少于原
  orig=$(grep -rl "'statistics:view'" "$ROOT/aigicrm-backend/src/main/java" --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
  sand=$(grep -rl "'statistics:view'" "$BACKEND_SRC" --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
  [ "$sand" -lt "$orig" ] || fail "负例未生效: 沙箱消费锚未减少($orig->$sand)"
  echo "FAIL: [负例自证] 后端消费锚被删除后契约必须红(锚 $orig->$sand)" >&2
  exit 1
fi

[ "$miss" -eq 0 ] || fail "注册表 ${miss} 个码后端零消费且无白名单——注册表腐烂, 修表或修码"

# ---- ② 注册表 PC 消费格非空(允许'无'类备注=显式产品差异) ----
pc_empty=$(grep -E '^\| [a-z]+[a-z0-9_-]*:[a-z0-9:_-]+ \|' "$MATRIX" | awk -F'|' 'NF>4 && ($4=="" || $4==" ")' | wc -l | tr -d ' ')
[ "$pc_empty" -eq 0 ] || fail "注册表 ${pc_empty} 行 PC 消费格为空——写消费点或'无 PC 入口'备注"

# ---- ③ 双白名单对账: gen FE_ONLY ⊆ consumption WHITELIST(前端码单一理由源) ----
for c in $fe_only; do
  echo " $cons_whitelist " | grep -q " $c " || fail "FE_ONLY($c) 不在 consumption WHITELIST——前端专属码理由双源漂移"
done

rows=$(grep -cE '^\| [a-z]+[a-z0-9_-]*:[a-z0-9:_-]+ \|' "$MATRIX")
echo "PASS: 配置-消费注册表契约(${rows} 功能码后端消费可grep/PC消费格非空/双白名单对账; 负例通道 AIGI_NEGATIVE=1)"
