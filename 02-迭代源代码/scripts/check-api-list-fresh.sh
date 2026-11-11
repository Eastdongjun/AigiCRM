#!/bin/bash
# 【T6 2026-11-11 审计整改】API 清单内容级新鲜度契约(审计 P1-11):
# gate 的文档项只查 mtime(2 天≤3 天阈值)——api-list.md 曾出现"mtime 新鲜但内容陈旧"
# (committed 230 接口 vs 源码生成 227, 4 个退役 ACL 端点仍列在案)。本契约做内容级校验:
# 用 gen-api.py 重新生成到临时文件, 与 docs/api/api-list.md 逐行 diff(忽略生成时间行),
# 任何漂移即红——接口变更后必须重生成清单再提交。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GEN="$SCRIPT_DIR/gen-api.py"
SRC="$ROOT/aigicrm-backend/src/main/java/com/aigicrm"
COMMITTED="$ROOT/docs/api/api-list.md"
TMP="$(mktemp /tmp/api-list-fresh.XXXXXX.md)"
trap 'rm -f "$TMP"' EXIT

fail() { echo "FAIL: $1" >&2; exit 1; }

[ -f "$GEN" ] || fail "gen-api.py 不存在"
[ -f "$COMMITTED" ] || fail "docs/api/api-list.md 不存在"

python3 "$GEN" "$SRC" "$TMP" >/dev/null 2>&1 || fail "gen-api.py 生成失败"

# 忽略"生成时间"行(每次生成都变, 非内容漂移); 其余必须逐行一致
if ! diff <(grep -v '^> 生成时间' "$COMMITTED") <(grep -v '^> 生成时间' "$TMP") > /tmp/api-list-fresh.diff 2>&1; then
  echo "FAIL: docs/api/api-list.md 与源码生成的清单存在内容漂移(接口变更后未重生成):" >&2
  head -20 /tmp/api-list-fresh.diff | sed 's/^/  /' >&2
  echo "  处置: python3 scripts/gen-api.py aigicrm-backend/src/main/java/com/aigicrm docs/api/api-list.md" >&2
  exit 1
fi

echo "PASS: API 清单内容级新鲜度(与源码生成一致)"
