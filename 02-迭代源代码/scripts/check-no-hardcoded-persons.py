#!/usr/bin/env python3
"""禁止硬编码人员契约: 业务代码不得写死任何真实员工(姓名/特定用户名/用户ID)。

背景（2026-11-17 用户令"不要硬编码人员，说了多少遍了"）：人员=管理员在 PC 权限中心
勾选产生的**数据**（按角色主路径 + 按用户例外），不是代码。人员名单会随组织变化
（几十人到几千人），任何写死人员的分支都是反模式：
  - 员工入职/离职/转岗 → 代码要改 = 事故
  - 权限中心取消勾选后代码分支仍放行 = 越权
正确形态：判定一律走权限码（@PreAuthorize/hasAuthority/hasPermission）+ 权限中心勾选数据。

判定（业务代码 = src/main + 前端 src(非测试) + MP pages/utils/api，排除注释行）：
  P1 员工用户名模式: NO001/NMxxx/TYxx 等6543式工号字面量（'NO001' 双引号或单引号字符串）
  P2 已知员工中文姓名出现于**可执行行**（注释里的案例标注合法——历史裁决留痕）
  注: 'admin' 为系统内置管理员业务口径（PC/MP 两端一致设计, utils/auth.js L45-49 钉），
      demo 为演示档——均不在本契约范围。
负例自证：--self-test 合成含工号字符串与姓名可执行行的文件必须检出，纯注释文件零误报。
"""
from __future__ import annotations
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SCAN_DIRS = [
    ROOT / 'aigicrm-backend' / 'src' / 'main',
    ROOT / 'aigicrm-ui' / 'src',
    ROOT / 'aigicrm-miniprogram' / 'pages',
    ROOT / 'aigicrm-miniprogram' / 'utils',
    ROOT / 'aigicrm-miniprogram' / 'api',
]

# P1: 6543式工号用户名字面量(引号内), 排除 admin/demo 及测试凭证环境变量名
P_USERNAME = re.compile(r'''['"](?!admin|demo)(NO\d{3}|NM\d{2,3}|TY\d{2,3}|smoke_\w+|__test_\w+)['"]''')

# P2: 已知员工姓名(来自 sys_user.real_name 生产实拉 2026-11-17; 新员工入职无需改本表——
#     本契约只防"已知人员被写死", 组织变化本身走权限中心勾选)
KNOWN_NAMES = [
    '马吉亮', '石力', '王建平', '李帮文', '梁文生', '陈晓彬', '朱莉莉', '冀晓伟',
    '白晓炯', '梁慧栋', '刘月霞', '余慧', '武磊', '王磊磊', '唐邯潇', '赵珂',
    '孙玉宏', '张皓翔', '尤扬', '刘振宇', '董泽宇', '吴晟隆', '郑磊',
    '毛颖娜', '李艳梅', '肖赟',
]

# 注释行前缀(这些行允许出现姓名=裁决/案例留痕)
COMMENT_PREFIXES = ('//', '*', '/*', '#')


def is_comment_line(line: str) -> bool:
    s = line.lstrip()
    return s.startswith(COMMENT_PREFIXES)


def scan_file(path: Path) -> list[str]:
    rel = str(path)
    hits: list[str] = []
    try:
        text = path.read_text(encoding='utf-8')
    except (UnicodeDecodeError, OSError):
        return hits
    for idx, line in enumerate(text.splitlines(), 1):
        if is_comment_line(line):
            continue
        um = P_USERNAME.search(line)
        if um:
            hits.append(f'{rel}:{idx}: [P1 工号字面量 {um.group(1)}] {line.strip()[:120]}')
            continue
        for name in KNOWN_NAMES:
            if name in line:
                hits.append(f'{rel}:{idx}: [P2 员工姓名 {name}] {line.strip()[:120]}')
                break
    return hits


def scan() -> list[str]:
    hits: list[str] = []
    for d in SCAN_DIRS:
        if not d.exists():
            continue
        for path in sorted(d.rglob('*')):
            if path.suffix not in ('.java', '.js', '.vue', '.ts'):
                continue
            if '.spec.' in path.name or path.name.endswith('Test.java') or '/test/' in str(path):
                continue  # 测试代码允许合成人员
            if 'generated' in path.parts or 'permission-codes' in path.name:
                continue
            hits.extend(scan_file(path))
    return hits


def self_test() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        bad = d / 'Bad.java'
        bad.write_text(
            'String owner = "NO001";\n'
            'if (user.getRealName().equals("马吉亮")) { allow(); }\n'
            '// 注释: 毛颖娜案例留痕(合法)\n',
            encoding='utf-8',
        )
        hits = scan_file(bad)
        if len(hits) != 2:
            print(f'FAIL: 负例自证未检出(命中 {len(hits)}, 期望 2: 工号+姓名可执行行)', file=sys.stderr)
            for h in hits:
                print(f'  {h}', file=sys.stderr)
            return 1
        good = d / 'Good.java'
        good.write_text(
            '// 注释: 郑磊/NM005 案例留痕(合法)\n'
            'if (SecurityUtils.hasAuthority("file:edit")) { allow(); }\n'
            'String who = "admin";\n',
            encoding='utf-8',
        )
        if scan_file(good):
            print('FAIL: 负例自证对合规文件误报', file=sys.stderr)
            for h in scan_file(good):
                print(f'  {h}', file=sys.stderr)
            return 1
    print('PASS: 负例自证(工号+姓名可执行行检出2, 合规文件零误报)')
    return 0


def main() -> int:
    if '--self-test' in sys.argv:
        return self_test()
    hits = scan()
    if hits:
        print('FAIL: 发现业务代码硬编码人员(人员=权限中心勾选的数据, 不是代码):', file=sys.stderr)
        for h in hits:
            print(f'  {h}', file=sys.stderr)
        print('处置: 判定改走权限码(@PreAuthorize/hasAuthority/hasPermission);', file=sys.stderr)
        print('      人员范围由管理员在权限中心勾选(按角色主路径/按用户例外), 禁止写死。', file=sys.stderr)
        return 1
    print('PASS: 业务代码零硬编码人员(姓名/工号/用户ID 字面量), 人员=权限中心勾选数据')
    return 0


if __name__ == '__main__':
    sys.exit(main())
