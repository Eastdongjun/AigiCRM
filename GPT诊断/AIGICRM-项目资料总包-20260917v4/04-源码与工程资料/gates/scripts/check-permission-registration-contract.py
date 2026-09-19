#!/usr/bin/env python3
"""权限码登记契约：**代码里在用的权限码，迁移链必须真正登记过且末态启用**。

背景（2026-09-12/13 两次真实事故）：
  · `file:upload/edit/share/folder:manage` 被 V22 停用 ⇒ 小程序文件功能对所有人失效；
  · `incentive:read-stats` 从未登记 ⇒ `/api/incentive/read-stats` 的 `@PreAuthorize` 恒假
    ⇒ 全用户（含 admin）403（用户实测"激励查询 没有权限"）；同批还有 `contact:view`/
    `finance:view`（静默返回空）与 `customer:date:edit`（仅 admin 可改）；
  · 更隐蔽的一类：`V25` 用 `INSERT INTO sys_menu ... SELECT ... FROM sys_menu
    WHERE permission='customer:view'` 作为 parent 来源 —— V1 基线是纯结构（0 INSERT），
    该 SELECT 当时无行 ⇒ **静默插入 0 行**，行从未建立，后续所有鉴权恒假。

判定口径（离线、不连数据库、不碰生产）：
  · 在用码 = 后端 `hasAuthority('X')`/`hasAuthority("X")` + 前端与小程序 `hasPermission('X')`
    （排除测试文件，避免把 `a:add` 这类测试桩当需求）。
  · 按 Flyway 版本序**模拟整条链**：
      常量来源插入（VALUES / 无 FROM 的 SELECT / FROM (SELECT 1)）→ 记为已登记；
      依赖既有行的插入 → 仅当语句内引用的**其它权限码**都已在链上登记时才记为登记（否则不证明）；
      之后的 `UPDATE sys_menu SET status=N` 覆盖状态；`DELETE FROM sys_menu` 视为删除。
  · 末态：未登记 / 末次 status=0 / 状态值无法解析 → FAIL；登记且末态启用 → PASS。

局限：本契约证明"迁移链的最终意图"，不证明某个存量库（如生产）的当前状态；
生产实际状态需另用只读 SQL 核对。用法：`python3 scripts/check-permission-registration-contract.py`
"""

import argparse
import json
import re
import sys
from pathlib import Path

SOURCE_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BACKEND = SOURCE_ROOT / 'aigicrm-backend/src/main/java'
DEFAULT_FRONTEND = SOURCE_ROOT / 'aigicrm-ui/src'
DEFAULT_MINIPROGRAM = SOURCE_ROOT / 'aigicrm-miniprogram'
DEFAULT_MIGRATIONS = SOURCE_ROOT / 'aigicrm-backend/src/main/resources/db/migration'

BACKEND_CODES = [
    re.compile(r"hasAuthority\(\s*'([^']+)'\s*\)"),
    re.compile(r'hasAuthority\(\s*"([^"]+)"\s*\)'),
]
CLIENT_CODES = re.compile(r"hasPermission\(\s*'([^']+)'\s*\)")
VERSION = re.compile(r'^V(\d+(?:\.\d+)*)__')
TARGETS = {
    'insert': re.compile(r'INSERT\s+INTO\s+`?(\w+)`?', re.IGNORECASE),
    'update': re.compile(r'UPDATE\s+`?(\w+)`?', re.IGNORECASE),
    'delete': re.compile(r'DELETE\s+FROM\s+`?(\w+)`?', re.IGNORECASE),
}
STATUS_VALUE = re.compile(r'STATUS`?\s*=\s*(\d+)', re.IGNORECASE)
STATUS_MENTION = re.compile(r'STATUS', re.IGNORECASE)
CONST_SOURCE = re.compile(r'FROM\s*(?:\(\s*SELECT\s+1\s*\)|DUAL\b)', re.IGNORECASE)
ANY_FROM = re.compile(r'\bFROM\b', re.IGNORECASE)
GUARD_PERMISSION = re.compile(r"permission\s*=\s*'([^']+)'", re.IGNORECASE)


def is_test_path(path):
    parts = set(path.parts)
    return bool(parts & {'test', 'tests', '__tests__'}) or path.name.endswith(('.spec.js', '.test.js'))


def collect(directory, patterns, extensions):
    codes = set()
    for path in Path(directory).rglob('*'):
        if path.suffix not in extensions or is_test_path(path):
            continue
        try:
            text = path.read_text(encoding='utf-8', errors='replace')
        except OSError:
            continue
        for pattern in patterns:
            codes.update(pattern.findall(text))
    return codes


def used_codes(backend_dir, frontend_dir, miniprogram_dir):
    codes = collect(backend_dir, BACKEND_CODES, {'.java'})
    codes |= collect(frontend_dir, [CLIENT_CODES], {'.js', '.vue'})
    codes |= collect(miniprogram_dir, [CLIENT_CODES], {'.js'})
    return sorted(codes)


def flyway_order(path):
    match = VERSION.match(path.name)
    return None if not match else tuple(int(part) for part in match.group(1).split('.'))


def migration_files(migrations_dir):
    files = [(flyway_order(p), p) for p in Path(migrations_dir).glob('V*.sql')]
    files = [(order, path) for order, path in files if order is not None]
    return [path for _, path in sorted(files, key=lambda item: item[0])]


def statements(path):
    text = re.sub(r'--[^\n]*', '', path.read_text(encoding='utf-8', errors='replace'))
    return [stmt for stmt in text.split(';') if stmt.strip()]


def strip_exists_subqueries(statement):
    """剥掉 `NOT EXISTS (...)` 子查询（其中的 FROM 不是本语句的数据来源）。"""
    pattern = re.compile(r'\bNOT\s+EXISTS\s*\(', re.IGNORECASE)
    text, out, index = statement, [], 0
    while True:
        match = pattern.search(text, index)
        if not match:
            out.append(text[index:])
            return ''.join(out)
        out.append(text[index:match.start()])
        depth, cursor = 0, match.end() - 1
        while cursor < len(text):
            if text[cursor] == '(':
                depth += 1
            elif text[cursor] == ')':
                depth -= 1
                if depth == 0:
                    break
            cursor += 1
        index = cursor + 1


def insert_is_constant_source(statement):
    body = strip_exists_subqueries(statement)
    if re.search(r'\bVALUES\b', body, re.IGNORECASE):
        return True
    if CONST_SOURCE.search(body):
        return True
    return not ANY_FROM.search(body)


def sys_menu_operation(statement):
    """返回 (op, value)。op ∈ insert / insert-dependent / status / status-unknown / update / delete。"""
    for op, pattern in TARGETS.items():
        match = pattern.search(statement)
        if not match or match.group(1).lower() != 'sys_menu':
            continue
        if op == 'update':
            value = STATUS_VALUE.search(statement)
            if value:
                return 'status', int(value.group(1))
            return ('status-unknown', None) if STATUS_MENTION.search(statement) else ('update', None)
        if op == 'insert':
            return ('insert', None) if insert_is_constant_source(statement) else ('insert-dependent', None)
        return op, None
    return None, None


def simulate(codes, files):
    """按链序模拟"哪些码真的被登记了、末态 status 是多少"。"""
    registered = set()
    status = {}
    evidence = {}

    for path in files:
        for statement in statements(path):
            present = [code for code in codes if f"'{code}'" in statement]
            if not present:
                continue
            op, value = sys_menu_operation(statement)
            if op is None:
                continue
            if op == 'insert':
                for code in present:
                    registered.add(code)
                    status.setdefault(code, 1)
                    evidence[code] = f'{path.name} 常量插入'
            elif op == 'insert-dependent':
                guards = {g for g in GUARD_PERMISSION.findall(statement)} - set(present)
                if guards and guards <= registered:
                    for code in present:
                        registered.add(code)
                        status.setdefault(code, 1)
                        evidence[code] = f'{path.name} 条件插入（前置 {",".join(sorted(guards))} 已登记）'
            elif op == 'status' and value is not None:
                for code in present:
                    if code in registered:
                        status[code] = value
                        evidence[code] = f'{path.name} 置 status={value}'
            elif op == 'status-unknown':
                for code in present:
                    if code in registered:
                        status[code] = 'unknown'
            elif op == 'delete':
                for code in present:
                    registered.discard(code)
                    evidence[code] = f'{path.name} 删除'
    return registered, status, evidence


def verdict(code, registered, status, evidence):
    if code not in registered:
        return 'FAIL', '迁移链无法证明该码被登记（从未出现，或只有"依赖既有行"的插入而前置未登记）'
    current = status.get(code, 1)
    if current == 0:
        return 'FAIL', f'{evidence.get(code, "")} 末态为 status=0 ⇒ 管理员不可见、不可授予'
    if current == 'unknown':
        return 'FAIL', f'{evidence.get(code, "")} 状态值无法解析（fail-closed，不猜）'
    return 'PASS', f'{evidence.get(code, "已登记")} 末态启用'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend-dir', default=str(DEFAULT_BACKEND))
    parser.add_argument('--frontend-dir', default=str(DEFAULT_FRONTEND))
    parser.add_argument('--miniprogram-dir', default=str(DEFAULT_MINIPROGRAM))
    parser.add_argument('--migrations-dir', default=str(DEFAULT_MIGRATIONS))
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args(argv)

    codes = used_codes(args.backend_dir, args.frontend_dir, args.miniprogram_dir)
    if not codes:
        print('FAIL: 未从代码中扫描到任何权限码（fail-closed）', file=sys.stderr)
        return 1
    files = migration_files(args.migrations_dir)
    if not files:
        print(f'FAIL: 迁移目录没有可解析的 V*.sql：{args.migrations_dir}', file=sys.stderr)
        return 1

    registered, status, evidence = simulate(codes, files)
    results = []
    failed = []
    for code in codes:
        state, reason = verdict(code, registered, status, evidence)
        results.append({'code': code, 'status': state, 'reason': reason})
        if state != 'PASS':
            failed.append(results[-1])

    if args.json:
        print(json.dumps({'migrations': [p.name for p in files], 'codes': len(codes), 'results': results},
                         ensure_ascii=False, indent=2))
    else:
        for item in failed:
            print(f"FAIL  {item['code']:26} {item['reason']}")
        print(f"\n迁移链 {len(files)} 个迁移 / 代码在用权限码 {len(codes)} 个 / 未登记或未启用 {len(failed)} 个")

    if failed:
        print('FAIL: 权限码登记契约不成立 —— ' + '、'.join(item['code'] for item in failed), file=sys.stderr)
        return 1
    if not args.json:
        print('PASS: 权限码登记契约成立（代码在用的权限码在迁移链中均已真正登记且末态启用）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
