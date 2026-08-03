#!/usr/bin/env python3
"""Cross-check @PreAuthorize permission strings in controllers against sys_menu rows.

Catches the bug class:
  - @PreAuthorize("hasAuthority('approval:edit')") where no sys_menu row grants
    'approval:edit' -> endpoint returns 403 for every user, including admin.

Usage:
  python3 check_permission_strings.py --backend <src/main/java> --sql <dump.sql> [--sql ...]

Exit code 1 when required permissions are missing from the menu definitions.
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
    """Parse permission column (7th field) of every sys_menu row in INSERT VALUES."""
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
    return available


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
    ap.add_argument("--sql", required=True, action="append", type=Path)
    args = ap.parse_args()

    required = extract_required(args.backend)
    available = extract_menu_permissions(args.sql)
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
