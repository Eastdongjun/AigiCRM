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
# 【退出码守卫】见 exit-guard.sh: bash 3.2 在 set -e + EXIT 陷阱下会把未定义变量引起的中止报成退出码 0
cleanup_work() { rm -rf "$WORK"; }
. "$(cd "$(dirname "$0")" && pwd)/exit-guard.sh"
exit_guard_install cleanup_work
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

# ---- 3b) 本机覆盖档：develop 与 trial(体验版)采用其中的地址；release 永远不读 ----
# 【GL-15 2026-11-24】体验版也读本机覆盖档: 从配了覆盖档的机器上传的体验版指向测试环境, 用来在真机上验测试环境。
#   此前这里断言的是“覆盖档不得影响 trial”。现在分工: “release 不读覆盖档”由这里守;
#   “带着覆盖档不许传正式包”由 scripts/miniprogram/upload.mjs 的上传守卫守(test-miniprogram-upload-guard.js)。
printf "module.exports = { apiBaseUrl: 'http://127.0.0.1:8080' }\n" > "$WORK/env.local.js"
develop_out="$(resolve develop "$EMPTY_MODULE")" || { echo "FAIL: 配置本机覆盖档后 env.js 无法解析" >&2; rc=1; }
[ "$develop_out" = "http://127.0.0.1:8080|false" ] \
  || { echo "FAIL: 本机覆盖档应让 develop 指向该地址并关闭 demo，实际 '$develop_out'" >&2; rc=1; }
trial_out="$(resolve trial "$EMPTY_MODULE")" || { echo "FAIL: 配置本机覆盖档后 trial 无法解析" >&2; rc=1; }
[ "$trial_out" = "http://127.0.0.1:8080|false" ] \
  || { echo "FAIL: 本机覆盖档应让 trial(体验版)指向该地址并关闭 demo，实际 '$trial_out'" >&2; rc=1; }
hijack_out="$(resolve release "$EMPTY_MODULE")" || { echo "FAIL: 配置本机覆盖档后 release 无法解析" >&2; rc=1; }
case "${hijack_out%%|*}" in
  https://www.aigicrm.com) : ;;
  *) echo "FAIL: 本机覆盖档不得影响 release（必须仍是生产 HTTPS 地址），实际 '$hijack_out'" >&2; rc=1 ;;
esac

# ---- 3c) 覆盖档指向测试域名：体验版跟着走、不会悄悄回落到生产；没有覆盖档的体验版仍是生产 ----
printf "module.exports = { apiBaseUrl: 'https://test.example.invalid' }\n" > "$WORK/env.local.js"
trial_test="$(resolve trial "$EMPTY_MODULE")" || { echo "FAIL: 覆盖档指向测试域名时 trial 无法解析" >&2; rc=1; }
[ "$trial_test" = "https://test.example.invalid|false" ] \
  || { echo "FAIL: 覆盖档指向测试域名时体验版应指向该域名，实际 '$trial_test'" >&2; rc=1; }
release_test="$(resolve release "$EMPTY_MODULE")" || { echo "FAIL: 覆盖档指向测试域名时 release 无法解析" >&2; rc=1; }
[ "$release_test" = "https://www.aigicrm.com|false" ] \
  || { echo "FAIL: 覆盖档指向测试域名时 release 仍须是生产地址，实际 '$release_test'" >&2; rc=1; }
printf 'module.exports = {}\n' > "$WORK/env.local.js"
trial_plain="$(resolve trial "$EMPTY_MODULE")" || { echo "FAIL: 未配置覆盖档时 trial 无法解析" >&2; rc=1; }
[ "$trial_plain" = "https://www.aigicrm.com|false" ] \
  || { echo "FAIL: 未配置覆盖档的体验版应指向生产，实际 '$trial_plain'" >&2; rc=1; }

# ---- 3d) 覆盖档结构不对（没有可用的接口地址）：trial 回落到生产、develop 回落到 demo、release 不变，都不抛错 ----
for bad in 'module.exports = {}' 'module.exports = { demoMode: false }' "module.exports = { apiBaseUrl: '' }" \
           'module.exports = { apiBaseUrl: 123 }' 'module.exports = null' 'this is not valid javascript {'; do
  printf '%s\n' "$bad" > "$WORK/env.local.js"
  for v in develop trial release; do
    bad_out="$(resolve "$v" "$EMPTY_MODULE" 2>/dev/null)" || { echo "FAIL: 覆盖档结构不对时 $v 无法解析: $bad" >&2; rc=1; continue; }
    case "$v" in
      develop) want='|true' ;;
      trial|release) want='https://www.aigicrm.com|false' ;;
    esac
    [ "$bad_out" = "$want" ] \
      || { echo "FAIL: 覆盖档结构不对时 $v 应为 '$want'，实际 '$bad_out'（覆盖档: $bad）" >&2; rc=1; }
  done
done

# ---- 3e) 认不出的版本号：一律 demo，且不会捡起覆盖档里的地址（既有口径，体验版能读覆盖档之后更要守住） ----
# 说明: “读不到版本号”是另一回事——既有设计是按 develop 处理(没有覆盖档时落到 demo), 这里不改它。
printf "module.exports = { apiBaseUrl: 'https://test.example.invalid' }\n" > "$WORK/env.local.js"
odd_out="$(resolve beta "$EMPTY_MODULE")" || { echo "FAIL: envVersion=beta 时 env.js 无法解析" >&2; rc=1; }
[ "$odd_out" = "|true" ] \
  || { echo "FAIL: 认不出的版本号必须是 demo 且不采用覆盖档，实际 '$odd_out'" >&2; rc=1; }
printf 'module.exports = {}\n' > "$WORK/env.local.js"
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

EXIT_GUARD_DONE=1
echo 'PASS: 小程序环境档案隔离正确（默认档 develop=demo / trial+release=HTTPS / 本机覆盖档供 develop 与 trial 使用、release 不读 / 覆盖档结构不对时安全回落 / 认不出的版本号一律 demo / 无凭据）'
