#!/bin/bash
# 【底座收敛 P1 第一步 2026-11-11】权限码生成物新鲜度契约:
# gen-permission-codes.py 重新生成与 committed 产物 diff 必须为空——后端码变更后
# 未重生成双端常量文件即红(与 check-api-list-fresh.sh 同模式)。
# 消费侧: PC/MP 手写字面量替换为 PERMISSION_CODES 常量分批进行; 替换完成前
# 漂移由 check-permission-code-consumption.sh 兜底。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
fail() { echo "FAIL: $1" >&2; exit 1; }

cd "$ROOT"
for f in aigicrm-ui/src/generated/permission-codes.js aigicrm-miniprogram/generated/permission-codes.js; do
  [ -f "$ROOT/$f" ] || fail "生成物缺失: $f"
  git -C "$ROOT" ls-files --error-unmatch "$f" >/dev/null 2>&1 || fail "生成物未纳入版本控制: $f"
done
python3 "$SCRIPT_DIR/gen-permission-codes.py" >/dev/null || fail "生成器执行失败"
git diff --quiet -- aigicrm-ui/src/generated/permission-codes.js aigicrm-miniprogram/generated/permission-codes.js \
  || { git diff -- aigicrm-ui/src/generated/permission-codes.js aigicrm-miniprogram/generated/permission-codes.js | head -15 | sed 's/^/  /' >&2
       fail "权限码生成物与后端权威集漂移——运行 python3 scripts/gen-permission-codes.py 后提交"; }
echo "PASS: 权限码生成物新鲜(PC/MP 双端与后端权威集一致)"
