#!/usr/bin/env bash
# Local-only development verification entrypoint.
# It never commits, pushes, deploys, SSHes, migrates databases, or calls production.

set -e
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
      local run_dir run_json result
      run_dir="$(basename "$(dirname "$handoff")")"
      run_json="$(dirname "$handoff")/run.json"
      result="$(awk '
        /^-?[[:space:]]*Result:[[:space:]]*/ {
          sub(/^-?[[:space:]]*Result:[[:space:]]*/, "")
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
      if [ -z "$result" ]; then
        result="$(awk '
          /^-[[:space:]]*状态：/ {
            sub(/^-?[[:space:]]*状态：/, "status: ")
            print
            exit
          }
        ' "$handoff")"
      fi
      if [ -z "$result" ] && [ -f "$run_json" ]; then
        result="$(sed -n 's/^[[:space:]]*"status":[[:space:]]*"\([^"]*\)".*/run status: \1/p' "$run_json" | head -1)"
      fi
      [ -n "$result" ] || result="unknown"
      echo "$run_dir: $result"
    done
}

latest_sonar_evidence_summary() {
  local evidence_root log_file local_status qg_status exit_code
  evidence_root="/Users/dongjun/东来/Agent/00-管理/项目执行/802fad80-8665-4b11-9f9d-04b92bda4666/runs"
  [ -d "$evidence_root" ] || {
    echo "unavailable (no central evidence runs)"
    return 0
  }
  log_file="$(
    find "$evidence_root" -path '*/evidence/sonar-status.log' -print0 |
      xargs -0 stat -f '%m %N' 2>/dev/null |
      sort -n |
      tail -1 |
      cut -d' ' -f2-
  )"
  [ -n "$log_file" ] && [ -f "$log_file" ] || {
    echo "unavailable (run: bash scripts/dev-evidence.sh sonar-status)"
    return 0
  }

  local_status="$(sed -n 's/^SONAR_LOCAL_SCAN=//p' "$log_file" | tail -1)"
  qg_status="$(sed -n 's/^SONAR_QUALITY_GATE=//p' "$log_file" | tail -1)"
  exit_code="$(sed -n 's/^exit_code=//p' "$log_file" | tail -1)"
  echo "local=${local_status:-unknown}; qg=${qg_status:-unknown}; evidence_exit=${exit_code:-unknown}; log=$log_file"
}

changed_breakdown() {
  changed_files | awk '
    NF {
      total++
      if ($0 ~ /^aigicrm-backend\/src\/main\/resources\/db\/migration\/V[^\/]+\.sql$/) migrations++
      else if ($0 ~ /^aigicrm-backend\//) backend++
      else if ($0 ~ /^aigicrm-ui\//) frontend++
      else if ($0 ~ /^aigicrm-miniprogram\//) miniprogram++
      else if ($0 ~ /^scripts\//) scripts++
      else if ($0 ~ /^docs\//) docs++
      else if ($0 ~ /^\.vscode\/|^\.githooks\/|^docker-|^sonar-project|^AGENTS\.md$|^AIGICRM\.code-workspace$/) config++
      else other++
    }
    END {
      printf "total=%d; migrations=%d; backend=%d; frontend=%d; miniprogram=%d; scripts=%d; docs=%d; config=%d; other=%d",
        total + 0, migrations + 0, backend + 0, frontend + 0, miniprogram + 0, scripts + 0, docs + 0, config + 0, other + 0
    }
  '
}

untracked_files() {
  (
    cd "$ROOT" &&
    git -c core.quotePath=false ls-files --others --exclude-standard
  )
}

untracked_breakdown() {
  untracked_files | awk '
    NF {
      total++
      if ($0 ~ /^aigicrm-backend\/src\/main\/resources\/db\/migration\/V[^\/]+\.sql$/) migrations++
      else if ($0 ~ /^aigicrm-backend\//) backend++
      else if ($0 ~ /^aigicrm-ui\//) frontend++
      else if ($0 ~ /^aigicrm-miniprogram\//) miniprogram++
      else if ($0 ~ /^scripts\//) scripts++
      else if ($0 ~ /^docs\//) docs++
      else if ($0 ~ /^\.vscode\/|^\.githooks\/|^docker-|^sonar-project|^AGENTS\.md$|^AIGICRM\.code-workspace$/) config++
      else other++
    }
    END {
      printf "total=%d; migrations=%d; backend=%d; frontend=%d; miniprogram=%d; scripts=%d; docs=%d; config=%d; other=%d",
        total + 0, migrations + 0, backend + 0, frontend + 0, miniprogram + 0, scripts + 0, docs + 0, config + 0, other + 0
    }
  '
}

untracked_inventory() {
  echo "AIGICRM untracked inventory"
  echo "  summary: $(untracked_breakdown)"
  untracked_files | awk '
    function category(path) {
      # 【2026-10-20】migrations 桶=Flyway SQL 迁移文件(与 changed_migrations 列路径同口径);
      # 迁移目录内的 FROZEN-SHA256.txt 等非 SQL 文件归 backend——此前目录前缀计数会让
      # check-dev-verify-progress-contract 的"计数>0 必须列出 V*.sql 路径"双向断言在改清单时误红
      if (path ~ /^aigicrm-backend\/src\/main\/resources\/db\/migration\/V[^\/]+\.sql$/) return "migrations"
      if (path ~ /^aigicrm-backend\//) return "backend"
      if (path ~ /^aigicrm-ui\//) return "frontend"
      if (path ~ /^aigicrm-miniprogram\//) return "miniprogram"
      if (path ~ /^scripts\//) return "scripts"
      if (path ~ /^docs\//) return "docs"
      if (path ~ /^\.vscode\/|^\.githooks\/|^docker-|^sonar-project|^AGENTS\.md$|^AIGICRM\.code-workspace$/) return "config"
      return "other"
    }
    NF {
      bucket=category($0)
      files[bucket]=files[bucket] "    - " $0 "\n"
      counts[bucket]++
    }
    END {
      split("migrations backend frontend miniprogram scripts docs config other", order, " ")
      for (i = 1; i <= length(order); i++) {
        bucket=order[i]
        printf "  %s (%d):\n", bucket, counts[bucket] + 0
        if (counts[bucket] > 0) printf "%s", files[bucket]
      }
      print "  review batches:"
      printf "    - database-permissions: review %d Flyway migrations before staging.\n", counts["migrations"] + 0
      printf "    - backend-regression: review %d backend tests/helpers before staging.\n", counts["backend"] + 0
      printf "    - frontend-features: review %d Vue/API/utility files and specs before staging.\n", counts["frontend"] + 0
      printf "    - automation-contracts: review %d scripts and hooks before staging.\n", (counts["scripts"] + 0) + (counts["config"] + 0)
      printf "    - delivery-docs: review %d docs before staging.\n", counts["docs"] + 0
    }
  '
}

changed_migrations() {
  changed_files | awk '
    /^aigicrm-backend\/src\/main\/resources\/db\/migration\/V.*\.sql$/ {
      print "    - " $0
    }
  '
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

http_body() {
  local url="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -sS --max-time 8 "$url"
    return
  fi
  command -v node >/dev/null 2>&1 || blocked "curl or node is required for HTTP preflight"
  node - "$url" <<'NODE'
const http = require('http')
const https = require('https')
const url = process.argv[2]
const client = url.startsWith('https:') ? https : http
const request = client.get(url, { timeout: 8000 }, (response) => {
  response.setEncoding('utf8')
  response.on('data', chunk => process.stdout.write(chunk))
})
request.on('timeout', () => request.destroy(new Error('request timed out')))
request.on('error', (error) => {
  console.error(error.message)
  process.exitCode = 1
})
NODE
}

backend_http_preflight() {
  local base_url docs_status protected_status config_status docs_body
  base_url="${AIGICRM_BACKEND_URL:-http://127.0.0.1:8080}"

  docs_status="$(http_status "$base_url/v3/api-docs")" || {
    blocked "backend is not reachable at $base_url"
  }
  [ "$docs_status" = "200" ] || fail "backend API docs returned HTTP $docs_status (expected 200)"
  docs_body="$(http_body "$base_url/v3/api-docs")" || {
    blocked "backend API docs body is not readable at $base_url"
  }
  printf '%s' "$docs_body" | grep -Fq '"title":"OpenAPI definition"' ||
    fail "backend API docs at $base_url do not look like the AIGICRM OpenAPI document"
  printf '%s' "$docs_body" | grep -Fq 'sys-user-controller' ||
    fail "backend API docs at $base_url are missing the AIGICRM system user controller"
  printf '%s' "$docs_body" | grep -Fq '/api/system/configs/public' ||
    fail "backend API docs at $base_url are missing the AIGICRM public config endpoint"

  protected_status="$(http_status "$base_url/api/auth/info")" || {
    blocked "backend protected endpoint is not reachable at $base_url"
  }
  case "$protected_status" in
    401|403) ;;
    *) fail "unauthenticated /api/auth/info returned HTTP $protected_status (expected 401 or 403)" ;;
  esac

  config_status="$(http_status "$base_url/api/system/configs/public")" || {
    blocked "backend public config endpoint is not reachable at $base_url"
  }
  [ "$config_status" = "200" ] || fail "backend public config returned HTTP $config_status (expected 200)"

  echo "PASS: backend HTTP preflight ($base_url; docs=200; public config=200; protected endpoint=$protected_status without credentials)."
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
  miniprogram_deps_preflight
  /bin/bash "$ROOT/scripts/check-mobile-demo-contract.sh"
  /bin/bash "$ROOT/scripts/check-mobile-customer-indicator-edit-contract.sh"
  /bin/bash "$ROOT/scripts/check-mp-view-bindings.sh"
  # 【P2-7 2026-10-19】静态 class 定义契约: 无定义=视觉静默退化或冗余 token 债
  python3 "$ROOT/scripts/check-mp-class-definitions.py"

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
  done < <(cd "$ROOT" && git ls-files --cached --others --exclude-standard 'scripts/*.sh')
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

# 【F2 2026-10-21】契约/小程序检查的依赖 preflight(fail-closed): contracts 被 gate.sh [5b] 与
#   pre-push 按路径调用后, 缺 rg/python3/node 不再是"某脚本莫名单飞红", 而是统一 BLOCKED(exit 2)
#   并点名缺哪个工具——CI/换机导致的假红可被一眼区分于真实契约违例。
contract_deps_preflight() {
  local missing=""
  for tool in git node python3 rg; do
    command -v "$tool" >/dev/null 2>&1 || missing="${missing}${tool} "
  done
  [ -z "$missing" ] || blocked "contracts 检查依赖缺失: ${missing}——请安装后重跑(多个 check-* 契约脚本使用 rg; fail-closed, 不静默跳过)"
}

miniprogram_deps_preflight() {
  local missing=""
  for tool in node python3; do
    command -v "$tool" >/dev/null 2>&1 || missing="${missing}${tool} "
  done
  [ -z "$missing" ] || blocked "miniprogram 检查依赖缺失: ${missing}——node --check/JSON 解析需要 node, 类名契约需要 python3(fail-closed)"
}

# 【F2 2026-10-21 契约更新责任表】P1 修复改动下列领域时, 必须同提交更新对应契约/测试,
#   否则 contracts(本函数)会把你的正常改动判红——这不是 F2 的故障, 是契约在等你的更新:
#   A6 统计范围   → scripts/check-statistics-scope-contract.sh(teamActivities 调用与 wrapper.in 断言)
#   B2 提醒分页   → aigicrm-ui/src/views/reminder/index.spec.js + scripts/check-pagination-anti-patterns.sh
#   C5 客户表单   → scripts/check-mobile-customer-form-contract.sh + scripts/test-miniprogram-customer-idempotency.js
#   C2 激励上传   → scripts/check-mobile-incentive-contract.sh
#   C4 请求空串   → scripts/test-miniprogram-request-failures.js(keepEmpty 用例)
#   E1 财务权限   → 若放开/收紧 assertCanManageFinance, 同步财务权限契约与 docs/business/finance.md
#   凭据/冒烟(F1) → scripts/check-secret-scan.sh(违例→改 env 读取+轮换; 历史物→ALLOWLIST 精确条目+理由)
local_contracts() {
  contract_deps_preflight
  run "$ROOT/scripts/check-vscode-workspace-contract.sh"
  run "$ROOT/scripts/check-vscode-diagnostics-export.sh"
  run "$ROOT/scripts/check-dev-verify-modes-contract.sh"
  run "$ROOT/scripts/check-dev-verify-executable-contract.sh"
  run "$ROOT/scripts/check-dev-verify-progress-contract.sh"
  run "$ROOT/scripts/check-dev-verify-untracked-contract.sh"
  run "$ROOT/scripts/check-dev-verify-fail-fast-contract.sh"
  run "$ROOT/scripts/check-backend-http-identity-contract.sh"
  run "$ROOT/scripts/check-local-backend-start-contract.sh"
  run "$ROOT/scripts/check-local-smoke-contract.sh"
  run "$ROOT/scripts/check-dev-evidence-plan-contract.sh"
  run "$ROOT/scripts/check-sonar-status-contract.sh"
  run "$ROOT/scripts/check-dockerfile-contract.sh"
  run "$ROOT/scripts/check-indicator-contract.sh"
  # 【2026-11-03 统一权限中心】门面统一+收口单入口+finance:edit 拆码+公海/激励按角色 契约
  run "$ROOT/scripts/check-perm-center-contract.sh"
  # 【2026-11-05 产品化批】接口守卫全量覆盖(此前只抽查, 新端点漏挂守卫无人知晓)
  run "$ROOT/scripts/check-endpoint-guard-coverage.sh"
  # 【2026-11-05 权限审计】按钮级敏感动作门禁覆盖(四项激励 G1/G2 审计的扫描器固化)
  run python3 "$ROOT/scripts/check-action-gate-coverage.py"
  # 【2026-11-04 双端权限绑定一致性】注册表对账(零死码)+MP排行注册码并集归位+接口守卫=注册码 契约
  run "$ROOT/scripts/check-dual-end-permission-binding-contract.sh"
  # 【2026-11-04 TDD】排行页注册码并集纯函数单测(utils/ranking-tabs.js, 先红后绿)
  run node "$ROOT/scripts/test-miniprogram-ranking-tabs.js"
  # 【2026-11-04 TDD】激励上传手机文件 H5 回挂选取规则单测(utils/upload-handback.js, 先红后绿)
  run node "$ROOT/scripts/test-miniprogram-upload-handback.js"
  # 【2026-11-04 TDD】激励统计页 pane 门禁纯函数单测(utils/incentive-panes.js, 先红后绿)
  run node "$ROOT/scripts/test-miniprogram-incentive-panes.js"
  run "$ROOT/scripts/check-read-stats-migration.sh"
  # 【T6 2026-11-11 审计整改 P1-11】API 清单内容级新鲜度(mtime 之外的真校验)
  run "$ROOT/scripts/check-api-list-fresh.sh"
  run "$ROOT/scripts/check-migration-contract.sh"
  run "$ROOT/scripts/check-migration-chain-contract.sh"
  run "$ROOT/scripts/check-mobile-auth-contract.sh"
  run "$ROOT/scripts/check-mobile-approval-contract.sh"
  run "$ROOT/scripts/check-mobile-indicator-contract.sh"
  run "$ROOT/scripts/check-mobile-incentive-contract.sh"
  run "$ROOT/scripts/check-incentive-file-schema-contract.sh"
  run "$ROOT/scripts/check-incentive-period-contract.sh"
  run "$ROOT/scripts/check-incentive-record-table-contract.sh"
  run "$ROOT/scripts/check-incentive-read-ranking-contract.sh"
  run "$ROOT/scripts/check-native-sql-tenant-scope.sh"
  run "$ROOT/scripts/check-pr22-coverage-ledger.sh"
  run "$ROOT/scripts/check-incentive-search-detail-contract.sh"
  run "$ROOT/scripts/check-incentive-stats-detail-contract.sh"
  run "$ROOT/scripts/check-reminder-detail-contract.sh"
  # 【2026-10-27 深度检查修复】以下四份契约曾无任何执行入口(git log -S 为证): 守卫存在但无强制力——
  # customer-center(客户列表/详情) / profile(我的页) / shell(工作台/应用中心) / reminder(提醒中心)
  run "$ROOT/scripts/check-mobile-reminder-contract.sh"
  run "$ROOT/scripts/check-mobile-customer-center-contract.sh"
  run "$ROOT/scripts/check-mobile-profile-contract.sh"
  run "$ROOT/scripts/check-mobile-shell-contract.sh"
  run "$ROOT/scripts/check-mobile-ranking-contract.sh"
  run "$ROOT/scripts/check-mobile-feature-matrix-contract.sh"
  run python3 "$ROOT/scripts/check-mobile-live-qa-boundary.py"
  run "$ROOT/scripts/check-mobile-pc-only-boundary.sh"
  run "$ROOT/scripts/check-mobile-pc-api-parity-contract.sh"
  # 【2026-10-29 字典来源契约】系统字典清单对账+接线钉+防复活钉(回应"字典建了业务端硬编码不用"审计)
  run "$ROOT/scripts/check-dict-source-contract.sh"
  run "$ROOT/scripts/check-sensitive-permission-contract.sh"
  run "$ROOT/scripts/check-jwt-secret-contract.sh"
  # 【F1 2026-10-21】全量 secret 扫描(p1-p6 形态, 含 aigicrm-ui/*.mjs)+其行为测试;
  #   quality-gate.sh 复用同一脚本, 检查逻辑单一出处
  run "$ROOT/scripts/check-secret-scan.sh"
  run bash "$ROOT/scripts/test-secret-scan.sh"
  run "$ROOT/scripts/check-prod-config-contract.sh"
  run "$ROOT/scripts/check-client-ip-contract.sh"
  run "$ROOT/scripts/check-customer-status-contract.sh"
  run "$ROOT/scripts/check-customer-protection-365-contract.sh"
  run "$ROOT/scripts/check-legacy-routes-contract.sh"
  run "$ROOT/scripts/check-customer-share-contract.sh"
  run "$ROOT/scripts/check-file-permission-contract.sh"
  # 【2026-09-13】check-customer-file-contract.sh 已随「客户文件」功能移除（见 V2026.09.13.5 迁移）
  run "$ROOT/scripts/check-miniprogram-local-profile.sh"
  run python3 "$ROOT/scripts/check-miniprogram-permission-contract.py"
  run python3 "$ROOT/scripts/check-permission-registration-contract.py"
  run "$ROOT/scripts/check-wechat-project-root.sh"
  run "$ROOT/scripts/check-mobile-manual-qa-contract.sh"
  run "$ROOT/scripts/check-wechat-identity-contract.sh"
  run "$ROOT/scripts/check-mobile-file-boundary.sh"
  run "$ROOT/scripts/check-mobile-sea-boundary.sh"
  run "$ROOT/scripts/check-sea-delete-contract.sh"
  run "$ROOT/scripts/check-mobile-feedback-contract.sh"
  run "$ROOT/scripts/check-mobile-statistics-contract.sh"
  run "$ROOT/scripts/check-local-statistics-fixture.sh"
  run "$ROOT/scripts/check-statistics-scope-contract.sh"
  run python3 "$ROOT/scripts/check-feature-audit-contract.py"
  run "$ROOT/scripts/check-task-admin-contract.sh"
  run "$ROOT/scripts/check-mobile-customer-form-contract.sh"
  run bash "$ROOT/scripts/check-mobile-customer-indicator-edit-contract.sh"
  run "$ROOT/scripts/check-mobile-dashboard-contract.sh"
  run "$ROOT/scripts/check-pc-statistics-contract.sh"
  run "$ROOT/scripts/check-menu-readonly-contract.sh"
  run "$ROOT/scripts/check-menu-icon-contract.sh"
  run "$ROOT/scripts/check-pagination-anti-patterns.sh"
  run bash "$ROOT/scripts/test-pagination-anti-patterns-path-safety.sh"
  run node "$ROOT/scripts/test-demo-customers.js"
  run node "$ROOT/scripts/test-demo-folders.js"
  run node "$ROOT/scripts/test-demo-contacts.js"
  run node "$ROOT/scripts/test-demo-finance.js"
  run node "$ROOT/scripts/test-demo-reminders.js"
  run node "$ROOT/scripts/test-feedback-draft.js"
  run node "$ROOT/scripts/test-miniprogram-incentive-files.js"
  run node "$ROOT/scripts/test-miniprogram-request-failures.js"
  run node "$ROOT/scripts/test-miniprogram-customer-idempotency.js"
  run node "$ROOT/scripts/test-miniprogram-customer-filter.js"
  run node "$ROOT/scripts/test-miniprogram-files-scope.js"
  run node "$ROOT/scripts/test-miniprogram-data-scope-label.js"
  run node "$ROOT/scripts/test-miniprogram-reminder-list.js"
  run node "$ROOT/scripts/test-miniprogram-statistics-page.js"
  run bash "$ROOT/scripts/test-sea-claim-matrix-safety.sh"
  run "$ROOT/scripts/check-prototype-state-contract.sh"
  run python3 "$ROOT/scripts/check-mobile-navigation-contract.py"
  # 【2026-11-07 工程纪律守则】复盘25条错误固化的七段机器执法层(假绿写法/显式出口/迁移硬编码id/
  #   部署钉/凭据入仓/嵌套wx:for基线/自登记防复活), 守则=docs/工程纪律守则-20261107.md
  run "$ROOT/scripts/check-discipline-gates.sh"
  # 【2026-11-07 守则D6】PC 前端死引用扫描(相对/@ 本地引用必须可解析, 502 条全检)
  run node "$ROOT/scripts/check-ui-dead-imports.mjs"
  # 【2026-11-08 第四类门禁·双端一致性】前端调用→后端端点必须存在(防必败按钮/死调用);
  #   后端写端点→双端必须有调用方或白名单带理由(防休眠端点复发)。背景=11-08全量审计25项漂移。
  run "$ROOT/scripts/check-endpoint-parity.sh"
  # 【治本④ 2026-11-11】文件权限端到端一致性(勾→双端出→操作放行→收回复位, 打测试服):
  #   背景=当日 7 个权限 bug 全是"API 对、最后一公里断"(词表/归组/入口错位), 此钉闭环之。
  run "$ROOT/scripts/check-file-perm-e2e.sh"
  # 【治本③ 2026-11-11】文件动作词表单一源: 后端常量=权威, 前端矩阵/MP 消费必须一致(edit 兼容词须与 rename 并存)
  run "$ROOT/scripts/check-file-action-vocab.sh"
  # 【T1 2026-11-11 审计整改】权限码绑定覆盖率: @PreAuthorize 码必须有锚点角色绑定(防空库重放全员403)
  run "$ROOT/scripts/check-permission-binding-coverage.sh"
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
  echo "  changed breakdown: $(changed_breakdown)"
  echo "  untracked breakdown: $(untracked_breakdown)"
  if changed_migrations | grep -q .; then
    echo "  changed migrations:"
    changed_migrations
  fi
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
  echo "  sonar evidence: $(latest_sonar_evidence_summary)"
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
  echo "  Generic prototype states: $state_done/$state_total code/automated coverage ($state_coverage; DevTools/manual QA pending)"
  echo "  Backend endpoints (audit): $backend_endpoints"
  echo "  Local verification: bash scripts/dev-verify.sh changed"
  echo "  Latest central handoff: ${latest:-none found}"
  echo "  Latest Sonar evidence: $(latest_sonar_evidence_summary)"
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
  untracked) untracked_inventory ;;
  evidence-history) evidence_history "${2:-5}" ;;
  on-save) on_save ;;
  changed) changed ;;
  automation-safety) automation_safety ;;
  verify-modes) bash "$ROOT/scripts/check-dev-verify-modes-contract.sh" ;;
  verify-executables) bash "$ROOT/scripts/check-dev-verify-executable-contract.sh" ;;
  verify-fail-fast) bash "$ROOT/scripts/check-dev-verify-fail-fast-contract.sh" ;;
  verify-evidence-plans) bash "$ROOT/scripts/check-dev-evidence-plan-contract.sh" ;;
  verify-dockerfile) bash "$ROOT/scripts/check-dockerfile-contract.sh" ;;
  backend-compile) backend_compile ;;
  backend-package) backend_package ;;
  backend-test) backend_test ;;
  backend-http) backend_http_preflight ;;
  frontend-http) frontend_http_preflight ;;
  doc-freshness-local) doc_freshness_local ;;
  frontend-test) frontend_test ;;
  frontend-build) frontend_build ;;
  miniprogram) miniprogram_check ;;
  shell-syntax) shell_syntax ;;
  python-syntax) python_syntax ;;
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
  untracked        Show categorized untracked files that remain to review.
  evidence-history Show recent central evidence handoff results.
  on-save          Local quick checks for editor save hooks.
  backend-compile  Maven compile with JDK 21.
  backend-package  Maven package without tests with JDK 21.
  backend-test     Maven unit tests with JDK 21.
  backend-http     Check local backend reachability and unauthenticated boundary.
  frontend-http    Check local frontend root, login, and 403 routes.
  doc-freshness-local
                   Compare generated data/permission/API docs with the local database.
  frontend-test    Vitest run.
  frontend-build   Vite production build.
  miniprogram      Check WeChat miniprogram JS/JSON syntax and empty WXML/WXSS files.
  shell-syntax     Check tracked and untracked scripts/*.sh with bash -n.
  python-syntax    Check tracked and untracked Python files with py_compile.
  contracts        Run local permission, navigation, prototype, demo-data, and secret-scan contracts.
                   Missing tooling (git/node/python3/rg) fails closed as BLOCKED.
  changed          Run checks selected from currently changed files.
  automation-safety
                   Check daily automation files for forbidden remote/deploy commands.
  verify-modes     Check dev-verify dispatch modes are documented and required modes exist.
  verify-executables
                   Check dev-verify invoked scripts exist, shell entrypoints are executable, and script syntax passes.
  verify-fail-fast
                   Check dev-verify exits non-zero when a direct or wrapped command fails.
  verify-evidence-plans
                   Check dev-evidence generated plans keep required checks and diff scopes.
  verify-dockerfile
                   Check Dockerfile structure without requiring registry access.
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
