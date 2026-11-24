#!/usr/bin/env bash
# verify-webview-kernel-compat.sh 的行为测试（2026-11-24，gate 预演查出产物级检查误报之后补）。
# 在临时目录里搭最小的 dist（每个场景几个 chunk 文件），只认退出码与输出里的结论：
#   · 主线程注入成立 = polyfill 落在 reader chunk 自己里，或落在它静态依赖（直接或间接，含 re-export）的 chunk 里，
#     哪怕那个 chunk 的文件名长得像 worker；动态 import 不算依赖。
#   · 主线程一份丢了（polyfill 只在 worker 文件里 / 只在 reader 依赖不到的 chunk 里 / 只有动态 import）、
#     worker 一份丢了、没有真正的 worker 文件、pdf bundle 不再消费新 API、缺 chunk、dist 不存在 —— 都必须报红。
#   · “真正的 worker 文件”按“没有被任何 chunk 静态 import”来认，不看文件名顺序
#     （两份文件同前缀时，ls 挑到哪一份取决于系统的排序规则）。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATE="$HERE/verify-webview-kernel-compat.sh"
BASE="${TMPDIR:-/tmp}"
WORK="$BASE/kernel-compat-test.$$"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

LIT='throw new TypeError("Method x called on incompatible receiver")'

new_dist() { rm -rf "$WORK/dist"; mkdir -p "$WORK/dist/assets"; }
put() { printf '%s\n' "$2" > "$WORK/dist/assets/$1"; }

# 每个场景共有的文件：入口（只在依赖表里提到别的 chunk，不是静态 import）、vue 核心、pdf bundle（消费新 API）
common() {
  put 'index-aaa.js' 'import{a}from"./vue-core-v1.js";const load=()=>import("./pdf-ppp.js");__vite__mapDeps=["assets/useFabushiPdfReader-rrr.js","assets/pdfjs-worker-shim.worker-AbCd.js"];'
  put 'vue-core-v1.js' 'export const a=1'
  put 'pdf-ppp.js' 'const m=new Map();m.getOrInsertComputed("k",()=>1);'
}
# 新布局：polyfill 在两个页面共用的 chunk 里（文件名像 worker），真正的 worker 是另一份文件
#   $1=共用 chunk 的名字  $2=真正的 worker 文件名  $3=真正的 worker 文件里有没有 polyfill(有/无)
shared_layout() {
  new_dist; common
  put 'useFabushiPdfReader-rrr.js' "import{t as i}from\"./$1\";import{a}from\"./index-aaa.js\";const useFabushiPdfReader=()=>i;"
  put "$1" "const S=\"/assets/$2\";const poly=()=>{$LIT};export{S as t};"
  if [ "$3" = "有" ]; then put "$2" "$LIT"; else put "$2" 'self.onmessage=()=>{}'; fi
  put 'FabushiReader-fff.js' "import{t}from\"./$1\";"
}

passed=0
failed=0
# $1=输出 $2=用 ¦ 分隔的词。has_all: 每个词都在输出里才算成功; lacks_all: 每个词都不在才算成功。
# (系统 bash 是 3.2: 空数组配 set -u 会报未定义变量, 所以不用数组)
#   词的切分只用 bash 的参数展开, 每个词末尾带换行再交给 read —— 不带换行的最后一行 while read 是读不进来的,
#   那样循环一次都不执行, 断言就成了空转通过(本测试的第一版就犯过这个错, 靠下面的匹配器自检拦住)。
split_words() { # $1=¦分隔的词 → 每行一个(空串不输出)
  local s="$1" w
  while [ -n "$s" ]; do
    w="${s%%¦*}"
    if [ "$w" = "$s" ]; then s=""; else s="${s#*¦}"; fi
    [ -n "$w" ] && printf '%s\n' "$w"
  done
}
has_all() {
  split_words "$2" | while IFS= read -r w; do
    if ! printf '%s' "$1" | /usr/bin/grep -qF -- "$w"; then exit 1; fi
    :
  done
}
lacks_all() {
  split_words "$2" | while IFS= read -r w; do
    if printf '%s' "$1" | /usr/bin/grep -qF -- "$w"; then exit 1; fi
    :
  done
}
# 匹配器自检: 四种情形都对才继续。匹配器不灵时, 后面每个场景的“通过”都可能是空转, 所以直接判失败。
matcher_ok=1
has_all '甲 乙' '甲¦乙' || matcher_ok=0
has_all '甲' '甲¦丙' && matcher_ok=0
lacks_all '甲' '丙¦丁' || matcher_ok=0
lacks_all '甲' '丙¦甲' && matcher_ok=0
[ "$matcher_ok" -eq 1 ] || { echo "FAIL 匹配器自检 —— 断言用的词匹配本身不灵, 本测试的结论不可信"; exit 1; }
echo "PASS 匹配器自检"
run_case() { # <标签> <期望退出码> <输出必须包含(¦分隔)> <输出不得包含(¦分隔)> [--self-test]
  local label="$1" expect="$2" must="$3" mustnot="$4" extra="${5:-}" out rc ok=1
  if [ -n "$extra" ]; then
    out="$(AIGI_DIST_DIR="$WORK/dist" bash "$GATE" "$extra" 2>&1)"; rc=$?
  else
    out="$(AIGI_DIST_DIR="$WORK/dist" bash "$GATE" 2>&1)"; rc=$?
  fi
  has_all "$out" "$must" || ok=0
  lacks_all "$out" "$mustnot" || ok=0
  if [ "$rc" -eq "$expect" ] && [ "$ok" -eq 1 ]; then
    passed=$((passed + 1)); echo "PASS $label"
  else
    failed=$((failed + 1)); echo "FAIL $label — 期望退出码 $expect, 输出含 [$must] 且不含 [$mustnot]; 实际退出码 $rc, 输出: $(printf '%s' "$out" | /usr/bin/sed -E 's|/[^ ]*/dist/assets/||g' | tr '\n' ' ' | cut -c1-420)"
  fi
}

new_dist; common
put 'useFabushiPdfReader-rrr.js' "import{a}from\"./index-aaa.js\";const poly=()=>{$LIT};"
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
run_case '① 旧布局：polyfill 在 reader chunk 自己里' 0 'useFabushiPdfReader-rrr.js:x1¦pdfjs-worker-shim.worker-Www1.js:x1¦内核兼容门禁: PASS' 'FAIL'

shared_layout 'pdfjs-worker-shim.worker-AbCd.js' 'pdfjs-worker-shim.worker-Zz99.js' 有
run_case '② 新布局：polyfill 在 reader chunk 静态依赖的共用 chunk 里，文件名还像 worker' 0 'pdfjs-worker-shim.worker-AbCd.js:x1¦pdfjs-worker-shim.worker-Zz99.js:x1¦内核兼容门禁: PASS' 'FAIL'

shared_layout 'pdfjs-worker-shim.worker-zzz9.js' 'pdfjs-worker-shim.worker-aaaa.js' 有
run_case '②b 同②，两份文件名字的先后换过来：主线程与 worker 各认各的，不看文件名顺序' 0 'pdfjs-worker-shim.worker-zzz9.js:x1¦pdfjs-worker-shim.worker-aaaa.js:x1¦内核兼容门禁: PASS' 'FAIL'

new_dist; common
put 'useFabushiPdfReader-rrr.js' 'import{m}from"./mid-mmm.js";import{a}from"./index-aaa.js";'
put 'mid-mmm.js' 'import{p}from"./poly-xxx.js";export const m=p'
put 'poly-xxx.js' "$LIT"
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
run_case '③ 间接依赖：polyfill 在 reader 的依赖的依赖里' 0 'poly-xxx.js:x1¦内核兼容门禁: PASS' 'FAIL'

new_dist; common
put 'useFabushiPdfReader-rrr.js' 'export{p}from"./poly-xxx.js";'
put 'poly-xxx.js' "$LIT"
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
run_case '④ 只以 re-export（export … from）依赖：也算静态依赖' 0 'poly-xxx.js:x1¦内核兼容门禁: PASS' 'FAIL'

new_dist; common
printf '%s\n' "import { a }" "  from './index-aaa.js'" "import { p }" "  from './poly-xxx.js'" "export const load = () => p()" > "$WORK/dist/assets/useFabushiPdfReader-rrr.js"
printf '%s\n' 'import { d }' '  from "./deep-dd.js"' 'export const p = d' > "$WORK/dist/assets/poly-xxx.js"
printf '%s\n' "$LIT" > "$WORK/dist/assets/deep-dd.js"
printf '%s\n' "$LIT" > "$WORK/dist/assets/pdfjs-worker-shim.worker-Www1.js"
run_case '⑤ 未压缩、单引号与带空格的双引号混用、语句跨行、三跳依赖：依赖照样认得出' 0 'deep-dd.js:x1¦内核兼容门禁: PASS' 'FAIL'

new_dist; common
put 'useFabushiPdfReader-rrr.js' 'import{a}from"./index-aaa.js";'
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
run_case '⑥ 主线程一份丢了（polyfill 只在真正的 worker 文件里）' 1 '主线程注入丢失' 'worker 线程注入丢失'

new_dist; common
put 'useFabushiPdfReader-rrr.js' 'import{a}from"./index-aaa.js";'
put 'book-bbb.js' "$LIT"
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
run_case '⑦ polyfill 只在 reader 依赖不到的 chunk 里：不算' 1 '主线程注入丢失' 'book-bbb.js¦worker 线程注入丢失'

new_dist; common
put 'useFabushiPdfReader-rrr.js' 'import{a}from"./index-aaa.js";const loadPoly=()=>import("./poly-xxx.js");'
put 'poly-xxx.js' "$LIT"
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
run_case '⑧ reader 对 polyfill 只有动态 import：不算（保证不了先于 pdfjs 生效）' 1 '主线程注入丢失' 'worker 线程注入丢失'

shared_layout 'pdfjs-worker-shim.worker-AbCd.js' 'pdfjs-worker-shim.worker-Zz99.js' 无
run_case '⑨ worker 一份丢了（主线程那份在文件名像 worker 的共用 chunk 里，不能拿来顶替）' 1 'worker 线程注入丢失' '主线程注入丢失'

shared_layout 'pdfjs-worker-shim.worker-zzz9.js' 'pdfjs-worker-shim.worker-aaaa.js' 无
run_case '⑨b 同⑨，文件名先后换过来：结论不变' 1 'worker 线程注入丢失' '主线程注入丢失'

new_dist; common
put 'useFabushiPdfReader-rrr.js' "import{t}from\"./pdfjs-worker-shim.worker-AbCd.js\";import{a}from\"./index-aaa.js\";"
put 'pdfjs-worker-shim.worker-AbCd.js' "$LIT"
run_case '⑩ 没有真正的 worker 文件（同名的那一份是被静态 import 的主线程 chunk）' 1 '缺 worker chunk' 'worker 线程注入丢失¦内核兼容门禁: PASS'

new_dist; common
put 'useFabushiPdfReader-rrr.js' "import{a}from\"./index-aaa.js\";const poly=()=>{$LIT};"
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
put 'pdf-ppp.js' 'const m=new Map();m.set("k",1);'
run_case '⑪ pdf bundle 不再消费新 API：口径要复核' 1 '本门禁口径需复核' '主线程注入丢失'

new_dist; common
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
run_case '⑫ 缺 reader chunk' 1 '缺 reader chunk' ''

new_dist; common
put 'useFabushiPdfReader-rrr.js' "import{a}from\"./index-aaa.js\";const poly=()=>{$LIT};"
put 'pdfjs-worker-shim.worker-Www1.js' "$LIT"
rm -f "$WORK/dist/assets/pdf-ppp.js"
run_case '⑬ 缺 pdf bundle' 1 '缺 pdf bundle' ''

rm -rf "$WORK/dist"
run_case '⑭ dist 目录不存在' 1 '不存在' ''

shared_layout 'pdfjs-worker-shim.worker-AbCd.js' 'pdfjs-worker-shim.worker-Zz99.js' 有
run_case '⑮ --self-test 对着新布局：三种剥离都必须被判红' 0 '负例一¦负例二¦负例三¦self-test 全部成立' 'FAIL: --self-test' --self-test

shared_layout 'pdfjs-worker-shim.worker-AbCd.js' 'pdfjs-worker-shim.worker-Zz99.js' 无
run_case '⑯ --self-test 对着一个本身就不通过的 dist：必须明说前提不成立，不能拿它自证' 1 '前提不成立' 'self-test 全部成立' --self-test

echo "通过 $passed 项, 未通过 $failed 项"
[ "$failed" -eq 0 ] || exit 1
