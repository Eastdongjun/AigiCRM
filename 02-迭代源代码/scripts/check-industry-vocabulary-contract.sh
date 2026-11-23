#!/usr/bin/env bash
# S6.3 契约：DB 行业字典种子与前端断网兜底列表必须同源，防止漂移。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.23.6__industry_dict_seed.sql"
JS="$ROOT/aigicrm-ui/src/utils/dict-fallback.js"
[ -f "$SQL" ] || { echo "FAIL: 缺少 $SQL" >&2; exit 1; }
[ -f "$JS" ] || { echo "FAIL: 缺少 $JS" >&2; exit 1; }

python3 - "$SQL" "$JS" <<'PY'
import re, sys
sql = open(sys.argv[1], encoding='utf-8').read()
js = open(sys.argv[2], encoding='utf-8').read()
db = re.findall(r"(?:SELECT|UNION ALL SELECT)\s+'([^']+)'", sql)
js_block = re.search(r'INDUSTRY_FALLBACK_OPTIONS\s*=\s*\[(.*?)\]', js, re.S)
if not js_block:
    print('FAIL: 未找到 INDUSTRY_FALLBACK_OPTIONS')
    sys.exit(1)
front = re.findall(r"'([^']+)'", js_block.group(1))
db = [x for x in db if x not in ('行业', 'industry', 'customer', 'B1 行业词表')]
if db != front:
    print('FAIL: 行业词表漂移')
    print('  DB :', db)
    print('  UI :', front)
    sys.exit(1)
print(f'PASS: 行业词表同源（{len(db)} 项）')
PY
