#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MINI="$ROOT/aigicrm-miniprogram"
fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$MINI/app.json" ] || fail 'AIGICRM mini-program root is missing app.json'
[ -f "$MINI/project.config.json" ] || fail 'AIGICRM mini-program root is missing project.config.json'
python3 - "$MINI/app.json" "$MINI/project.config.json" <<'PY'
import json, sys
from pathlib import Path

app = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
project = json.loads(Path(sys.argv[2]).read_text(encoding='utf-8'))
pages = app.get('pages', [])
if len(pages) != 46:
    raise SystemExit(f'expected 46 pages, got {len(pages)}')
if project.get('compileType') != 'miniprogram':
    raise SystemExit('project.config.json compileType must be miniprogram')
if project.get('miniprogramRoot') != './':
    raise SystemExit('project.config.json miniprogramRoot must be ./')
if project.get('appid') != 'wxde685ea31717512c':
    raise SystemExit('project.config.json appid is not the registered AIGICRM appid')
PY
echo 'PASS: WeChat developer-tool project root is self-contained and AIGICRM-specific.'
