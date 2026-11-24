#!/usr/bin/env python3
"""退出码守卫契约(扫描器)的夹具测试(2026-11-24)。

扫描器是对脚本做文本分析的, 写错的方式恰恰是"漏报": 该报的脚本没报, 契约显示通过。
本测试用一组临时生成的小脚本逐条验证判定, 每一条都对应扫描器在开发中实际犯过或可能犯的错:
  应当报问题的:  裸陷阱 / 写在行尾的陷阱 / 用了守卫却没有完成标记 / 用了守卫又自设陷阱 /
                完成标记之后还有可能失败的命令 / set -o 长写法 / set 行尾带注释 / heredoc 没有闭合
  不应当报问题的: 经过守卫 / 陷阱只在 heredoc 里 / 没启用 errexit(缩进的 set 行在字符串里) /
                set +e 关闭了 errexit / trap - EXIT 是清除陷阱 / 内联守卫(标记在注释里)
"""
import importlib.util
import pathlib
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
CONTRACT = HERE / 'check-exit-guard-contract.py'
spec = importlib.util.spec_from_file_location('exit_guard_contract', CONTRACT)
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)

GUARD_HEAD = 'set -euo pipefail\n. "$(cd "$(dirname "$0")" && pwd)/exit-guard.sh"\ncleanup() { rm -rf "$TMP"; }\nexit_guard_install cleanup\n'
DONE = 'EXIT_GUARD_DONE=1\n'

# 行号按"夹具正文前面再加一行 #!/bin/bash"之后的实际行号写(见 main 里的拼接)
CASES = [
    # ---- 应当报问题 ----
    ('裸陷阱', 1, ['没有退出码守卫'],
     'set -euo pipefail\nTMP="$(mktemp -d)"\ntrap \'rm -rf "$TMP"\' EXIT\necho "PASS"\n'),
    ('写在行尾的陷阱', 1, ['没有退出码守卫', '第 4 行'],
     'set -euo pipefail\nTMP=X\n  NEG=$(mktemp -d /tmp/x.XXXXXX); trap \'rm -rf "$NEG"\' EXIT\necho "PASS"\n'),
    ('set 的长写法', 1, ['没有退出码守卫'],
     'set -o errexit\nset -o nounset\ntrap cleanup EXIT\n'),
    ('set 行尾带注释', 1, ['没有退出码守卫'],
     'set -euo pipefail  # strict\ntrap cleanup EXIT\n'),
    ('用了守卫却没有完成标记', 1, ['没有 EXIT_GUARD_DONE=1'],
     GUARD_HEAD + 'echo "PASS"\n'),
    ('用了守卫又自设陷阱', 1, ['会覆盖守卫', '第 6 行'],
     GUARD_HEAD + 'trap cleanup EXIT\n' + DONE),
    ('完成标记之后还有可能失败的命令', 1, ['之后还有可能失败的命令', '第 7 行'],
     GUARD_HEAD + DONE + 'python3 -c "raise SystemExit(1)"\n'),
    ('完成标记之后还有带非零字面量的 exit', 1, ['之后还有可能失败的命令'],
     GUARD_HEAD + DONE + 'exit 2\n'),
    ('内联守卫缺完成标记', 1, ['没有退出码守卫'],
     'set -Eeuo pipefail\n# 【退出码守卫】说明\non_exit() { local rc=$?; exit "$rc"; }\ntrap on_exit EXIT\necho ok\n'),
    ('内联守卫的陷阱不是 trap on_exit EXIT', 1, ['没有退出码守卫'],
     'set -Eeuo pipefail\n# 【退出码守卫】说明\nDONE=1\ntrap cleanup EXIT\n'),
    # ---- 不应当报问题 ----
    ('经过守卫', 0, [],
     GUARD_HEAD + DONE + 'echo "PASS"\n'),
    ('经过守卫, 完成标记在最后', 0, [],
     GUARD_HEAD + 'echo "PASS"\n' + DONE),
    ('经过守卫, 其后是带变量的 exit', 0, [],
     GUARD_HEAD + DONE + 'exit "$fails"\n'),
    ('经过守卫, 其后是 exit 0', 0, [],
     GUARD_HEAD + DONE + 'exit 0\n'),
    ('陷阱只在 heredoc 里', 0, [],
     'set -euo pipefail\nbash -s <<\'REMOTE\'\nset -Eeuo pipefail\ntrap cleanup EXIT\ncleanup\nREMOTE\n'),
    ('没启用 errexit, 缩进的 set 行在字符串里', 0, [],
     'set -uo pipefail\ntrap \'rm -rf "$T"\' EXIT\nbash -c \'\n  set -Eeuo pipefail\n  echo hi\n\'\n'),
    ('set +e 关闭了 errexit', 0, [],
     'set -euo pipefail\nset +e\ntrap \'true\' EXIT\n'),
    ('trap - EXIT 是清除陷阱', 0, [],
     'set -euo pipefail\ntrap - EXIT\necho ok\n'),
    ('整行注释里的 trap 不算', 0, [],
     'set -euo pipefail\n# 不要再自己写 trap cleanup EXIT\necho ok\n'),
    ('内联守卫: 标记在注释里', 0, [],
     'set -Eeuo pipefail\n# 【退出码守卫】说明\nDONE=0\non_exit() { local rc=$?; exit "$rc"; }\ntrap on_exit EXIT\necho ok\nDONE=1\n'),
    ('heredoc 嵌套 heredoc', 0, [],
     'set -uo pipefail\ncat <<\'OUTER\'\nexec docker exec -i x <<\'INNER\'\ntrap cleanup EXIT\nINNER\nOUTER\n'),
]


def main():
    fails = 0
    with tempfile.TemporaryDirectory(prefix='aigicrm-exit-guard-contract-test.') as tmp:
        for i, (name, want_problems, needles, text) in enumerate(CASES, 1):
            path = pathlib.Path(tmp) / ('case_%02d.sh' % i)
            path.write_text('#!/bin/bash\n' + text, encoding='utf-8')
            info, error = contract.scan(path)
            problems = contract.judge(info)
            if error:
                # 只有"heredoc 没有闭合"这一类用例才允许返回 error; 其它用例返回 error 说明扫描器解析不了普通脚本
                print('FAIL: [%s] 扫描器无法判断: %s' % (name, error))
                fails += 1
                continue
            got_problem = bool(problems)
            if got_problem != bool(want_problems):
                print('FAIL: [%s] %s, 实际%s' % (name, '应当报问题' if want_problems else '不应报问题',
                                                '报了: %s' % problems if problems else '没报'))
                fails += 1
                continue
            joined = ' || '.join(problems)
            missing = [n for n in needles if n not in joined]
            if missing:
                print('FAIL: [%s] 问题说明里缺少 %s; 实际: %s' % (name, missing, joined))
                fails += 1
                continue
        # ---- heredoc 没有闭合: 不能猜, 必须明确说"无法判断" ----
        path = pathlib.Path(tmp) / 'unterminated.sh'
        path.write_text('#!/bin/bash\nset -euo pipefail\ncat <<\'REMOTE\'\ntrap cleanup EXIT\necho hi\n', encoding='utf-8')
        info, error = contract.scan(path)
        if not error or '没有闭合' not in error:
            print('FAIL: [heredoc 没有闭合] 应返回"无法判断", 实际 error=%r' % error)
            fails += 1
    if fails:
        print('FAIL: 退出码守卫契约扫描器的夹具测试: %d 条不符合预期(共 %d 条)' % (fails, len(CASES) + 1))
        return 1
    print('PASS: 退出码守卫契约扫描器的夹具测试(%d 条: 应报的都报、不应报的不报、无法判断的不猜)' % (len(CASES) + 1))
    return 0


if __name__ == '__main__':
    sys.exit(main())
