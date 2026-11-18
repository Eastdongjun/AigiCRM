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
  注: 姓名名单以 SHA-256 哈希保存（公开仓库不落真实姓名）；命中只回报哈希前缀。
负例自证：--self-test 合成含工号字符串与姓名可执行行的文件必须检出，纯注释文件零误报。
"""
from __future__ import annotations
import hashlib
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

# P2: 已知员工姓名（SHA-256，公开仓库不落真实姓名；来自 sys_user.real_name 生产实拉 2026-11-17）
KNOWN_NAME_HASHES = {
    '09cce4f5beed6c741136c3f0dc9184c6f6bfa05e8c765c6fcf720244b95feffd',
    '0c032ea1d8a7559cb948e7526b8fec48a358c3079fea9d7da9c068ea32e7259d',
    '0d89fdf86e7d50ede619f56b91712326f75ecc36c33c9f0cca1b0193b3b5d7b3',
    '12ed97b575b195caab2df0824d80d7995de894e4c875135106badef7aaa8205a',
    '42a2f1bab7203144c7c69d4b86c7e8cce5cc5d44b97f4755d76465aa390ea78c',
    '4627a3e22f32033e46daffc2d8d3cdcd521ac936afb587212f080d9cffadb906',
    '4d5a51dd918344ad4baeb5bee91d28d52ea046544b26c0a84cb0dfbe4f7196cc',
    '5b739f7ef08a83b6e038f37957a73087e1f843eed78aa10aef0dbdaa0947bcce',
    '5c3d8d926913b6de8dca3037c036839cd73943e95dee39822b38169516f41c1a',
    '5ccecfea7616476c31a64eee7fb846c235f5dde2e94834b7ed710caa9e367c71',
    '5f37e0ba17e28da1cbe94455e52891398630ef47d5d3fed5ba6f3ee0db884ebe',
    '7324c5fe24469cd6dce97484d11df7e7286ef558fe6b86accf6eb8bf35e20b0c',
    '810ea6452c0d752bf6eee77314deb98a1f3a88c3dcd29cbc19a50280544f48e9',
    '8a8ddc3da056a87b4c960b79ccecd68909ba3fb7a29ea0fa4c692125921c8c75',
    '94c14d363624f09fe2027f13ac9224cab67109a60caa92cfe9d9da457d5bc3c9',
    '95d353ceed6dc0247e955030791d3ccac888696b7de2851a699f4aaacfa8ab5f',
    '9adc26948e61766fac96e9c776f3f8040f715e6edead3edf0660f71975d8da7f',
    'b4b7d2f6dee18a07f52246206f405ac194776e9ffdeec34c392162eed19eecf6',
    'dcb19fc266e05f5943140a54f735c8299b82c56fec1971d3c2d045328902dc82',
    'dd046605e7f9db5654615ddaeeaefd0c779a5b98859ab1ddf66f59dca4b293f9',
    'e871b6abbeb7c86539ea909d5217f2d39e7cfb7ad4ed9efb08f6fb41fde8432d',
    'ea1c4f1e81345248f1215753a54762774cf1850eba44f8ae38f5fee48d3d5b7c',
    'eb350392baec2a0f3aafca0c17f6bbbba1fa83b42c5c6d5432e645f9b8d52e0a',
    'f7f6e504dd6425b56ec23f0e740a1aa77d320cbf307c4c8079da728e8d4a8325',
    'f95c9481ae75eb21bf546225d9c460d3322e91ca87298aa2aea423890e8e7202',
    'fbbb5c9f5a3de56a2d22e58f5786eae98c3ede1f40f679c91b6186afc7a5e26f',
}

CHINESE_RUN = re.compile(r'[一-龥]{2,}')
NAME_LENGTHS = (2, 3, 4)
COMMENT_PREFIXES = ('//', '*', '/*', '#')


def is_comment_line(line: str) -> bool:
    s = line.lstrip()
    return s.startswith(COMMENT_PREFIXES)


def name_hash_hit(line: str, name_hashes: set[str]) -> str | None:
    """返回命中的哈希前缀（不返回/打印真实姓名）。"""
    for run in CHINESE_RUN.findall(line):
        for length in NAME_LENGTHS:
            for i in range(len(run) - length + 1):
                candidate = run[i:i + length]
                digest = hashlib.sha256(candidate.encode('utf-8')).hexdigest()
                if digest in name_hashes:
                    return digest[:12]
    return None


def scan_file(path: Path, name_hashes: set[str] = KNOWN_NAME_HASHES) -> list[str]:
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
        digest_prefix = name_hash_hit(line, name_hashes)
        if digest_prefix:
            hits.append(f'{rel}:{idx}: [P2 员工姓名 hash:{digest_prefix}] {line.strip()[:120]}')
    return hits


def is_scannable(path: Path) -> bool:
    if path.suffix not in ('.java', '.js', '.vue', '.ts'):
        return False
    if '.spec.' in path.name or path.name.endswith('Test.java') or '/test/' in str(path):
        return False  # 测试代码允许合成人员
    if 'generated' in path.parts or 'permission-codes' in path.name:
        return False
    return True


def iter_scan_files():
    for d in SCAN_DIRS:
        if not d.exists():
            continue
        for path in sorted(d.rglob('*')):
            if is_scannable(path):
                yield path


def scan() -> list[str]:
    hits: list[str] = []
    for path in iter_scan_files():
        hits.extend(scan_file(path))
    return hits


def self_test() -> int:
    test_names = ['契约甲', '契约乙']
    test_hashes = {hashlib.sha256(n.encode('utf-8')).hexdigest() for n in test_names}
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        bad = d / 'Bad.java'
        bad.write_text(
            'String owner = "NO001";\n'
            'if (user.getRealName().equals("契约甲")) { allow(); }\n'
            '// 注释: 契约乙案例留痕(合法)\n',
            encoding='utf-8',
        )
        hits = scan_file(bad, test_hashes)
        if len(hits) != 2:
            print(f'FAIL: 负例自证未检出(命中 {len(hits)}, 期望 2: 工号+姓名可执行行)', file=sys.stderr)
            for h in hits:
                print(f'  {h}', file=sys.stderr)
            return 1
        good = d / 'Good.java'
        good.write_text(
            '// 注释: 契约乙/NM005 案例留痕(合法)\n'
            'if (SecurityUtils.hasAuthority("file:edit")) { allow(); }\n'
            'String who = "admin";\n',
            encoding='utf-8',
        )
        if scan_file(good, test_hashes):
            print('FAIL: 负例自证对合规文件误报', file=sys.stderr)
            for h in scan_file(good, test_hashes):
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
