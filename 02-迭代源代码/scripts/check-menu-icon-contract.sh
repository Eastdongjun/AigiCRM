#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROUTER="$ROOT/aigicrm-ui/src/router/index.js"
MAIN="$ROOT/aigicrm-ui/src/main.js"

python3 - "$ROUTER" "$MAIN" <<'PY'
import re
import sys
from pathlib import Path

router = Path(sys.argv[1]).read_text(encoding="utf-8")
main = Path(sys.argv[2]).read_text(encoding="utf-8")
route_icons = set(re.findall(r"icon:\s*'([^']+)'", router))
# 【钉随代码 2026-10-07】D22 消除双清单: 注册形态从裸名列表改为 Icons.Xxx 静态成员访问
registered_icons = set(re.findall(r"Icons\.([A-Z][A-Za-z]+)", main))
missing = sorted(route_icons - registered_icons)
if missing:
    print("FAIL: route icons missing from main.js whitelist: " + ", ".join(missing), file=sys.stderr)
    raise SystemExit(1)
print(f"PASS: {len(route_icons)} route menu icons are registered in main.js whitelist.")
PY
