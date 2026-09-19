#!/usr/bin/env python3
"""小程序权限契约：客户端把关的权限码必须在迁移链里"存在且最终启用"。

背景（2026-09-12）：小程序定位为**真实客户端**，页面用 `hasPermission('xxx')` 把关。
而权限码只有在 `sys_menu` 里存在且 `status=1` 时，管理员才能在角色权限树上看见并授予
（见 `SysMenuService.tree()` 的 `eq(status,1)` 过滤）。历史上 V22 把 5 个文件写权限码
`SET status=0` 且删除其角色绑定，而小程序正在这 4 个码上把关 ⇒ 该功能对所有人（含 admin）
失效，直到 V2026.09.11.3 恢复。本契约把这个"客户端在用、迁移链停用"的回归钉死。

判定口径（离线、不连数据库、不碰生产）：
  · 客户端码集合 = 小程序源码里所有 `hasPermission('xxx')` 的字面量（fail-closed：扫不到即失败）。
  · 迁移链 = `db/migration/V*.sql`，按 Flyway 版本序（数字版本在前，日期版本在后）。
  · 逐条语句解析对 `sys_menu` 的状态操作（语句级，不用字符窗口猜）：
      INSERT INTO sys_menu → 登记；UPDATE sys_menu ... SET status=N → 改状态；DELETE FROM sys_menu → 删除。
  · 每个码取**最后一个状态操作**判定：
      末次 = 删除            → FAIL（码被移除）
      末次 = status=0        → FAIL（停用 ⇒ 管理员不可见、不可授予，客户端把关必然失效）
      末次 = status 无法判定 → FAIL（fail-closed，不猜）
      末次 = 插入 / status=1 → PASS
      全程未出现             → FAIL（客户端在用，链里没有）

注意：本契约只证明"迁移链的最终意图"。生产库实际状态需另用只读查询或临时库重放核对。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SOURCE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MINIPROGRAM = SOURCE_ROOT / 'aigicrm-miniprogram'
DEFAULT_MIGRATIONS = SOURCE_ROOT / 'aigicrm-backend/src/main/resources/db/migration'

PERMISSION_CALL = re.compile(r"hasPermission\(\s*'([^']+)'")
VERSION = re.compile(r'^V(\d+(?:\.\d+)*)__')
STATEMENT_TARGET = {
    'insert': re.compile(r'INSERT\s+INTO\s+`?(\w+)`?', re.IGNORECASE),
    'update': re.compile(r'UPDATE\s+`?(\w+)`?', re.IGNORECASE),
    'delete': re.compile(r'DELETE\s+FROM\s+`?(\w+)`?', re.IGNORECASE),
}
STATUS_VALUE = re.compile(r'STATUS`?\s*=\s*(\d+)', re.IGNORECASE)
STATUS_MENTION = re.compile(r'STATUS', re.IGNORECASE)


def client_codes(miniprogram_dir):
    codes = set()
    for path in sorted(Path(miniprogram_dir).rglob('*.js')):
        if 'node_modules' in path.parts:
            continue
        try:
            text = path.read_text(encoding='utf-8', errors='replace')
        except OSError:
            continue
        codes.update(PERMISSION_CALL.findall(text))
    return sorted(codes)


def flyway_order(path):
    match = VERSION.match(path.name)
    if not match:
        return None
    return tuple(int(part) for part in match.group(1).split('.'))


def migration_files(migrations_dir):
    files = []
    for path in Path(migrations_dir).glob('V*.sql'):
        order = flyway_order(path)
        if order is not None:
            files.append((order, path))
    return [path for _, path in sorted(files, key=lambda item: item[0])]


def statements(path):
    """语句级切分：先剥掉 `--` 行注释，再按分号切。"""
    text = path.read_text(encoding='utf-8', errors='replace')
    text = re.sub(r'--[^\n]*', '', text)
    return [stmt for stmt in text.split(';') if stmt.strip()]


def sys_menu_operation(statement):
    """返回 (op, status) —— op ∈ {insert, update, delete, None}；仅针对 sys_menu 本身。"""
    for op, pattern in STATEMENT_TARGET.items():
        match = pattern.search(statement)
        if not match or match.group(1).lower() != 'sys_menu':
            continue
        if op == 'update':
            value = STATUS_VALUE.search(statement)
            if value:
                return 'status', int(value.group(1))
            if STATUS_MENTION.search(statement):
                return 'status-unknown', None
            return 'update', None
        return op, None
    return None, None


def evaluate(codes, files):
    operations = {}
    for path in files:
        for statement in statements(path):
            for code in codes:
                if f"'{code}'" not in statement:
                    continue
                op, status = sys_menu_operation(statement)
                if op is None:
                    continue
                operations.setdefault(code, []).append((path.name, op, status))
    return operations


def verdict(code, ops):
    if not ops:
        return 'FAIL', '客户端在用，但迁移链里从未登记该权限码'
    name, op, status = ops[-1]
    if op == 'delete':
        return 'FAIL', f'{name} 删除了该码，其后没有重新登记'
    if op == 'status' and status == 0:
        tail = ', '.join(f'{n}:{o}{"" if s is None else f"={s}"}' for n, o, s in ops[-4:])
        return 'FAIL', f'{name} 把该码停用（status=0），管理员将不可见/不可授予；最近状态操作: {tail}'
    if op == 'status-unknown':
        return 'FAIL', f'{name} 修改了该码所在行但状态值无法解析（fail-closed，不猜）'
    if op == 'status' and status == 1:
        return 'PASS', f'{name} 末次置为启用（status=1）'
    return 'PASS', f'{name} 登记该码（未再被停用/删除）'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--miniprogram-dir', default=str(DEFAULT_MINIPROGRAM))
    parser.add_argument('--migrations-dir', default=str(DEFAULT_MIGRATIONS))
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args(argv)

    codes = client_codes(args.miniprogram_dir)
    if not codes:
        print('FAIL: 未扫描到任何 hasPermission(...) 权限码（fail-closed，脚本或路径可能失效）', file=sys.stderr)
        return 1
    files = migration_files(args.migrations_dir)
    if not files:
        print(f'FAIL: 迁移目录没有可解析的 V*.sql：{args.migrations_dir}', file=sys.stderr)
        return 1
    operations = evaluate(codes, files)

    results = []
    failed = []
    for code in codes:
        status, reason = verdict(code, operations.get(code, []))
        results.append({'code': code, 'status': status, 'reason': reason})
        if status != 'PASS':
            failed.append(results[-1])

    if args.json:
        print(json.dumps({'migrations': [p.name for p in files], 'codes': len(codes), 'results': results},
                         ensure_ascii=False, indent=2))
    else:
        for item in results:
            print(f"{'PASS' if item['status'] == 'PASS' else 'FAIL'}  {item['code']:28} {item['reason']}")
        print(f"\n迁移链 {len(files)} 个迁移 / 客户端权限码 {len(codes)} 个 / 失败 {len(failed)} 个")

    if failed:
        print(f"FAIL: 小程序权限契约不成立 —— 下列 {len(failed)} 个码在迁移链末态不可用："
              + '、'.join(item['code'] for item in failed), file=sys.stderr)
        return 1
    print('PASS: 小程序权限契约成立（客户端把关的权限码在迁移链中均存在且末态启用）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
