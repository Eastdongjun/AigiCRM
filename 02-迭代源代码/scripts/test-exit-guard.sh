#!/usr/bin/env bash
# exit-guard.sh「退出码守卫」的行为测试(2026-11-24)
#
# 背景: macOS 自带的 bash 3.2 在 set -e + set -u + EXIT 陷阱同时存在时, 未定义变量引起的中止会以退出码 0 结束。
#   检查脚本因此可能"假通过", 部署脚本因此可能"假成功"。守卫的做法是: 陷阱里看脚本是否走到了最后一行。
#
# 验证(每个场景都是一段临时生成的小脚本, 用系统 bash 真实运行):
#   ① 未定义变量中止 → 1(并说明原因)
#   ② 正常走完(EXIT_GUARD_DONE=1) → 0
#   ③ 显式 exit 3 → 3; set -e 下命令失败 → 1; 置了完成标记之后命令失败 → 1
#   ④ 没置完成标记就 exit 0 → 1(只有走到最后一行才算成功)
#   ⑤ 清理函数在成功、失败两条路径上都执行; 清理函数失败不改变退出码
#   ⑥ 信号: SIGTERM → 143
# 另打印一行"未装守卫时"的对照退出码(仅作参考, 不断言: 新版 bash 已无此缺陷)。
set -uo pipefail

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUARD="$SELF_DIR/exit-guard.sh"
fail() { echo "FAIL: $*" >&2; exit 1; }
[ -f "$GUARD" ] || fail "缺少 $GUARD"

T="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-exit-guard-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
RC=0
run_case() { # $1=脚本正文(已 set -Eeuo pipefail 并加载守卫); 退出码进 RC, 输出进 $T/out
  { echo 'set -Eeuo pipefail'; printf '. %q\n' "$GUARD"; printf '%s\n' "$1"; } > "$T/case.sh"
  RC=0
  CLEANLOG="$T/clean.log" /bin/bash "$T/case.sh" > "$T/out" 2>&1 < /dev/null || RC=$?
}
out() { cat "$T/out"; }

# ---- ① 未定义变量中止 ----
run_case 'exit_guard_install; echo "$PROBE_UNBOUND_VAR"'
[ "$RC" -eq 1 ] || fail "未定义变量中止时应退出 1, 实际 rc=${RC}; 输出: $(out)"
grep -q 'unbound variable' "$T/out" || fail "场景无效: 探针没有触发未定义变量; 输出: $(out)"
grep -q '收尾之前异常终止' "$T/out" || fail "应说明是异常终止: $(out)"
echo "参考(不断言): 同样的脚本不装守卫、直接 trap EXIT 时, 本机 bash 的退出码是 $(/bin/bash -c 'set -Eeuo pipefail; trap "true" EXIT; echo "$PROBE_UNBOUND_VAR"' >/dev/null 2>&1; echo $?)"

# ---- ② 正常走完 ----
run_case 'exit_guard_install; echo built; EXIT_GUARD_DONE=1'
[ "$RC" -eq 0 ] || fail "正常走完时应退出 0, 实际 rc=${RC}; 输出: $(out)"
grep -q 'built' "$T/out" || fail "正常输出应保留: $(out)"

# ---- ③ 显式退出码与命令失败 ----
run_case 'exit_guard_install; exit 3'
[ "$RC" -eq 3 ] || fail "显式 exit 3 应保持 3, 实际 rc=${RC}; 输出: $(out)"
run_case 'exit_guard_install; echo step; false; echo unreachable; EXIT_GUARD_DONE=1'
[ "$RC" -eq 1 ] || fail "set -e 下命令失败应退出 1, 实际 rc=${RC}; 输出: $(out)"
grep -q 'unreachable' "$T/out" && fail "命令失败后不应继续执行: $(out)"
run_case 'exit_guard_install; EXIT_GUARD_DONE=1; false'
[ "$RC" -eq 1 ] || fail "置了完成标记之后的命令失败仍应退出 1, 实际 rc=${RC}; 输出: $(out)"

# ---- ④ 没置完成标记就 exit 0 ----
run_case 'exit_guard_install; exit 0'
[ "$RC" -eq 1 ] || fail "没走到最后一行就 exit 0 应判失败, 实际 rc=${RC}; 输出: $(out)"

# ---- ⑤ 清理函数 ----
: > "$T/clean.log"
run_case 'cleanup_case() { echo cleaned >> "$CLEANLOG"; }; exit_guard_install cleanup_case; exit 3'
[ "$RC" -eq 3 ] || fail "带清理函数时显式 exit 3 应保持 3, 实际 rc=${RC}; 输出: $(out)"
[ "$(grep -c cleaned "$T/clean.log")" -eq 1 ] || fail "失败路径上清理函数应执行一次, 实际 $(grep -c cleaned "$T/clean.log") 次"
run_case 'cleanup_case() { echo cleaned >> "$CLEANLOG"; }; exit_guard_install cleanup_case; EXIT_GUARD_DONE=1'
[ "$RC" -eq 0 ] || fail "带清理函数时正常走完应退出 0, 实际 rc=${RC}; 输出: $(out)"
[ "$(grep -c cleaned "$T/clean.log")" -eq 2 ] || fail "成功路径上清理函数也应执行, 累计应为 2 次, 实际 $(grep -c cleaned "$T/clean.log") 次"
run_case 'cleanup_case() { return 7; }; exit_guard_install cleanup_case; EXIT_GUARD_DONE=1'
[ "$RC" -eq 0 ] || fail "清理函数失败不应改变退出码, 实际 rc=${RC}; 输出: $(out)"
grep -q '收尾清理函数' "$T/out" || fail "清理函数失败时应提示: $(out)"

# ---- ⑥ 信号 ----
run_case 'exit_guard_install; kill -TERM $$'
[ "$RC" -eq 143 ] || fail "SIGTERM 应保持 143, 实际 rc=${RC}; 输出: $(out)"

echo "PASS: exit-guard.sh 退出码守卫(未定义变量中止 → 1 / 正常走完 → 0 / 显式退出码与信号原样保留 / 清理函数两条路径都执行且不改变退出码)。"
