#!/usr/bin/env bash
# check-fabushi-terminology.sh 的行为测试（2026-11-24 GL-16）。
# 契约按“根目录”参数扫描, 所以在临时目录里搭最小的源码树即可隔离验证:
#   干净的树必须通过; 小程序与 PC 前端里的残留必须逐处点名; 不该扫的地方(依赖包、打包产物、文档、脚本自身)不得误报;
#   根目录不对必须报错而不是通过。判定只认退出码与输出里点名的文件。最后一个场景对真实源码树跑一次。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTRACT="$HERE/check-fabushi-terminology.sh"
REAL="$(cd "$HERE/.." && pwd)"
BASE="${TMPDIR:-/tmp}"
WORK="$BASE/gl16-terminology.$$"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

stage() {
  rm -rf "$WORK/tree"
  mkdir -p "$WORK/tree/aigicrm-miniprogram/pages/fabushi/detail" "$WORK/tree/aigicrm-ui/src/views/fabushi" \
    "$WORK/tree/docs/prototypes" "$WORK/tree/scripts" "$WORK/tree/aigicrm-miniprogram/node_modules/lib" \
    "$WORK/tree/aigicrm-miniprogram/miniprogram_npm/pkg" "$WORK/tree/aigicrm-ui/node_modules/lib"
  printf '%s\n' '<view>仅作者可见 · 删除后可在 PC「内容管理」找回</view>' > "$WORK/tree/aigicrm-miniprogram/pages/fabushi/detail/index.wxml"
  printf '%s\n' "const removePost = () => '已进入法布施认知广场'" > "$WORK/tree/aigicrm-miniprogram/pages/fabushi/detail/index.js"
  printf '%s\n' '<template><h1>认知广场</h1></template>' > "$WORK/tree/aigicrm-ui/src/views/fabushi/feed.vue"
  # 不该被扫的地方故意放着旧名称
  printf '%s\n' 'module.exports = () => "内容治理"' > "$WORK/tree/aigicrm-miniprogram/node_modules/lib/old.js"
  printf '%s\n' '内容治理 分享广场' > "$WORK/tree/aigicrm-miniprogram/miniprogram_npm/pkg/README.js"
  printf '%s\n' '内容治理 分享广场' > "$WORK/tree/aigicrm-ui/node_modules/lib/old.js"
  printf '%s\n' '内容治理 分享广场' > "$WORK/tree/docs/prototypes/fabushi_pc_proto_v8.html"
  printf '%s\n' '内容治理 分享广场' > "$WORK/tree/docs/fabushi-notes.md"
}

passed=0
failed=0
run_case() { # <标签> <期望退出码> <输出必须包含的词(空格分隔)> [根目录]
  local label="$1" expect="$2" words="$3" root="${4:-$WORK/tree}" out rc
  out="$(bash "$CONTRACT" "$root" 2>&1)"; rc=$?
  local ok=1 w
  for w in $words; do printf '%s' "$out" | /usr/bin/grep -qF -- "$w" || ok=0; done
  if [ "$rc" -eq "$expect" ] && [ "$ok" -eq 1 ]; then
    passed=$((passed + 1)); echo "PASS $label"
  else
    failed=$((failed + 1)); echo "FAIL $label — 期望退出码 $expect 且输出含 [$words]; 实际退出码 $rc, 输出: $(printf '%s' "$out" | head -3 | tr '\n' ' ' | cut -c1-200)"
  fi
}

stage
run_case '① 干净的源码树通过(依赖包、打包产物、文档里的旧名称不算)' 0 'PASS'

stage
printf '%s\n' '<text>删除后可在 PC 内容治理找回</text>' >> "$WORK/tree/aigicrm-miniprogram/pages/fabushi/detail/index.wxml"
run_case '② 小程序页面里的“内容治理”被点名' 1 '内容治理 aigicrm-miniprogram/pages/fabushi/detail/index.wxml'

stage
printf '%s\n' "const text = '已进入分享广场'" >> "$WORK/tree/aigicrm-miniprogram/pages/fabushi/detail/index.js"
run_case '③ 小程序脚本里的“分享广场”被点名' 1 '分享广场 aigicrm-miniprogram/pages/fabushi/detail/index.js'

stage
printf '%s\n' '<el-button>内容治理</el-button>' >> "$WORK/tree/aigicrm-ui/src/views/fabushi/feed.vue"
run_case '④ PC 前端里的“内容治理”被点名' 1 'aigicrm-ui/src/views/fabushi/feed.vue'

stage
printf '%s\n' '{"navigationBarTitleText": "分享广场"}' > "$WORK/tree/aigicrm-miniprogram/pages/fabushi/detail/index.json"
run_case '⑤ 小程序页面配置里的“分享广场”被点名' 1 'index.json'

stage
run_case '⑥ 根目录不对: 报错, 不通过' 1 '被扫的目录不存在' "$WORK/nope"
mkdir -p "$WORK/half/aigicrm-miniprogram/pages"
run_case '⑥b 只有小程序目录、没有 PC 前端目录: 报错, 不通过' 1 'aigicrm-ui/src' "$WORK/half"

run_case '⑦ 真实源码树' 0 'PASS' "$REAL"

echo "通过 $passed 项, 未通过 $failed 项"
[ "$failed" -eq 0 ] || exit 1
