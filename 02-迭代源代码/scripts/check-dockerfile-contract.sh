#!/usr/bin/env bash
# Local Dockerfile quality checks that do not require Docker Hub access.

set -e
set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKERFILE="$ROOT/aigicrm-backend/Dockerfile"
UI_DOCKERFILE="$ROOT/aigicrm-ui/Dockerfile"
WRAPPER_PROPS="$ROOT/aigicrm-backend/.mvn/wrapper/maven-wrapper.properties"

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

# 【PR-23.D-03/D-04 构建工具链冻结】
# 1) 前端镜像必须 npm ci(严格按 lockfile, 失同步构建失败); 禁 npm install(语义漂移)
[ -f "$UI_DOCKERFILE" ] || fail "Missing frontend Dockerfile: $UI_DOCKERFILE"
grep -Fq "RUN npm ci" "$UI_DOCKERFILE" || fail "frontend Dockerfile must install with npm ci (lockfile-frozen)"
if grep -Fq "RUN npm install" "$UI_DOCKERFILE"; then fail "frontend Dockerfile must not use npm install (semver drift breaks reproducibility)"; fi

# 2) 后端构建镜像的 Maven 版本必须与 mvnw wrapper 锁定版一致(浮动 tag 会漂移)
WRAPPER_MAVEN=$(sed -n 's/.*apache-maven-\([0-9][0-9.]*\)-bin.zip.*/\1/p' "$WRAPPER_PROPS" | head -1)
[ -n "$WRAPPER_MAVEN" ] || fail "cannot read Maven version from $WRAPPER_PROPS"
grep -Fq "FROM maven:${WRAPPER_MAVEN}-" "$DOCKERFILE" || fail "backend Dockerfile maven image must be pinned to wrapper version ${WRAPPER_MAVEN}"

echo "PASS: dockerfiles keep frozen installs (npm ci + maven ${WRAPPER_MAVEN} pinned to wrapper)."
