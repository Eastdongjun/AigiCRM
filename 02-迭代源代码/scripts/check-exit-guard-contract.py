#!/usr/bin/env python3
"""退出码守卫契约(2026-11-24)。

为什么需要: macOS 自带的 bash 3.2 有一个缺陷 —— 脚本同时满足"set -e、set -u、本地 EXIT 陷阱"三个条件时,
未定义变量引起的中止会以退出码 0 结束。检查脚本因此可能"假通过", 部署脚本因此可能"假成功"。
守卫的做法见 scripts/exit-guard.sh: 陷阱里看脚本是否走到了最后一行。

规则(对 scripts/ 下的 .sh 逐个检查):
  [1] 没有同时启用 errexit 与 nounset 的脚本不受此缺陷影响, 不作要求。
      是否启用只看顶格的 set 行: 缩进的 set 行是函数体或 heredoc 里的内容, 不是脚本自己的设置。
  [2] 同时启用了两者、又自己写了本地 EXIT 陷阱的脚本, 必须满足二者之一:
        a. 用 exit_guard_install 安装陷阱, 脚本里有 EXIT_GUARD_DONE=1, 且没有别的本地 EXIT 陷阱;
        b. 内联守卫(留给收尾逻辑与部署状态耦合的生产部署脚本): 含标记"退出码守卫"与 DONE=1,
           且每个本地 EXIT 陷阱都是 `trap on_exit EXIT`。
  [3] 用了 exit_guard_install 却没有 EXIT_GUARD_DONE=1 的脚本永远不可能成功, 直接判失败。
  [4] heredoc 没有闭合导致无法判断的, 判失败(不猜)。

"本地"的判定: 整行注释和 heredoc 正文不算(heredoc 里是发给服务器执行的远端脚本, 运行在 Linux 的 bash 上,
不受此缺陷影响); 陷阱写在行首、写在函数名后面、或写在一行的后半段, 都算。
这里刻意不追踪引号状态: 常见写法 SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)" 里的引号是嵌套在命令替换里的,
按字符数引号会把后面的整个文件误当成字符串正文, 从而一个陷阱都看不见 —— 漏报比误报危险得多。
已知局限: 引号字符串里出现的"trap … EXIT"文字会被当成陷阱。目前仓库里只有守卫自己的测试有这种写法, 已列入例外。
扫描器自己的判定由 scripts/test-exit-guard-contract.py 用夹具逐条验证; 改扫描器必须同时跑它。
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent
SET_SHORT = re.compile(r'^set\s+([+-])([A-Za-z]+)(?:\s+[a-z]+)*\s*(#.*)?$')   # 如 set -euo pipefail
SET_LONG = re.compile(r'^set\s+([+-])o\s+(errexit|nounset)\s*(#.*)?$')
HEREDOC = re.compile(r'<<-?\s*(?:"([A-Za-z_]\w*)"|\'([A-Za-z_]\w*)\'|([A-Za-z_]\w*))')
TRAP = re.compile(r'(?<![\w.-])trap\s+(?P<rm>-\s|--\s)?(?P<rest>[^\n;|&]*)')
DONE_GUARD = re.compile(r'^\s*EXIT_GUARD_DONE=1\s*(#.*)?$')
DONE_INLINE = re.compile(r'^\s*DONE=1\s*(#.*)?$')
INLINE_TRAP = re.compile(r'^trap on_exit EXIT$')

SELF_TESTS = {'exit-guard.sh', 'test-exit-guard.sh'}   # 守卫自己与它的测试(测试正文里有成段的示例代码)


def scan(path):
    """返回 (info, error)。error 非空表示无法判断。"""
    info = {'errexit': False, 'nounset': False, 'traps': [], 'uses_guard': False,
            'has_done': False, 'inline_done': False, 'marker': False,
            'done_line': 0, 'inline_done_line': 0, 'after_done': [], 'after_inline_done': []}
    try:
        text = path.read_text(encoding='utf-8')
    except (OSError, UnicodeDecodeError) as exc:
        return info, '无法读取: %s' % exc
    term = None
    for n, raw in enumerate(text.splitlines(), 1):
        if term is not None:
            if raw.strip() == term:
                term = None
            continue
        stripped = raw.strip()
        if stripped.startswith('#'):
            if '退出码守卫' in raw:
                info['marker'] = True
            continue
        code = raw.rstrip()
        if not code:
            continue
        if not code[:1].isspace():
            m = SET_SHORT.match(code)
            if m:
                if 'e' in m.group(2):
                    info['errexit'] = (m.group(1) == '-')
                if 'u' in m.group(2):
                    info['nounset'] = (m.group(1) == '-')
            m = SET_LONG.match(code)
            if m:
                if m.group(2) == 'errexit':
                    info['errexit'] = (m.group(1) == '-')
                else:
                    info['nounset'] = (m.group(1) == '-')
        for m in TRAP.finditer(code):
            if m.group('rm'):
                continue                # trap - EXIT 是清除陷阱
            if re.search(r'\bEXIT\b', m.group('rest')):
                info['traps'].append((n, m.group(0).strip()))
        if re.search(r'\bexit_guard_install\b', code):
            info['uses_guard'] = True
        if DONE_GUARD.match(code):
            info['has_done'] = True
            info['done_line'] = n
        if DONE_INLINE.match(code):
            info['inline_done'] = True
            info['inline_done_line'] = n
        if '退出码守卫' in code:
            info['marker'] = True
        for key, idx in (('after_done', 'done_line'), ('after_inline_done', 'inline_done_line')):
            if info.get(idx) and n > info[idx]:
                info[key].append((n, code.strip()))
        h = HEREDOC.search(code)
        if h:
            term = next(g for g in h.groups() if g)
    if term is not None:
        return info, 'heredoc %r 没有闭合' % term
    return info, ''


# 完成标记之后只允许"不可能失败"的收尾: 成功提示, 或带变量/0 的 exit(非零退出码不会被标记掩盖)
TAIL_OK = re.compile(r'^(?:echo|printf)\s|^exit\s+(?:"?\$\{?[A-Za-z_][A-Za-z0-9_]*\}?"?|0)\s*(#.*)?$')


def tail_problem(marker, tail):
    if not tail:
        return ''
    bad = ['第 %d 行: %s' % (n, t[:60]) for n, t in tail if not TAIL_OK.match(t)]
    if bad:
        return '%s=1 之后还有可能失败的命令 —— 标记等于没设, 这些命令之后的异常中止又会被报成 0: %s' % (marker, '; '.join(bad))
    return ''


def judge(info):
    """返回该脚本的问题列表(空表示合规)。"""
    problems = []
    if info['uses_guard']:
        if not info['has_done']:
            problems.append('用了 exit_guard_install 却没有 EXIT_GUARD_DONE=1 —— 脚本永远不可能以 0 退出')
        if info['traps']:
            problems.append('用了 exit_guard_install, 却还有自己写的本地 EXIT 陷阱(第 %s 行) —— 会覆盖守卫'
                            % ', '.join(str(n) for n, _ in info['traps']))
        p = tail_problem('EXIT_GUARD_DONE', info['after_done'])
        if p:
            problems.append(p)
    elif info['errexit'] and info['nounset'] and info['traps']:
        inline_ok = (info['marker'] and info['inline_done']
                     and all(INLINE_TRAP.match(t) for _, t in info['traps']))
        if not inline_ok:
            problems.append('启用了 set -e + set -u, 又自己写了本地 EXIT 陷阱(第 %s 行), 但没有退出码守卫 —— '
                            '未定义变量引起的中止会被报成退出码 0。改法见 scripts/exit-guard.sh 文件头'
                            % ', '.join(str(n) for n, _ in info['traps']))
        else:
            p = tail_problem('DONE', info['after_inline_done'])
            if p:
                problems.append(p)
    return problems


def main():
    files = sorted(ROOT.glob('*.sh')) + sorted(p for p in ROOT.glob('*/*.sh'))
    problems = []
    guarded = 0
    for path in files:
        if path.name in SELF_TESTS:
            continue
        info, error = scan(path)
        name = str(path.relative_to(ROOT.parent))
        if error:
            problems.append('%s: %s, 无法判断是否有本地 EXIT 陷阱' % (name, error))
            continue
        found = judge(info)
        problems.extend('%s: %s' % (name, p) for p in found)
        if not found and (info['uses_guard'] or (info['errexit'] and info['nounset'] and info['traps'])):
            guarded += 1
    if problems:
        print('FAIL: 退出码守卫契约: %d 处' % len(problems))
        for p in problems:
            print('  ' + p)
        return 1
    print('PASS: 退出码守卫契约(%d 个脚本; %d 个带本地 EXIT 陷阱的 errexit+nounset 脚本都经过了守卫)'
          % (len(files), guarded))
    return 0


if __name__ == '__main__':
    sys.exit(main())
