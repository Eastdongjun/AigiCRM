#!/usr/bin/env bash
# B1 安装器离线测试：语法、preflight 正/负例、dry-run。不启动容器。
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAIL=0

ok(){ echo "PASS: $1"; }
bad(){ echo "FAIL: $1" >&2; FAIL=$((FAIL+1)); }

bash -n "$ROOT/scripts/product/preflight.sh" && ok "preflight 语法" || bad "preflight 语法"
bash -n "$ROOT/scripts/product/install.sh" && ok "install 语法" || bad "install 语法"

cat > "$TMP/good.env" <<ENV
DB_PASSWORD=test-root
APP_DB_USER=aigicrm_app
APP_DB_PASSWORD=test-app
JWT_SECRET=0123456789abcdef0123456789abcdef
TENANT_ENABLE=true
ADMIN_USER=admin
ADMIN_PASSWORD=test-admin-pass
ENV
cat > "$TMP/bad.env" <<ENV
DB_PASSWORD=x
APP_DB_USER=x
APP_DB_PASSWORD=x
JWT_SECRET=short
TENANT_ENABLE=false
ENV

bash "$ROOT/scripts/product/preflight.sh" --env-file "$TMP/good.env" --ports 18080 >"$TMP/good.log" 2>&1
[ $? -eq 0 ] && grep -q 'RESULT: PASS=' "$TMP/good.log" && ok "preflight 合法 env" || bad "preflight 合法 env"

bash "$ROOT/scripts/product/preflight.sh" --env-file "$TMP/bad.env" --ports 18080 >"$TMP/bad.log" 2>&1
[ $? -eq 1 ] && grep -q 'TENANT_ENABLE 必须为 true' "$TMP/bad.log" && ok "preflight 非法 env 拒绝" || bad "preflight 非法 env 拒绝"

# dry-run 使用临时 runtime，不触碰真实 env/容器
RUNTIME_DIR="$TMP/runtime" bash "$ROOT/scripts/product/install.sh" --dry-run --project "b1-test-$$" \
  --http-port 18080 --web-port 18082 --mysql-port 13306 --redis-port 16379 >"$TMP/dry.log" 2>&1
[ $? -eq 0 ] && grep -q 'DRY-RUN' "$TMP/dry.log" && ok "install dry-run" || bad "install dry-run"

echo
echo "RESULT: FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
