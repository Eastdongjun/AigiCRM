#!/usr/bin/env python3
"""小程序入口守卫必须等权限回填（BUG-MP-TABBAR-PERM-RACE-20261127 防复发契约）。

判据（只扫 pages/**/*.js 的 onLoad/onShow）：
  · 视为"入口权限守卫"：方法体里同时出现 hasPermission( 与
    showToast(/switchTab(/navigateBack(/redirectTo( 之一（会拦人或踢人）；
  · 合规：同一方法体里出现 whenPermissionsReady(/ensurePermissionsReady(
    /isAuthReady(/whenAuthReady( 之一（等回填/已就绪判定）；
  · 否则 FAIL，输出 file:line。

白名单必须写理由（历史遗留/非入口守卫），防止用一个空清单把契约做废。
--self-test：在临时目录合成"坏/好"两页，坏页必须被检出、好页必须零违规。
退出码：0=PASS；1=有违规；2=无法执行/自检失败。
"""
from __future__ import annotations
import argparse
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MP = ROOT / 'aigicrm-miniprogram'
METHOD = re.compile(r'^  (onLoad|onShow)\(.*\)\s*\{\s*$')
SIBLING = re.compile(r'^  [A-Za-z_][A-Za-z0-9_]*\(')
END = re.compile(r'^\}\)')
READY = ('whenPermissionsReady(', 'ensurePermissionsReady(', 'isAuthReady(', 'whenAuthReady(')
NAV = ('showToast(', 'switchTab(', 'navigateBack(', 'redirectTo(')

# 白名单：相对 pages/ 的路径 -> 理由（必须写，防止无脑豁免）
WHITELIST = {
    # 登录页：没有权限码守卫；其 onShow 的 switchTab 是"已登录回首页"，不判权限
    'login/index.js': '登录页无权限码守卫；switchTab 是已登录回首页',
}

findings: list[str] = []


def scan(root: Path) -> list[str]:
    out: list[str] = []
    for path in sorted((root / 'pages').rglob('*.js')):
        rel = path.relative_to(root / 'pages').as_posix()
        if rel in WHITELIST:
            continue
        lines = path.read_text(encoding='utf-8').splitlines()
        for i, line in enumerate(lines):
            m = METHOD.match(line)
            if not m:
                continue
            name = m.group(1)
            j = i + 1
            while j < len(lines) and not (SIBLING.match(lines[j]) or END.match(lines[j])):
                j += 1
            body = '\n'.join(lines[i + 1:j])
            if 'hasPermission(' not in body:
                continue
            if not any(k in body for k in NAV):
                continue
            if any(k in body for k in READY):
                continue
            out.append(f'{path.relative_to(root).as_posix()}:{i + 1} {name} 入口守卫未等权限回填')
    return out


def self_test() -> int:
    with tempfile.TemporaryDirectory() as td:
        base = Path(td)
        bad = base / 'pages' / 'bad' / 'index.js'
        good = base / 'pages' / 'good' / 'index.js'
        bad.parent.mkdir(parents=True)
        good.parent.mkdir(parents=True)
        bad.write_text(
            "Page({\n  onLoad() {\n    if (!hasPermission('x:view')) {\n"
            "      wx.showToast({ title: 'no' })\n      wx.switchTab({ url: '/pages/home/index' })\n"
            "      return\n    }\n  }\n})\n", encoding='utf-8')
        good.write_text(
            "const { whenPermissionsReady } = require('../../utils/permission-ready')\n"
            "Page({\n  onLoad() {\n    return whenPermissionsReady(() => {\n"
            "      if (!hasPermission('x:view')) {\n        wx.showToast({ title: 'no' })\n"
            "        wx.switchTab({ url: '/pages/home/index' })\n        return\n      }\n    })\n  }\n})\n",
            encoding='utf-8')
        got = scan(base)
        if len(got) != 1 or 'bad/index.js' not in got[0]:
            print('FAIL: self-test 未按预期只检出坏页: %r' % got, file=sys.stderr)
            return 1
        if any('good/index.js' in item for item in got):
            print('FAIL: self-test 误报好页: %r' % got, file=sys.stderr)
            return 1
    print('PASS: self-test（坏页检出 / 好页零违规）')
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--self-test', action='store_true')
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    if not MP.is_dir():
        print(f'BLOCKED: 小程序目录不存在: {MP}', file=sys.stderr)
        return 2
    hits = scan(MP)
    if hits:
        print(f'FAIL: 小程序入口守卫未等权限回填（{len(hits)} 处）:')
        for h in hits:
            print('  -', h)
        return 1
    print(f'PASS: 小程序入口守卫均等权限回填（扫描 pages/**/*.js，白名单 {len(WHITELIST)} 条）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
