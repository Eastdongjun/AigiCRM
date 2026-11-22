#!/bin/bash
# 【待建A·转正立项 2026-11-22】web-view 内核兼容门禁——K-ENG-FE-001(PDF 崩溃)事故沉淀。
#
# 事故复盘: pdfjs-dist 6.5.438 依赖 Map.getOrInsertComputed/Uint8Array.toHex 等新 API,
# Chrome 全绿、微信 web-view 内核(140-144)缺失即崩——"现代浏览器测不出内核兼容"。
# 本门禁把"最低支持内核"变成可回归断言, 三级:
#   [1] 产物级(默认): dist 三类 chunk 存在 + 双线程注入证据(polyfill 独有字面量
#       'called on incompatible receiver' 在 worker 与主线程 reader chunk 各≥1)
#       + pdf bundle 含消费点(getOrInsertComputed)。
#   [2] 运行级(AIGI_KERNEL_RUNTIME=1, 需 @playwright/test + vite preview):
#       chromium 启动前删 8 个新 API 模拟微信内核 → 先断言"确实删掉了"(负例半证) →
#       import 线上形态 chunk → 断言 polyfill 自愈恢复全部 API 且功能正确。
#   [3] --self-test 负例自证(门禁 H10): 把剥离 polyfill 的 /tmp 假 dist 喂给 [1] 必须红。
# 接入: gate.sh [3b](构建后默认跑 [1]); 用法:
#   bash scripts/verify-webview-kernel-compat.sh [--self-test]
#   AIGI_KERNEL_RUNTIME=1 bash scripts/verify-webview-kernel-compat.sh
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST="${AIGI_DIST_DIR:-$SCRIPT_DIR/../aigicrm-ui/dist}"
POLY_LIT='called on incompatible receiver'   # polyfill 独有字面量, 压缩不消失
FAILS=0
ok()  { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS+1)); }

artifact_check() {  # $1=dist 目录; 失败时 FAILS 累加
  local d="$1"
  [ -d "$d/assets" ] || { bad "产物级: $d/assets 不存在(先 npm run build)"; return; }
  local wk pdf rd
  wk=$(ls "$d/assets"/pdfjs-worker-shim.worker-*.js 2>/dev/null | head -1)
  pdf=$(ls "$d/assets"/pdf-*.js 2>/dev/null | head -1)
  rd=$(ls "$d/assets"/useFabushiPdfReader-*.js 2>/dev/null | head -1)
  [ -n "$wk" ] || bad "产物级: 缺 worker chunk(pdfjs-worker-shim.worker-*.js)"
  [ -n "$pdf" ] || bad "产物级: 缺 pdf bundle(pdf-*.js)"
  [ -n "$rd" ] || bad "产物级: 缺 reader chunk(useFabushiPdfReader-*.js)"
  if [ -n "$wk" ] && [ -n "$rd" ]; then
    # 双线程注入证据: polyfill 字面量必须同时落在 worker 与主线程 chunk(各打包一次)
    local n_wk n_rd
    n_wk=$(grep -c "$POLY_LIT" "$wk" || true)
    n_rd=$(grep -c "$POLY_LIT" "$rd" || true)
    [ "${n_wk:-0}" -ge 1 ] && ok "产物级: worker chunk 含 polyfill($wk:x$n_wk)" || bad "产物级: worker chunk 无 polyfill 字面量——worker 线程注入丢失"
    [ "${n_rd:-0}" -ge 1 ] && ok "产物级: 主线程 reader chunk 含 polyfill($rd:x$n_rd)" || bad "产物级: reader chunk 无 polyfill 字面量——主线程注入丢失"
  fi
  if [ -n "$pdf" ]; then
    local n_pdf
    n_pdf=$(grep -c 'getOrInsertComputed' "$pdf" || true)
    [ "${n_pdf:-0}" -ge 1 ] && ok "产物级: pdf bundle 含新 API 消费点(x$n_pdf, 兼容依赖成立)" || bad "产物级: pdf bundle 无 getOrInsertComputed——pdfjs 已升级/降级, 本门禁口径需复核"
  fi
}

runtime_check() {  # 运行级: 内核模拟 + import 产物 + 功能断言
  local d
  d="$(cd "$DIST" && pwd)"
  local rd wk
  rd=$(ls "$d/assets"/useFabushiPdfReader-*.js 2>/dev/null | head -1 | xargs basename)
  wk=$(ls "$d/assets"/pdfjs-worker-shim.worker-*.js 2>/dev/null | head -1 | xargs basename)
  [ -n "$rd" ] && [ -n "$wk" ] || { bad "运行级: 缺 reader/worker chunk"; return; }
  local port="${AIGI_PREVIEW_PORT:-4999}"
  (cd "$SCRIPT_DIR/../aigicrm-ui" && npx vite preview --port "$port" --strictPort >/tmp/kernel-preview.log 2>&1) &
  local PV=$!
  trap 'kill $PV 2>/dev/null' RETURN
  for _ in $(seq 1 30); do curl -s -o /dev/null "http://localhost:$port/" && break; sleep 1; done
  curl -s -o /dev/null "http://localhost:$port/" || { bad "运行级: vite preview 未就绪"; return; }
  (cd "$SCRIPT_DIR/../aigicrm-ui" && RD="$rd" WK="$wk" PORT="$port" node - <<'NODE'
const { chromium } = require('@playwright/test');
const DELETED = [
  ['Map.prototype.getOrInsert'], ['Map.prototype.getOrInsertComputed'],
  ['WeakMap.prototype.getOrInsert'], ['WeakMap.prototype.getOrInsertComputed'],
  ['Uint8Array.prototype.toHex'], ['Uint8Array.prototype.toBase64'],
  ['Uint8Array.fromBase64(静态)'], ['Uint8Array.fromHex(静态)'],
];
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.addInitScript(() => {
    for (const p of [Map.prototype, WeakMap.prototype]) {
      delete p.getOrInsert; delete p.getOrInsertComputed;
    }
    delete Uint8Array.prototype.toHex; delete Uint8Array.prototype.toBase64;
    delete Uint8Array.fromBase64; delete Uint8Array.fromHex;
  });
  await page.goto(`http://localhost:${process.env.PORT}/`, { waitUntil: 'domcontentloaded' });
  const r = await page.evaluate(async ([rd, wk]) => {
    const absent = [];
    if (Map.prototype.getOrInsert) absent.push('Map.getOrInsert 未删净');
    if (Map.prototype.getOrInsertComputed) absent.push('Map.getOrInsertComputed 未删净');
    if (WeakMap.prototype.getOrInsertComputed) absent.push('WeakMap.getOrInsertComputed 未删净');
    if (Uint8Array.prototype.toHex) absent.push('toHex 未删净');
    if (Uint8Array.fromBase64) absent.push('fromBase64 未删净');
    await import('/assets/' + rd);   // 主线程注入
    await import('/assets/' + wk);   // worker chunk 以 module 身份执行(注入 polyfill)
    const errs = [];
    const m = new Map();
    if (m.getOrInsertComputed('k', () => 42) !== 42 || m.getOrInsert('x', 7) !== 7) errs.push('Map upsert 功能错');
    const w = new WeakMap(); const o = {};
    if (w.getOrInsertComputed(o, () => 'w') !== 'w') errs.push('WeakMap upsert 功能错');
    if (new Uint8Array([1, 2, 255]).toHex() !== '0102ff') errs.push('toHex 功能错');
    if (new Uint8Array([72, 105]).toBase64() !== 'SGk=') errs.push('toBase64 功能错');
    if (!Uint8Array.fromBase64 || Uint8Array.fromBase64('SGk=')[1] !== 105) errs.push('fromBase64 功能错');
    if (!Uint8Array.fromHex || Uint8Array.fromHex('0102ff').length !== 3) errs.push('fromHex 功能错');
    return { absent, errs };
  }, [process.env.RD, process.env.WK]);
  await browser.close();
  if (r.absent.length) { console.log('FAIL: 运行级·内核模拟失效(浏览器原生已带新 API?): ' + r.absent.join(';')); process.exit(1); }
  if (r.errs.length)  { console.log('FAIL: 运行级·polyfill 功能断言: ' + r.errs.join(';')); process.exit(1); }
  console.log('PASS: 运行级·内核模拟(8 API 删除→import 产物→全部自愈且功能正确)');
})().catch(e => { console.log('FAIL: 运行级异常 ' + e.message); process.exit(1); });
NODE
  ) || FAILS=$((FAILS+1))
}

if [ "${1:-}" = "--self-test" ]; then
  # 负例自证(H10): 剥离 polyfill 的假 dist, 产物级检查必须红——证明检查器抓得住"注入丢失"
  NEG=/tmp/aigi-kernel-neg-dist
  rm -rf "$NEG" && mkdir -p "$NEG/assets"
  cp "$DIST"/assets/*.js "$NEG/assets/" 2>/dev/null
  for f in "$NEG"/assets/*.js; do perl -pi -e 's/called on incompatible receiver//g; s/getOrInsertComputed//g' "$f"; done
  BEFORE=$FAILS
  artifact_check "$NEG"
  if [ "$FAILS" -gt "$BEFORE" ]; then
    echo "PASS: --self-test 负例自证——剥离 polyfill 的假 dist 被正确判红(检查器有效)"
    exit 0
  else
    echo "FAIL: --self-test 负例自证——假 dist 竟然绿了, 检查器失效!"
    exit 1
  fi
fi

echo "=== [1] web-view 内核兼容·产物级 ==="
artifact_check "$DIST"
if [ -n "${AIGI_KERNEL_RUNTIME:-}" ]; then
  echo "=== [2] web-view 内核兼容·运行级(内核模拟) ==="
  runtime_check
else
  echo "INFO: 运行级未跑(AIGI_KERNEL_RUNTIME=1 开启; 依赖 @playwright/test)"
fi
[ "$FAILS" = "0" ] && echo "==> 内核兼容门禁: PASS" || echo "==> 内核兼容门禁: FAIL($FAILS)"
[ "$FAILS" = "0" ]