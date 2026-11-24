#!/usr/bin/env bash
# Regression test for check-pagination-anti-patterns.sh path resolution.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$ROOT/scripts/check-pagination-anti-patterns.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-pagination-path.XXXXXX")"
# 【退出码守卫】见 exit-guard.sh: bash 3.2 在 set -e + EXIT 陷阱下会把未定义变量引起的中止报成退出码 0
cleanup_tmp_dir() { rm -rf "$TMP_DIR"; }
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/exit-guard.sh"
exit_guard_install cleanup_tmp_dir

run_case() {
  local name="$1"
  local cwd="$2"
  local out="$TMP_DIR/$name.log"
  local rc=0
  # 契约报红时不能让 set -e 把测试本身静默带停(此前就是这样: 退出码 1、一个字不输出, 看不出是谁的问题)
  (cd "$cwd" && bash "$CHECK") >"$out" 2>&1 || rc=$?

  if grep -q "No such file or directory" "$out"; then
    echo "FAIL: $name emitted missing-path output" >&2
    sed 's/^/  /' "$out" >&2
    return 1
  fi
  if ! grep -q "分页反模式扫描通过" "$out"; then
    echo "FAIL: $name did not report a successful pagination scan (checker exit $rc)" >&2
    sed 's/^/  /' "$out" >&2
    return 1
  fi
}

run_case "source-root" "$ROOT"
run_case "workspace-root" "$ROOT/.."
run_case "external-cwd" "$TMP_DIR"

# ---- 检测行为(2026-11-25 补; 此前这个检查脚本没有自己的行为测试) ----
# 契约对“有分页器的页面里, 筛选事件绑的加载函数带着页码参数却不重置页码”必须红;
# 不分页的加载函数只有在定义处紧挨着写了 pagination-audit: no-pager 才放行; 标记写在别的函数上头不算。
# 夹具里分页器和筛选事件隔着 12 行, 避开契约“分页器自己的 @change 不查”的 ±10 行窗口。
make_fixture() { # $1=目录 $2=定义上方的那一行(可为空) $3=定义行本身(可省略, 省略时用标准定义行)
  mkdir -p "$1"
  {
    cat <<'EOF'
<template>
  <div>
    <el-pagination v-model:current-page="current" :page-size="10" :total="total" />
    <div class="filler-01" />
    <div class="filler-02" />
    <div class="filler-03" />
    <div class="filler-04" />
    <div class="filler-05" />
    <div class="filler-06" />
    <div class="filler-07" />
    <div class="filler-08" />
    <div class="filler-09" />
    <div class="filler-10" />
    <div class="filler-11" />
    <div class="filler-12" />
    <el-select v-model="kw" @change="loadList" />
  </div>
</template>
<script setup>
const current = ref(1)
EOF
    [ -n "$2" ] && printf '%s\n' "$2"
    printf '%s\n' "${3:-const loadList = async () => {}"
    cat <<'EOF'
  const r = await api({ kw: kw.value, current: current.value, size: 10 })
  list.value = r.data.records
}
</script>
EOF
  } > "$1/Page.vue"
}

expect_rc() { # <标签> <期望退出码 0|1> <输出必须包含的词>
  local label="$1" want="$2" word="$3" out rc=0
  out="$(AIGI_PAGINATION_SRC="$FIX_DIR" bash "$CHECK" 2>&1)" || rc=$?
  if [ "$rc" -eq "$want" ] && printf '%s' "$out" | grep -qF -- "$word"; then
    echo "  PASS $label"
  else
    echo "FAIL: $label —— 期望退出码 $want 且输出含 [$word]; 实际退出码 $rc, 输出: $(printf '%s' "$out" | tr '\n' ' ' | cut -c1-300)" >&2
    return 1
  fi
}

FIX_DIR="$TMP_DIR/fixture"
make_fixture "$FIX_DIR" ""
expect_rc "① 带页码参数却不重置页码: 必须红" 1 "[A]"
make_fixture "$FIX_DIR" "// pagination-audit: no-pager 这份列表整体返回"
expect_rc "② 定义处紧挨着的上一行(纯注释)写了标记: 放行" 0 "分页反模式扫描通过"
make_fixture "$FIX_DIR" "const loadOther = () => {} // pagination-audit: no-pager 标记在别的函数上头"
expect_rc "③ 标记不在紧贴定义的位置: 照旧红" 1 "[A]"
make_fixture "$FIX_DIR" "" "const loadList = async () => { // pagination-audit: no-pager 标在定义行本身"
expect_rc "④ 标记写在定义行本身: 放行" 0 "分页反模式扫描通过"
# 走到这里说明上面四个场景都对; 逐个场景的结果在 expect_rc 里输出
EXIT_GUARD_DONE=1
echo "通过 4 项, 未通过 0 项"
echo "PASS: pagination anti-pattern checker resolves project paths from any cwd."
