#!/bin/bash
# 生产性能/安全检查(FEAT-20260804-QA-GATES):gzip、安全头、no-cache、响应耗时
# 用法: bash scripts/check-prod-perf.sh <base_url> [token]
# 退出码: 0=通过; 1=存在异常
set -uo pipefail

BASE="${1:-https://www.aigicrm.com}"
TOKEN="${2:-}"
FAILS=0
# 登录凭据经 ~/.ycmn-deploy.env 注入(HEALTH_CHECK_USER/PASS)，禁止硬编码入仓
ENV_FILE="$HOME/.ycmn-deploy.env"
[ -f "$ENV_FILE" ] && source "$ENV_FILE"
HEALTH_CHECK_USER="${HEALTH_CHECK_USER:-admin}"
HEALTH_CHECK_PASS="${HEALTH_CHECK_PASS:-}"
fail() { echo "  ❌ $1"; FAILS=$((FAILS+1)); }
pass() { echo "  ✅ $1"; }

echo "===== 生产性能检查: $BASE ====="

# 1. gzip 生效(需带 token 的 API 才有意义, 用公开 logo 端点)
echo "-- [1/5] gzip 压缩"
# 用静态 JS 检测(响应大必压缩);API 小响应(<1k)不触发 gzip_min_length,不可用
JS=$(curl -sk "$BASE/" | grep -oE "assets/index-[A-Za-z0-9_-]+\.js" | head -1)
ENC=$(curl -sk -H "Accept-Encoding: gzip" -D - -o /dev/null "$BASE/$JS" 2>/dev/null | grep -i "content-encoding" | tr -d "\r")
if echo "$ENC" | grep -qi "gzip"; then
  pass "gzip 生效 ($ENC)"
else
  fail "gzip 未生效($ENC) — 大响应(JOSN/JS)慢网络下传输 4-9 倍"
fi

# 2. index.html no-cache(防旧版缓存)
echo "-- [2/5] SPA 入口缓存策略"
CACHE=$(curl -sk -D - -o /dev/null "$BASE/" 2>/dev/null | grep -i "cache-control" | tr -d "\r")
if echo "$CACHE" | grep -qi "no-cache"; then
  pass "index.html no-cache ($CACHE)"
else
  fail "index.html 无 no-cache($CACHE) — 浏览器缓存旧版引用已删 chunk 会"点击没反应""
fi

# 3. 安全头 4 项
echo "-- [3/5] 安全响应头"
HDRS=$(curl -sk -D - -o /dev/null "$BASE/" 2>/dev/null)
for h in "strict-transport-security" "x-frame-options" "x-content-type-options" "referrer-policy"; do
  echo "$HDRS" | grep -qi "$h" && pass "$h" || fail "缺 $h"
done

# 4. 首页耗时
echo "-- [4/5] 首页响应耗时"
T=$(curl -sk -o /dev/null -w "%{time_total}" "$BASE/" --connect-timeout 10)
echo "  首页: ${T}s"
awk -v t="$T" 'BEGIN{exit !(t<2)}' && pass "首页 < 2s" || fail "首页响应 ${T}s ≥ 2s"

# 5. 登录耗时(有 token 时)
if [ -n "$TOKEN" ]; then
  echo "-- [5/5] 登录接口耗时"
  if [ -n "$HEALTH_CHECK_PASS" ]; then
    T2=$(curl -sk -o /dev/null -w "%{time_total}" -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$HEALTH_CHECK_USER\",\"password\":\"$HEALTH_CHECK_PASS\"}" --connect-timeout 10)
    echo "  登录: ${T2}s"
    awk -v t="$T2" 'BEGIN{exit !(t<2)}' && pass "登录 < 2s" || fail "登录响应 ${T2}s ≥ 2s"
  else
    fail "缺少 HEALTH_CHECK_PASS(~/.ycmn-deploy.env)，跳过登录耗时检查"
  fi
fi

echo ""
if [ "$FAILS" -gt 0 ]; then
  echo "❌ 生产性能检查失败: $FAILS 项异常"
  exit 1
else
  echo "✅ 生产性能检查通过"
  exit 0
fi
