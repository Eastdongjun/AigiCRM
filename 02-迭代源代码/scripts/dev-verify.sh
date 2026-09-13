#!/usr/bin/env bash
# Local-only development verification entrypoint.
# It never commits, pushes, deploys, SSHes, migrates databases, or calls production.

set -u
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-help}"
JAVA_HELPER="$ROOT/scripts/ensure-java21.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

blocked() {
  echo "BLOCKED: $*" >&2
  exit 2
}

run() {
  echo
  echo "==> $*"
  "$@"
}

latest_evidence_run() {
  local evidence_root
  evidence_root="/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/runs"
  [ -d "$evidence_root" ] || return 0
  # Shared runs can be created by different hosts, so the run-id clock is not
  # authoritative. Use the handoff file's filesystem update time instead.
  find "$evidence_root" -maxdepth 2 -name handoff.md -print0 |
    xargs -0 stat -f '%m %N' 2>/dev/null |
    sort -n |
    tail -1 |
    cut -d' ' -f2-
}

evidence_history() {
  local evidence_root limit
  evidence_root="/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/runs"
  limit="${1:-5}"
  [ -d "$evidence_root" ] || {
    echo "No central evidence runs found."
    return 0
  }

  find "$evidence_root" -maxdepth 2 -name handoff.md -print0 |
    xargs -0 stat -f '%m %N' 2>/dev/null |
    sort -n |
    tail -"$limit" |
    cut -d' ' -f2- |
    while IFS= read -r handoff; do
      [ -n "$handoff" ] || continue
      local run_dir result
      run_dir="$(basename "$(dirname "$handoff")")"
      result="$(awk '
        /^Result:[[:space:]]*/ {
          sub(/^Result:[[:space:]]*/, "")
          print
          exit
        }
        found && NF {
          print
          exit
        }
        /^## Result$/ {
          found=1
        }
      ' "$handoff")"
      [ -n "$result" ] || result="unknown"
      echo "$run_dir: $result"
    done
}

ensure_backend_tools() {
  [ -f "$JAVA_HELPER" ] || fail "Missing $JAVA_HELPER"
  # shellcheck source=/dev/null
  source "$JAVA_HELPER"
  ensure_java21 || fail "JDK 21 is required"
}

backend_compile() {
  ensure_backend_tools
  (cd "$ROOT/aigicrm-backend" && run_maven -q compile)
}

backend_package() {
  ensure_backend_tools
  (cd "$ROOT/aigicrm-backend" && run_maven -q package -DskipTests)
}

backend_test_in_isolated_copy() {
  local verify_root backend_copy
  verify_root="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-backend-verify.XXXXXX")"
  backend_copy="$verify_root/aigicrm-backend"
  mkdir -p "$backend_copy" "$verify_root/docs/permission"
  cp -R "$ROOT/aigicrm-backend/." "$backend_copy/" || fail "failed to copy isolated backend test source"
  cp "$ROOT/docs/permission/permission-dict.md" "$verify_root/docs/permission/permission-dict.md"
  echo "Isolated backend test copy: $backend_copy"
  (cd "$backend_copy" && run_maven -q "$@")
}

backend_test() {
  ensure_backend_tools
  if [ "${AIGICRM_BACKEND_TEST_SHARED:-0}" = "1" ]; then
    (cd "$ROOT/aigicrm-backend" && run_maven -q test)
    return
  fi
  backend_test_in_isolated_copy test
}

sea_contract() {
  ensure_backend_tools
  if [ "${AIGICRM_BACKEND_TEST_SHARED:-0}" = "1" ]; then
    (
      cd "$ROOT/aigicrm-backend" &&
      run_maven -q \
        -Dtest='com.aigicrm.module.sea.service.SeaServiceTest,com.aigicrm.module.approval.service.ApprovalServiceTest,com.aigicrm.module.approval.service.ApprovalApproveRejectSecurityTest' \
        test
    )
    return
  fi
  backend_test_in_isolated_copy \
    -Dtest='com.aigicrm.module.sea.service.SeaServiceTest,com.aigicrm.module.approval.service.ApprovalServiceTest,com.aigicrm.module.approval.service.ApprovalApproveRejectSecurityTest' \
    test
}

frontend_test() {
  (cd "$ROOT/aigicrm-ui" && npm run test)
}

frontend_build() {
  (cd "$ROOT/aigicrm-ui" && npm run build)
}

http_status() {
  local url="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -sS -o /dev/null -w '%{http_code}' --max-time 8 "$url"
    return
  fi
  command -v node >/dev/null 2>&1 || blocked "curl or node is required for HTTP preflight"
  node - "$url" <<'NODE'
const http = require('http')
const https = require('https')
const url = process.argv[2]
const client = url.startsWith('https:') ? https : http
const request = client.get(url, { timeout: 8000 }, (response) => {
  response.resume()
  process.stdout.write(String(response.statusCode))
})
request.on('timeout', () => request.destroy(new Error('request timed out')))
request.on('error', (error) => {
  console.error(error.message)
  process.exitCode = 1
})
NODE
}

backend_http_preflight() {
  local base_url docs_status protected_status
  base_url="${AIGICRM_BACKEND_URL:-http://127.0.0.1:8080}"

  docs_status="$(http_status "$base_url/v3/api-docs")" || {
    blocked "backend is not reachable at $base_url"
  }
  [ "$docs_status" = "200" ] || fail "backend API docs returned HTTP $docs_status (expected 200)"

  protected_status="$(http_status "$base_url/api/auth/info")" || {
    blocked "backend protected endpoint is not reachable at $base_url"
  }
  case "$protected_status" in
    401|403) ;;
    *) fail "unauthenticated /api/auth/info returned HTTP $protected_status (expected 401 or 403)" ;;
  esac

  echo "PASS: backend HTTP preflight ($base_url; docs=200; protected endpoint=$protected_status without credentials)."
}

frontend_http_preflight() {
  local base_url status route
  base_url="${AIGICRM_FRONTEND_URL:-http://127.0.0.1:3000}"

  for route in / /login /403; do
    status="$(http_status "$base_url$route")" || {
      blocked "frontend is not reachable at $base_url$route"
    }
    [ "$status" = "200" ] || fail "frontend route $route returned HTTP $status (expected 200)"
  done

  echo "PASS: frontend HTTP preflight ($base_url; root/login/403=200)."
}

doc_freshness_local() {
  DOC_FRESHNESS_MODE=local bash "$ROOT/scripts/check-doc-freshness.sh"
}

miniprogram_check() {
  local js_count=0 json_count=0 view_count=0 route_count=0 rc=0
  [ -d "$ROOT/aigicrm-miniprogram" ] || {
    echo "SKIP: aigicrm-miniprogram is not present."
    return 0
  }
  /bin/bash "$ROOT/scripts/check-mobile-demo-contract.sh"

  if command -v node >/dev/null 2>&1; then
    while IFS= read -r file; do
      [ -n "$file" ] || continue
      js_count=$((js_count + 1))
      node --check "$ROOT/$file" || rc=1
    done < <(cd "$ROOT" && git ls-files --cached --others --exclude-standard 'aigicrm-miniprogram/**/*.js' 'aigicrm-miniprogram/*.js')

    while IFS= read -r file; do
      [ -n "$file" ] || continue
      json_count=$((json_count + 1))
      node -e 'const fs=require("fs"); JSON.parse(fs.readFileSync(process.argv[1], "utf8"));' "$ROOT/$file" || rc=1
    done < <(cd "$ROOT" && git ls-files --cached --others --exclude-standard 'aigicrm-miniprogram/**/*.json' 'aigicrm-miniprogram/*.json')

    if [ -f "$ROOT/aigicrm-miniprogram/app.json" ]; then
      node - "$ROOT/aigicrm-miniprogram" <<'NODE' || rc=1
const fs = require("fs");
const path = require("path");

const root = process.argv[2];
const app = JSON.parse(fs.readFileSync(path.join(root, "app.json"), "utf8"));
const pages = Array.isArray(app.pages) ? app.pages : [];
const pageSet = new Set(pages);
let ok = true;

for (const page of pages) {
  for (const ext of [".js", ".json", ".wxml"]) {
    const candidate = path.join(root, page + ext);
    if (!fs.existsSync(candidate)) {
      console.error(`FAIL: app.json page '${page}' is missing ${ext}`);
      ok = false;
    }
  }
}

for (const item of app.tabBar?.list || []) {
  if (!pageSet.has(item.pagePath)) {
    console.error(`FAIL: tabBar pagePath '${item.pagePath}' is not declared in pages`);
    ok = false;
  }
}

console.log(`PASS: miniprogram app.json declares ${pages.length} pages.`);
process.exit(ok ? 0 : 1);
NODE
      route_count="$(node -e 'const fs=require("fs"); const app=JSON.parse(fs.readFileSync(process.argv[1],"utf8")); console.log(Array.isArray(app.pages) ? app.pages.length : 0)' "$ROOT/aigicrm-miniprogram/app.json")"
    fi
  else
    echo "SKIP: node is unavailable; cannot check miniprogram JS/JSON syntax."
  fi

  while IFS= read -r file; do
    [ -s "$ROOT/$file" ] || {
      echo "FAIL: empty miniprogram view/style file: $file" >&2
      rc=1
    }
    view_count=$((view_count + 1))
  done < <(cd "$ROOT" && git ls-files --cached --others --exclude-standard 'aigicrm-miniprogram/**/*.wxml' 'aigicrm-miniprogram/**/*.wxss' 'aigicrm-miniprogram/*.wxss')

  [ "$rc" -eq 0 ] && echo "PASS: miniprogram checked $js_count JS, $json_count JSON, $view_count WXML/WXSS files, $route_count app pages."
  return "$rc"
}

shell_syntax() {
  local rc=0
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    bash -n "$ROOT/$file" || rc=1
  done < <(cd "$ROOT" && git ls-files 'scripts/*.sh')
  return "$rc"
}

python_syntax() {
  local rc=0
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    python3 -m py_compile "$ROOT/$file" || rc=1
  done < <(cd "$ROOT" && git ls-files --cached --others --exclude-standard '*.py')
  return "$rc"
}

automation_safety() {
  bash "$ROOT/scripts/check-dev-automation-safety.sh"
}

local_contracts() {
  run "$ROOT/scripts/check-vscode-workspace-contract.sh"
  run "$ROOT/scripts/check-vscode-diagnostics-export.sh"
  run "$ROOT/scripts/check-indicator-contract.sh"
  run "$ROOT/scripts/check-read-stats-migration.sh"
  run "$ROOT/scripts/check-migration-contract.sh"
  run "$ROOT/scripts/check-mobile-approval-contract.sh"
  run "$ROOT/scripts/check-mobile-indicator-contract.sh"
  run "$ROOT/scripts/check-mobile-incentive-contract.sh"
  run "$ROOT/scripts/check-incentive-file-schema-contract.sh"
  run "$ROOT/scripts/check-mobile-ranking-contract.sh"
  run "$ROOT/scripts/check-sensitive-permission-contract.sh"
  run "$ROOT/scripts/check-jwt-secret-contract.sh"
  run "$ROOT/scripts/check-client-ip-contract.sh"
  run "$ROOT/scripts/check-customer-share-contract.sh"
  run "$ROOT/scripts/check-file-permission-contract.sh"
  # 【2026-09-13】check-customer-file-contract.sh 已随「客户文件」功能移除（见 V2026.09.13.5 迁移）
  run "$ROOT/scripts/check-miniprogram-local-profile.sh"
  run "$ROOT/scripts/check-wechat-project-root.sh"
  run "$ROOT/scripts/check-mobile-manual-qa-contract.sh"
  run "$ROOT/scripts/check-wechat-identity-contract.sh"
  run "$ROOT/scripts/check-mobile-file-boundary.sh"
  run "$ROOT/scripts/check-mobile-sea-boundary.sh"
  run "$ROOT/scripts/check-mobile-feedback-contract.sh"
  run "$ROOT/scripts/check-mobile-statistics-contract.sh"
  run "$ROOT/scripts/check-local-statistics-fixture.sh"
  run "$ROOT/scripts/check-statistics-scope-contract.sh"
  run python3 "$ROOT/scripts/check-feature-audit-contract.py"
  run "$ROOT/scripts/check-task-admin-contract.sh"
  run "$ROOT/scripts/check-mobile-customer-form-contract.sh"
  run "$ROOT/scripts/check-mobile-dashboard-contract.sh"
  run "$ROOT/scripts/check-pc-statistics-contract.sh"
  run "$ROOT/scripts/check-menu-readonly-contract.sh"
  run "$ROOT/scripts/check-menu-icon-contract.sh"
  run "$ROOT/scripts/check-pagination-anti-patterns.sh"
  run node "$ROOT/scripts/test-demo-customers.js"
  run node "$ROOT/scripts/test-demo-folders.js"
  run node "$ROOT/scripts/test-demo-contacts.js"
  run node "$ROOT/scripts/test-demo-finance.js"
  run node "$ROOT/scripts/test-demo-reminders.js"
  run node "$ROOT/scripts/test-feedback-draft.js"
  run node "$ROOT/scripts/test-miniprogram-request-failures.js"
  run bash "$ROOT/scripts/test-sea-claim-matrix-safety.sh"
  run "$ROOT/scripts/check-prototype-state-contract.sh"
  run python3 "$ROOT/scripts/check-mobile-navigation-contract.py"
}

changed_files() {
  (
    cd "$ROOT" &&
    {
      git -c core.quotePath=false diff --name-only --relative=02-迭代源代码
      git -c core.quotePath=false diff --name-only --cached --relative=02-迭代源代码
      git -c core.quotePath=false ls-files --others --exclude-standard
    } | sort -u
  )
}

has_changed() {
  local pattern="$1"
  printf '%s\n' "$CHANGED_FILES" | grep -Eq "$pattern"
}

on_save() {
  run shell_syntax

  local changed
  changed="$(changed_files)"
  if echo "$changed" | grep -Eq '(^|/)aigicrm-backend/.*\.(java|xml|yml|yaml)$'; then
    if [ "${AIGICRM_ON_SAVE_DEEP:-0}" = "1" ]; then
      run backend_compile
    else
      echo "SKIP: backend compile on save; set AIGICRM_ON_SAVE_DEEP=1 to enable."
    fi
  fi

  if echo "$changed" | grep -Eq '(^|/)aigicrm-ui/.*\.(js|vue|ts|json|css)$'; then
    if [ "${AIGICRM_ON_SAVE_DEEP:-0}" = "1" ]; then
      run frontend_test
    else
      echo "SKIP: frontend test on save; set AIGICRM_ON_SAVE_DEEP=1 to enable."
    fi
  fi
}

changed() {
  CHANGED_FILES="$(changed_files)"
  if [ -z "$CHANGED_FILES" ]; then
    echo "PASS: no local changes detected."
    return 0
  fi

  echo "Changed files:"
  printf '%s\n' "$CHANGED_FILES" | sed 's/^/  /'

  run local_contracts

  if has_changed '(^|/)scripts/.*\.sh$|(^|/)\.githooks/'; then
    run shell_syntax
    run automation_safety
  fi

  if has_changed '(^|/).*\.py$'; then
    run python_syntax
  fi

  if has_changed '(^|/)aigicrm-backend/.*\.(java|xml|yml|yaml)$'; then
    run backend_test
  fi

  if has_changed '(^|/)aigicrm-ui/.*\.(js|vue|ts|json|css)$'; then
    run frontend_test
    run frontend_build
  fi

  if has_changed '(^|/)aigicrm-miniprogram/.*\.(js|json|wxml|wxss)$'; then
    run miniprogram_check
  fi

  if has_changed '(^|/)\.vscode/|(^|/)docs/'; then
    echo "INFO: workspace/docs changes detected; covered by syntax and diff checks where applicable."
  fi

  if [ "${AIGICRM_CHANGED_REQUIRE_MATRIX:-0}" = "1" ]; then
    run "$ROOT/scripts/verify-sea-claim-matrix.sh" preflight || {
      local rc=$?
      [ "$rc" -eq 2 ] && blocked "real sea matrix needs explicit test env inputs"
      return "$rc"
    }
  else
    echo "SKIP: real sea matrix in changed mode; set AIGICRM_CHANGED_REQUIRE_MATRIX=1 to require it."
  fi
}

status() {
  local hooks_path latest changed_count
  hooks_path="$(git -C "$ROOT" config --local --get core.hooksPath || true)"
  latest="$(latest_evidence_run)"
  changed_count="$(changed_files | sed '/^$/d' | wc -l | tr -d ' ')"

  echo "AIGICRM automation status"
  echo "  source: $ROOT"
  echo "  changed files: $changed_count"
  if [ -n "$hooks_path" ]; then
    echo "  git hooks: $hooks_path"
  else
    echo "  git hooks: not installed"
  fi
  if [ -n "$latest" ]; then
    echo "  latest handoff: $latest"
  else
    echo "  latest handoff: none found"
  fi
  echo "  recent runs:"
  evidence_history 5 | sed 's/^/    /'
  echo "  daily check: bash scripts/dev-verify.sh changed"
  echo "  evidence check: bash scripts/dev-evidence.sh changed"
}

progress() {
  local mini_pages pc_routes mini_api backend_endpoints audit prototype_screenshots coverage latest
  local state_done state_total state_coverage
  prototype_screenshots=51
  audit="$ROOT/docs/unified-feature-audit.md"
  mini_pages="$(node -e 'const fs=require("fs"); const p=JSON.parse(fs.readFileSync(process.argv[1], "utf8")); console.log((p.pages || []).length)' "$ROOT/aigicrm-miniprogram/app.json" 2>/dev/null || echo unavailable)"
  if [ -f "$audit" ]; then
    pc_routes="$(sed -n 's/^- PC routes: //p' "$audit" | head -1)"
    mini_api="$(sed -n 's/^- Mini Program API functions: //p' "$audit" | head -1)"
    backend_endpoints="$(sed -n 's/^- Backend endpoints: //p' "$audit" | head -1)"
  else
    pc_routes="unavailable"
    mini_api="unavailable"
    backend_endpoints="unavailable"
  fi
  coverage="$(awk -v pages="$mini_pages" -v baseline="$prototype_screenshots" 'BEGIN { if (pages ~ /^[0-9]+$/ && baseline > 0) printf "%.1f%%", pages * 100 / baseline; else print "unavailable" }')"
  state_total=6
  state_done="$(awk '
    /^\| S[1-6] / {
      if ($0 ~ /已实现|自动化回归已覆盖/) done++
    }
    END { print done + 0 }
  ' "$ROOT/docs/prototype-state-coverage.md" 2>/dev/null || echo 0)"
  state_coverage="$(awk -v done="$state_done" -v total="$state_total" 'BEGIN { if (total > 0) printf "%.1f%%", done * 100 / total; else print "unavailable" }')"
  latest="$(latest_evidence_run)"

  echo "AIGICRM development progress"
  echo "  PC routes: $pc_routes"
  echo "  Mini Program pages: $mini_pages"
  echo "  Mini Program API functions: $mini_api"
  echo "  Prototype coverage: $mini_pages/$prototype_screenshots ($coverage independent pages; 6 generic states validated separately)"
  echo "  Generic prototype states: $state_done/$state_total code/automated coverage ($state_coverage; UI matrix pending)"
  echo "  Backend endpoints (audit): $backend_endpoints"
  echo "  Local verification: bash scripts/dev-verify.sh changed"
  echo "  Latest central handoff: ${latest:-none found}"
  echo "  Local VS Code quality: Problems/SonarLint + tests/build are the free primary loop"
  echo "  External blockers: live-environment smoke, backup freshness, real low-permission browser matrix, server Sonar gate"
}

pr() {
  run shell_syntax
  run sea_contract
  run backend_test
  run frontend_test
  run frontend_build
  # 【2026-09-12 修正】真实公海矩阵 preflight 需要 4 个测试账号凭据
  #   （AIGICRM_MATRIX_BASE_URL / _APPLICANT_USER / _APPROVER_USER / _TEST_CUSTOMER_ID），
  #   原先在 pr 模式下**无条件**执行 ⇒ 缺凭据时 blocked() 直接 exit 2，
  #   导致 pre-push 钩子（.githooks/pre-push 调 `dev-verify.sh pr`）**拦住每一次推送**，
  #   即使本次改动与公海矩阵毫无关系。实测即因此被迫用 AIGICRM_SKIP_PRE_PUSH=1 绕过。
  #   这与 changed 模式既有的做法不一致（那里早已是 opt-in）。
  #   现统一为 opt-in：需要时置 AIGICRM_PR_REQUIRE_MATRIX=1 强制校验。
  if [ "${AIGICRM_PR_REQUIRE_MATRIX:-0}" = "1" ]; then
    run "$ROOT/scripts/verify-sea-claim-matrix.sh" preflight || {
      local rc=$?
      [ "$rc" -eq 2 ] && blocked "real sea matrix needs explicit test env inputs"
      return "$rc"
    }
  else
    echo "SKIP: 真实公海矩阵 preflight 未执行（需要测试账号凭据）；置 AIGICRM_PR_REQUIRE_MATRIX=1 强制校验。"
    echo "      （矩阵本体仍可用 'dev-verify.sh sea-matrix' 单独运行）"
  fi
}

case "$MODE" in
  status) status ;;
  progress) progress ;;
  evidence-history) evidence_history "${2:-5}" ;;
  on-save) on_save ;;
  changed) changed ;;
  automation-safety) automation_safety ;;
  backend-compile) backend_compile ;;
  backend-package) backend_package ;;
  backend-test) backend_test ;;
  backend-http) backend_http_preflight ;;
  frontend-http) frontend_http_preflight ;;
  doc-freshness-local) doc_freshness_local ;;
  frontend-test) frontend_test ;;
  frontend-build) frontend_build ;;
  miniprogram) miniprogram_check ;;
  contracts) local_contracts ;;
  sea) sea_contract ;;
  sea-matrix) run "$ROOT/scripts/verify-sea-claim-matrix.sh" preflight ;;
  pr) pr ;;
  help|-h|--help)
    cat <<'EOF'
Usage: scripts/dev-verify.sh <mode>

Modes:
  status           Show automation status and latest central handoff.
  progress         Show current local source-surface counts and verification entrypoint.
  evidence-history Show recent central evidence handoff results.
  on-save          Local quick checks for editor save hooks.
  backend-compile  Maven compile with JDK 21.
  backend-package  Maven package without tests with JDK 21.
  backend-test     Maven unit tests with JDK 21.
  backend-http     Check local backend reachability and unauthenticated boundary.
  doc-freshness-local
                   Compare generated data/permission/API docs with the local database.
  frontend-test    Vitest run.
  frontend-build   Vite production build.
  miniprogram      Check WeChat miniprogram JS/JSON syntax and empty WXML/WXSS files.
  contracts        Run local permission, navigation, prototype, and demo-data contracts.
  changed          Run checks selected from currently changed files.
  automation-safety
                   Check daily automation files for forbidden remote/deploy commands.
  sea              Public sea claim/approval contract tests.
  sea-matrix       Safe real-account matrix preflight; BLOCKED when inputs are missing.
  pr               Local pre-PR gate: shell syntax, sea contract, backend tests, frontend tests/build.
                   Real sea matrix preflight is opt-in here (needs test-account credentials).

Set AIGICRM_ON_SAVE_DEEP=1 to let on-save run compile/tests for touched backend/frontend files.
Set AIGICRM_CHANGED_REQUIRE_MATRIX=1 to require real sea matrix preflight in changed mode.
Set AIGICRM_PR_REQUIRE_MATRIX=1 to require real sea matrix preflight in pr mode.
EOF
    ;;
  *) fail "Unknown mode: $MODE" ;;
esac
