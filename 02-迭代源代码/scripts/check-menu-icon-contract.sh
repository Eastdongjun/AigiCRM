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
registered_icons = set(re.findall(r"^\s+([A-Z][A-Za-z]+),$", main, re.MULTILINE))
missing = sorted(route_icons - registered_icons)
if missing:
    print("FAIL: route icons missing from main.js whitelist: " + ", ".join(missing), file=sys.stderr)
    raise SystemExit(1)
print(f"PASS: {len(route_icons)} route menu icons are registered in main.js whitelist.")
PY
