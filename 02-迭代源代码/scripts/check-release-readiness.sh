#!/usr/bin/env bash
# 发布就绪本地门禁：只读本地代码/本地服务，不 commit/push/部署/迁移。
# 用法:
#   bash scripts/check-release-readiness.sh          # 全量：后端测试 + 前端测试 + MP/API/租户 + 结构护栏 + Sonar
#   bash scripts/check-release-readiness.sh --quick  # 快速：跳过后端/前端测试，其余契约/结构/Sonar 照跑
#   bash scripts/check-release-readiness.sh --strict # 文档新鲜度失败也判 FAIL（默认仅 WARN）
#     【注意 2026-11-24】--strict 会跑 check-doc-freshness.sh, 它未指定模式时默认连【生产】做只读查询(库表结构)。
#     只想对本机库校验: DOC_FRESHNESS_MODE=local bash scripts/check-release-readiness.sh --strict
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="full"
STRICT=0
for arg in "$@"; do
  case "$arg" in
    --quick) MODE="quick" ;;
    --strict) STRICT=1 ;;
    -h|--help)
      sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

FAIL=0
PASS=0
STEP=0

step() {
  local name="$1"; shift
  STEP=$((STEP + 1))
  echo
  echo "==> [$STEP] $name"
  if "$@"; then
    PASS=$((PASS + 1))
    echo "PASS: $name"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL: $name" >&2
  fi
}

sonar_gate() {
  local url="${SONAR_URL:-http://127.0.0.1:19000}"
  local key="${SONAR_PROJECT_KEY:-Eastdongjun_AigiCRM-local}"
  command -v curl >/dev/null 2>&1 || { echo "SKIP: 无 curl"; return 0; }
  # 【GL-01 2026-11-24】不再内置安装默认口令：凭据只经环境变量传入（如 export SONAR_PASSWORD=…），
  # 未配置时明确报 SKIP，而不是拿默认值去试。
  if [ -z "${SONAR_PASSWORD:-}" ]; then
    echo "SKIP: 未配置 SONAR_PASSWORD（本地 Sonar 口令经环境变量传入，脚本不写默认值）"; return 0
  fi
  local body
  body="$(curl -fsS -m 10 -u "${SONAR_USER:-admin}:${SONAR_PASSWORD}" \
    "$url/api/qualitygates/project_status?projectKey=$key" 2>/dev/null)" || {
    echo "SKIP: 本地 Sonar 不可用（${url}）"; return 0; }
  python3 -c 'import json,sys; d=json.load(sys.stdin)["projectStatus"]; print("status="+d["status"]); [print(c["metricKey"], c["status"], c.get("actualValue")) for c in d["conditions"] if c["status"] != "OK"]; sys.exit(0 if d["status"] == "OK" else 1)' <<<"$body"
}

doc_freshness() {
  if [ "$STRICT" = "1" ]; then
    # 明说这次对哪个库校验: 不指定模式时 check-doc-freshness.sh 默认连生产(只读), 从命令行上看不出来
    echo "文档新鲜度模式: ${DOC_FRESHNESS_MODE:-prod} —— (不指定 DOC_FRESHNESS_MODE 时默认是 prod, 会连生产做只读查询)"
    bash "$ROOT/scripts/check-doc-freshness.sh"
  else
    echo "WARN: 文档新鲜度默认不阻塞（已知 data-dictionary 需生产窗口回填；--strict 才 FAIL）"
    return 0
  fi
}

if [ "$MODE" = "full" ]; then
  step "后端全量测试" bash -c "cd '$ROOT/aigicrm-backend' && ./mvnw -o test"
  step "前端单测" bash -c "cd '$ROOT/aigicrm-ui' && npm test"
  step "前端构建" bash -c "cd '$ROOT/aigicrm-ui' && npm run build"
else
  echo "==> quick 模式：跳过后端全量测试 / 前端单测与构建"
fi

step "MP 视图绑定" node "$ROOT/scripts/check-mp-view-bindings.mjs"
contract_checks() {
  bash "$ROOT/scripts/check-api-list-fresh.sh" && bash "$ROOT/scripts/check-industry-vocabulary-contract.sh"
}
step "API 清单/行业词表契约" contract_checks
step "租户表白名单" bash "$ROOT/scripts/check-tenant-table-registry.sh"
step "God class 行数护栏" bash "$ROOT/scripts/check-god-class-size.sh"
step "Sonar 新代码门禁" sonar_gate
step "文档新鲜度" doc_freshness

echo
echo "===== 汇总: PASS=$PASS FAIL=$FAIL MODE=$MODE ====="
[ "$FAIL" -eq 0 ] || exit 1
