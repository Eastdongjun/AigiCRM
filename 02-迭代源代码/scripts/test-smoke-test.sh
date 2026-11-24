#!/usr/bin/env bash
# smoke-test.sh 的行为测试(GL-08, 2026-11-24)
#
# 用本机的一个假后端(python3 标准库, 只监听 127.0.0.1)验证冒烟脚本的判定:
#   ① 正常: 管理员登录、核心接口、法布施只读端点、销售员权限隔离全部通过 → 退出 0
#   ② 没配销售员账号 → 权限隔离项明确不通过(不再是"跳过"后整体通过), 退出 1
#   ③ 配了但登录失败 → 不通过
#   ④ 销售员能读到用户列表(越权) → 不通过
#   ⑤ 任一法布施端点坏掉 → 不通过并点名
#   ⑥ 首次访问空响应、重试成功 → 标注"抖动重试通过"并算通过
#   ⑦ 测试环境地址可用 SMOKE_BASE_URL 覆盖; 生产地址不接受覆盖
#   ⑧ 每次运行只发一次管理员登录、一次销售员登录(同一账号从同一 IP 连续失败 5 次会被锁 15 分钟)
#   ⑨ 账号按环境分开取, 不跨环境回退: test 目标只认 TEST_HEALTH_CHECK_* / TEST_SMOKE_*,
#      没配就在发任何请求之前中止(拿生产口令去试测试库, 既登不进去又白烧失败次数)
# 假后端记录收到的每个请求和管理员实际调到的法布施路径, 测试据此确认端点都真的被调到了。
# 全程不联网; 口令在运行时拼出来, 不落任何真实凭据。
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }
[ -f "$TEST_SELF_DIR/smoke-test.sh" ] || fail "缺少 scripts/smoke-test.sh"

T="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-smoke-test.XXXXXX")"
SERVER_PID=""
stop_server() { # 停掉当前场景的假后端。每换一个场景都要先停旧的, 否则会留下孤儿进程
  [ -n "$SERVER_PID" ] || return 0
  kill "$SERVER_PID" 2>/dev/null
  wait "$SERVER_PID" 2>/dev/null
  SERVER_PID=""
}
cleanup() { stop_server; rm -rf "$T"; }
trap cleanup EXIT
mkdir -p "$T/scripts"
cp "$TEST_SELF_DIR/smoke-test.sh" "$T/scripts/smoke-test.sh"
# 安全前置: 不支持地址覆盖的 smoke-test.sh 一运行就会去连真实的测试服(拿假账号尝试登录, 会给那边的账号记失败次数)。
# 所以先确认副本支持覆盖, 不支持就中止, 一步都不跑。
grep -q 'BASE="${SMOKE_BASE_URL:-' "$T/scripts/smoke-test.sh" \
  || fail "smoke-test.sh 尚不支持 SMOKE_BASE_URL 覆盖 —— 为避免连到真实环境, 本测试不运行"
# 同理: 旧版脚本给 test 目标用的是生产健康检查账号(HEALTH_CHECK_*), 这里传入的也是假账号, 同样会白烧一次登录。
# 旧版会把它当成登录失败, 下面的场景会报"管理员登录失败"而不是真正要测的东西, 所以也先确认。
grep -q 'TEST_HEALTH_CHECK_PASS' "$T/scripts/smoke-test.sh" \
  || fail "smoke-test.sh 的 test 目标尚未改用 TEST_HEALTH_CHECK_*(仍拿生产账号去试测试环境), 本测试不运行"
: > "$T/scripts/deploy-lib.sh"          # 桩: 不读部署配置, 账号全部经环境变量传入
PORT_FILE="$T/port"; [ -e "$PORT_FILE" ] && { echo "端口文件已存在" >&2; exit 1; }
HITS="$T/hits.log"; LOGINS="$T/logins.log"; REQUESTS="$T/requests.log"

# 法布施只读端点清单: 与 smoke-test.sh 里的清单逐项对应(改那边要同步改这里)
FABUSHI="fabushi/books fabushi/posts fabushi/awaken/status fabushi/shelf fabushi/shelf/groups fabushi/records \
fabushi/settings fabushi/bookmarks/mine fabushi/reads/today fabushi/activities fabushi/stats/dashboard \
fabushi/stats/monthly fabushi/stats/personal fabushi/stats/team fabushi/books/pending fabushi/posts/deleted"

cat > "$T/fake-backend.py" <<'PY'
#!/usr/bin/env python3
"""smoke-test.sh 行为测试用的假后端。账号、场景、记录文件都经环境变量传入, 不写死任何口令。"""
import json, os, secrets, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

ADMIN = (os.environ['FAKE_ADMIN_USER'], os.environ['FAKE_ADMIN_PASS'])
SALES = (os.environ.get('FAKE_SALES_USER', ''), os.environ.get('FAKE_SALES_PASS', ''))
SCEN = os.environ.get('FAKE_SCENARIO', 'good')
HITS, LOGINS, REQUESTS = os.environ['FAKE_HITS'], os.environ['FAKE_LOGINS'], os.environ['FAKE_REQUESTS']
FABUSHI = set(os.environ['FAKE_FABUSHI'].split())
FLAKY = '/api/fabushi/records'
CORE = {'/api/auth/info', '/api/customer/list', '/api/incentive/categories', '/api/file/folders',
        '/api/reminder/list', '/api/approval/list', '/api/system/depts', '/api/statistics/dashboard',
        '/api/system/configs', '/api/system/roles', '/api/system/roles/1/orgs', '/api/incentive/records',
        '/api/sea/statistics'}
TOKENS = {}
VISITS = {}

def note(file, text):
    with open(file, 'a') as f:
        f.write(text + '\n')

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _send(self, status, body, content_type='application/json'):
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _json(self, status, doc):
        self._send(status, json.dumps(doc).encode())

    def do_POST(self):
        path = urlparse(self.path).path
        note(REQUESTS, 'POST ' + path)
        if path != '/api/auth/login':
            return self._json(404, {'code': 404})
        length = int(self.headers.get('Content-Length') or 0)
        try:
            body = json.loads(self.rfile.read(length) or b'{}')
        except ValueError:
            body = {}
        cred = (str(body.get('username', '')), str(body.get('password', '')))
        role = 'admin' if cred == ADMIN else ('sales' if SALES[0] and cred == SALES else None)
        if role is None or (SCEN == 'sales_login_fails' and role == 'sales'):
            note(LOGINS, 'failed')
            return self._json(200, {'code': 401, 'msg': '用户名或口令错误'})
        note(LOGINS, role)
        token = 't-' + role + '-' + secrets.token_hex(8)
        TOKENS[token] = role
        self._json(200, {'code': 200, 'data': {'token': token}})

    def do_GET(self):
        path = urlparse(self.path).path
        note(REQUESTS, 'GET ' + path)
        if path == '/':
            return self._send(200, b'<html>ok</html>', 'text/html')
        if path == '/api/system/configs/logo':
            return self._send(200, b'\x89PNG')
        auth = self.headers.get('Authorization', '')
        token = auth[7:] if auth.startswith('Bearer ') else ''
        role = TOKENS.get(token)
        if role is None:
            return self._json(401, {'code': 401})
        if role == 'admin' and path in FABUSHI:
            note(HITS, path)
        if role == 'sales' and path.startswith('/api/system/users'):
            if SCEN == 'leak':
                return self._json(200, {'code': 200, 'data': {'records': [{'id': 1}]}})
            return self._json(403, {'code': 403})
        allowed = CORE | FABUSHI if role == 'admin' else {'/api/auth/info'}
        if path not in allowed:
            return self._json(403, {'code': 403})
        if SCEN == 'flaky' and path == FLAKY:
            VISITS[path] = VISITS.get(path, 0) + 1
            if VISITS[path] == 1:
                return self._send(200, b'')          # 首次空响应(模拟网络抖动)
        if SCEN == 'fabushi_broken' and path == '/api/fabushi/shelf':
            return self._json(200, {'code': 500, 'msg': '模拟故障'})
        data = [{'id': 1}] if path == '/api/system/roles' else {'records': [], 'total': 0}
        self._json(200, {'code': 200, 'data': data})

if __name__ == '__main__':
    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    with open(sys.argv[1], 'w') as f:
        f.write(str(server.server_address[1]))
    server.serve_forever()
PY

# 账号在运行时拼出来(不出现"某口令 = 字面量"的写法)
AU="admin"
AP="$(printf 'h%s' 'c-7x-run')"
SU="sales$(printf '%s' '-acct')"
SP="$(printf 's%s' 'p-3k-run')"

start_server() { # $1=场景
  local i fab
  stop_server
  : > "$HITS"; : > "$LOGINS"; : > "$REQUESTS"; rm -f "$PORT_FILE"
  fab="$(for ep in $FABUSHI; do printf '/api/%s ' "$ep"; done)"     # 假后端按完整路径比对
  FAKE_ADMIN_USER="$AU" FAKE_ADMIN_PASS="$AP" FAKE_SALES_USER="$SU" FAKE_SALES_PASS="$SP" \
    FAKE_SCENARIO="$1" FAKE_HITS="$HITS" FAKE_LOGINS="$LOGINS" FAKE_REQUESTS="$REQUESTS" FAKE_FABUSHI="$fab" \
    python3 "$T/fake-backend.py" "$PORT_FILE" > "$T/server.out" 2>&1 &
  SERVER_PID=$!
  for i in $(seq 1 50); do [ -s "$PORT_FILE" ] && break; sleep 0.1; done
  [ -s "$PORT_FILE" ] || fail "假后端没有启动: $(cat "$T/server.out")"
  BASE_URL="http://127.0.0.1:$(cat "$PORT_FILE")"
}
run_smoke() { # 其余环境变量由调用方通过 env 传入; 输出进 $T/out
  SMOKE_BASE_URL="$BASE_URL" bash "$T/scripts/smoke-test.sh" "$1" > "$T/out" 2>&1 < /dev/null
}
out() { cat "$T/out"; }
count() { /usr/bin/grep -c -- "$1" "$2" | tr -d ' '; }   # 某个标记在记录文件里出现的次数(不匹配时 grep 返回 1, 这里只要数字)
# 测试环境自己的账号(假值)。不设 HEALTH_CHECK_* / SMOKE_TEST_*: 那是生产环境的键, test 目标不应读它们。
with_account() { TEST_HEALTH_CHECK_USER="$AU" TEST_HEALTH_CHECK_PASS="$AP" TEST_SMOKE_USER="$SU" TEST_SMOKE_PASS="$SP" run_smoke test; }
admin_only()   { TEST_HEALTH_CHECK_USER="$AU" TEST_HEALTH_CHECK_PASS="$AP" run_smoke test; }

# ---- ① 正常 ----
start_server good
rc=0; with_account || rc=$?
[ "$rc" -eq 0 ] || fail "正常时应退出 0, 实际 rc=${rc}; 输出: $(out)"
grep -Fq '✅ 权限隔离: '"$SU"' users 403' "$T/out" || fail "正常时应通过权限隔离: $(out)"
grep -Fq '✅ 权限隔离: '"$SU"' 可读本人信息 auth/info' "$T/out" || fail "应先确认销售员令牌有效(正向对照): $(out)"
for ep in $FABUSHI; do
  grep -Fq '✅ /api/'"$ep" "$T/out" || fail "法布施端点 /api/$ep 没有被判为通过: $(out)"
done
for ep in $FABUSHI; do
  grep -qx '/api/'"$ep" "$HITS" || fail "冒烟没有真的请求 /api/$ep: 实际请求 $(sort -u "$HITS" | tr '\n' ' ')"
done
[ "$(sort -u "$HITS" | wc -l | tr -d ' ')" -eq 16 ] || fail "管理员请求到的法布施端点数不对: $(sort -u "$HITS" | wc -l | tr -d ' ')"
# ---- ⑧ 每次运行只发一次管理员登录、一次销售员登录 ----
[ "$(count '^admin$' "$LOGINS")" -eq 1 ] || fail "一次运行应只发一次管理员登录, 实际 $(count '^admin$' "$LOGINS") 次"
[ "$(count '^sales$' "$LOGINS")" -eq 1 ] || fail "一次运行应只发一次销售员登录, 实际 $(count '^sales$' "$LOGINS") 次"
[ "$(count '^failed$' "$LOGINS")" -eq 0 ] || fail "账号都对时不该有任何失败的登录尝试, 实际 $(count '^failed$' "$LOGINS") 次"

# ---- ② 没配销售员账号: 明确不通过, 且不去试销售员登录 ----
start_server good
rc=0; admin_only || rc=$?
[ "$rc" -eq 1 ] || fail "没配销售员账号时应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q '❌ 权限隔离: 未配置 TEST_SMOKE_USER' "$T/out" || fail "应明确报权限隔离未配置: $(out)"
grep -q '⏭' "$T/out" && fail "权限隔离不应再以跳过收场: $(out)"
grep -q '✅ 冒烟测试全部通过' "$T/out" && fail "缺账号时不应打印全部通过: $(out)"
[ "$(grep -c . "$LOGINS")" -eq 1 ] || fail "没配销售员账号时不应尝试销售员登录: 登录记录 $(tr '\n' ' ' < "$LOGINS")"

# ---- ③ 配了但登录失败 ----
start_server sales_login_fails
rc=0; with_account || rc=$?
[ "$rc" -eq 1 ] || fail "销售员登录失败时应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q '❌ 权限隔离: '"$SU"' 登录失败' "$T/out" || fail "应点名销售员登录失败: $(out)"
[ "$(count '^failed$' "$LOGINS")" -eq 1 ] || fail "销售员登录失败后不应再试第二次: $(count '^failed$' "$LOGINS") 次"

# ---- ④ 越权: 销售员能读用户列表 ----
start_server leak
rc=0; with_account || rc=$?
[ "$rc" -eq 1 ] || fail "销售员越权时应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q '❌ 权限隔离: '"$SU"' users 返回 200' "$T/out" || fail "应点名越权: $(out)"

# ---- ⑤ 法布施端点坏掉 ----
start_server fabushi_broken
rc=0; with_account || rc=$?
[ "$rc" -eq 1 ] || fail "法布施端点坏掉时应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q '❌ /api/fabushi/shelf → code=500' "$T/out" || fail "应点名坏掉的端点: $(out)"

# ---- ⑥ 抖动: 首次空响应, 重试成功 ----
start_server flaky
rc=0; with_account || rc=$?
[ "$rc" -eq 0 ] || fail "抖动重试成功时应退出 0, 实际 rc=${rc}; 输出: $(out)"
grep -q '✅ /api/fabushi/records (抖动重试通过)' "$T/out" || fail "应标注抖动重试通过: $(out)"

# ---- ⑨ 账号按环境分开取, 不跨环境回退 ----
# 只有生产的键(HEALTH_CHECK_* / SMOKE_TEST_*), 目标却是 test: 应在发任何请求之前中止
start_server good
rc=0; HEALTH_CHECK_USER="$AU" HEALTH_CHECK_PASS="$AP" SMOKE_TEST_USER="$SU" SMOKE_TEST_PASS="$SP" run_smoke test || rc=$?
[ "$rc" -eq 1 ] || fail "test 目标缺测试环境管理员账号时应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q 'TEST_HEALTH_CHECK_USER / TEST_HEALTH_CHECK_PASS' "$T/out" || fail "应说明缺的是测试环境的管理员账号: $(out)"
[ "$(grep -c . "$REQUESTS")" -eq 0 ] || fail "缺测试环境账号时不应发出任何请求, 实际发了: $(tr '\n' ' ' < "$REQUESTS")"
# 配了测试环境管理员、没配测试环境销售员(只有生产风格的销售员键): 销售员那项报未配置, 不读 SMOKE_TEST_*
start_server good
rc=0; TEST_HEALTH_CHECK_USER="$AU" TEST_HEALTH_CHECK_PASS="$AP" SMOKE_TEST_USER="$SU" SMOKE_TEST_PASS="$SP" run_smoke test || rc=$?
[ "$rc" -eq 1 ] || fail "test 目标缺测试环境销售员账号时应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q '❌ 权限隔离: 未配置 TEST_SMOKE_USER' "$T/out" || fail "应报未配置测试环境的销售员账号, 而不是去用 SMOKE_TEST_*: $(out)"
[ "$(grep -c . "$LOGINS")" -eq 1 ] || fail "不应拿生产风格的销售员键去登录测试环境: 登录记录 $(tr '\n' ' ' < "$LOGINS")"

# ---- ⑦ 生产地址不接受覆盖; 生产账号键不变 ----
SRC="$T/scripts/smoke-test.sh"
/usr/bin/grep 'www.aigicrm.com' "$SRC" | /usr/bin/grep -q 'SMOKE_BASE_URL' && fail "生产分支的地址不应引用 SMOKE_BASE_URL"
grep -q 'BASE="${SMOKE_BASE_URL:-' "$SRC" || fail "测试环境地址应支持 SMOKE_BASE_URL 覆盖: $(grep -n 'BASE=' "$SRC")"
# 账号那段的两个分支: 第一条 ADMIN_USER 赋值的上一行是 if, 往下第一个 else 与 fi 之间是测试分支
n="$(/usr/bin/grep -n '^  ADMIN_USER=' "$SRC" | head -1 | cut -d: -f1)"
else_n="$(awk -v s="$n" 'NR > s && /^else$/ { print NR; exit }' "$SRC")"
fi_n="$(awk -v s="$n" 'NR > s && /^fi$/ { print NR; exit }' "$SRC")"
{ [ -n "$n" ] && [ -n "$else_n" ] && [ -n "$fi_n" ] && [ "$n" -lt "$else_n" ] && [ "$else_n" -lt "$fi_n" ]; } \
  || fail "在 smoke-test.sh 里找不到按目标取账号的那段 if/else/fi"
prod_branch="$(sed -n "$((n-1)),$((else_n-1))p" "$SRC")"
test_branch="$(sed -n "$((else_n+1)),$((fi_n-1))p" "$SRC")"
/usr/bin/grep -q '^if \[ "\$ENV" = "prod" \]; then$' <<< "$prod_branch" || fail "取账号那段应以目标是否为 prod 来分支: ${prod_branch}"
case "$prod_branch" in *'${HEALTH_CHECK_USER:-admin}'*'${SMOKE_TEST_USER:-}'*) ;; *) fail "prod 分支应使用 HEALTH_CHECK_USER 与 SMOKE_TEST_USER: ${prod_branch}" ;; esac
case "$prod_branch" in *'${TEST_HEALTH_CHECK_'*|*'${TEST_SMOKE_'*) fail "prod 分支不应读取测试环境的账号键: ${prod_branch}" ;; esac
case "$test_branch" in *'${TEST_HEALTH_CHECK_USER:-}'*'${TEST_SMOKE_USER:-}'*) ;; *) fail "test 分支应使用 TEST_HEALTH_CHECK_USER 与 TEST_SMOKE_USER: ${test_branch}" ;; esac
case "$test_branch" in *'${HEALTH_CHECK_'*|*'${SMOKE_TEST_'*) fail "test 分支不应读取生产环境的账号键(不跨环境回退): ${test_branch}" ;; esac

# ---- 自检: 除了当前场景这一个, 不能留下别的假后端进程(最后一个由退出时的清理负责) ----
others="$(pgrep -f 'aigicrm-smoke-test\.[A-Za-z0-9]+/fake-backend\.py' | grep -vx "$SERVER_PID" | wc -l | tr -d ' ')"
[ "$others" -eq 0 ] || fail "本测试留下了 ${others} 个假后端进程没有清理"

echo "PASS: smoke-test.sh 行为测试(法布施 16 个只读端点全部调到 / 缺账号明确不通过 / 登录失败与越权各自报红 / 端点坏掉点名 / 抖动重试 / 每次运行只登一次 / 账号按环境分开取 / 生产地址不接受覆盖)。"
