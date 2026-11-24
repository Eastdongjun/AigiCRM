#!/bin/bash
# ============================================================
# 退出码守卫(2026-11-24)
#
# 为什么需要: macOS 自带的 bash 3.2 有一个缺陷 —— 脚本同时满足"set -e、set -u、设了 EXIT 陷阱"三个条件时,
#   未定义变量引起的中止会以退出码 0 结束。也就是说脚本中途死掉, 调用方却看到"成功":
#   对检查脚本是"假通过", 对部署脚本是"假成功"。陷阱里也拿不到真实退出码(看到的同样是 0)。
#   实测(本机 bash 3.2.57): 函数参数、内置命令、外部命令、普通命令里的未定义变量都如此; 不设 EXIT 陷阱则为 1。
#
# 做法: 陷阱里看"脚本是否走到了最后一行"。没走到却要退出, 一律按失败。
#   显式 exit 3 仍然是 3, 信号仍然是 128+编号, 正常走完是 0。
#
# 用法(三步):
#   1) . "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/exit-guard.sh"; exit_guard_install          # 或 exit_guard_install <清理函数名>
#   2) 脚本最后一行(最后一条会失败的命令之后、成功输出之前)写 EXIT_GUARD_DONE=1
#   3) 不要再自己写 trap … EXIT
# 清理函数的失败不改变退出码。
# 行为测试见 scripts/test-exit-guard.sh; 契约见 scripts/check-exit-guard-contract.sh。
# 两份生产部署脚本(deploy-backend.sh / deploy-frontend.sh)内联了同一段守卫, 因为它们的收尾逻辑与部署状态耦合。
# ============================================================
EXIT_GUARD_CLEANUP=""
EXIT_GUARD_DONE=0

exit_guard_install() { # $1=清理函数名(可选)
  EXIT_GUARD_CLEANUP="${1:-}"
  EXIT_GUARD_DONE=0
  __exit_guard_on_exit() {
    local rc=$?
    if [ -n "$EXIT_GUARD_CLEANUP" ]; then
      "$EXIT_GUARD_CLEANUP" || echo "⚠️ 收尾清理函数 ${EXIT_GUARD_CLEANUP} 失败(不改变本次结论)" >&2
    fi
    if [ "$rc" -eq 0 ] && [ "${EXIT_GUARD_DONE:-0}" -ne 1 ]; then
      echo "FAIL: 脚本在收尾之前异常终止(退出码被 bash 3.2 吞成 0), 按失败处理" >&2
      rc=1
    fi
    exit "$rc"
  }
  trap __exit_guard_on_exit EXIT
}
