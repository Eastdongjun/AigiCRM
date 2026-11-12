#!/usr/bin/env bash
# 【T10.2 2026-11-12】码↔范围键映射双源对账(轻量契约):
# 前端 PermScopeManager.PERMISSION_SCOPE_TARGETS(权限码→module|action)与后端
# PermCenterService.SCOPE_TARGETS(module|action→label)是同一映射的两处手写——
# 本契约断言两侧 (module|action) 对集合完全一致, 漂移即红(新增码只改一侧即炸)。
# 注: 生成化(scope-targets.json)留待后续, 当前先机器锁双源(行为零变化)。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
fail() { echo "FAIL: $1" >&2; exit 1; }

# 【P1 修复 2026-11-12】负例自证通道(AIGI_NEGATIVE=1): 前端映射沙箱副本删一行——
# 双源 diff 必须红; 证明对账器"改坏会红"。
if [ "${AIGI_NEGATIVE:-0}" = "1" ]; then
  NEG_DIR=$(mktemp -d /tmp/t9neg-par.XXXXXX); trap 'rm -rf "$NEG_DIR"' EXIT
  cp "$ROOT/aigicrm-ui/src/views/system/PermScopeManager.vue" "$NEG_DIR/PermScopeManager.vue"
  python3 - "$NEG_DIR/PermScopeManager.vue" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1]); t = p.read_text()
t2 = t.replace("  'sea:view': { module: 'sea', action: 'view' },\n", "")
assert t2 != t, "变异未生效: 沙箱中未找到 sea:view 映射行"
p.write_text(t2)
PY
  backend=$({ grep -oE '\{"[a-z]+", "[a-z_]+"' "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/PermCenterService.java" || true; } | tr -d '{"' | tr -d ' ' | tr ',' '|' | sort -u)
  frontend=$({ grep -oE "module: '[a-z]+', action: '[a-z_]+'" "$NEG_DIR/PermScopeManager.vue" || true; } | sed "s/module: '//; s/', action: '/|/; s/'$//" | sort -u)
  if [ "$backend" = "$frontend" ]; then
    fail "[负例自证] 前端映射删行后 diff 未红——对账器失效"
  fi
  echo "FAIL: [负例自证] 前端删 sea:view 映射(沙箱变异)后双源 diff 正确抓红" >&2
  exit 1
fi

SVC="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/PermCenterService.java"
UI="$ROOT/aigicrm-ui/src/views/system/PermScopeManager.vue"
[ -f "$SVC" ] && [ -f "$UI" ] || fail "映射双源文件缺失"

# 后端: SCOPE_TARGETS 数组的 {"module","action",...} 行
backend=$({ grep -oE '\{"[a-z]+", "[a-z_]+"' "$SVC" || true; } | tr -d '{"' | tr -d ' ' | tr ',' '|' | sort -u)
# 前端: 'code': { module: 'x', action: 'y' } 行
frontend=$({ grep -oE "module: '[a-z]+', action: '[a-z_]+'" "$UI" || true; } | sed "s/module: '//; s/', action: '/|/; s/'$//" | sort -u)

[ -n "$backend" ] || fail "后端 SCOPE_TARGETS 抽取为空(扫描形态漂移?)"
[ -n "$frontend" ] || fail "前端 PERMISSION_SCOPE_TARGETS 抽取为空(扫描形态漂移?)"

if [ "$backend" != "$frontend" ]; then
  echo "  [码↔范围键双源漂移] 差异(左=后端 右=前端):" >&2
  diff <(echo "$backend") <(echo "$frontend") | sed 's/^/  /' >&2 || true
  fail "PermScopeManager 与 PermCenterService 的映射不一致——改一侧须同步另一侧"
fi

cnt=$(echo "$backend" | grep -c .)
echo "PASS: 码↔范围键映射双源对账($cnt 个 module|action 对两侧一致)"
