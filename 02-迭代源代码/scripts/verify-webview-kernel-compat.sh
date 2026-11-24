#!/bin/bash
# 【待建A·转正立项 2026-11-22】web-view 内核兼容门禁——K-ENG-FE-001(PDF 崩溃)事故沉淀。
#
# 事故复盘: pdfjs-dist 6.5.438 依赖 Map.getOrInsertComputed/Uint8Array.toHex 等新 API,
# Chrome 全绿、微信 web-view 内核(140-144)缺失即崩——"现代浏览器测不出内核兼容"。
# 本门禁把"最低支持内核"变成可回归断言, 三级:
#   [1] 产物级(默认): dist 三类 chunk 存在 + 双线程注入证据 + pdf bundle 含消费点(getOrInsertComputed)。
#       · 主线程: polyfill 独有字面量 'called on incompatible receiver' 落在 reader chunk 自己里, 或落在它
#         静态依赖(直接或间接, 含 re-export)的某个 chunk 里。静态 import 先于本模块求值, 所以只要在这条
#         依赖链上, polyfill 一定先于 reader 里的 pdfjs 动态 import 生效。
#         【2026-11-24 修正】此前只认“落在 reader chunk 自己里”。自 0a578b9b 起 PDF 挂载质检
#         (pdf-inspect.js)也引用 polyfill, 打包器把它拆进了几个页面共用的 chunk, reader chunk 改为静态
#         import 它 —— 功能完好(运行级验证: 删掉 8 个新 API 后, 单独加载 reader chunk / 小程序阅读页 /
#         PC 阅读页均自愈), 但这一步自那个提交起一直误报“主线程注入丢失”。
#       · worker: 名字匹配 pdfjs-worker-shim.worker-*.js 且【没有被任何 chunk 静态 import】的文件
#         (worker 只以 URL 被 new Worker 加载)。名字像 worker 的主线程 chunk 不算; 也不用 ls|head -1 挑文件
#         —— 两份文件同前缀时挑到哪一份取决于系统的排序规则。
#   [2] 运行级(AIGI_KERNEL_RUNTIME=1, 需 @playwright/test + vite preview):
#       chromium 启动前删 8 个新 API 模拟微信内核 → 先断言"确实删掉了"(负例半证) → import reader chunk 后
#       主线程已自愈 → 每个真正的 worker 文件各以模块身份执行一次 → polyfill 功能正确。
#   [3] --self-test 负例自证(门禁 H10): 对着真实 dist 的副本做三种剥离, 都必须红(见 --self-test 分支)。
# 接入: gate.sh [3b](构建后默认跑 [1]); 行为测试: scripts/test-verify-webview-kernel-compat.sh(隔离夹具)。
#   bash scripts/verify-webview-kernel-compat.sh [--self-test]
#   AIGI_KERNEL_RUNTIME=1 bash scripts/verify-webview-kernel-compat.sh
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST="${AIGI_DIST_DIR:-$SCRIPT_DIR/../aigicrm-ui/dist}"
POLY_LIT='called on incompatible receiver'   # polyfill 独有字面量, 压缩不消失
FAILS=0
ok()  { echo "PASS: $1"; }
bad() { echo "FAIL: $1"; FAILS=$((FAILS+1)); }

# chunk 的静态依赖: ESM 静态 import / export-from 语句里引用的 ./x.js, 每行一个。
# 动态 import(有括号)与依赖表/URL 字符串里的文件名不算 —— 它们不保证求值先后。
# 语句可能跨行(未压缩产物), 先把换行并成分号再匹配。
chunk_edges() {  # $1=assets 目录 $2=文件名
  local src
  src="$(LC_ALL=C tr '\n' ';' < "$1/$2" 2>/dev/null)" || return 0
  [ -n "$src" ] || return 0
  {
    printf '%s' "$src" | grep -oE '(from|import)[[:space:]]*"\./[^"]+\.js"' \
      | sed -E 's#^(from|import)[[:space:]]*"\./(.*)"$#\2#' || true
    printf '%s' "$src" | grep -oE "(from|import)[[:space:]]*'\./[^']+\.js'" \
      | sed -E "s#^(from|import)[[:space:]]*'\\./(.*)'\$#\\2#" || true
  } | sort -u
}

# $2 的静态依赖闭包(含自己), 每行一个。系统 bash 是 3.2, 不用关联数组。
chunk_closure() {  # $1=assets 目录 $2=起点文件名
  local a="$1" cur edge todo seen=""
  todo="$2"$'\n'
  while [ -n "$todo" ]; do
    cur="${todo%%$'\n'*}"
    case "$todo" in *$'\n'*) todo="${todo#*$'\n'}" ;; *) todo="" ;; esac
    case "$seen" in *"|$cur|"*) continue ;; esac
    seen="$seen$cur|"
    echo "$cur"
    for edge in $(chunk_edges "$a" "$cur"); do todo="${todo}${edge}"$'\n'; done
  done
}

lit_count() {  # $1=文件 → 里面 $2 出现的行数(文件不存在按 0)
  local n
  n="$(grep -c "$2" "$1" 2>/dev/null || true)"
  echo "${n:-0}"
}

# 识别三类 chunk。PDF_NAME / RD_NAME / WORKER_NAMES(空格分隔, 可能有多个 worker 文件)
analyze_dist() {  # $1=dist 目录
  local a="$1/assets" f imported
  PDF_NAME=""; RD_NAME=""; WORKER_NAMES=""
  f="$(ls "$a"/pdf-*.js 2>/dev/null | head -1)"; [ -n "$f" ] && PDF_NAME="$(basename "$f")"
  f="$(ls "$a"/useFabushiPdfReader-*.js 2>/dev/null | head -1)"; [ -n "$f" ] && RD_NAME="$(basename "$f")"
  imported="$(for f in "$a"/*.js; do [ -f "$f" ] && chunk_edges "$a" "$(basename "$f")"; done | sort -u)"
  for f in "$a"/pdfjs-worker-shim.worker-*.js; do
    [ -f "$f" ] || continue
    f="$(basename "$f")"
    # 被某个 chunk 静态 import 的是主线程 chunk(哪怕名字像 worker), 不是 worker 文件
    grep -qxF -- "$f" <<< "$imported" && continue
    WORKER_NAMES="$WORKER_NAMES $f"
  done
}

# 文本判断不走管道: `检查函数 | grep -q` 里 grep 一命中就退出, 上游再写会被 SIGPIPE 掐断, 开着 pipefail 时
# 整条管道的状态是 141, if 判断随之失真(本脚本的自证前提检查犯过这个错)。
has_text() { case "$1" in *"$2"*) return 0 ;; esac; return 1; }
has_fail_line() { case "$1" in FAIL:*|*$'\n'FAIL:*) return 0 ;; esac; return 1; }

artifact_check() {  # $1=dist 目录; 失败时 FAILS 累加
  local d="$1" f n hit="" hit_n=0
  [ -d "$d/assets" ] || { bad "产物级: $d/assets 不存在(先 npm run build)"; return; }
  analyze_dist "$d"
  [ -n "$PDF_NAME" ] || bad "产物级: 缺 pdf bundle(pdf-*.js)"
  [ -n "$RD_NAME" ] || bad "产物级: 缺 reader chunk(useFabushiPdfReader-*.js)"
  [ -n "$WORKER_NAMES" ] || bad "产物级: 缺 worker chunk(pdfjs-worker-shim.worker-*.js 里没有被静态 import 的那一个)"
  if [ -n "$RD_NAME" ]; then
    # 主线程注入: polyfill 在 reader chunk 自己里, 或在它静态依赖闭包里的某个 chunk 里
    for f in $(chunk_closure "$d/assets" "$RD_NAME"); do
      n="$(lit_count "$d/assets/$f" "$POLY_LIT")"
      if [ "$n" -ge 1 ]; then hit="$f"; hit_n="$n"; break; fi
    done
    if [ -n "$hit" ]; then
      ok "产物级: 主线程 polyfill 在($hit:x$hit_n; reader chunk 自身或其静态依赖)"
    else
      bad "产物级: reader chunk 及其静态依赖里都没有 polyfill 字面量——主线程注入丢失"
    fi
  fi
  if [ -n "$WORKER_NAMES" ]; then
    hit=""; hit_n=0
    for f in $WORKER_NAMES; do
      n="$(lit_count "$d/assets/$f" "$POLY_LIT")"
      if [ "$n" -ge 1 ]; then hit="$f"; hit_n="$n"; break; fi
    done
    if [ -n "$hit" ]; then
      ok "产物级: worker chunk 含 polyfill($hit:x$hit_n)"
    else
      bad "产物级: worker chunk 无 polyfill 字面量——worker 线程注入丢失"
    fi
  fi
  if [ -n "$PDF_NAME" ]; then
    n="$(lit_count "$d/assets/$PDF_NAME" 'getOrInsertComputed')"
    if [ "$n" -ge 1 ]; then
      ok "产物级: pdf bundle 含新 API 消费点(x$n, 兼容依赖成立)"
    else
      bad "产物级: pdf bundle 无 getOrInsertComputed——pdfjs 已升级/降级, 本门禁口径需复核"
    fi
  fi
}

runtime_check() {  # 运行级: 内核模拟 + import 产物 + 功能断言
  local d rd wks
  d="$(cd "$DIST" && pwd)"
  analyze_dist "$d"
  rd="$RD_NAME"; wks="$WORKER_NAMES"
  [ -n "$rd" ] && [ -n "$wks" ] || { bad "运行级: 缺 reader/worker chunk"; return; }
  local port="${AIGI_PREVIEW_PORT:-4999}"
  # 端口上已经有监听就拒绝运行: 不拿来历不明的服务当被测对象(它可能在服务另一份产物)
  if [ -n "$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null)" ]; then
    bad "运行级: 端口 $port 已被占用 —— 换 AIGI_PREVIEW_PORT, 或先结束占用它的进程"; return
  fi
  (cd "$SCRIPT_DIR/../aigicrm-ui" && npx vite preview --port "$port" --strictPort >/tmp/kernel-preview.log 2>&1) &
  local PV=$!
  # 收尾: 结束这个端口上的监听进程(启动前已确认端口是空的, 所以监听者只能是本次起的 preview)。
  # 只 kill $PV 结束的是外层子壳, node 监听进程会留下来占着端口 —— 修正前就是这样, 下一次运行会撞上它。
  stop_preview() {
    local pid
    for pid in $(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null); do kill "$pid" 2>/dev/null; done
    kill "$PV" 2>/dev/null
  }
  trap stop_preview RETURN
  for _ in $(seq 1 30); do curl -s -o /dev/null "http://localhost:$port/" && break; sleep 1; done
  curl -s -o /dev/null "http://localhost:$port/" || { bad "运行级: vite preview 未就绪"; return; }
  (cd "$SCRIPT_DIR/../aigicrm-ui" && RD="$rd" WKS="$wks" PORT="$port" node - <<'NODE'
const { chromium } = require('@playwright/test');
(async () => {
  const workers = process.env.WKS.split(' ').filter(Boolean);
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
  const r = await page.evaluate(async ([rd, workers]) => {
    const present = () => {
      const out = [];
      if (Map.prototype.getOrInsert) out.push('Map.getOrInsert 未删净');
      if (Map.prototype.getOrInsertComputed) out.push('Map.getOrInsertComputed 未删净');
      if (WeakMap.prototype.getOrInsertComputed) out.push('WeakMap.getOrInsertComputed 未删净');
      if (Uint8Array.prototype.toHex) out.push('toHex 未删净');
      if (Uint8Array.fromBase64) out.push('fromBase64 未删净');
      return out;
    };
    const missing = () => {
      const out = [];
      if (!Map.prototype.getOrInsert) out.push('Map.getOrInsert');
      if (!Map.prototype.getOrInsertComputed) out.push('Map.getOrInsertComputed');
      if (!WeakMap.prototype.getOrInsert) out.push('WeakMap.getOrInsert');
      if (!WeakMap.prototype.getOrInsertComputed) out.push('WeakMap.getOrInsertComputed');
      if (!Uint8Array.prototype.toHex) out.push('Uint8Array.toHex');
      if (!Uint8Array.prototype.toBase64) out.push('Uint8Array.toBase64');
      if (!Uint8Array.fromBase64) out.push('Uint8Array.fromBase64');
      if (!Uint8Array.fromHex) out.push('Uint8Array.fromHex');
      return out;
    };
    const before = present();
    await import('/assets/' + rd);   // 主线程注入: reader chunk 及其全部静态依赖
    const mainMissing = missing();
    for (const w of workers) {       // 每个真正的 worker 文件各以模块身份执行一次
      await import('/assets/' + w);
    }
    const errs = [];
    const m = new Map();
    if (m.getOrInsertComputed('k', () => 42) !== 42 || m.getOrInsert('x', 7) !== 7) errs.push('Map upsert 功能错');
    const w = new WeakMap(); const o = {};
    if (w.getOrInsertComputed(o, () => 'w') !== 'w') errs.push('WeakMap upsert 功能错');
    if (new Uint8Array([1, 2, 255]).toHex() !== '0102ff') errs.push('toHex 功能错');
    if (new Uint8Array([72, 105]).toBase64() !== 'SGk=') errs.push('toBase64 功能错');
    if (!Uint8Array.fromBase64 || Uint8Array.fromBase64('SGk=')[1] !== 105) errs.push('fromBase64 功能错');
    if (!Uint8Array.fromHex || Uint8Array.fromHex('0102ff').length !== 3) errs.push('fromHex 功能错');
    return { before, mainMissing, errs };
  }, [process.env.RD, workers]);
  await browser.close();
  if (r.before.length) { console.log('FAIL: 运行级·内核模拟失效(浏览器原生已带新 API?): ' + r.before.join('; ')); process.exit(1); }
  if (r.mainMissing.length) { console.log('FAIL: 运行级·主线程注入: import reader chunk 后仍缺 ' + r.mainMissing.join(', ')); process.exit(1); }
  if (r.errs.length)  { console.log('FAIL: 运行级·polyfill 功能断言: ' + r.errs.join('; ')); process.exit(1); }
  console.log(`PASS: 运行级·内核模拟(8 API 删除→import reader chunk 后主线程自愈→${workers.length} 个 worker 文件各自执行→全部自愈且功能正确)`);
})().catch(e => { console.log('FAIL: 运行级异常 ' + e.message); process.exit(1); });
NODE
  ) || FAILS=$((FAILS+1))
}

if [ "${1:-}" = "--self-test" ]; then
  # 负例自证(H10): 对着真实 dist 的三个副本做三种剥离, 都必须红 —— 证明检查器抓得住每一种“注入丢失”。
  NEG=/tmp/aigi-kernel-neg-dist
  sf_ok=1
  strip_except() { # 剥掉 polyfill 字面量; 参数是不剥的文件名
    local f keep
    for f in "$NEG"/assets/*.js; do
      [ -f "$f" ] || continue
      for keep in "$@"; do [ "$(basename "$f")" = "$keep" ] && continue 2; done
      perl -pi -e 's/called on incompatible receiver//g' "$f"
    done
  }
  # 0) 前提: 被检查的 dist 本身是绿的。它自己就红的话, “剥离后变红”说明不了什么。
  rm -rf "$NEG" && mkdir -p "$NEG/assets"
  cp "$DIST"/assets/*.js "$NEG/assets/" 2>/dev/null
  out="$(artifact_check "$NEG")"
  if has_fail_line "$out"; then
    echo "FAIL: --self-test 前提不成立——被检查的 dist 本身就不通过(先看产物级结果), 自证无从谈起"; exit 1
  fi
  # 1) 全部剥离: polyfill 与消费点都拿掉 —— 主线程、worker、消费点各报一处
  for f in "$NEG"/assets/*.js; do perl -pi -e 's/called on incompatible receiver//g; s/getOrInsertComputed//g' "$f"; done
  out="$(artifact_check "$NEG")"
  if has_text "$out" '主线程注入丢失' && has_text "$out" 'worker 线程注入丢失' && has_text "$out" '本门禁口径需复核'; then
    echo "PASS: --self-test 负例一——全部剥离的假 dist 被判红(主线程、worker、消费点各报一处)"
  else
    echo "FAIL: --self-test 检查器失效——负例一(全部剥离)没有被完整判红: $(printf '%s' "$out" | tr '\n' ' ')"; sf_ok=0
  fi
  # 2) 只剥主线程那一份(worker 那份留着): 必须红, 且只报主线程 —— 闭包计算不得把 worker 文件算成主线程注入
  rm -rf "$NEG" && mkdir -p "$NEG/assets"
  cp "$DIST"/assets/*.js "$NEG/assets/" 2>/dev/null
  analyze_dist "$NEG"
  strip_except $WORKER_NAMES
  out="$(artifact_check "$NEG")"
  if has_text "$out" '主线程注入丢失' && ! has_text "$out" 'worker 线程注入丢失'; then
    echo "PASS: --self-test 负例二——只剥主线程那一份(留着 worker 那份)被判红, 且只报主线程"
  else
    echo "FAIL: --self-test 检查器失效——负例二(只剥主线程那一份)没有被正确判红: $(printf '%s' "$out" | tr '\n' ' ')"; sf_ok=0
  fi
  # 3) 在 2) 的基础上, 往一个 reader 依赖不到的 chunk 里放回 polyfill: 仍然必须红
  printf '%s\n' "// unrelated chunk: const poly = () => { throw new TypeError('Method x $POLY_LIT') }" > "$NEG/assets/unrelated-zz.js"
  out="$(artifact_check "$NEG")"
  if has_text "$out" '主线程注入丢失'; then
    echo "PASS: --self-test 负例三——polyfill 只剩在一个无关 chunk 里, 仍被判红"
  else
    echo "FAIL: --self-test 检查器失效——负例三(无关 chunk 里的 polyfill)被当成了主线程注入: $(printf '%s' "$out" | tr '\n' ' ')"; sf_ok=0
  fi
  rm -rf "$NEG"
  if [ "$sf_ok" -eq 1 ]; then echo "PASS: --self-test 全部成立(三个负例都被正确判红)"; exit 0; fi
  exit 1
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
