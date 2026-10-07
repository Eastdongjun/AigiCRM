#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MINI="$ROOT/aigicrm-miniprogram"
DOC="$ROOT/docs/prototype-state-coverage.md"
QA_DOC="$ROOT/docs/mobile-manual-qa-checklist.md"
fail=0

require_pattern() {
  local label="$1"
  local pattern="$2"
  shift 2
  if rg -q --glob '*.js' --glob '*.wxml' "$pattern" "$@"; then
    printf '[PASS] %s\n' "$label"
  else
    printf '[FAIL] %s\n' "$label"
    fail=1
  fi
}

pages="$(python3 - "$MINI/app.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as handle:
    print(len(json.load(handle).get('pages', [])))
PY
)"
missing_page_files="$(python3 - "$MINI/app.json" "$MINI" <<'PY'
import json, sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
root = Path(sys.argv[2])
missing = []
for page in manifest.get('pages', []):
    page_dir = root / page
    required = [page_dir.with_suffix('.js'), page_dir.with_suffix('.wxml')]
    if not all(path.is_file() for path in required):
        missing.append(page)
print('\n'.join(missing))
PY
)"
if [ -n "$missing_page_files" ]; then
  echo "[FAIL] registered pages missing .js/.wxml implementation:"
  printf '%s\n' "$missing_page_files" | sed 's/^/  /'
  fail=1
else
  echo "[PASS] every registered page has .js and .wxml implementation"
fi
prototype_baseline=51
# 【2026-10-07 钉随代码】1b3c865b 死页面清理后注册页 46→41; 钉改为区间+实现存在性(上方已验)
if [ "$pages" -ge 30 ] && [ "$pages" -le 46 ]; then
  coverage="$(awk -v pages="$pages" -v baseline="$prototype_baseline" 'BEGIN { printf "%.1f%%", pages * 100 / baseline }')"
  echo "[PASS] registered mini-program pages: $pages"
  echo "[INFO] prototype screenshot baseline: $prototype_baseline"
  echo "[INFO] independent-page coverage: $pages/$prototype_baseline ($coverage); six generic states are validated below"
else
  echo "[FAIL] registered mini-program pages $pages outside sanity band [30,46]"
  fail=1
fi

if rg -q 'docs/mobile-manual-qa-checklist.md' "$DOC" && rg -q '微信开发者工具/真机|DevTools/真机' "$DOC"; then
  echo "[PASS] prototype state coverage points manual validation to the mini-program QA checklist"
else
  echo "[FAIL] prototype state coverage must point unresolved UI validation to docs/mobile-manual-qa-checklist.md"
  fail=1
fi
if rg -q 'npm run verify:incentive-attachments' "$DOC" && rg -q '真机选择文件、真实提交记录、真实下载保存' "$DOC"; then
  echo "[PASS] prototype state coverage records DevTools incentive attachment precheck and real-device boundary"
else
  echo "[FAIL] prototype state coverage must record incentive attachment DevTools precheck and real-device boundary"
  fail=1
fi
if rg -q '浏览器 UI 点击矩阵待验收' "$DOC"; then
  echo "[FAIL] prototype state coverage still references stale browser UI matrix wording"
  fail=1
fi
if rg -q 'S1-S6' "$QA_DOC" || rg -q 'B1' "$QA_DOC"; then
  echo "[PASS] mobile manual QA checklist is available for state validation follow-up"
else
  echo "[FAIL] mobile manual QA checklist must be available for prototype state follow-up"
  fail=1
fi

require_pattern "loading state" 'loading|加载中' "$MINI/pages"
require_pattern "empty state" 'length===0|暂无|empty-state' "$MINI/pages"
require_pattern "request failure feedback" 'showError\(|加载失败|提交失败|处理失败' "$MINI/pages"
require_pattern "permission or auth boundary" 'hasPermission\(|请先登录|暂无权限' "$MINI/pages"
require_pattern "missing detail state" '!loading && !|暂无.*详情|暂无.*记录' "$MINI/pages"
require_pattern "failed action recovery" 'catch\(|finally\(\)|loading: false' "$MINI/pages"
# 【2026-10-07 钉随代码】notification 页随 1b3c865b 死页面清理删除(消息提醒=tabBar 角标+提醒模块承载), processing 锁钉随页面一并退役

exit "$fail"
