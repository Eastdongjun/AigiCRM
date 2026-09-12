#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MINI="$ROOT/aigicrm-miniprogram"
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
if [ "$pages" = "46" ]; then
  coverage="$(awk -v pages="$pages" -v baseline="$prototype_baseline" 'BEGIN { printf "%.1f%%", pages * 100 / baseline }')"
  echo "[PASS] registered mini-program pages: $pages"
  echo "[INFO] prototype screenshot baseline: $prototype_baseline"
  echo "[INFO] independent-page coverage: $pages/$prototype_baseline ($coverage); six generic states are validated below"
else
  echo "[FAIL] expected 46 registered mini-program pages, got $pages"
  fail=1
fi

require_pattern "loading state" 'loading|加载中' "$MINI/pages"
require_pattern "empty state" 'length===0|暂无|empty-state' "$MINI/pages"
require_pattern "request failure feedback" 'showError\(|加载失败|提交失败|处理失败' "$MINI/pages"
require_pattern "permission or auth boundary" 'hasPermission\(|请先登录|暂无权限' "$MINI/pages"
require_pattern "missing detail state" '!loading && !|暂无.*详情|暂无.*记录' "$MINI/pages"
require_pattern "failed action recovery" 'catch\(|finally\(\)|loading: false' "$MINI/pages"
if rg -q 'processing: false' "$MINI/pages/notification/index.js" && rg -q 'processing\) return|processing: true' "$MINI/pages/notification/index.js" && rg -q 'finally\(.*processing: false' "$MINI/pages/notification/index.js"; then
  echo "[PASS] notification action resets processing lock"
else
  echo "[FAIL] notification action must lock duplicate submits and reset in finally"
  fail=1
fi

exit "$fail"
