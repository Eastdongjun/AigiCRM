#!/bin/bash
# 【2026-11-07 工程纪律守则 D8 执法器】PC 类型检查基线对账(vue-tsc)
# 缘起: 复盘条目1——PC 前端零类型检查, loadScopeCounts 悬空数周无人知。
# 口径: 存量 561 错(67 文件)冻结在 scripts/ui-typecheck-baseline.json, 新增即 FAIL, 修一处减一数。
# 依赖: aigicrm-ui devDependencies 里的 vue-tsc(2026-11-07 引入)。
# 用法: bash scripts/check-ui-typecheck.sh   (gate.sh [9] AIGI_TSC=1 可选档; 全量跑约 1-3 分钟)
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UI="$ROOT/aigicrm-ui"
BIN="$UI/node_modules/.bin/vue-tsc"
BASE="$ROOT/scripts/ui-typecheck-baseline.json"

if [ ! -x "$BIN" ]; then
  echo "FAIL: vue-tsc 未安装——cd aigicrm-ui && npm install 后重试(守则 D8)"
  exit 1
fi

LOG=/tmp/ui-typecheck-run.log
(cd "$UI" && "$BIN" -p tsconfig.typecheck.json --noEmit) > "$LOG" 2>&1
TSC_RC=$?

node -e "
const fs = require('fs');
const base = JSON.parse(fs.readFileSync(process.argv[1], 'utf8'));
const now = {};
for (const line of fs.readFileSync(process.argv[2], 'utf8').split('\n')) {
  const m = line.match(/^([^(]+\.(?:vue|js|mjs))\([^)]*\): error TS\d+/);
  if (!m) continue;
  const f = m[1].replace(/^aigicrm-ui\//, '');
  now[f] = (now[f] || 0) + 1;
}
let newErr = 0, fixed = 0, detail = [];
for (const [f, n] of Object.entries(now)) {
  const b = base[f] ?? 0;
  if (n > b) { newErr += n - b; detail.push('  ' + f + ': ' + n + ' > 基线 ' + b + (b === 0 ? '(新文件)' : '')); }
  else if (n < b) fixed += b - n;
}
if (newErr > 0) {
  console.log('FAIL: vue-tsc 类型错误新增 ' + newErr + ' 处(存量冻结, 修一处减一数):');
  detail.forEach((d) => console.log(d));
  process.exit(1);
}
const total = Object.entries(now).reduce((a, [, n]) => a + n, 0);
console.log('PASS: vue-tsc 类型基线对账通过(当前 ' + total + ' 错/基线 ' + (total + fixed) + ', 本次修复 ' + fixed + ')' + (fixed > 0 ? ' —— 请同步收紧 scripts/ui-typecheck-baseline.json' : ''));
" "$BASE" "$LOG"
PARSED=$?

if [ "$PARSED" -ne 0 ]; then exit 1; fi
# vue-tsc 自身崩溃(非类型错误, 如配置坏)也要红: RC=1 且解析不到任何文件计数 = 可疑
if [ "$TSC_RC" -ne 0 ] && ! grep -qE 'error TS' "$LOG"; then
  echo "FAIL: vue-tsc 运行异常(exit=$TSC_RC 且无类型错误输出):"
  tail -5 "$LOG"
  exit 1
fi
exit 0
