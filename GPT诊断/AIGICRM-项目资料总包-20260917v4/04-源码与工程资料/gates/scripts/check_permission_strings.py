#!/usr/bin/env python3
"""Cross-check @PreAuthorize permission strings in controllers against sys_menu rows.

Catches the bug class:
  - @PreAuthorize("hasAuthority('approval:edit')") where no sys_menu row grants
    'approval:edit' -> endpoint returns 403 for every user, including admin.

Usage:
  python3 check_permission_strings.py --backend <src/main/java> [--sql <dump.sql>] [--migrations-dir <dir>]

Exit code 1 when required permissions are missing from the menu definitions.
Exit code 2 when a requested SQL evidence file is unavailable.
"""

import argparse
import re
import sys
from pathlib import Path


AUTH_RE = re.compile(
    r'@PreAuthorize\s*\(\s*"has(?:Any)?Authority\(([^)]*)\)"\s*\)'
)


def extract_required(java_dir: Path):
    required = {}
    for path in java_dir.rglob("*.java"):
        text = path.read_text(errors="ignore")
        for m in AUTH_RE.finditer(text):
            for token in re.findall(r"'([^']+)'", m.group(1)):
                required.setdefault(token, []).append(str(path.relative_to(java_dir)))
    return required


def extract_menu_permissions(sql_files):
    """Parse sys_menu permission definitions from INSERT VALUES and INSERT SELECT seeds."""
    available = set()
    row_re = re.compile(r"\(([^)]*)\)")
    for sf in sql_files:
        text = sf.read_text(errors="ignore")
        # capture the INSERT INTO `sys_menu` ... VALUES ( ... ); block
        for im in re.finditer(
            r"INSERT\s+INTO\s+`sys_menu`\s+[^;]*?VALUES\s*(.*?);", text, re.S | re.I
        ):
            for rm in row_re.finditer(im.group(1)):
                fields = split_top_level(rm.group(1))
                if len(fields) >= 7:
                    perm = fields[6].strip().strip("'").strip('"')
                    if perm:
                        available.add(perm)
        # capture INSERT INTO sys_menu (..., permission, ...) SELECT ..., 'perm', ...;
        for im in re.finditer(
            r"INSERT\s+INTO\s+`?sys_menu`?\s*\((.*?)\)\s*SELECT\s+(.*?);",
            text,
            re.S | re.I,
        ):
            columns = [c.strip().strip("`").lower() for c in split_top_level(im.group(1))]
            if "permission" not in columns:
                continue
            permission_index = columns.index("permission")
            select_fields = split_top_level(im.group(2))
            if len(select_fields) <= permission_index:
                continue
            perm = select_fields[permission_index].strip().strip("'").strip('"')
            if perm and re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]*(?::[A-Za-z0-9_-]+)+", perm):
                available.add(perm)
    return available


def has_menu_seed(sql_files):
    """Return whether the supplied evidence contains full sys_menu INSERT seed data."""
    seed_re = re.compile(r"INSERT\s+INTO\s+`?sys_menu`?\s+.*?VALUES\s+", re.S | re.I)
    return any(seed_re.search(path.read_text(errors="ignore")) for path in sql_files)


def split_top_level(s):
    """Split on commas that are not inside quotes."""
    parts, buf, in_q = [], [], False
    for ch in s:
        if ch == "'":
            in_q = not in_q
        if ch == "," and not in_q:
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    if buf:
        parts.append("".join(buf))
    return parts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--backend", required=True, type=Path)
    ap.add_argument("--sql", action="append", type=Path, default=[])
    ap.add_argument("--migrations-dir", type=Path)
    args = ap.parse_args()

    required = extract_required(args.backend)
    sql_files = list(args.sql)
    if args.migrations_dir:
        sql_files.extend(sorted(args.migrations_dir.glob("*.sql")))
    if not sql_files:
        ap.error("provide --sql and/or --migrations-dir")
    missing_files = [str(path) for path in sql_files if not path.is_file()]
    if missing_files:
        print("INCOMPLETE: permission evidence file(s) unavailable:", file=sys.stderr)
        for path in missing_files:
            print(f"  {path}", file=sys.stderr)
        print("Provide the database dump or a valid migrations directory before treating this check as complete.", file=sys.stderr)
        sys.exit(2)
    if not has_menu_seed(sql_files):
        print("INCOMPLETE: supplied SQL evidence has no sys_menu seed rows; role/menu drift cannot be proven.", file=sys.stderr)
        print("Provide the database dump or a complete sys_menu seed export before treating this check as complete.", file=sys.stderr)
        sys.exit(2)
    available = extract_menu_permissions(sql_files)
    missing = {p: locs for p, locs in required.items() if p not in available}

    print(f"@PreAuthorize 用到的权限 {len(required)} 个, sys_menu 中存在的权限 {len(available)} 个")
    if missing:
        print(f"\nERROR - 权限字符串在菜单中不存在, 相关端点将 403 ({len(missing)}):")
        for p, locs in sorted(missing.items()):
            print(f"  {p}")
            for l in locs[:4]:
                print(f"      {l}")
        sys.exit(1)
    else:
        print("\nOK: 所有权限字符串均有菜单定义")
        sys.exit(0)


if __name__ == "__main__":
    main()
