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
# 【2026-09-23 对齐 3e46baeb】模板已移至 docs/env-templates(DevTools 依赖分析 0 未使用), 断言语义不变
PROFILE="$ROOT/docs/env-templates/env.local.example.js"
DEFAULT="$MP/env.js"
PROD_EXAMPLE="$ROOT/docs/env-templates/env.prod.example.js"
LOCAL="$MP/env.local.js"
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

# ---- 3) 行为断言：在 wx 桩下真正解析 env.js ----
# 用**临时副本**解析，避免开发者本机的 config/env.local.js 影响断言（该文件已 gitignore）：
# 先放一个空覆盖档（等价于"未配置"），再放一个指向本机的覆盖档，分别断言两种语义。
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp "$DEFAULT" "$WORK/env.js"
printf 'module.exports = {}\n' > "$WORK/env.local.js"
EMPTY_MODULE="$WORK/env.js"

resolve() { # $1=envVersion $2=模块路径 → 输出 "apiBaseUrl|demoMode"
  node -e '
    const version = process.argv[1];
    global.wx = { getAccountInfoSync: () => ({ miniProgram: { envVersion: version } }) };
    delete require.cache[require.resolve(process.argv[2])];
    const m = require(process.argv[2]);
    process.stdout.write(String(m.apiBaseUrl) + "|" + String(m.demoMode));
  ' "$1" "$2"
}

rc=0
for v in develop trial release; do
  out="$(resolve "$v" "$EMPTY_MODULE")" || { echo "FAIL: env.js 无法在 envVersion=$v 下解析" >&2; rc=1; continue; }
  api="${out%%|*}"; demo="${out##*|}"
  case "$v" in
    develop)
      [ "$api" = "" ] && [ "$demo" = "true" ] \
        || { echo "FAIL: 未配置本机覆盖档时 develop 必须为 demo 且无接口地址，实际 apiBaseUrl='$api' demoMode=$demo" >&2; rc=1; }
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

# ---- 3b) 本机覆盖档：只影响 develop，且不得劫持 release/trial ----
printf "module.exports = { apiBaseUrl: 'http://127.0.0.1:8080' }\n" > "$WORK/env.local.js"
develop_out="$(resolve develop "$EMPTY_MODULE")" || { echo "FAIL: 配置本机覆盖档后 env.js 无法解析" >&2; rc=1; }
[ "$develop_out" = "http://127.0.0.1:8080|false" ] \
  || { echo "FAIL: 本机覆盖档应让 develop 指向该地址并关闭 demo，实际 '$develop_out'" >&2; rc=1; }
for v in trial release; do
  hijack_out="$(resolve "$v" "$EMPTY_MODULE")" || { echo "FAIL: 配置本机覆盖档后 $v 无法解析" >&2; rc=1; continue; }
  case "${hijack_out%%|*}" in
    https://www.aigicrm.com) : ;;
    *) echo "FAIL: 本机覆盖档不得影响 $v（必须仍是生产 HTTPS 地址），实际 '$hijack_out'" >&2; rc=1 ;;
  esac
done
[ "$rc" -eq 0 ] || exit 1

# ---- 4) 凭据断言（fail-closed：扫描器不可用即失败，不得静默跳过）----
CRED_RE='(password|secret|token|access_key|private_key)[[:space:]]*[:=]'
if command -v rg >/dev/null 2>&1; then
  scan_creds() { rg -n "$CRED_RE" "$1" >/dev/null 2>&1; }
elif command -v grep >/dev/null 2>&1; then
  scan_creds() { grep -nE "$CRED_RE" "$1" >/dev/null 2>&1; }
else
  fail 'no scanner available (rg/grep) —— 拒绝静默跳过凭据断言'
fi
for f in "$DEFAULT" "$PROFILE" "$PROD_EXAMPLE" "$LOCAL"; do
  [ -f "$f" ] || continue
  if scan_creds "$f"; then
    fail "$(basename "$f") must not contain credentials"
  fi
done

echo 'PASS: 小程序环境档案隔离正确（默认档 develop=demo / trial+release=HTTPS / 本机覆盖档只影响 develop / 无凭据）'
