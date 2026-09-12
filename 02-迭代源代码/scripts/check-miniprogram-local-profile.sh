#!/usr/bin/env bash
# 校验小程序的环境档案：默认档保持 demo/free，真实档为 HTTPS，且档案内不得含凭据。
#
# 2026-09-12 修订（小程序定位为"真实客户端"后）：
#   env.js 改为**运行时**按 wx.getAccountInfoSync().miniProgram.envVersion 选择接口地址。
#   因此不再用 `grep -Fq "apiBaseUrl: ''"` 这类字面量断言（实现方式一变即失效），
#   改为断言**行为语义**：在受控 wx 桩下用 node 真正 require 该模块并检查导出值。
#
# 同时修掉本脚本原有的 fail-open 缺陷：原实现为
#     if rg -n "(password|secret|...)" "$PROFILE"; then fail; fi
# 当 rg 不存在时条件恒假 ⇒ 凭据断言被**静默跳过**且仍打印 PASS（假绿）。
# 现改为 fail-closed：扫描器不可用即 FAIL。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MP="$ROOT/aigicrm-miniprogram/config"
PROFILE="$MP/env.local.example.js"
DEFAULT="$MP/env.js"
PROD_EXAMPLE="$MP/env.prod.example.js"
fail() { echo "FAIL: $*" >&2; exit 1; }

command -v node >/dev/null 2>&1 || fail 'node is required for profile resolution assertions'

[ -f "$PROFILE" ] || fail 'local mini-program profile is missing'
[ -f "$DEFAULT" ] || fail 'default mini-program profile is missing'

# ---- 1) 本地档案必须指向本地后端且关闭 demo ----
grep -Fq "apiBaseUrl: 'http://127.0.0.1:8080'" "$PROFILE" || fail 'local profile must target the local backend'
grep -Fq 'demoMode: false' "$PROFILE" || fail 'local profile must disable demo mode'

# ---- 2) 生产档案模板必须为 HTTPS 且域名正确 ----
if [ -f "$PROD_EXAMPLE" ]; then
  grep -Fq 'https://' "$PROD_EXAMPLE" || fail 'production profile example must use HTTPS'
  grep -Fq 'aigicrm.com' "$PROD_EXAMPLE" || fail 'production profile example must target the aigicrm.com domain'
fi

# ---- 3) 行为断言：在 wx 桩下真正解析 env.js（每种 envVersion 一个用例）----
resolve() { # $1=envVersion → 输出 "apiBaseUrl|demoMode"
  node -e '
    const version = process.argv[1];
    global.wx = { getAccountInfoSync: () => ({ miniProgram: { envVersion: version } }) };
    delete require.cache[require.resolve(process.argv[2])];
    const m = require(process.argv[2]);
    process.stdout.write(String(m.apiBaseUrl) + "|" + String(m.demoMode));
  ' "$1" "$DEFAULT"
}

rc=0
for v in develop trial release; do
  out="$(resolve "$v")" || { echo "FAIL: env.js 无法在 envVersion=$v 下解析" >&2; rc=1; continue; }
  api="${out%%|*}"; demo="${out##*|}"
  case "$v" in
    develop)
      [ "$api" = "" ] && [ "$demo" = "true" ] \
        || { echo "FAIL: develop 必须为 demo 且无接口地址，实际 apiBaseUrl='$api' demoMode=$demo" >&2; rc=1; }
      ;;
    release|trial)
      case "$api" in
        https://*) : ;;
        *) echo "FAIL: $v 的 apiBaseUrl 必须是 HTTPS，实际 '$api'" >&2; rc=1 ;;
      esac
      [ "$demo" = "false" ] \
        || { echo "FAIL: $v 必须关闭 demoMode，实际 '$demo'" >&2; rc=1; }
      ;;
  esac
done
[ "$rc" -eq 0 ] || exit 1

# ---- 4) 凭据断言（fail-closed：扫描器不可用即失败，不得静默跳过）----
if command -v rg >/dev/null 2>&1; then
  CRED_RE='(password|secret|token|access_key|private_key)[[:space:]]*[:=]'
  for f in "$DEFAULT" "$PROFILE" "$PROD_EXAMPLE"; do
    [ -f "$f" ] || continue
    if rg -n "$CRED_RE" "$f" >/dev/null 2>&1; then
      fail "$(basename "$f") must not contain credentials"
    fi
  done
elif command -v grep >/dev/null 2>&1; then
  CRED_RE='(password|secret|token|access_key|private_key)[[:space:]]*[:=]'
  for f in "$DEFAULT" "$PROFILE" "$PROD_EXAMPLE"; do
    [ -f "$f" ] || continue
    if grep -nE "$CRED_RE" "$f" >/dev/null 2>&1; then
      fail "$(basename "$f") must not contain credentials"
    fi
  done
else
  fail 'no scanner available (rg/grep) —— 拒绝静默跳过凭据断言'
fi

echo 'PASS: 小程序环境档案隔离正确（develop=demo 无地址 / trial+release=HTTPS / 无凭据）'
