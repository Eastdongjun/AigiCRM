#!/usr/bin/env bash
# 【全系统测试矩阵·13 兼容域】web-view 内核/浏览器兼容证据编排 2026-11-22
# 口径: 内核兼容门禁已常态化接入 gate.sh [3b](K-ENG-FE-001 事故沉淀);
#      本脚本编排三级证据 + 领域层通用工具参数化实跑 + 基线契约与残余登记。
# 断言:
#   CMP-01 产物级: 注入位双线程 + 消费位 chunk(调 verify-webview-kernel-compat.sh)
#   CMP-02 运行级: chromium 删 8 API 内核模拟 → import 产物 → 自愈(AIGI_KERNEL_RUNTIME=1)
#   CMP-03 负例自证: --self-test 剥离 polyfill 假 dist 必须红
#   CMP-04 领域层通用工具参数化实跑(05-领域 test-system, 注入位/消费位分离口径)
#   CMP-05 最低内核基线契约: v140 起支持(微信内核 140-144 覆盖), 契约登记注记
#   CMP-06/07 真机与 PC 矩阵: 已验记录 + 未覆盖注记(真机扩展/Safari/Firefox)
# 用法: bash scripts/verify-compat-domain.sh  (运行级需 aigicrm-ui devDeps + preview 端口空闲)
set -uo pipefail
cd "$(dirname "$0")/.."
PASS=0; FAIL=0; INFO=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }
info() { echo "INFO: $1"; INFO=$((INFO+1)); }

echo "=== [CMP-01/02/03] 项目内核兼容门禁三级 ==="
if bash scripts/verify-webview-kernel-compat.sh > /tmp/cmp-proj.log 2>&1; then
  ok "CMP-01 产物级(注入位双线程+消费位)"; grep '^PASS' /tmp/cmp-proj.log | sed 's/^/    /'
else bad "CMP-01 产物级 FAIL"; tail -5 /tmp/cmp-proj.log; fi

if AIGI_KERNEL_RUNTIME=1 bash scripts/verify-webview-kernel-compat.sh > /tmp/cmp-rt.log 2>&1; then
  ok "CMP-02 运行级内核模拟(删 8 API→import→自愈)"; grep '^PASS.*运行级' /tmp/cmp-rt.log | sed 's/^/    /'
else bad "CMP-02 运行级 FAIL"; tail -6 /tmp/cmp-rt.log; fi

if bash scripts/verify-webview-kernel-compat.sh --self-test > /tmp/cmp-neg.log 2>&1; then
  ok "CMP-03 负例自证(剥离 polyfill 假 dist 判红)"
else bad "CMP-03 负例自证失效"; tail -3 /tmp/cmp-neg.log; fi

echo "=== [CMP-04] 领域层通用工具参数化实跑(注入位/消费位分离) ==="
DOM=/Users/dongjun/东来/Agent/05-领域/01-工程/06-测试/01-技能/test-system/scripts/verify-kernel-compat.sh
if [ -f "$DOM" ]; then
  if KERNEL_DIST=aigicrm-ui/dist POLY_LIT='called on incompatible receiver' \
     POLY_CHUNK_GLOBS='pdfjs-worker-shim.worker-*.js useFabushiPdfReader-*.js' \
     MARK_CHUNK_GLOBS='pdf-*.js' MARKERS='getOrInsertComputed fromBase64' \
     bash "$DOM" > /tmp/cmp-dom.log 2>&1; then
    ok "CMP-04 领域工具(跨项目复用形态)实跑 PASS"; grep '^PASS' /tmp/cmp-dom.log | sed 's/^/    /'
  else bad "CMP-04 领域工具 FAIL"; tail -5 /tmp/cmp-dom.log; fi
  KERNEL_DIST=aigicrm-ui/dist POLY_LIT='called on incompatible receiver' \
    POLY_CHUNK_GLOBS='pdfjs-worker-shim.worker-*.js' bash "$DOM" --self-test >/dev/null 2>&1 \
    && ok "CMP-04 领域工具负例自证" || bad "CMP-04 领域工具负例自证失效"
else
  bad "CMP-04 领域工具缺失: $DOM"
fi

echo "=== [CMP-05/06/07] 契约登记与矩阵注记 ==="
info "CMP-05 最低内核基线=微信内核 v140(K-ENG-FE-001 定档): polyfill 覆盖 Map/WeakMap upsert + Uint8Array toHex/fromBase64/toBase64/fromHex 家族; <140 内核不支持(已登记, 如有需求另立专项); fromBase64 签名 PDF 路径维持登记不修"
info "CMP-06 真机矩阵: PDF 阅读+翻页已于 2026-11-22 用户真机回归通过(内核 140-144 区间); 机型扩展矩阵属手工项(低端安卓/iOS 抽样), 挂矩阵待办"
info "CMP-07 PC 浏览器: chromium 经 CMP-02 运行级+PERF-08 页面指标覆盖; Safari/Firefox 未测(内部 CRM 以 chromium 系为主), 按需扩展"

echo "==> 兼容域回归: PASS=$PASS FAIL=$FAIL INFO=$INFO"
[ "$FAIL" = "0" ]