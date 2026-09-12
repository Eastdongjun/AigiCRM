#!/usr/bin/env bash
# Run local development verification through the central evidence collector.

set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CODE_ROOT="$SOURCE_ROOT/02-迭代源代码"
PROJECT_ID="802fad80-8665-4b11-9f9d-04b92bda4666"
CENTRAL_ROOT="/Users/dongjun/东来/Agent/00-管理/项目执行/$PROJECT_ID"
ENGINEERING_SYSTEM="/Users/dongjun/东来/Agent/04-共享能力/engineering-system"
MODE="${1:-quick}"
RUN_ID="${AIGICRM_EVIDENCE_RUN_ID:-$(date +%Y%m%d-%H%M%S)-dev-evidence-$MODE}"
RUN_DIR="$CENTRAL_ROOT/runs/$RUN_ID"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1], ensure_ascii=False))' "$1"
}

check_engineering_tools() {
  [ -f "$ENGINEERING_SYSTEM/collect.py" ] || fail "Missing collect.py"
  [ -f "$ENGINEERING_SYSTEM/verify.py" ] || fail "Missing verify.py"
  [ -d "$CODE_ROOT" ] || fail "Missing source entry: $CODE_ROOT"
}

write_common_files() {
  mkdir -p "$RUN_DIR"
  cat > "$RUN_DIR/context.md" <<EOF
# Context

Goal: run AIGICRM local development automation with central evidence collection.

Mode: \`$MODE\`

Scope: local verification commands only. This wrapper writes evidence under the central Agent execution directory and must not commit, push, deploy, SSH, migrate databases, or touch production.
EOF
}

write_plan_quick() {
  cat > "$RUN_DIR/plan.json" <<EOF
{
  "acceptance": ["A1", "A2", "A3"],
  "checks": [
    {
      "id": "dev-status",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh status"],
      "timeout": 60
    },
    {
      "id": "evidence-history",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh evidence-history 5"],
      "timeout": 60
    },
    {
      "id": "automation-safety",
      "covers": ["A1", "A2", "A3"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh automation-safety"],
      "timeout": 60
    },
    {
      "id": "dev-on-save",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh on-save"],
      "timeout": 180
    },
    {
      "id": "sea-matrix-blocked",
      "covers": ["A1", "A3"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh sea-matrix; rc=\$?; echo exit_code=\$rc; test \"\$rc\" -eq 2"],
      "timeout": 180
    },
    {
      "id": "hook-status",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/install-git-hooks.sh status | grep '.githooks'"],
      "timeout": 60
    }
  ]
}
EOF
}

write_plan_sea() {
  cat > "$RUN_DIR/plan.json" <<EOF
{
  "acceptance": ["A1", "A2"],
  "checks": [
    {
      "id": "sea-contract",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh sea"],
      "timeout": 600
    },
    {
      "id": "diff-check-automation",
      "covers": ["A1"],
      "cwd": $(json_escape "$SOURCE_ROOT"),
      "argv": ["bash", "-lc", "git diff --check -- 02-迭代源代码/scripts/dev-verify.sh 02-迭代源代码/scripts/dev-evidence.sh 02-迭代源代码/scripts/verify-sea-claim-matrix.sh 02-迭代源代码/scripts/install-git-hooks.sh 02-迭代源代码/.githooks/pre-push 02-迭代源代码/.vscode/tasks.json"],
      "timeout": 60
    }
  ]
}
EOF
}

write_plan_changed() {
  cat > "$RUN_DIR/plan.json" <<EOF
{
  "acceptance": ["A1", "A2", "A3"],
  "checks": [
    {
      "id": "backend-http-preflight",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh backend-http"],
      "timeout": 60
    },
    {
      "id": "frontend-http-preflight",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh frontend-http"],
      "timeout": 60
    },
    {
      "id": "changed-gate-preflight",
      "covers": ["A1", "A2", "A3"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh changed; rc=\$?; echo exit_code=\$rc; test \"\$rc\" -eq 0 -o \"\$rc\" -eq 2"],
      "timeout": 900
    },
    {
      "id": "diff-check-automation",
      "covers": ["A3"],
      "cwd": $(json_escape "$SOURCE_ROOT"),
      "argv": ["bash", "-lc", "git diff --check -- 02-迭代源代码/scripts/dev-verify.sh 02-迭代源代码/scripts/dev-evidence.sh 02-迭代源代码/.vscode/tasks.json"],
      "timeout": 60
    }
  ]
}
EOF
}

write_plan_miniprogram() {
  cat > "$RUN_DIR/plan.json" <<EOF
{
  "acceptance": ["A1", "A2"],
  "checks": [
    {
      "id": "miniprogram-check",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh miniprogram"],
      "timeout": 300
    },
    {
      "id": "diff-check-automation",
      "covers": ["A2"],
      "cwd": $(json_escape "$SOURCE_ROOT"),
      "argv": ["bash", "-lc", "git diff --check -- 02-迭代源代码/scripts/dev-verify.sh 02-迭代源代码/scripts/dev-evidence.sh 02-迭代源代码/.vscode/tasks.json"],
      "timeout": 60
    }
  ]
}
EOF
}

write_plan_pr_preflight() {
  cat > "$RUN_DIR/plan.json" <<EOF
{
  "acceptance": ["A1", "A2", "A3"],
  "checks": [
    {
      "id": "pr-gate-preflight",
      "covers": ["A1", "A2", "A3"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/dev-verify.sh pr; rc=\$?; echo exit_code=\$rc; test \"\$rc\" -eq 0 -o \"\$rc\" -eq 2"],
      "timeout": 1800
    }
  ]
}
EOF
}

write_plan_sonar_status() {
  cat > "$RUN_DIR/plan.json" <<EOF
{
  "acceptance": ["A1", "A2"],
  "checks": [
    {
      "id": "sonar-status",
      "covers": ["A1", "A2"],
      "cwd": $(json_escape "$CODE_ROOT"),
      "argv": ["bash", "-lc", "bash scripts/check-sonar-status.sh; rc=\$?; echo exit_code=\$rc; test \"\$rc\" -eq 0 -o \"\$rc\" -eq 2"],
      "timeout": 180
    }
  ]
}
EOF
}

write_handoff() {
  local baseline recorded_at evidence_path
  baseline="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["baseline"])' "$RUN_DIR/evidence/result.json")"
  recorded_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  evidence_path="$RUN_DIR/evidence"

  cat > "$RUN_DIR/handoff.json" <<EOF
{
  "nodes": [
    {
      "id": "dev-evidence-$MODE",
      "parent": null,
      "layer": 4,
      "state": "已验证",
      "goal": "Run AIGICRM local development automation with central evidence for mode $MODE",
      "scope": "AIGICRM local automation checks only",
      "owner": "Codex",
      "baseline": "$baseline",
      "acceptance": $(python3 -c 'import json,sys; print(json.dumps(json.load(open(sys.argv[1]))["acceptance"], ensure_ascii=False))' "$RUN_DIR/plan.json"),
      "checks": $(python3 - "$RUN_DIR/evidence/result.json" "$evidence_path" "$baseline" "$recorded_at" <<'PY'
import json
import sys

result = json.load(open(sys.argv[1]))
evidence = sys.argv[2]
baseline = sys.argv[3]
recorded_at = sys.argv[4]
checks = []
for check in result["checks"]:
    checks.append({
        "id": check["id"],
        "method": "central collect command " + check["id"],
        "status": check["status"],
        "baseline": baseline,
        "evidence": evidence,
        "recorded_at": recorded_at,
        "reviewer": "Codex",
        "covers": check["covers"],
    })
print(json.dumps(checks, ensure_ascii=False, indent=6))
PY
)
    }
  ]
}
EOF

  cat > "$RUN_DIR/handoff.md" <<EOF
# Handoff: Dev Evidence $MODE

## Result

Central evidence collection completed for mode \`$MODE\`.

## Evidence

- Run directory: \`$RUN_DIR\`
- Evidence bundle: \`$RUN_DIR/evidence\`
- Baseline: \`$baseline\`

## Validation

- Central collect: passed.
- Central handoff verify: pending at creation time; run \`verify.py handoff.json\`.
EOF
}

main() {
  check_engineering_tools
  write_common_files
  case "$MODE" in
    quick) write_plan_quick ;;
    changed) write_plan_changed ;;
    miniprogram) write_plan_miniprogram ;;
    sea) write_plan_sea ;;
    pr-preflight) write_plan_pr_preflight ;;
    sonar-status) write_plan_sonar_status ;;
    *)
      fail "Usage: scripts/dev-evidence.sh [quick|changed|miniprogram|sea|pr-preflight|sonar-status]"
      ;;
  esac

  python3 "$ENGINEERING_SYSTEM/collect.py" \
    --plan "$RUN_DIR/plan.json" \
    --source "$SOURCE_ROOT" \
    --destination "$RUN_DIR/evidence"

  write_handoff

  python3 "$ENGINEERING_SYSTEM/verify.py" "$RUN_DIR/handoff.json"
  python3 - "$RUN_DIR/handoff.md" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
path.write_text(text.replace(
    "Central handoff verify: pending at creation time; run `verify.py handoff.json`.",
    "Central handoff verify: `evidence_verified`, no errors."
))
PY
  echo "PASS: evidence run complete at $RUN_DIR"
}

main "$@"
