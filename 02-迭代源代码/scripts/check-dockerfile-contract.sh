#!/usr/bin/env bash
# Local Dockerfile quality checks that do not require Docker Hub access.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKERFILE="$ROOT/aigicrm-backend/Dockerfile"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$DOCKERFILE" ] || fail "Missing backend Dockerfile: $DOCKERFILE"

python3 - "$DOCKERFILE" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
previous_instruction = None
previous_line = None
violations = []

for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
    stripped = raw_line.strip()
    if not stripped or stripped.startswith("#"):
        continue
    instruction = stripped.split(None, 1)[0].upper()
    if instruction == "FROM":
        previous_instruction = None
        previous_line = None
        continue
    if instruction == "RUN" and previous_instruction == "RUN":
        violations.append((previous_line, line_number))
    previous_instruction = instruction
    previous_line = line_number

if violations:
    for previous, current in violations:
        print(f"FAIL: consecutive RUN instructions at lines {previous} and {current}", file=sys.stderr)
    raise SystemExit(1)

print(f"PASS: {path} has no consecutive RUN instructions within a Docker stage.")
PY
