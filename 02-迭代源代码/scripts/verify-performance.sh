#!/usr/bin/env bash
# 【全系统测试矩阵·12 性能流畅性域】基线延迟+页面指标回归 2026-11-22
# 口径: 测试环境(共享服务器)——**采样基线对账, 非压测**(不冲击共享环境);
#      页面指标走 Playwright(PC 端)。预算先填目标, 实测对账。
# 断言:
#   PERF-01 登录接口 P95<800ms (30 采样)
#   PERF-02 /auth/info 权限装配 P95<500ms (30 采样)
#   PERF-03 客户列表 P95<800ms (30 采样)
#   PERF-04 统计接口 P95<2000ms (10 采样, B3-PERF-001 痛区)
#   PERF-05 审批列表 P95<800ms (20 采样)
#   PERF-06 小并发探针: 10 并发客户列表全 200 且 P95<3s (稳定性探针)
#   PERF-07 深分页 current=50 <1500ms
#   PERF-08 页面加载(Playwright): 登录页 FCP<2000ms, load<4000ms
#   PERF-09 ensure-expiring 全表扫描型任务 单次<5000ms
# 用法: AIGI_TEST_USER=admin AIGI_TEST_PASS=... bash scripts/verify-performance.sh
set -uo pipefail
BASE="${AIGI_BASE_URL:-http://111.229.37.220}"
API_PASS="${AIGI_TEST_PASS:?缺少 AIGI_TEST_PASS}"
PASS=0; FAIL=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }

login_token() {
  curl -s -m 15 -X POST "$BASE/api/auth/login" -H "Origin: $BASE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("data") or {}).get("token") or "")'
}
# 采样器: $1=URL $2=token(空=匿名) $3=次数 $4=POST body(空=GET) ; 输出 P50/P95/最大(ms)
sample() {
  local url="$1" tok="$2" n="$3" body="${4:-}" i
  for i in $(seq 1 "$n"); do
    if [ -n "$body" ]; then
      curl -s -o /dev/null -w '%{time_total}\n' -m 20 -X POST "$url" -H "Authorization: Bearer $tok" -H "Origin: $BASE" -H 'Content-Type: application/json' -d "$body"
    elif [ -n "$tok" ]; then
      curl -s -o /dev/null -w '%{time_total}\n' -m 20 "$url" -H "Authorization: Bearer $tok" -H "Origin: $BASE"
    else
      curl -s -o /dev/null -w '%{time_total}\n' -m 20 "$url"
    fi
  done | python3 -c "
import sys
t = sorted(float(x)*1000 for x in sys.stdin if x.strip())
if not t: print('NO_DATA'); raise SystemExit
p50 = t[len(t)//2]; p95 = t[max(0, min(len(t)-1, int(round(len(t)*0.95))-1))]
print(f'{p50:.0f}/{p95:.0f}/{t[-1]:.0f}')"
}
# 对账器: $1=标签 $2=P95实测 $3=预算ms
check() {
  local label="$1" got="$2" budget="$3" p95
  p95=$(echo "$got" | cut -d/ -f2)
  if [ "$p95" = "NO_DATA" ]; then bad "$label: 无采样数据"; return; fi
  if [ "$p95" -le "$budget" ]; then ok "$label: P50/P95/Max=${got}ms ≤ 预算${budget}ms"
  else bad "$label: P95=${p95}ms > 预算${budget}ms (P50/P95/Max=${got})"; fi
}

TADM=$(login_token admin "$API_PASS")
[ -n "$TADM" ] && ok "admin 登录" || { bad "admin 登录失败"; echo "==> 性能域回归: PASS=$PASS FAIL=$FAIL"; exit 1; }

echo "=== [PERF-01..05,07] 接口基线 P50/P95/Max (ms) ==="
R=$(sample "$BASE/api/auth/login" "" 30 "{\"username\":\"admin\",\"password\":\"$API_PASS\"}")
check "PERF-01 登录接口" "$R" 800
R=$(sample "$BASE/api/auth/info" "$TADM" 30)
check "PERF-02 /auth/info 权限装配" "$R" 500
R=$(sample "$BASE/api/customer/list?current=1&size=20" "$TADM" 30)
check "PERF-03 客户列表(20条)" "$R" 800
R=$(sample "$BASE/api/statistics/indicator-stats" "$TADM" 10)
check "PERF-04 统计接口(B3痛区)" "$R" 2000
R=$(sample "$BASE/api/approval/list?current=1&size=20" "$TADM" 20)
check "PERF-05 审批列表" "$R" 800
R=$(sample "$BASE/api/customer/list?current=50&size=20" "$TADM" 10)
check "PERF-07 深分页 current=50" "$R" 1500

echo "=== [PERF-06] 小并发稳定性探针(10 并发, 非压测) ==="
CONC=$(seq 1 10 | xargs -P 10 -I{} curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -m 20 \
  -H "Authorization: Bearer $TADM" -H "Origin: $BASE" "$BASE/api/customer/list?current=1&size=20")
N200=$(echo "$CONC" | grep -c '^200 ' || true)
P95C=$(echo "$CONC" | python3 -c "
import sys
t = sorted(float(l.split()[1])*1000 for l in sys.stdin if l.strip())
print(int(t[-1]) if t else 99999)")
[ "$N200" = "10" ] && ok "PERF-06 并发探针: 10/10 全 200, 最慢 ${P95C}ms" || bad "PERF-06 并发: 仅 $N200/10 成功: $(echo "$CONC" | head -3)"
if [ "$N200" = "10" ] && [ "${P95C:-99999}" -gt 3000 ]; then bad "PERF-06 并发最慢 ${P95C}ms > 3000ms"; fi

echo "=== [PERF-08] 页面加载指标(Playwright, 测试环境前端) ==="
(cd aigicrm-ui && BASE="$BASE" node - <<'NODE' 2>/dev/null || echo "PAGE_FAIL"
const { chromium } = require('@playwright/test');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto(process.env.BASE + '/', { waitUntil: 'load', timeout: 30000 });
  const m = await p.evaluate(() => {
    const paint = performance.getEntriesByType('paint');
    const nav = performance.getEntriesByType('navigation')[0] || {};
    const fcp = (paint.find(e => e.name === 'first-contentful-paint') || {}).startTime;
    return { fcp: Math.round(fcp || -1), load: Math.round(nav.loadEventEnd || nav.duration || -1) };
  });
  await b.close();
  console.log(`FCP=${m.fcp} LOAD=${m.load}`);
})().catch(e => { console.log('PAGE_FAIL ' + e.message); process.exit(1); });
NODE
) > /tmp/perf-page.txt 2>&1
PAGE=$(grep -oE 'FCP=[0-9]+ LOAD=[0-9]+' /tmp/perf-page.txt | head -1)
if [ -n "$PAGE" ]; then
  FCP=$(echo "$PAGE" | grep -oE 'FCP=[0-9]+' | cut -d= -f2)
  LOAD=$(echo "$PAGE" | grep -oE 'LOAD=[0-9]+' | cut -d= -f2)
  if [ "$FCP" -le 2000 ] && [ "$LOAD" -le 4000 ]; then ok "PERF-08 登录页: FCP=${FCP}ms ≤2000, load=${LOAD}ms ≤4000"
  else bad "PERF-08 登录页: FCP=${FCP}ms/load=${LOAD}ms 超预算(2000/4000)"; fi
else bad "PERF-08 页面指标采集失败: $(head -2 /tmp/perf-page.txt)"; fi

echo "=== [PERF-09] 全表扫描型任务单次耗时 ==="
T0=$(python3 -c 'import time; print(time.time())')
curl -s -o /dev/null -m 30 -X POST "$BASE/api/reminder/ensure-expiring" -H "Authorization: Bearer $TADM" -H "Origin: $BASE"
T1=$(python3 -c 'import time; print(time.time())')
EL=$(( $(python3 -c "print(int(($T1-$T0)*1000))") ))
[ "$EL" -le 5000 ] && ok "PERF-09 ensure-expiring 单次 ${EL}ms ≤5000" || bad "PERF-09 ensure-expiring ${EL}ms >5000"

echo "==> 性能域回归: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]