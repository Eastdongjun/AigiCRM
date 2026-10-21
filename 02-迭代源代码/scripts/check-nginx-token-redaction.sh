#!/bin/bash
# ============================================================
# scripts/check-nginx-token-redaction.sh — 【D7 2026-10-21】access log token 脱敏契约
#
# 背景（审计 D7）：access log 的 token 脱敏正则贪婪匹配，一个请求带多个 token= 时
# 只遮蔽最后一个，前面的 JWT 明文写入 access.log（nginx 1.25-alpine 实测）。
# 修复采用链式 map（见 nginx/nginx-ssl.conf 与 aigicrm-ui/nginx.conf.template 的
# $request_redacted 段）。本脚本守护该契约：
#   [1] 静态断言：两份配置都必须含三级链式 map + 兜底 map + log_format 引用脱敏变量，
#       且不得再出现旧的单级贪婪 map（`.*[?&]token=`）；
#   [2] 语义验证（python3 re，与 nginx PCRE 在非贪婪/负向前瞻上语义一致）：
#       单/双/三 token 全部脱敏、无 token 不变、≥4 token 走兜底丢弃 query、幂等；
#   [3] 运行时验证（可选，需本机 docker）：真实 nginx 容器渲染 map 链，双 token
#       请求日志必须全部为 ***。docker 缺席时跳过并明示（不算失败）。
# 退出码：0=通过；1=契约破坏
# ============================================================
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
NGINX_SSL="$REPO_ROOT/nginx/nginx-ssl.conf"
NGINX_TEMPLATE="$REPO_ROOT/aigicrm-ui/nginx.conf.template"

FAIL=0
bad() { echo "❌ $1" >&2; FAIL=$((FAIL+1)); }
ok()  { echo "✅ $1"; }

# ---- [1] 静态断言 ----
for f in "$NGINX_SSL" "$NGINX_TEMPLATE"; do
  tag="$(basename "$(dirname "$f")")/$(basename "$f")"
  if [ ! -f "$f" ]; then bad "$tag 文件不存在"; continue; fi
  # 三级链式 map：每级非贪婪 .*? 定位第一个 token=
  chain_cnt="$(grep -cE '\(\?<pre[0-9]>\.\*\?\[\?\&\]token=\)' "$f" || true)"
  if [ "${chain_cnt:-0}" -ge 3 ]; then ok "$tag: 三级链式脱敏 map 存在($chain_cnt 级)"; else bad "$tag: 链式脱敏 map 不足 3 级(找到 ${chain_cnt:-0})"; fi
  if grep -qF '(?!\*\*\*)' "$f"; then ok "$tag: 负向前瞻跳过已脱敏值"; else bad "$tag: 缺少 (?!\*\*\*) 负向前瞻(会把已脱敏 token 再扫一遍而漏掉后续)"; fi
  if grep -qF 'guard_pre' "$f"; then ok "$tag: ≥4 token 兜底 map 存在"; else bad "$tag: 缺少兜底 map(guard_pre)"; fi
  if grep -q 'log_format aigicrm_redacted .*"\$request_redacted"' "$f"; then ok "$tag: 日志格式使用 \$request_redacted"; else bad "$tag: log_format 未引用 \$request_redacted"; fi
  # 旧贪婪单级 map（`.*[?&]token=` 且无非贪婪 ? 标记）不得回归
  if grep -qE '"\^\(\?<req_pre>\.\*\[\?\&\]token=\)' "$f"; then
    bad "$tag: 旧贪婪单级 map 回归(多 token URL 只遮蔽最后一个)"
  else
    ok "$tag: 无旧贪婪单级 map"
  fi
done

# ---- [2] 语义验证（python3 re 模拟 map 链；非贪婪/负向前瞻与 nginx PCRE 语义一致）----
if command -v python3 >/dev/null 2>&1; then
  if python3 - "$NGINX_SSL" <<'PY'
import re, sys
conf = open(sys.argv[1], encoding='utf-8').read()
# 从配置中原样提取 map 链的带引号模式串（与 nginx 实际使用的 PCRE 同文），避免二次转义；
# 排除换行防止文件头注释里的引号跨行吞并模式串
quoted = re.findall(r'"([^"\n]*token=[^"\n]*)"', conf)
stage = [q for q in quoted if q.startswith('~^(?<pre')]
guard = [q for q in quoted if q.startswith('~^(?<guard_pre')]
if len(stage) < 3 or not guard:
    print(f"配置正则提取失败: stage={len(stage)} guard={len(guard)}"); sys.exit(1)

def build(pattern):
    # nginx map 用前导 ~ 标记大小写敏感正则，非正则内容，编译前剥掉；
    # 命名组 (?<name>) → python (?P<name>)；其余语法(非贪婪/前瞻/字符类)两者一致
    if pattern.startswith('~'):
        pattern = pattern[1:]
    return re.compile(re.sub(r'\(\?<(\w+)>', r'(?P<\1>', pattern))

stages = [build(p) for p in stage]
g = build(guard[0])

def redact(req):
    s = req
    for st in stages:
        m = st.match(s)
        if not m:
            continue
        names = list(m.groupdict())          # (preN, postN)
        s = m.group(names[0]) + '***' + m.group(names[-1])
    m = g.match(s)
    if m:
        s = m.group('guard_pre') + 'token=***&more-redacted'
    return s

cases = [
    ("GET /api/file/download/1?token=AAA HTTP/1.1", "GET /api/file/download/1?token=*** HTTP/1.1"),
    ("GET /api?x=1&token=AAA&token=BBB HTTP/1.1", "GET /api?x=1&token=***&token=*** HTTP/1.1"),
    ("GET /api?token=AAA&y=2&token=BBB&token=CCC HTTP/1.1", "GET /api?token=***&y=2&token=***&token=*** HTTP/1.1"),
    ("GET /index.html HTTP/1.1", "GET /index.html HTTP/1.1"),
    ("GET /api?token=AAA&token=BBB&token=CCC&token=DDD HTTP/1.1", "GET /api?token=***&more-redacted"),
    ("GET /api?other=AAA HTTP/1.1", "GET /api?other=AAA HTTP/1.1"),
]
fails = 0
for req, expect in cases:
    got = redact(req)
    if got != expect:
        print(f"FAIL {req!r} -> {got!r} (期望 {expect!r})"); fails += 1
# 幂等：已脱敏结果再过一遍不变
s0 = "GET /api?x=1&token=***&token=*** HTTP/1.1"
if redact(s0) != s0:
    print(f"FAIL 幂等: {s0!r} -> {redact(s0)!r}"); fails += 1
sys.exit(1 if fails else 0)
PY
  then ok "map 链语义验证(单/双/三 token 全脱敏, 无 token 不变, ≥4 兜底, 幂等)"; else bad "map 链语义验证失败——多 token URL 仍有 JWT 残留"; fi
else
  echo "⚠️ 缺少 python3，跳过语义验证（不算失败，但请补装后复跑）" >&2
fi

# ---- [3] 运行时验证（可选：docker + nginx 容器内渲染真实 map 链）----
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  RT_DIR="$(mktemp -d /tmp/aigicrm-nginx-redact.XXXXXX)"
  cleanup_rt() { rm -rf "$RT_DIR"; }
  trap cleanup_rt EXIT
  # 最小 nginx 配置：只挂 map 链 + 本地 200 服务，抓真实 access.log 断言双 token 全 ***
  {
    sed -n '/^map \$request \$req_redact_1/,/^}/p' "$NGINX_SSL"
    sed -n '/^map \$req_redact_1 /,/^}/p' "$NGINX_SSL"
    sed -n '/^map \$req_redact_2 /,/^}/p' "$NGINX_SSL"
    sed -n '/^map \$req_redact_3 /,/^}/p' "$NGINX_SSL"
    sed -n '/^log_format aigicrm_redacted/,/x_forwarded_for.;/p' "$NGINX_SSL"
    cat <<'EOF'
events { worker_connections 16; }
http {
    access_log /var/log/nginx/access.log aigicrm_redacted;
    server {
        listen 127.0.0.1:18080;
        return 200 "ok";
    }
}
EOF
  } > "$RT_DIR/nginx.conf"
  if docker run --rm -d --name aigicrm-redact-test -v "$RT_DIR/nginx.conf":/etc/nginx/nginx.conf:ro \
       -p 127.0.0.1:18080:18080 nginx:1.25-alpine >/dev/null 2>&1; then
    sleep 1
    # shellcheck disable=SC2154
    curl -s "http://127.0.0.1:18080/api?x=1&token=AAA&token=BBB" >/dev/null
    sleep 1
    LOG="$(docker logs aigicrm-redact-test 2>/dev/null; docker exec aigicrm-redact-test cat /var/log/nginx/access.log 2>/dev/null || true)"
    docker rm -f aigicrm-redact-test >/dev/null 2>&1 || true
    if printf '%s' "$LOG" | grep -q 'token=AAA\|token=BBB'; then
      bad "运行时双 token 验证失败——日志仍含明文 token"
    elif printf '%s' "$LOG" | grep -q 'token=\*\*\*&token=\*\*\*'; then
      ok "运行时双 token 验证通过(真实 nginx 日志全部 ***)"
    else
      bad "运行时验证未取到日志（容器启动/请求失败），请人工复跑"
    fi
  else
    echo "⚠️ nginx 容器启动失败，跳过运行时验证（不算失败，请人工复跑）" >&2
  fi
else
  echo "⚠️ docker 不可用，跳过运行时验证（静态+语义验证仍生效；不算失败）" >&2
fi

echo "结果: $([ "$FAIL" -eq 0 ] && echo PASS || echo FAIL)"
[ "$FAIL" -eq 0 ] || exit 1
exit 0
